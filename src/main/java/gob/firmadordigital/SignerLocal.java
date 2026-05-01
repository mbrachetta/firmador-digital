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
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.util.encoders.Base64;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/* Implementa el firmador local de un documento, tomando el Certificado
 * del repo local o dispositivo criptografico */
public class SignerLocal extends Signer {

    private Certificate[] chain;
    private PrivateKey key;
    private ImageData img;

    /* Constantes */
    private static final String CAMPO_FIRMA_BASE = "MIB Firma ";
    private static final String DIGEST_ALGORITHM = DigestAlgorithms.SHA256;
    private static final String SIGN_PROVIDER = "SunMSCAPI";

    public SignerLocal(Certificate[] chain, PrivateKey key, ImageData img_estampaFirma, ThreadFirma hilofirma) {
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

            byte[] pdfFirmadoBytes = addLtv(firmaDetached(documentoAFirmar));

            String docfirmadoBase64 = Base64.toBase64String(pdfFirmadoBytes);

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
                        PDFirma.resourceBundle.getString("ERROR_016"), documentoAFirmar.getMetadata(), false, 16);
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
                        PDFirma.resourceBundle.getString("ERROR_032"), documentoAFirmar.getMetadata(), false, 32);
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
                        PDFirma.resourceBundle.getString("ERROR_034"), documentoAFirmar.getMetadata(), false, 34);
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
                        PDFirma.resourceBundle.getString("ERROR_036"), documentoAFirmar.getMetadata(), false, 36);
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
                        PDFirma.resourceBundle.getString("ERROR_037"), documentoAFirmar.getMetadata(), false, 37);
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

    private byte[] firmaDetached(DocumentoAFirmar documentoAFirmar)
            throws IOException, GeneralSecurityException, ParametroIncorrectoException {

        byte[] pdfBytes = Base64.decode(documentoAFirmar.getDocumento());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));

        PdfSigner signer = new PdfSigner(
                reader,
                baos,
                new StampingProperties().useAppendMode()
        );

        gestionarApariencia(signer, pdfBytes, documentoAFirmar);

        IExternalDigest digest = new BouncyCastleDigest();
        IExternalSignature externalSignature = new PrivateKeySignature(
                key,
                DIGEST_ALGORITHM,
                SIGN_PROVIDER
        );

        ITSAClient tsaClient = null;
        String tsaUrl = PDFirma.parametros.getUrl_TSA();
        if (tsaUrl != null && !tsaUrl.isBlank()) {
            String tsaUser = PDFirma.parametros.getUser_TSA();
            String tsaPass = PDFirma.parametros.getPassword_TSA();

            tsaClient = new TSAClientBouncyCastle(
                    tsaUrl,
                    tsaUser,
                    tsaPass
            );
        }

        signer.signDetached(
                digest,
                externalSignature,
                chain,
                null,
                null,
                tsaClient,
                0,
                PdfSigner.CryptoStandard.CADES
        );

        return baos.toByteArray();
    }

     private void gestionarApariencia(
            PdfSigner signer,
            byte[] pdfBytes,
            DocumentoAFirmar documentoAFirmar
    ) throws IOException {

        AparienciaFirma apariencia = calculaUbicacion_y_NombreFirma(pdfBytes);

        PdfSignatureAppearance sap = signer.getSignatureAppearance();
        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        sap.setLayer2Font(font);
        sap.setLayer2FontSize(9);
        sap.setPageRect(apariencia.rectangulo());
        sap.setPageNumber(apariencia.nroPagina());
        sap.setReason(documentoAFirmar.getRazon());
        sap.setLocation(documentoAFirmar.getLocalidad());
        sap.setCertificate(chain[0]);
        if (img != null) sap.setImage(img);

        signer.setFieldName(apariencia.nombreFirma());
    }

    /* Se define un registro para llevar los datos de la apariencia de la firma */
    private record AparienciaFirma(Rectangle rectangulo, int nroPagina, String nombreFirma) {
    }

    private AparienciaFirma calculaUbicacion_y_NombreFirma(byte[] pdfBytes) throws IOException {

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

    private byte[] addLtv(byte[] pdfBytes) throws GeneralSecurityException, IOException {

        ByteArrayOutputStream baosFirmado = new ByteArrayOutputStream();

        try (PdfDocument pdfDoc = new PdfDocument(
                new PdfReader(new ByteArrayInputStream(pdfBytes)),
                new PdfWriter(baosFirmado),
                new StampingProperties().useAppendMode()
        )) {
            LtvVerification ltvVerification = new LtvVerification(pdfDoc);
            SignatureUtil signatureUtil = new SignatureUtil(pdfDoc);

            List<String> names = signatureUtil.getSignatureNames();
            if (names == null || names.isEmpty()) {
                throw new IllegalStateException("El PDF no contiene firmas.");
            }

            for (String name : names) {
                PdfPKCS7 pkcs7 = signatureUtil.readSignatureData(name);

                Certificate[] certs = pkcs7.getCertificates();

                X509Certificate signerCert = identifySigningCertificate(certs);
                X509Certificate issuerCert = findIssuer(signerCert, certs);

                if (signerCert == null || issuerCert == null) {
                    throw new IllegalStateException("No se pudo identificar firmante o issuer.");
                }

                byte[] ocspFirmante = CustomManualOcspClient.getOcspResponseBytes(
                        signerCert,
                        issuerCert
                );

                if (ocspFirmante == null || ocspFirmante.length == 0) {
                    throw new IllegalStateException("No se obtuvo respuesta OCSP válida del firmante.");
                }

                List<byte[]> ocspList = new ArrayList<>();
                ocspList.add(ocspFirmante);

                List<byte[]> crlList = new ArrayList<>();

                byte[] crlIntermedia = descargarCrlIntermediaSolamente(issuerCert);
                if (crlIntermedia != null && crlIntermedia.length > 0) {
                    crlList.add(crlIntermedia);
                }

                List<byte[]> certList = new ArrayList<>();
                for (Certificate c : certs) {
                    certList.add(c.getEncoded());
                }

                boolean added = ltvVerification.addVerification(
                        name,
                        ocspList,
                        crlList,
                        certList
                );

                if (!added) {
                    throw new IllegalStateException("No se pudo agregar la verificación LTV para la firma: " + name);
                }
            }
            ltvVerification.merge();
        }
        return baosFirmado.toByteArray();
    }

    private static X509Certificate identifySigningCertificate(Certificate[] certs) {
        if (certs == null || certs.length == 0) {
            return null;
        }

        List<X509Certificate> candidates = new ArrayList<>();

        for (Certificate c : certs) {
            if (!(c instanceof X509Certificate)) {
                continue;
            }

            X509Certificate cert = (X509Certificate) c;

            boolean isEndEntity = cert.getBasicConstraints() < 0;
            boolean selfSigned = cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal());
            boolean signatureUsage = hasDigitalSignatureUsage(cert);

            if (isEndEntity && !selfSigned && signatureUsage) {
                candidates.add(cert);
            }
        }

        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static boolean hasDigitalSignatureUsage(X509Certificate cert) {
        boolean[] ku = cert.getKeyUsage();

        if (ku == null) {
            return true;
        }

        boolean digitalSignature = ku.length > 0 && ku[0];
        boolean nonRepudiation = ku.length > 1 && ku[1];

        return digitalSignature || nonRepudiation;
    }

    private static X509Certificate findIssuer(
            X509Certificate target,
            Certificate[] certs
    ) {
        if (target == null || certs == null) {
            return null;
        }

        for (Certificate c : certs) {
            if (!(c instanceof X509Certificate)) {
                continue;
            }

            X509Certificate candidate = (X509Certificate) c;

            if (target.getIssuerX500Principal().equals(candidate.getSubjectX500Principal())
                    && !target.getSubjectX500Principal().equals(candidate.getSubjectX500Principal())) {
                return candidate;
            }
        }

        return null;
    }

    private static byte[] descargarCrlIntermediaSolamente(X509Certificate intermediaCert) {
        try {
            List<String> crlUrls = getCrlUrls(intermediaCert);

            for (String crlUrl : crlUrls) {
                byte[] crlBytes = downloadUrl(crlUrl);

                if (crlBytes != null && crlBytes.length > 0) {
                    return crlBytes;
                }
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] downloadUrl(String url) throws Exception {
        HttpURLConnection conn = null;

        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();

            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Java-CRL-Client/1.0");

            if (conn.getResponseCode() != 200) {
                return null;
            }

            try (InputStream is = conn.getInputStream()) {
                return is.readAllBytes();
            }

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static List<String> getCrlUrls(X509Certificate cert) throws Exception {
        List<String> urls = new ArrayList<>();

        byte[] extVal = cert.getExtensionValue(Extension.cRLDistributionPoints.getId());

        if (extVal == null) {
            return urls;
        }

        ASN1OctetString oct = (ASN1OctetString) ASN1Primitive.fromByteArray(extVal);

        CRLDistPoint distPoint =
                CRLDistPoint.getInstance(
                        ASN1Primitive.fromByteArray(oct.getOctets())
                );

        for (DistributionPoint dp : distPoint.getDistributionPoints()) {
            DistributionPointName dpn = dp.getDistributionPoint();

            if (dpn != null && dpn.getType() == DistributionPointName.FULL_NAME) {
                GeneralNames gns = GeneralNames.getInstance(dpn.getName());

                for (GeneralName gn : gns.getNames()) {
                    if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                        urls.add(gn.getName().toString());
                    }
                }
            }
        }

        return urls;
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
                        PDFirma.resourceBundle.getString("ERROR_037"), documentoAFirmar.getMetadata(), false, 37);
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