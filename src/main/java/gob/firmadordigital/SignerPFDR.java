package gob.firmadordigital;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gob.firmadordigital.excepciones.CanceloFirmaException;
import gob.firmadordigital.excepciones.OtpBaneadoException;
import gob.firmadordigital.excepciones.ParametroIncorrectoException;
import gob.firmadordigital.gui.OtpDialog;
import gob.firmadordigital.model.DocumentoAFirmar;
import gob.firmadordigital.model.DocumentoFirmado;
import gob.firmadordigital.pfdr.*;
import jakarta.ws.rs.client.*;
import jakarta.ws.rs.core.*;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.encoders.Base64;

import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;


/* Implementa la firma remota de un documento, tomando el Certificado
 * de la PFDR del Ministerio de Modernizacion */

public class SignerPFDR extends Signer {

    private IdentidadRemota identidadremota;
    private String pin;
    private String otp;
    private String url_firmadorPFDR;

    public SignerPFDR(IdentidadRemota identidad, String pin, String otp, ThreadFirma hilo) {
        super(hilo);
        this.identidadremota = identidad;
        this.pin = pin;
        this.otp = otp;
        this.url_firmadorPFDR = PDFirma.parametros.getUrl_firmadorpfdr();
    }

    @Override
    public DocumentoFirmado firmar(DocumentoAFirmar documentoAFirmar) {

        /* Se realiza el proceso de firma remota, conectandose por REST a los webservice de la PFDR*/
        DocumentoFirmado documentoFirmado = null;

        try {
            /* Se conecta al primer servicio de la API de la PFDR para obtener el access-token */
            Client client = ClientBuilder.newBuilder().build();
            WebTarget target = client.target(url_firmadorPFDR + "/RA/oauth/token");

            // autenticación Basic: construimos cabecera Authorization
            String clientId = "1ccfc086-2ddb-41d3-ae8f-f69250fe74a9";
            String clientSecret = "04d15ec3df0c43ef9e8e48e2d6f71c97";
            String basic = Base64.toBase64String((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            Invocation.Builder builder = target.request();
            builder.header(HttpHeaders.AUTHORIZATION, "Basic " + basic);

            Form form = new Form().param("grant_type", "client_credentials");
            Response response1 = builder.post(Entity.form(form));

            /* Si se obtuvo con exito el access-token */
            if (response1.getStatus() == 201 || response1.getStatus() == 200) {
                /* Se obtienen las cookies de la respuesta */
                final NewCookie BIGipServerfirmar_pool_new = response1.getCookies().get("BIGipServerfirmar_pool");
                final Cookie BIGipServerfirmar_pool = (BIGipServerfirmar_pool_new != null) ? BIGipServerfirmar_pool_new.toCookie() : null;
                /* Se obtiene el access-token de la respuesta del primer servicio*/
                String respuesta = response1.readEntity(String.class);

                int pos_inicio_accessToken = respuesta.indexOf("<access_token>") + 14;
                int pos_fin_accessToken = respuesta.indexOf("</access_token>");
                String access_token = respuesta.substring(pos_inicio_accessToken, pos_fin_accessToken);

                /* Se construye el JSon que se debe enviar por post al segundo servicio */
                byte[] bytearchivo = Base64.decode(documentoAFirmar.getDocumento());
                DatosFirmaRemota datosfirmaremota = new DatosFirmaRemota(identidadremota.getCuil(), documentoAFirmar.getNdoc(), bytearchivo, documentoAFirmar.getMetadata(), "PDF");

                Gson gson = new GsonBuilder().disableHtmlEscaping().create();
                String datosfirmaremota_json = gson.toJson(datosfirmaremota);

                /* Se conecta al segundo servicio de la PFDR, autenticandose con el access-token
                 * para enviar el documento a firmar y demas datos de firma requeridos */
                String bearerToken = "Bearer " + access_token;

                // nuevo client / target para el request
                client = ClientBuilder.newBuilder().build();
                target = client.target(url_firmadorPFDR + "/firmador/api/signatures");
                builder = target.request();
                builder.header(HttpHeaders.AUTHORIZATION, bearerToken);
                builder.header(HttpHeaders.ACCEPT_CHARSET, "UTF-8");
                if (BIGipServerfirmar_pool != null) builder.cookie(BIGipServerfirmar_pool);

                Response response2 = builder.post(Entity.entity(datosfirmaremota_json, "application/json"));
                if (response2.getStatus() == 201 || response2.getStatus() == 200) {
                    URI location = response2.getLocation();
                    final NewCookie SESSION_new = response2.getCookies().get("SESSION");
                    final Cookie SESSION = (SESSION_new != null) ? SESSION_new.toCookie() : null;

                    /* Se realiza un get a la URL que se envio como respuesta al request anterior */
                    target = client.target(location.toString());
                    builder = target.request();
                    if (SESSION != null) builder.cookie(SESSION);
                    if (BIGipServerfirmar_pool != null) builder.cookie(BIGipServerfirmar_pool);
                    builder.header(HttpHeaders.AUTHORIZATION, bearerToken);
                    builder.header(HttpHeaders.ACCEPT_CHARSET, "UTF-8");
                    Response response3 = builder.get();

                    if (response3.getStatus() == 300 || response3.getStatus() == 301 || response3.getStatus() == 302) {
                        final NewCookie BIGipServerfirmar_pool1_new = response1.getCookies().get("BIGipServerfirmar_pool");
                        final Cookie BIGipServerfirmar_pool1 = (BIGipServerfirmar_pool1_new != null) ? BIGipServerfirmar_pool1_new.toCookie() : null;

                        /* Se dirige directamente al servicio de login */
                        DatosLoginPFDR datosloginPFDR = new DatosLoginPFDR(identidadremota.getCuil(), pin);
                        gson = new Gson();
                        String datosloginPFDR_json = gson.toJson(datosloginPFDR);

                        target = client.target(url_firmadorPFDR + "/firmador/firstLogin");
                        builder = target.request();
                        if (SESSION != null) builder.cookie(SESSION);
                        if (BIGipServerfirmar_pool1 != null) builder.cookie(BIGipServerfirmar_pool1);
                        Response response4 = builder.post(Entity.entity(datosloginPFDR_json, "application/json"));

                        if (response4.getStatus() == 201 || response4.getStatus() == 200) {

                            Response response5 = null;
                            synchronized (this) {
                                target = client.target(url_firmadorPFDR + "/firmador/otpLogin");
                                for (int intentos = 0; intentos < 3; ++intentos) {
                                    Otp otp_model = new Otp(otp);
                                    String otp_json = gson.toJson(otp_model);

                                    builder = target.request();
                                    if (SESSION != null) builder.cookie(SESSION);
                                    if (BIGipServerfirmar_pool1 != null) builder.cookie(BIGipServerfirmar_pool1);
                                    try (Response resp = builder.post(Entity.entity(otp_json, "application/json"))) {
                                        if (resp.getStatus() == 201 || resp.getStatus() == 200) {
                                            /* El Otp se valido correctamente, se sale del proceso de intento. */
                                            response5 = resp;
                                            break;
                                        } else {
                                            if (intentos == 2) {
                                                throw new OtpBaneadoException(PDFirma.resourceBundle.getString("ERROR_079"));
                                            }
                                            /* Se vencio el OTP o el ingresado es erroneo, se pide al usuario que lo reingrese para reintentar el proceso */
                                            loguearError_y_Response("ERROR_055", null);
                                            OtpDialog otpDialog = new OtpDialog("OTP", true);
                                            if (intentos == 1) otpDialog.setMensaje_ultimoIntento();
                                            otpDialog.setVisible(true);
                                            otp = otpDialog.getOTP();
                                            if (otp.isEmpty()) {
                                                /* Se cancela el proceso de firma porque asi lo requirio el usuario */
                                                throw new CanceloFirmaException("ERROR_073");
                                            }
                                        }
                                    }
                                }
                            }
                            /* Una vez validado el Otp ingresado se continua con el proceso de firma */
                            DocumentoAFirmarPFDR datosDocumento = new DocumentoAFirmarPFDR(documentoAFirmar.getNdoc(), bytearchivo, pin);
                            gson = new GsonBuilder().disableHtmlEscaping().create();
                            String datosDocumento_json = gson.toJson(datosDocumento);

                            // construir el target para signDocument con el mismo client
                            target = client.target(url_firmadorPFDR + "/firmador/signDocument");
                            builder = target.request();
                            if (SESSION != null) builder.cookie(SESSION);
                            if (BIGipServerfirmar_pool1 != null) builder.cookie(BIGipServerfirmar_pool1);
                            builder.accept("application/json");
                            Response response6 = builder.post(Entity.entity(datosDocumento_json, "application/json"));
                            if (response6.getStatus() == 201 || response6.getStatus() == 200) {
                                /* Se conecta al servidor del organismo donde se haya guardado el JSON con la respuesta
                                 * que dio el servicio de la PFDR. Se lee ese JSON para evaluar si el status de firma fue
                                 * exitoso o fallo algo; y para obtener el documento que devolvera a la url de callback definida
                                 * en DocumentoAFirmar */
                                BufferedInputStream bis = new BufferedInputStream(new URL(PDFirma.parametros.getUrl_redirectfirmaremota() + "/JSONPFDR_" + datosfirmaremota.getCuil() + "_" + documentoAFirmar.getNdoc() + ".tmp").openStream());
                                String json = IOUtils.toString(bis, StandardCharsets.UTF_8);
                                Gson parser = new Gson();
                                JSON_DocumentoFirmadoPFDR json_documentoFirmado = parser.fromJson(json, JSON_DocumentoFirmadoPFDR.class);

                                /* Se evalua si la PFDR informo algun error al firmar el documento. */
                                if (json_documentoFirmado.getStatus().getSuccess()) {
                                    /* La firma del documento en la PFDR fue exitosa */
                                    
                                    /* Se guarda localmente el documento firmado - descomentar con fines de prueba
                                    byte [] bytearchivo_aFirmar = Base64.decode(json_documentoFirmado.getDocumento());
                                    Files.write(Paths.get(PDFirma.fdefault_dir+"/doc firmado " + Math.random()+".pdf"), bytearchivo_aFirmar,StandardOpenOption.CREATE_NEW);
                                    /* fin guardo localmente el documento*/
                                    String docfirmado_base64 = json_documentoFirmado.getDocumento();
                                    bis.close();
                                    documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(), docfirmado_base64, documentoAFirmar.getMetadata(), true, 0);
                                } else {
                                    synchronized (this) {
                                        hilofirma.bloquear();
                                        /* La PFDR reporto un estado de error y un mensaje al intentar
                                         * firmar el documento.El manejo de este error es diferente.
                                         * No se reporta un error de firma porque fue el servidor remoto
                                         * quien no pudo firmar el documento y el objeto
                                         * devuelto ya fue enviado desde PDFSignerPFDR*/
                                        documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(), json_documentoFirmado.getMsg(), documentoAFirmar.getMetadata(), false, 0);
                                        loguearError_y_Response("ERROR_063", null);
                                        if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                PDFirma.resourceBundle.getString("ERROR_063") + "\n" +
                                                        PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                                "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != 0) {
                                            /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                                            hilofirma.cancelarProcesoFirma();
                                        }
                                        hilofirma.desbloquear();
                                    }
                                }
                            } else { //de response6
                                synchronized (this) {
                                    hilofirma.bloquear();
                                    documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                                            PDFirma.resourceBundle.getString("ERROR_054"), documentoAFirmar.getMetadata(), false, Integer.parseInt("054"));
                                    loguearError_y_Response("ERROR_054", response6);
                                    if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                            PDFirma.resourceBundle.getString("ERROR_054") + "\n" +
                                                    PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                            "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != 0) {
                                        /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                                        hilofirma.cancelarProcesoFirma();
                                    }
                                    hilofirma.desbloquear();
                                }
                            }
                            response6.close();
                        } else { //de response4
                            synchronized (this) {
                                hilofirma.bloquear();
                                /* Los datos de login del usuario (cuil y pin) proporcionados fueron rechazados
                                 * por el servicio de firma remota. Se cancela el proceso de firma */
                                loguearError_y_Response("ERROR_056", response4);
                                JOptionPane.showMessageDialog(PDFirma.ffirma,
                                        PDFirma.resourceBundle.getString("ERROR_056"),
                                        "ERROR", JOptionPane.ERROR_MESSAGE);
                                hilofirma.cancelarProcesoFirma();
                                hilofirma.desbloquear();
                            }
                        }
                        response4.close();
                    } else { //de response3
                        synchronized (this) {
                            hilofirma.bloquear();
                            documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                                    PDFirma.resourceBundle.getString("ERROR_057"), documentoAFirmar.getMetadata(), false, Integer.parseInt("057"));
                            loguearError_y_Response("ERROR_057", null);
                            if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                    PDFirma.resourceBundle.getString("ERROR_057") + "\n" +
                                            PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                    "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != 0) {
                                /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                                hilofirma.cancelarProcesoFirma();
                            }
                            hilofirma.desbloquear();
                        }
                    }
                } else { //de response2
                    synchronized (this) {
                        hilofirma.bloquear();
                        /* El bearerToken, el cuil o los datos del documento fueron rechazados
                         * por el servicio de firma remota. */
                        documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                                PDFirma.resourceBundle.getString("ERROR_059"), documentoAFirmar.getMetadata(), false, Integer.parseInt("059"));
                        loguearError_y_Response("ERROR_059", response2);
                        if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                PDFirma.resourceBundle.getString("ERROR_059") + "\n" +
                                        PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                "ERROR", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) != 0) {
                            /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso */
                            hilofirma.cancelarProcesoFirma();
                        }
                        hilofirma.desbloquear();
                    }
                }
                response2.close();
            } else { // de response1
                /* El token de la aplicacion y credenciales proporcionadas fueron rechazados
                 * por el servicio de firma remota. Se cancela el proceso de firma */
                synchronized (this) {
                    hilofirma.bloquear();
                    documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                            PDFirma.resourceBundle.getString("ERROR_058"), documentoAFirmar.getMetadata(), false, Integer.parseInt("058"));
                    loguearError_y_Response("ERROR_058", response1);
                    JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_058"),
                            "ERROR", JOptionPane.ERROR_MESSAGE);
                    hilofirma.cancelarProcesoFirma();
                    hilofirma.desbloquear();
                }
            }
            response1.close();
        } catch (CanceloFirmaException e) {
            synchronized (this) {
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_073"), documentoAFirmar.getMetadata(), false, Integer.parseInt("073"));
                PDFirma.loguearExcepcion(e, "ERROR_073");
                JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_073"),
                        "ERROR", JOptionPane.ERROR_MESSAGE);
                hilofirma.cancelarProcesoFirma();
                hilofirma.desbloquear();
            }
        } catch (OtpBaneadoException e) {
            synchronized (this) {
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_079"), documentoAFirmar.getMetadata(), false, Integer.parseInt("079"));
                PDFirma.loguearExcepcion(e, "ERROR_079");
                JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_079"),
                        "ERROR", JOptionPane.ERROR_MESSAGE);
                hilofirma.cancelarProcesoFirma();
                hilofirma.desbloquear();
            }
        } catch (ParametroIncorrectoException e) {
            synchronized (this) {
                hilofirma.bloquear();
                /* Esta es una excepcion no declarada pero que puede
                 * producirse y debe ser tratada.
                 * Por el tipo de error se cancela el proceso de firma
                 * sin dar opcion al usuario de decidir */
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_080"), documentoAFirmar.getMetadata(), false, Integer.parseInt("080"));
                PDFirma.loguearExcepcion(e, "ERROR_080");
                JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_080"),
                        "ERROR", JOptionPane.ERROR_MESSAGE);
                hilofirma.cancelarProcesoFirma();
                hilofirma.desbloquear();
            }
        } catch (MalformedURLException e) {
            synchronized (this) {
                hilofirma.bloquear();
                documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(), documentoAFirmar.getCantdoc(), documentoAFirmar.getTipodocumento(),
                        PDFirma.resourceBundle.getString("ERROR_080"), documentoAFirmar.getMetadata(), false, Integer.parseInt("080"));
                /* Se produjo un error con la Url_redirectfirmaremota.
                 * Se debe revisar la configuracion de este parametro.
                 * Por el tipo de error se cancela el proceso de firma. */
                PDFirma.loguearExcepcion(e, "ERROR_080");
                JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_080"),
                        "ERROR", JOptionPane.ERROR_MESSAGE);
                hilofirma.cancelarProcesoFirma();
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
        return documentoFirmado;
    }

    private synchronized void loguearError_y_Response(String error, Response response) {
        /* Este metodo loguea un error que se produjo durante el proceso de firma
         * al hacer un request y los datos obtenidos del response donde se detecto. */
        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        PDFirma.filelog.println(PDFirma.resourceBundle.getString(error));
        if (response != null) {
            PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
            PDFirma.filelog.println("status code de la respuesta: " + response.getStatus());
            PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
            PDFirma.filelog.println("entity de la respuesta: " + response.readEntity(String.class));
        }
    }
}
