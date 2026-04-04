package gob.firmadordigital;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.exceptions.PdfException;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.layout.element.Image;
import com.itextpdf.signatures.*;
import gob.firmadordigital.excepciones.ParametroIncorrectoException;
import gob.firmadordigital.model.DocumentoAFirmar;
import gob.firmadordigital.model.DocumentoFirmado;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import java.util.List;
import javax.swing.JOptionPane;

import org.bouncycastle.util.encoders.Base64;

/* Implementa el firmador local de un documento, tomando el Certificado
 * del repo local o dispositivo criptografico */
public class PDFSignerLocal extends PDFSigner {
    
    private Certificate[] chain;
    private PrivateKey key;
    private Image img;
    
    public PDFSignerLocal(Certificate[] chain,PrivateKey key,Image img_estampaFirma, ThreadFirma hilofirma) {
        
        super(hilofirma);
        this.chain = chain;
        this.key = key;
        this.img = img_estampaFirma;
    }


    @Override
    public DocumentoFirmado firmar(DocumentoAFirmar documentoAFirmar) {
        if (documentoAFirmar.getTipodocumento().equalsIgnoreCase("pdf")){
            return firmarPDF(documentoAFirmar);
        }
        else {
            return firmarXML(documentoAFirmar);
        }
    }
    
    public DocumentoFirmado firmarPDF(DocumentoAFirmar documentoAFirmar){
        DocumentoFirmado documentoFirmado = null;
       
        try {
            System.out.println("entre a firmar el documento " + documentoAFirmar.getNdoc() + " del tipo " + documentoAFirmar.getTipodocumento());
            byte [] bytearchivo_aFirmar = Base64.decode(documentoAFirmar.getDocumento());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            /* Se obtienen firmas previas antes de crear el PdfSigner */
            PdfDocument tmpDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(bytearchivo_aFirmar)));
            SignatureUtil sigUtil = new SignatureUtil(tmpDoc);
            List<String> firmasPrevias = sigUtil.getSignatureNames();

            /* Se obtiene el nombre de la última firma existente */
            String ultima_firma = null;
            if (!firmasPrevias.isEmpty()) {
                ultima_firma = firmasPrevias.get(firmasPrevias.size() - 1);
            }
            int nro_signature = firmasPrevias.size() + 1;

            /* Ahora se crea el PdfReader y PdfSigner con el byte array original */
            PdfReader pdfReader = new PdfReader(new ByteArrayInputStream(bytearchivo_aFirmar));
            PdfSigner signer = new PdfSigner(pdfReader,
                                             baos,
                                             new StampingProperties().useAppendMode());

            /* Se prepara la apariencia de firma (reason, location, imagen y texto) */
            PdfSignatureAppearance sap = signer.getSignatureAppearance();
            sap.setReason(documentoAFirmar.getRazon());
            sap.setLocation(documentoAFirmar.getLocalidad());

            Rectangle rectPage = null;

            if (img != null !digitallySignedBy.isEmpty()){
                // Intentamos obtener el rectángulo de la última firma usando campos AcroForm (iText7)
                if (ultima_firma != null) {
                    PdfAcroForm form = PdfAcroForm.getAcroForm(tmpDoc, false);
                    if (form != null && form.getField(ultima_firma) != null){
                        PdfFormField field = form.getField(ultima_firma);
                        if (!field.getWidgets().isEmpty()){
                            PdfWidgetAnnotation w = field.getWidgets().get(0);
                            PdfArray rectArr = w.getRectangle();
                            if (rectArr != null) {
                                Rectangle r = rectArr.toRectangle();
                                // Calculamos rectangulo próximo a la derecha o en nueva línea como en la versión anterior
                                if (r.getRight() < 400)
                                    rectPage = new Rectangle(r.getRight()+5, r.getBottom(), img.getImageScaledWidth(), img.getImageScaledHeight());
                                else
                                    rectPage = new Rectangle(5, r.getTop()+10, img.getImageScaledWidth(), img.getImageScaledHeight()+10);
                            }
                        }
                    }
                }
                if (rectPage == null){
                    // Posición por defecto: izquierda, pie de página
                    rectPage = new Rectangle(5, 25, img.getImageScaledWidth(), img.getImageScaledHeight()+25);
                }

                /* Se asigna rectángulo, página y texto del sello */
                sap.setPageRect(rectPage);
                sap.setPageNumber(tmpDoc.getNumberOfPages());
                sap.setRenderingMode(PdfSignatureAppearance.RenderingMode.DESCRIPTION);
                sap.setLayer2Text(digitallySignedBy);
            } else {
                // Firma invisible: rectángulo 0 y página 1
                sap.setPageRect(new com.itextpdf.kernel.geom.Rectangle(0,0,0,0));
                sap.setPageNumber(1);
            }

