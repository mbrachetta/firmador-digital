package gob.firmadordigital.websocket;

import gob.firmadordigital.PDFirma;
import jakarta.websocket.DeploymentException;
import org.glassfish.tyrus.server.Server;

import javax.swing.*;


public class WebSocketServer {

    private static Server server = new Server("localhost", PDFirma.parametros.getWebsocketserver_port(), "/websockets", null, WebSocketServerEndpoint.class);

    public static void runServer() {
        try {
            server.start();
            System.out.println("Se inicia Servidor de WebSocket");
        } catch (DeploymentException e) {
            PDFirma.loguearExcepcion(e, "ERROR_026");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_026"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_050");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_050"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void stopServer() {
        PDFirma.ffirma.dispose();
        server.stop();
        System.out.println("Se detiene servidor de WebSocket - Detiene el funcionamiento del Firmador Digital");
        System.exit(0);
    }
}