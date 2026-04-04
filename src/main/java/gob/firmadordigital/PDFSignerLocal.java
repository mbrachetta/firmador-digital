package gob.firmadordigital;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.signatures.*;
import gob.firmadordigital.excepciones.ParametroIncorrectoException;
import gob.firmadordigital.model.DocumentoAFirmar;
import gob.firmadordigital.model.DocumentoFirmado;
import org.bouncycastle.util.encoders.Base64;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.List;

/* Implementa el firmador local de un documento, tomando el Certificado
 * del repo local o dispositivo criptografico */
public class PDFSignerLocal extends PDFSigner {

    private Certificate[] chain;
    private PrivateKey key;
    private ImageData img;

    /* Constantes */
    private static final String CAMPO_FIRMA_BASE = "MIB Firma ";
    private static final String DIGEST_ALGORITHM = DigestAlgorithms.SHA256;
    private static final String SIGN_PROVIDER = "SunMSCAPI";
    private static final int EXTRA_BUFFER_FIRMA = 16_384;
    private static final int EXTRA_BUFFER_LTV = 8_192;

    public PDFSignerLocal(Certificate[] chain, PrivateKey key, ImageData img_estampaFirma, ThreadFirma hilofirma) {
        super(hilofirma);
        this.chain = chain;
        this.key = key;
        this.img = img_estampaFirma;
    }


    @Override
    public DocumentoFirmado firmar(DocumentoAFirmar documentoAFirmar) {
        if (documentoAFirmar.getTipodocumento().equalsIgnoreCase("pdf")) {
            return firmarPDF(documentoAFirmar);
        } else {
            return firmarXML(documentoAFirmar);
        }
    }