            // Cerramos el tmpDoc de lectura
            tmpDoc.close();

            /* Se prepara el objeto de firma (digest + private key signature) */
            IExternalSignature signature = new PrivateKeySignature(key, DigestAlgorithms.SHA256, null);
            IExternalDigest digest = new BouncyCastleDigest();
            OCSPVerifier ocspVerifier = new OCSPVerifier(null, null);
            IOcspClient ocspClient = new OcspClientBouncyCastle(ocspVerifier);
            ITSAClient tsaClient = null;
            if (!PDFirma.parametros.getUrl_TSA().isEmpty()){
                String tsaUrl = PDFirma.parametros.getUrl_TSA();
                String tsaUser = PDFirma.parametros.getUser_TSA();
                String tsaPass = PDFirma.parametros.getPassword_TSA();
                tsaClient = new TSAClientBouncyCastle(tsaUrl, tsaUser, tsaPass);
            }

            /* Se firma el documento B-T */
            signer.signDetached(digest,signature,chain,null,ocspClient,tsaClient,0,PdfSigner.CryptoStandard.CADES);

            /* Se agrega B-LT (Se crea contenedor DSS en PDF) */
            byte[] current = baos.toByteArray();
            baos.reset();
            PdfDocument pdfDoc = new PdfDocument(
                    new PdfReader(new ByteArrayInputStream(current)),
                    new PdfWriter(baos),
                    new StampingProperties().useAppendMode()
            );

            LtvVerification ltv = new LtvVerification(pdfDoc);
            List<String> signatureNames = new SignatureUtil(pdfDoc).getSignatureNames();

            /* Se valida que exista al menos una firma antes de agregar verificación LTV */
            if (!signatureNames.isEmpty()) {
                String sigName = signatureNames.get(0);
                ltv.addVerification(
                        sigName,
                        ocspClient,
                        null,
                        LtvVerification.CertificateOption.WHOLE_CHAIN,
                        LtvVerification.Level.OCSP,
                        LtvVerification.CertificateInclusion.YES
                );
                ltv.merge();
            }
            pdfDoc.close();

            /* Si es última firma, se añade LTA (Document Timestamp) */
            if (documentoAFirmar.getUltima_firma().equalsIgnoreCase("si") ||
                documentoAFirmar.getUltima_firma().equalsIgnoreCase("yes") ||
                documentoAFirmar.getUltima_firma().equalsIgnoreCase("true")){
                if (tsaClient == null){
                    throw new ParametroIncorrectoException("ERROR_035");
                }
                else {
                    System.out.println("Se añadirá sello de tiempo al documento firmado");
                    current = baos.toByteArray();
                    baos.reset();
                    PdfSigner tsSigner = new PdfSigner(
                            new PdfReader(new ByteArrayInputStream(current)),
                            baos,
                            new StampingProperties().useAppendMode()
                    );
                    tsSigner.timestamp(tsaClient, "DocTimeStamp");
                }
            }

            /* Se carga el documento firmado en la cola de documentos firmados para que el hilo
             * correspondiente se encargue de tomarlo de allí y devolverlo a la url de callback
             * definida por el organismo */
            System.out.println("Documento firmado correctamente, se procede a convertirlo a Base64 para su envío");
            String docfirmado_base64= Base64.toBase64String(baos.toByteArray());
            /* Se Guarda localmente el documento firmado - descomentar con fines de prueba
            baos.writeTo(new FileOutputStream(PDFirma.fdefault_dir+"/doc firmado " + Math.random()+".pdf")); */
                      
