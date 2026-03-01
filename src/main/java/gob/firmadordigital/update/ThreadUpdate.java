package gob.firmadordigital.update;

import gob.firmadordigital.PDFirma;
import gob.firmadordigital.excepciones.ParametroIncorrectoException;

import gob.firmadordigital.websocket.WebSocketServerEndpoint;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.InetAddress;

import java.net.Proxy;
import java.net.URL;

import javax.swing.JOptionPane;

import jupar.objects.Modes;
import jupar.objects.Release;
import jupar.parsers.ReleaseXMLParser;

import org.xml.sax.SAXException;


public class ThreadUpdate extends Thread {
    
    private WebSocketServerEndpoint wsse;
    
    public ThreadUpdate() {
        super("ThreadUpdate");
    }
    public void run() {
        int todook = -1;
        try {
          Release release = new Release();
          release.setpkgver(PDFirma.parametros.getVersion());
          release.setPkgrel(PDFirma.parametros.getRelease());
          ReleaseXMLParser parser = new ReleaseXMLParser();
            PDFirma.ffirma.setjlabelUpdate(PDFirma.resourceBundle.getString("TXT_COMPROBANDOACTUALIZACIONES"));
            PDFirma.ffirma.setjProgressBarUpdate(10);
            
          String proxyHost = null;
          int proxyPort = 0;
          String usuarioProxy = null;
          String passwordProxy = null;
 
          Release current = parser.parse(PDFirma.parametros.getUrl_update() + "/lite/latest.xml", Modes.URL,proxyHost,proxyPort,usuarioProxy,passwordProxy);
          PDFirma.ffirma.setjProgressBarUpdate(25);  
          if (current.compareTo(release) > 0) {
                PDFirma.ffirma.setjProgressBarUpdate(100);    
                todook = 0;
                PDFirma.ffirma.setjlabelUpdate(PDFirma.resourceBundle.getString("TXT_DESCARGANDOACTUALIZACIONES"));
                PDFirma.ffirma.setjProgressBarUpdate(0);  
                Downloader dl = new Downloader();

                dl.download(PDFirma.parametros.getUrl_update() + "/lite/files.xml", "tmp", Modes.URL,proxyHost,proxyPort,usuarioProxy,passwordProxy);
                PDFirma.ffirma.setjlabelUpdate(PDFirma.resourceBundle.getString("TXT_INSTALANDOACTUALIZACIONES"));
                PDFirma.ffirma.setjProgressBarUpdate(10);
                Updater update = new Updater();
                update.update("update.xml","tmp",Modes.FILE,proxyHost,proxyPort,usuarioProxy,passwordProxy);
                PDFirma.ffirma.setjlabelUpdate(PDFirma.resourceBundle.getString("TXT_ACTUALIZACIONCOMPLETA"));
                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                  PDFirma.resourceBundle.getString("TXT_REINICIOPORACTUALIZACION"),  
                                                 "AVISO",JOptionPane.OK_OPTION,JOptionPane.INFORMATION_MESSAGE)==0) {
                    
                    /* Se elimina el directorio temporal y lo que se hubiese creado para la actualizacion */
                    File tmp = new File("tmp");
                    if (tmp.exists()) {
                        for (File file : tmp.listFiles()) {
                            file.delete();
                        }
                        tmp.delete();
                    }
                    restartApplication();
                }

          }
        } 
        catch (ParametroIncorrectoException e){
            PDFirma.loguearExcepcion(e, e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma, e.getMessage(),
                                          "ERROR", JOptionPane.ERROR_MESSAGE);
            todook = -1;
        }
        catch (SAXException e) {
            PDFirma.loguearExcepcion(e, "ERROR_007");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                                          PDFirma.resourceBundle.getString("ERROR_007")+"\n" +
                                          PDFirma.resourceBundle.getString("TXT_REVISECONEXIONINTERNET")+"\n" +
                                          PDFirma.resourceBundle.getString("TXT_EJECUCIONNORMAL")+"\n" +
                                          PDFirma.resourceBundle.getString("TXT_SOPORTETECNICO"),
                                        "ERROR", JOptionPane.ERROR_MESSAGE);
            todook = -1;
        } 
        catch (FileNotFoundException e) {
            PDFirma.loguearExcepcion(e, "ERROR_008");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                                          PDFirma.resourceBundle.getString("ERROR_008") + "\n" +
                                          PDFirma.resourceBundle.getString("TXT_PERMISOS") + "\n" +
                                          PDFirma.resourceBundle.getString("TXT_REVISECONEXIONINTERNET"),
                  "ERROR", JOptionPane.ERROR_MESSAGE); 
            todook = -1;
        } 
        catch (IOException e) {
            PDFirma.loguearExcepcion(e, "ERROR_009");
            e.printStackTrace();
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                                          PDFirma.resourceBundle.getString("ERROR_009") + "\n" +
                                          PDFirma.resourceBundle.getString("TXT_PERMISOS") + "\n" +
                                          PDFirma.resourceBundle.getString("TXT_REVISECONEXIONINTERNET"),
                                            "ERROR", JOptionPane.ERROR_MESSAGE);
            todook = -1;
        } 
        catch (InterruptedException e) {
            PDFirma.loguearExcepcion(e, "ERROR_010");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                                          PDFirma.resourceBundle.getString("ERROR_010") + "\n" +
                                          PDFirma.resourceBundle.getString("TXT_REVISECONEXIONINTERNET"),
                                            "ERROR", JOptionPane.ERROR_MESSAGE);
            todook = -1;
        }
        catch (Exception e){
            PDFirma.loguearExcepcion(e, "ERROR_093");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                                          PDFirma.resourceBundle.getString("ERROR_093")+"\n" +
                                          PDFirma.resourceBundle.getString("TXT_REVISECONEXIONINTERNET")+"\n" +
                                          PDFirma.resourceBundle.getString("TXT_EJECUCIONNORMAL")+"\n" +
                                          PDFirma.resourceBundle.getString("TXT_SOPORTETECNICO"),
                                        "ERROR", JOptionPane.ERROR_MESSAGE);
            todook = -1;            
        }
        finally {
            /**
             * Si se llego a crear, se borra directorio temporal y su contenido
             */
            File tmp = new File("tmp");
            if (tmp.exists()) {
                for (File file : tmp.listFiles()) {
                    file.delete();
                }
                tmp.delete();
            }
            try {
                Thread.sleep(700);
            } 
            catch (InterruptedException e) {
                PDFirma.loguearExcepcion(e, "ERROR_077");
            }
            PDFirma.ffirma.ocultarInfoUpdate();  
          }
   }
    
    public void restartApplication() throws IOException, InterruptedException {
        String exePath = System.getProperty("user.dir")+"/firmador-digital.exe";

        ProcessBuilder processBuilder = new ProcessBuilder(exePath);
        processBuilder.start();
        System.exit(0); // Finaliza la ejecucion actual       
    } 
}