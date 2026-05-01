package gob.firmadordigital;

import com.github.markusbernhardt.proxy.ProxySearch;
import gob.firmadordigital.excepciones.DefaultDirException;
import gob.firmadordigital.gui.About_Dialog;
import gob.firmadordigital.gui.FrameFirma;
import gob.firmadordigital.gui.IdentidadesRemotasSerializer;
import gob.firmadordigital.pfdr.IdentidadRemota;
import gob.firmadordigital.websocket.WebSocketServerEndpoint;
import jakarta.websocket.DeploymentException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.glassfish.tyrus.server.Server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintStream;
import java.net.*;
import java.security.Security;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;


public class PDFirma {

    public final static ResourceBundle resourceBundle = ResourceBundle.getBundle("Strings");

    public static FrameFirma ffirma;
    public static PrintStream filelog;
    public static Parametros parametros;
    public static String fecha_fileLog;
    public static File fdefault_dir;

    /* Variable con la lista de identidades remotas configurada */
    public static List<IdentidadRemota> lista_identidadesremotas;

    /* Constructor estatico - en el se crea un directorio FimadorDigital dentro del cual se grabara
     * el archivo ErrorLog.txt. El directorio FirmadorDigital se crea dependiendo del directorio local
     * appdata, o en su defecto user.home, o en su defecto el directorio donde se esta ejecutando la aplicacion
     * */
    static {
        try {

            /* Dado que se ejecutara como una aplicacion Java GUI se envia la salida estandar a un archivo
             * para loguear los mensajes y errores de mas bajo nivel que pueden producirse al iniciar el programa.
             * Todo el resto se envia al log de transacciones que se crea al iniciar la aplicacion.
            PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream("salida.txt")));
            System.setOut(out);
            System.setErr(out); */

            /* Carga parametros */
            parametros = new Parametros();
            String default_dir = System.getenv("LOCALAPPDATA") + File.separator;
            fdefault_dir = new File(default_dir);
            if (!fdefault_dir.exists() || !fdefault_dir.canWrite()) {
                default_dir = System.getProperty("user.home") + File.separator;
                fdefault_dir = new File(default_dir);
                if (!fdefault_dir.exists() || !fdefault_dir.canWrite()) {
                    System.out.println(PDFirma.resourceBundle.getString("INFO_NOSEENCONTROPROPIEDADUSERHOME"));
                    fdefault_dir = new File(".");
                    if (!fdefault_dir.exists() || !fdefault_dir.canWrite()) {
                        throw new DefaultDirException(PDFirma.resourceBundle.getString("TXT_DEFAULTDIREXCEPTION") + "\n" + PDFirma.resourceBundle.getString("TXT_CONFIGUREAPPDATA") + "\n" + PDFirma.resourceBundle.getString("TXT_PROPIEDADUSERHOME"));
                    }
                }
            }
            default_dir = default_dir + "FirmadorDigital" + File.separator;
            fdefault_dir = new File(default_dir);
            if (!fdefault_dir.exists()) {
                fdefault_dir.mkdir();
            }
            filelog = new PrintStream(default_dir + "FirmadorLog.txt");

            /* Carga las identidades remotas */
            lista_identidadesremotas = new IdentidadesRemotasSerializer().decode();

            /* Calcula la fecha y hora actual para que se agregue al filelog en cada mensaje que se registre */
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            fecha_fileLog = dateFormat.format(new Date());

            /* agrega el proveedor criptografico de BouncyCastle */
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            String version = System.getProperty("java.version");
            filelog.println("JRE version: " + System.getProperty("java.version"));

        } catch (DefaultDirException e) {
            System.out.println(PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + " " +
                    PDFirma.resourceBundle.getString("ERROR_003") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_003"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);

        } catch (Exception e) {
            System.out.println(PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + " " +
                    PDFirma.resourceBundle.getString("ERROR_004") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_004"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        }
        //Todo sacar todos los otros Throwable, dejar solo este.
        catch (Throwable e) {
            e.printStackTrace();
            System.out.println(PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + " " +
                    PDFirma.resourceBundle.getString("ERROR_005") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_005"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /* Servidor de WebSocket */
    private Server server = new Server("localhost", PDFirma.parametros.getWebsocketserver_port(), "/websockets", null, WebSocketServerEndpoint.class);

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            detectarConexion();

            /* Se chequea el soporte para SystemTray.
             * En caso afirmativo se agrega el icono de la aplicacion
             * al System Tray o barra de iconos ocultos en el area de
             * notificacion de la barra de tareas de Windows.
             * Este icono debe agregarse cuando el servidor de websocket
             * se levanta y quitarse cuando se cierra. */
            if (!SystemTray.isSupported()) {
                System.out.println(PDFirma.resourceBundle.getString("TXT_SUPPORTSYSTEMTRAY"));
            }
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    crearIconoEnSystemTray();
                }
            });

            /* Se lanza el servidor de websocket local */
            PDFirma pdfirma = new PDFirma();
            pdfirma.server.start();

            /* La siguiente linea evita que termine el
             * hilo principal de programa mientras se este ejecutando el
             * servidor de websocket. Como son procesos independientes
             * aparentemente no hace falta. Probar bien. */
            Thread.currentThread().join();
        } catch (UnsupportedLookAndFeelException e) {
            System.out.println(PDFirma.resourceBundle.getString("TXT_ERRORENLOOKANDFEEL") + " " +
                    PDFirma.resourceBundle.getString("ERROR_064") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENLOOKANDFEEL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_064"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        } catch (URISyntaxException e) {
            /* Esta excepcion se produce porque la url hardcodeada
             * no es correcta. No deberia producirse nunca si el
             * codigo esta bien. Pero en caso que se produzca se
             * deja que el programa siga funcionando.*/
            loguearExcepcion(e, "ERROR_071");
        } catch (ClassNotFoundException e) {
            System.out.println(PDFirma.resourceBundle.getString("ERROR_065") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_064"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        } catch (DeploymentException e) {
            loguearExcepcion(e, "ERROR_026");
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_026"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        } catch (RuntimeException e) {
            System.out.println(PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + " " +
                    PDFirma.resourceBundle.getString("ERROR_002") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_002"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        } catch (Exception e) {
            System.out.println(PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + " " +
                    PDFirma.resourceBundle.getString("ERROR_001") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_003"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        } catch (Throwable e) {
            System.out.println(PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + " " +
                    PDFirma.resourceBundle.getString("ERROR_000") + "\n" + e.getMessage());
            JOptionPane.showMessageDialog(PDFirma.ffirma,
                    PDFirma.resourceBundle.getString("TXT_ERRORENFIRMADORDIGITAL") + "\n" +
                            PDFirma.resourceBundle.getString("ERROR_000"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private static void crearIconoEnSystemTray() {

        /* Se chequea soporte para SystemTray */
        if (!SystemTray.isSupported()) {
            System.out.println(PDFirma.resourceBundle.getString("TXT_SUPPORTSYSTEMTRAY"));
            JOptionPane.showMessageDialog(null, PDFirma.resourceBundle.getString("TXT_SUPPORTSYSTEMTRAY"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        final PopupMenu popup = new PopupMenu();
        final TrayIcon trayIcon;
        trayIcon = new TrayIcon(new ImageIcon(PDFirma.class.getClassLoader().getResource("images/iconosystemtray.gif")).getImage());
        final SystemTray tray = SystemTray.getSystemTray();

        // Create a popup menu components
        MenuItem aboutItem = new MenuItem(PDFirma.resourceBundle.getString("MENU_ITEM_ACERCADE"));
        MenuItem exitItem = new MenuItem(PDFirma.resourceBundle.getString("MENU_ITEM_EXIT"));

        //Add components to popup menu
        popup.add(aboutItem);
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println(PDFirma.resourceBundle.getString("ERROR_067"));
            JOptionPane.showMessageDialog(null, PDFirma.resourceBundle.getString("ERROR_067"),
                    "ERROR 067",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        trayIcon.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(null, PDFirma.resourceBundle.getString("TXT_SYSTEMTRAYDIALOGBOX"));
            }
        });

        aboutItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JDialog about;
                about = new About_Dialog(null,
                        PDFirma.resourceBundle.getString("TXT_ACERCADE") + " " +
                                PDFirma.resourceBundle.getString("LABEL_TITLE"), false);
                about.setVisible(true);
            }
        });

        exitItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                /* TODO resolver el cierre de la conexion */
                if (ffirma != null) {
                    ffirma.esperarProcesoUpdate();
                }
                tray.remove(trayIcon);
                System.exit(0);
            }
        });
    }

    public static void detectarConexion() throws URISyntaxException {

        System.setProperty("http.nonProxyHosts", "127.0.0.1|localhost");

        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_DETECTANDOPROXIES"));

        ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
        ProxySelector myProxySelector = proxySearch.getProxySelector();

        if (myProxySelector != null) {
            ProxySelector.setDefault(myProxySelector);

            List l = myProxySelector.select(new URI("http://www.yahoo.com/"));

            for (Iterator iter = l.iterator(); iter.hasNext(); ) {
                Proxy proxy = (Proxy) iter.next();

                PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
                PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_PROXYTYPE") + proxy.type());

                InetSocketAddress addr = (InetSocketAddress)
                        proxy.address();

                if (addr == null) {
                    /* No se detecta proxy. Conexion directa */
                    PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
                    PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_NOPROXY"));
                } else {
                    /* Se detecto un proxy */
                    PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
                    PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_PROXYHOSTNAME") + " " + addr.getHostName());

                    PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
                    PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_PROXYPORT") + " " + addr.getPort());
                }
            }
        } else {
            /* No se detecta proxy. Conexion directa */
            PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
            PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_NOPROXY"));
        }
    }

    public final static void loguearExcepcion(Exception e, String codigoError) {
        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        PDFirma.filelog.println(PDFirma.resourceBundle.getString(codigoError));
        PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
        e.printStackTrace(PDFirma.filelog);
    }
}