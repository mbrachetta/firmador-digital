package gob.firmadordigital.websocket;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import gob.firmadordigital.PDFirma;
import gob.firmadordigital.excepciones.FiltroException;
import gob.firmadordigital.excepciones.ImagenEstampaException;
import gob.firmadordigital.excepciones.Url_CierreLoteException;
import gob.firmadordigital.excepciones.Url_ServicioDelOrganismoException;
import gob.firmadordigital.gui.FrameFirma;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.apache.commons.validator.routines.UrlValidator;

import javax.swing.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


/* Esta clase implementa el servidor de websocket. Es el server endpoint, no el cliente */

@ServerEndpoint(value = "/firmador")
public class WebSocketServerEndpoint {

    private Session session;

    /* Filtro que utilizara el sistema corporativo del Organismo
     * para filtrar/seleccionar el lote de documentos a firmar. */
    private String filtro;

    /* URL a donde debe conectarse el firmador digital para requerir
     * los documentos a firmar*/
    private String url_serviciodelorganismo;

    /* URL a donde debe conectarse el firmador digital para enviar
     * los datos de cierre del lote de documentos a la firma*/
    private String url_cierrelote;

    /* Nombre del archivo de imagen con la estampa de firma a utilizar */
    private String imagen_estampafirma;

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Conexion abierta");
        this.session = session;
        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_SESIONWEBSOCKETABIERTA") + session.getId());