    public DocumentoFirmado firmarPDF(DocumentoAFirmar documentoAFirmar) {

        DocumentoFirmado documentoFirmado = null;

        try {

            byte[] pdfBytes = Base64.decode(documentoAFirmar.getDocumento());

            AparienciaFirma apariencia = calcularAparienciaFirma(pdfBytes);

            IOcspClient ocspClient = new OcspClientBouncyCastle(null);

            byte[] pdfFirmadoBytes = firmarDetached(documentoAFirmar, pdfBytes, apariencia, ocspClient);
            pdfBytes = null;

            byte[] pdfFinalBytes = agregarLtv(pdfFirmadoBytes, ocspClient);
            pdfFirmadoBytes = null;

            String docfirmadoBase64 = Base64.toBase64String(pdfFinalBytes);
            pdfFinalBytes = null;

            documentoFirmado = new DocumentoFirmado(
                    documentoAFirmar.getNdoc(),
                    documentoAFirmar.getCantdoc(),
                    documentoAFirmar.getTipodocumento(),
                    docfirmadoBase64,
                    documentoAFirmar.getMetadata(),
                    true,
                    0
            );
        } catch (ParametroIncorrectoException e) {
            synchronized (this) {
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_016"), documentoAFirmar.getMetadata(), false, Integer.parseInt("016"));
                PDFirma.loguearExcepcion(e, "ERROR_016");
                JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_016"),
                        "ERROR", JOptionPane.ERROR_MESSAGE);
                hilofirma.cancelarProcesoFirma();
                hilofirma.desbloquear();
            }
        } catch (NoSuchAlgorithmException e) {
            synchronized (this) {
                hilofirma.bloquear();
                /* Por el tipo de error se cancela el proceso de firma
                 * sin dar opcion al usuario de decidir */
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_032"), documentoAFirmar.getMetadata(), false, Integer.parseInt("032"));
                PDFirma.loguearExcepcion(e, "ERROR_032");
                JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_032"),
                        "ERROR", JOptionPane.ERROR_MESSAGE);
                hilofirma.cancelarProcesoFirma();
                hilofirma.desbloquear();
            }
        } catch (GeneralSecurityException e) {
            synchronized (this) {
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_034"), documentoAFirmar.getMetadata(), false, Integer.parseInt("034"));
                PDFirma.loguearExcepcion(e, "ERROR_034");
                if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                        PDFirma.resourceBundle.getString("ERROR_034") + "\n" +
                                PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                        "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != 0) {
                    /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                    hilofirma.cancelarProcesoFirma();
                }
                hilofirma.desbloquear();
            }
        } catch (IOException e) {
            synchronized (this) {
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_036"), documentoAFirmar.getMetadata(), false, Integer.parseInt("036"));
                PDFirma.loguearExcepcion(e, "ERROR_036");
                if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                        PDFirma.resourceBundle.getString("ERROR_036") + "\n" +
                                PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                        "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != 0) {
                    /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                    hilofirma.cancelarProcesoFirma();

                }
                hilofirma.desbloquear();
            }
        } catch (Exception e) {
            synchronized (this) {
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_037"), documentoAFirmar.getMetadata(), false, Integer.parseInt("037"));
                PDFirma.loguearExcepcion(e, "ERROR_037");
                if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                        PDFirma.resourceBundle.getString("ERROR_037") + "\n" +
                                PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                        "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != 0) {
                    /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                    hilofirma.cancelarProcesoFirma();
                }
                hilofirma.desbloquear();
            }
        }
        documentoAFirmar.setDocumento(null);
        return documentoFirmado;
    }

    /* Se define un registro para llevar los datos de la apariencia de la firma */
    private record AparienciaFirma(Rectangle rectangulo, int nroPagina, String nombreFirma) {
    }

    private AparienciaFirma calcularAparienciaFirma(byte[] pdfBytes) throws IOException {

        int nroPagina = 1;
        Rectangle rectangulo = new Rectangle(0, 0, 0, 0);
        String nombreFirma = CAMPO_FIRMA_BASE + "1";

        try (PdfDocument tmpDoc = new PdfDocument(
                new PdfReader(new ByteArrayInputStream(pdfBytes)))) {

            SignatureUtil sigUtil = new SignatureUtil(tmpDoc);
            List<String> firmasPrevias = sigUtil.getSignatureNames();

            if (!firmasPrevias.isEmpty()) {
                int nroUltimaFirma = firmasPrevias.size();
                String nombreUltimaFirma = firmasPrevias.get(nroUltimaFirma - 1);

                PdfAcroForm form = PdfAcroForm.getAcroForm(tmpDoc, true);
                PdfFormField field = form.getField(nombreUltimaFirma);

                if (field != null && !field.getWidgets().isEmpty()) {
                    PdfWidgetAnnotation widget = field.getWidgets().get(0);
                    PdfArray rect = widget.getRectangle();

                    float lly = rect.getAsNumber(1).floatValue();
                    float urx = rect.getAsNumber(2).floatValue();
                    float ury = rect.getAsNumber(3).floatValue();

                    float altoFirmaAnterior = ury - lly;
                    float anchoNuevaFirma = img.getWidth();
                    float altoNuevaFirma = img.getHeight();

                    if (urx < 400) {
                        rectangulo = new Rectangle(
                                urx + 5,
                                lly,
                                anchoNuevaFirma,
                                altoFirmaAnterior
                        );
                    } else {
                        rectangulo = new Rectangle(
                                5,
                                ury + 10,
                                anchoNuevaFirma,
                                altoNuevaFirma
                        );
                    }

                    nroPagina = tmpDoc.getPageNumber(widget.getPage());
                    nombreFirma = CAMPO_FIRMA_BASE + (nroUltimaFirma + 1);
                }
            } else {
                rectangulo = new Rectangle(
                        5,
                        25,
                        img.getWidth(),
                        img.getHeight()
                );
                nroPagina = tmpDoc.getNumberOfPages();
            }
        }

        return new AparienciaFirma(rectangulo, nroPagina, nombreFirma);
    }

    private byte[] firmarDetached( DocumentoAFirmar documentoAFirmar, byte[] pdfBytes, AparienciaFirma apariencia, IOcspClient ocspClient) throws GeneralSecurityException, IOException {

       ByteArrayOutputStream baos = new ByteArrayOutputStream(pdfBytes.length + EXTRA_BUFFER_FIRMA);

        PdfReader signerReader = new PdfReader(new ByteArrayInputStream(pdfBytes));
        PdfSigner signer = new PdfSigner(
                signerReader,
                baos,
                new StampingProperties().useAppendMode()
        );

        PdfSignatureAppearance sap = signer.getSignatureAppearance();
        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        sap.setLayer2Font(font);
        sap.setLayer2FontSize(9);
        sap.setPageRect(apariencia.rectangulo());
        sap.setPageNumber(apariencia.nroPagina());
        sap.setReason(documentoAFirmar.getRazon());
        sap.setLocation(documentoAFirmar.getLocalidad());
        sap.setCertificate(chain[0]);
        if (img != null)  sap.setImage(img);

        signer.setFieldName(apariencia.nombreFirma());

        IExternalDigest digest = new BouncyCastleDigest();
        IExternalSignature signature = new PrivateKeySignature(
                key,
                DIGEST_ALGORITHM,
                SIGN_PROVIDER
        );

        List<ICrlClient> crlList = null;

        ITSAClient tsaClient = null;
        String tsaUrl = PDFirma.parametros.getUrl_TSA();
        if (tsaUrl != null && !tsaUrl.isBlank()) {
            String tsaUser = PDFirma.parametros.getUser_TSA();
            String tsaPass = PDFirma.parametros.getPassword_TSA();

            tsaClient = new TSAClientBouncyCastle(
                    tsaUrl,
                    tsaUser,
                    tsaPass,
                    EXTRA_BUFFER_LTV,
                    DIGEST_ALGORITHM
            );
        }

        signer.signDetached(
                digest,
                signature,
                chain,
                crlList,
                ocspClient,
                tsaClient,
                0,
                PdfSigner.CryptoStandard.CADES
        );

        return baos.toByteArray();
    }

    private byte[] agregarLtv(byte[] pdfFirmadoBytes, IOcspClient ocspClient) throws GeneralSecurityException, IOException {

        ByteArrayOutputStream baosFirmado = new ByteArrayOutputStream(pdfFirmadoBytes.length + EXTRA_BUFFER_LTV);

        ICrlClient crlClient = null;

        try (PdfDocument pdfDoc = new PdfDocument(
                new PdfReader(new ByteArrayInputStream(pdfFirmadoBytes)),
                new PdfWriter(baosFirmado),
                new StampingProperties().useAppendMode()
        )) {
            LtvVerification ltvVerification = new LtvVerification(pdfDoc);
            SignatureUtil signatureUtil = new SignatureUtil(pdfDoc);

            for (String sigName : signatureUtil.getSignatureNames()) {
                boolean ok = ltvVerification.addVerification(
                        sigName,
                        ocspClient,
                        crlClient,
                        LtvVerification.CertificateOption.WHOLE_CHAIN,
                        LtvVerification.Level.OCSP,
                        LtvVerification.CertificateInclusion.YES
                );

                if (!ok) {
                    PDFirma.loguearExcepcion(
                            new Exception(PDFirma.resourceBundle.getString("ERROR_033") + " " + sigName),
                            "ERROR_033"
                    );
                }
            }
            ltvVerification.merge();
        }

        return baosFirmado.toByteArray();
    }

    public DocumentoFirmado firmarXML(DocumentoAFirmar documentoAFirmar) {

        DocumentoFirmado documentoFirmado = null;

        try {
            System.out.println("entre a firmar el documento " + documentoAFirmar.getNdoc() + " del tipo " + documentoAFirmar.getTipodocumento());

            /* Se convierte el documento firmado a String
            java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
            XMLUtils.outputDOM(doc, os, true);
            String docfirmado = new String(os.toByteArray(), StandardCharsets.UTF_8);            
            documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),docfirmado,documentoAFirmar.getMetadata(),true,0);        */
        } catch (Exception e) {
            e.printStackTrace();
            synchronized (this) {
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), "XML",
                        PDFirma.resourceBundle.getString("ERROR_037"), documentoAFirmar.getMetadata(), false, Integer.parseInt("037"));
                PDFirma.loguearExcepcion(e, "ERROR_037");
                if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                        PDFirma.resourceBundle.getString("ERROR_037") + "\n" +
                                PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                        "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != 0) {
                    // El usuario decidio NO continuar el proceso de firma, cancelo el proceso
                    hilofirma.cancelarProcesoFirma();
                }
                hilofirma.desbloquear();
            }
        }
        return documentoFirmado;
    }
}