            /* Se cierran los flujos abiertos */
            baos.close();
            //pdfReader.close();
            documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),docfirmado_base64,documentoAFirmar.getMetadata(),true,0);
        }
        catch (ParametroIncorrectoException e){
            synchronized(this){
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),
                                         PDFirma.resourceBundle.getString("ERROR_016"),documentoAFirmar.getMetadata(),false,Integer.parseInt("016"));
                PDFirma.loguearExcepcion(e, "ERROR_016");
                JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_016"),
                                                     "ERROR", JOptionPane.ERROR_MESSAGE);
                hilofirma.cancelarProcesoFirma();
                hilofirma.desbloquear();
            }
        }
        catch (NoSuchAlgorithmException e){
            synchronized(this){
                hilofirma.bloquear();
                /* Por el tipo de error se cancela el proceso de firma 
                 * sin dar opcion al usuario de decidir */
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),
                                         PDFirma.resourceBundle.getString("ERROR_032"),documentoAFirmar.getMetadata(),false,Integer.parseInt("032"));
                PDFirma.loguearExcepcion(e, "ERROR_032");
                JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_032"),
                                                     "ERROR", JOptionPane.ERROR_MESSAGE);
                hilofirma.cancelarProcesoFirma();
                hilofirma.desbloquear();
            }
        }
        catch (PdfException e){
            synchronized(this){
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),
                                         PDFirma.resourceBundle.getString("ERROR_033"),documentoAFirmar.getMetadata(),false,Integer.parseInt("033"));
                PDFirma.loguearExcepcion((Exception)e, "ERROR_033");
                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                  PDFirma.resourceBundle.getString("ERROR_033")+ "\n" +
                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                                "ERROR", JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                    /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                    hilofirma.cancelarProcesoFirma();
                }
                hilofirma.desbloquear();
            }
        }
        catch (GeneralSecurityException e){
            synchronized(this){
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),
                                         PDFirma.resourceBundle.getString("ERROR_034"),documentoAFirmar.getMetadata(),false,Integer.parseInt("034"));
                PDFirma.loguearExcepcion(e, "ERROR_034");
                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                  PDFirma.resourceBundle.getString("ERROR_034")+ "\n" +
                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                                "ERROR", JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                    /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                    hilofirma.cancelarProcesoFirma();
                }
                hilofirma.desbloquear();
            }
        }
        catch (IOException e){
            synchronized(this){
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),
                                         PDFirma.resourceBundle.getString("ERROR_036"),documentoAFirmar.getMetadata(),false,Integer.parseInt("036"));
                PDFirma.loguearExcepcion(e, "ERROR_036");
                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                  PDFirma.resourceBundle.getString("ERROR_036")+ "\n" +
                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                                "ERROR", JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                    /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                    hilofirma.cancelarProcesoFirma();
                }
                hilofirma.desbloquear();
            }
        }
        catch (Exception e){
            synchronized(this){
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),
                                         PDFirma.resourceBundle.getString("ERROR_037"),documentoAFirmar.getMetadata(),false,Integer.parseInt("037"));
                PDFirma.loguearExcepcion(e,"ERROR_037");
                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                  PDFirma.resourceBundle.getString("ERROR_037")+ "\n" +
                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                                "ERROR", JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                    /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                    hilofirma.cancelarProcesoFirma();
                }
                hilofirma.desbloquear();
            }
        }
        return documentoFirmado;
    }
    
    public DocumentoFirmado firmarXML(DocumentoAFirmar documentoAFirmar){
        
        DocumentoFirmado documentoFirmado = null;
       
        try {           
            System.out.println("entre a firmar el documento " + documentoAFirmar.getNdoc() + " del tipo " + documentoAFirmar.getTipodocumento());

            /* Se convierte el documento firmado a String
            java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
            XMLUtils.outputDOM(doc, os, true);
            String docfirmado = new String(os.toByteArray(), StandardCharsets.UTF_8);            
            documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),docfirmado,documentoAFirmar.getMetadata(),true,0);        */
        }
        catch (Exception e){
            e.printStackTrace();
            synchronized(this){
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),"XML",
                                       PDFirma.resourceBundle.getString("ERROR_037"),documentoAFirmar.getMetadata(),false,Integer.parseInt("037"));
                PDFirma.loguearExcepcion(e,"ERROR_037");
                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                  PDFirma.resourceBundle.getString("ERROR_037")+ "\n" +
                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                                "ERROR",JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                    // El usuario decidio NO continuar el proceso de firma, cancelo el proceso 
                    hilofirma.cancelarProcesoFirma();
                }
                hilofirma.desbloquear();
            }
        }  
        return documentoFirmado;
    }
}