        /* Se levanta el cuadro de dialogo y se inicia la ejecucion del firmador digital
         * controlando que: o bien no exista una instancia previa
         * o bien esta instancia no este visible
         * Considerar la evaluacion de esta condicion - es un buen ejemplo
         * de evaluacion por cortocircuito */
        if (PDFirma.ffirma == null || !PDFirma.ffirma.isVisible()) {
            PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
            PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_SELECTORDECERTIFICADOS"));
            PDFirma.ffirma = new FrameFirma(this);
            PDFirma.ffirma.setAlwaysOnTop(true);
            PDFirma.ffirma.setVisible(true);
        
            /* Se verifica que el producto este activo 
            if (!verificarFecha()) { */

                /* Se controla activacion del producto por fecha
                JOptionPane.showMessageDialog(PDFirma.ffirma,
                                              PDFirma.resourceBundle.getString("ERROR_019") +
                                              "\n" +
                                              PDFirma.resourceBundle.getString("TXT_NOPODRAUSARFIRMADOR"), "ERROR", 
                                              JOptionPane.ERROR_MESSAGE);

                PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_027"));
                this.cerrarSesion(PDFirma.resourceBundle.getString("TXT_NOPODRAUSARFIRMADOR"));
                System.exit(0);
            }  fin verificar fecha */
        }
    }

    @OnMessage
    public String onMessage(String message, Session session) {
        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_MENSAJERECIBIDODESDECLIENTEDEWEBSOCKET") + "sesion id: " + session.getId() + " " + message);
        int pos = message.indexOf(":");
        String prefijo_mensaje = message.substring(0, pos);
        message = message.substring(pos + 1, message.length());

        switch (prefijo_mensaje) {
            case "filtro":
                filtro = message;
                break;
            case "url_docs":
                url_serviciodelorganismo = message;
                break;
            case "url_cierre":
                url_cierrelote = message;
                break;
            case "img_estampa":
                imagen_estampafirma = message;
                break;
            default:
                PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
                PDFirma.filelog.println(PDFirma.resourceBundle.getString("El prefijo de mensaje recibido no fue identificado - prefijo: ") + prefijo_mensaje);
        }
        return message;
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Conexion cerrada");
        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_SESIONWEBSOCKETCERRADA_OK") + " " + session.getId() + " " + closeReason.getReasonPhrase());
    }

    @OnError
    public void onError(Throwable t) {
        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_051") + " " + session.getId());
        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        t.printStackTrace(PDFirma.filelog);
    }


    /* Este metodo permite enviar mensajes al cliente de websockets */
    public void sendTextMessage(String message) {
        try {
            session.getBasicRemote().sendText(message);
            PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
            PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_MENSAJEENVIADOASERVIDOR") + session.getId());
            PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
            PDFirma.filelog.println(message);
        } catch (IOException e) {
            PDFirma.loguearExcepcion(e, "ERROR_052");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_052"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_053");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_053"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean verificarFecha() {

        /* Control por fecha limite */
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Date fecha_limite;
        try {
            fecha_limite = sdf.parse("30/11/2025");
            Date fecha_hoy = new Date();
            if (fecha_hoy.before(fecha_limite)) {
                return true;
            } else {
                return false;
            }
        } catch (ParseException e) {
            PDFirma.loguearExcepcion(e, "ERROR_028");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_028"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public String getFiltro() throws FiltroException {
        if (filtro != null && !filtro.isEmpty()) {
            System.out.println(filtro);
            return filtro;
        } else {
            throw new FiltroException(PDFirma.resourceBundle.getString("ERROR_070"));
        }
    }


    public String getUrl_serviciodelorganismo() throws Url_ServicioDelOrganismoException {

        /* Quitar o comentar en produccion */
        String[] schemes = {"http", "https"};
        UrlValidator validator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS);

        /*
         * Descomentar para produccionUrlValidator validator = new UrlValidator(); */

        if (validator.isValid(url_serviciodelorganismo)) {
            return url_serviciodelorganismo;
        } else {
            throw new Url_ServicioDelOrganismoException(PDFirma.resourceBundle.getString("ERROR_013"));
        }
    }

    public String getUrl_cierrelote() throws Url_CierreLoteException {
        
        /* Descomentar para produccion 
        UrlValidator validator = new UrlValidator(); */

        /* Quitar o comentar en produccion */
        String[] schemes = {"http", "https"};
        UrlValidator validator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS);

        if (validator.isValid(url_cierrelote)) {
            return url_cierrelote;
        } else {
            throw new Url_CierreLoteException(PDFirma.resourceBundle.getString("ERROR_084"));
        }
    }


    public ImageData getImagen_Estampa() {
        /* Este metodo tiene el siguiente comportamiento:
         *   a. Si se recibio el mensaje img_estampa y la imagen apuntada existe, devuelve la imagen para la estampa de firma
         *   b. Si se recibio el mensaje img_estampa pero no se puede cargar la imagen, devuelve null
         *   c. Si no se recibio el mensaje img_estampa, devuelve null
         *   d. Si se produce otro tipo de error al cargar la imagen maneja las excepciones.
         *   Luego al firmar se controlara. Si la imagen no es nula se usa firma visible, de
         *   lo contrario se usa firma no visible. */
        ImageData imageData = null;
        try {
            if (imagen_estampafirma != null) {
                URL imageURL = PDFirma.class.getClassLoader().getResource("images/" + imagen_estampafirma);
                if (imageURL != null) {
                    try (InputStream is = imageURL.openStream();
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096];
                        int r;
                        while ((r = is.read(buffer)) != -1) baos.write(buffer, 0, r);
                        byte[] imageBytes = baos.toByteArray();
                        imageData = ImageDataFactory.create(imageBytes);
                    }
                } else {
                    throw new ImagenEstampaException(PDFirma.resourceBundle.getString("ERROR_078"));
                }
            }
        } catch (ImagenEstampaException e) {
            PDFirma.loguearExcepcion(e, "ERROR_078");
            JOptionPane.showMessageDialog(PDFirma.ffirma, e.getMessage(),
                    "ADVERTENCIA", JOptionPane.WARNING_MESSAGE);
            this.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_078"));
        } catch (IOException e) {
            PDFirma.loguearExcepcion(e, "ERROR_075");
            JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_075"),
                    "ERROR", JOptionPane.ERROR_MESSAGE);
            this.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_075"));
        } catch (Exception e) {
            // Cualquier otra excepción no esperada
            PDFirma.loguearExcepcion(e, "ERROR_074");
            JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_074"),
                    "ERROR", JOptionPane.ERROR_MESSAGE);
            this.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_074"));
        }
        return imageData;
    }


    public void cerrarSesion(String reason) {
        try {
            PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
            PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_REQUIERECERRARWEBSOCKETSESSION") + " " + session.getId() + ". " + reason);

            /* Se envia mensaje a cliente de websocket */
            sendTextMessage(PDFirma.resourceBundle.getString("INFO_CLOSEWEBSOCKETSESSION") + " " + reason);
            session.close();
        } catch (IOException e) {
            PDFirma.loguearExcepcion(e, "ERROR_042");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_042"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}

