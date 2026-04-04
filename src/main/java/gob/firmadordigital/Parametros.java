package gob.firmadordigital;

import gob.firmadordigital.excepciones.ParametroIncorrectoException;
import org.apache.commons.validator.routines.RegexValidator;
import org.apache.commons.validator.routines.UrlValidator;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class Parametros {

    /* version y release del firmador digital actual. Este codigo compilado. */
    private String version;
    private String release;

    /* Apunta al servidor del organismo desde donde se deben descargar los Updates del firmador digital */
    private String url_update;

    /* Puerto en el que escucha el WebSocketServer */
    private String websocketserver_port;

    private int cantdias_preavisovencimientocertificado;

    /* Servidor de TimeStamping */
    private String url_TSA;
    private String user_TSA;
    private String password_TSA;

    /* Firma Remota */
    private String url_redirectfirmaremota;
    private String url_firmadorpfdr;


    public Parametros() {
        /*
         * Levanto el archivo parametros.properties para leer las propiedades
         * que son parametrizables en la aplicacion
         * */
        Properties props = new Properties();
        try {
            InputStream is_propiedades = this.getClass().getClassLoader().getResourceAsStream("parametros.properties");
            props.load(is_propiedades);

            version = props.getProperty("version");
            release = props.getProperty("release");

            url_update = props.getProperty("url_update");
            websocketserver_port = props.getProperty("websocketserver_port");

            cantdias_preavisovencimientocertificado = Integer.valueOf(props.getProperty("cantdias_preavisovencimientocertificado"));

            url_TSA = props.getProperty("url_TSA");
            user_TSA = props.getProperty("user_TSA");
            password_TSA = props.getProperty("password_TSA");

            url_redirectfirmaremota = props.getProperty("url_redirectfirmaremota");
            url_firmadorpfdr = props.getProperty("url_firmadorpfdr");

            is_propiedades.close();
        } catch (IOException e) {
            /* Aqui al no poder leer el archivo de parametros no puede ni iniciar el errorlog para loguear el problema */
            System.out.println(PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + " " +
                    PDFirma.resourceBundle.getString("ERROR_006") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_006"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public String getVersion() throws ParametroIncorrectoException {
        RegexValidator validator = new RegexValidator("[0-9]{1,2}.[0-9]{1,2}", false);
        if (validator.isValid(version)) {
            return version;
        } else {
            throw new ParametroIncorrectoException("ERROR_011");
        }
    }

    public String getRelease() throws ParametroIncorrectoException {
        RegexValidator validator = new RegexValidator("[0-9]{1,2}", false);
        if (validator.isValid(release)) {
            return release;
        } else {
            throw new ParametroIncorrectoException("ERROR_012");
        }
    }

    public int getWebsocketserver_port() {
        RegexValidator validator = new RegexValidator("[0-9]{2,5}", false);
        if (validator.isValid(websocketserver_port)) {
            return Integer.parseInt(websocketserver_port);
        } else {
            PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
            PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_069"));
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("ERROR_069") + "\n" +
                            PDFirma.resourceBundle.getString("TXT_WEBSOCKETSERVER_PORT_BYDEFAULT"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);

            /* Si se produce un error en el formato del puerto se retorna
             * el puerto por defecto*/
            return 8025;
        }
    }

    public String getUrl_update() throws ParametroIncorrectoException {
        
        /* Descomentar para produccion 
        UrlValidator validator = new UrlValidator(); */

        /* Quitar o comentar en produccion */
        String[] schemes = {"http", "https"};
        UrlValidator validator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS);

        if (validator.isValid(url_update)) {
            return url_update;
        } else {
            throw new ParametroIncorrectoException("ERROR_014");
        }
    }

    public int getcantdias_preavisovencimientocertificado() throws ParametroIncorrectoException {
        if (cantdias_preavisovencimientocertificado > 0) {
            return cantdias_preavisovencimientocertificado;
        } else {
            throw new ParametroIncorrectoException("ERROR_015");
        }
    }

    public String getUrl_TSA() throws ParametroIncorrectoException {
        UrlValidator validator = new UrlValidator();
        if (validator.isValid(url_TSA)) {
            return url_TSA;
        } else {
            if (url_TSA == null) {
                return "";
            } else {
                throw new ParametroIncorrectoException("ERROR_016");
            }
        }
    }

    public String getUser_TSA() {
        if (user_TSA != null)
            return user_TSA;
        else
            return "";
    }

    public String getPassword_TSA() {
        if (password_TSA != null) {
            return password_TSA;
        } else
            return "";
    }

    public String getUrl_redirectfirmaremota() throws ParametroIncorrectoException {
        UrlValidator validator = new UrlValidator();
        if (validator.isValid(url_redirectfirmaremota)) {
            return url_redirectfirmaremota;
        } else {
            if (url_redirectfirmaremota == null) {
                return "";
            } else {
                throw new ParametroIncorrectoException("ERROR_062");
            }
        }
    }

    public String getUrl_firmadorpfdr() throws ParametroIncorrectoException {
        UrlValidator validator = new UrlValidator();
        if (validator.isValid(url_firmadorpfdr)) {
            return url_firmadorpfdr;
        } else {
            if (url_firmadorpfdr == null) {
                return "";
            } else {
                throw new ParametroIncorrectoException("ERROR_068");
            }
        }
    }
}
