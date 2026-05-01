package gob.firmadordigital.gui;

import gob.firmadordigital.PDFirma;
import gob.firmadordigital.ThreadFirma;
import gob.firmadordigital.excepciones.CanceloFirmaException;
import gob.firmadordigital.excepciones.ParametroIncorrectoException;
import gob.firmadordigital.pfdr.IdentidadRemota;
import gob.firmadordigital.update.ThreadUpdate;
import gob.firmadordigital.websocket.WebSocketServerEndpoint;
import net.miginfocom.swing.MigLayout;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import javax.swing.Timer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;


public class FrameFirma extends JFrame {
    //Todo verificar las excepciones que no he manejado bien  luego de la refactorización en los métodos de esta clase.
    private static KeyStore ks;
    private List<Certificate[]> signCertificates;
    private Certificate[] chain;
    private PrivateKey key;

    /* Datos relevantes que son usados luego por el firmador cuando se va
     * a realizar una firma remota contra la PFDR */
    private IdentidadRemota identidadremota;

    private JScrollPane scrlTable = new JScrollPane();
    private JTable tabla_certs;
    private JButton jButtonlink_identidadesPFDR = new JButton();
    private JButton jButtonFirmar = new JButton();
    private JButton jButtonCancelar = new JButton();

    private JPanel jPanelUpdate = new JPanel();
    private JProgressBar jProgressBarUpdate = new JProgressBar(0, 100);
    private JLabel jLabelUpdate = new JLabel();

    private Dimension screenSize;
    private WebSocketServerEndpoint wsse;
    private ThreadUpdate threadupdate = new ThreadUpdate();
    private Timer timer;

    public FrameFirma(WebSocketServerEndpoint wsse) {
        this.wsse = wsse;
        this.signCertificates = new java.util.ArrayList<>();

        /* Se redefine el comportamiento al cerrar la ventana, para que se cierre adecuadamente la conexion de websocket
         * y se avise al client. */
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                esperarProcesoUpdate();
                wsse.cerrarSesion(PDFirma.resourceBundle.getString("TXT_FIRMACANCELADA"));
                timer.stop();
            }
        });
        jbInit();
    }

    private void jbInit() {

        //todo ver las excepciones que se pueden lanzar en este proceso
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setSize((int) (screenSize.width * 0.6), screenSize.height / 4);
        this.setBackground(Color.LIGHT_GRAY);
        this.setTitle(PDFirma.resourceBundle.getString("LABEL_TITLE") + " " + PDFirma.parametros.getVersion());
        ImageIcon iconofirma = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconoaplicacion.png"));
        this.setIconImage(iconofirma.getImage());

        Dimension frameSize = this.getSize();
        this.setLocation((screenSize.width - frameSize.width) / 2,
                (screenSize.height - frameSize.height) / 2);

        MigLayout layout = new MigLayout();
        this.setLayout(layout);

        this.getKestore();
        
        DefaultTableModel tabla_certsModel = new DefaultTableModel(getFilas(), getColumnas());
        tabla_certsModel.fireTableDataChanged();

        tabla_certs = new JTable(tabla_certsModel) {
            public boolean isCellEditable(int rowIndex, int vColIndex) {
                return false;
            }
        };
        tabla_certs.setModel(tabla_certsModel);
        tabla_certs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla_certs.setPreferredScrollableViewportSize(tabla_certs.getPreferredSize());
        tabla_certs.setFillsViewportHeight(true);
        tabla_certs.setShowHorizontalLines(false);
        tabla_certs.setShowVerticalLines(false);
        tabla_certs.setBackground(Color.WHITE);
        tabla_certs.getTableHeader().setOpaque(false);
        tabla_certs.getTableHeader().setBackground(Color.LIGHT_GRAY);
        tabla_certs.changeSelection(0, 0, false, true);
        tabla_certs.setRowHeight(frameSize.height / 10);

        /* Se oculta la ultima columna de la tabla, para que no se vea si el
         * tipo de certificado es por token o pfdr*/
        tabla_certs.getColumnModel().getColumn(5).setWidth(0);
        tabla_certs.getColumnModel().getColumn(5).setMinWidth(0);
        tabla_certs.getColumnModel().getColumn(5).setMaxWidth(0);

        scrlTable.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrlTable.setBackground(Color.LIGHT_GRAY);

        jButtonlink_identidadesPFDR.setText(PDFirma.resourceBundle.getString("BOTON_LINKIDENTIDADESREMOTAS"));
        jButtonlink_identidadesPFDR.setForeground(new Color(66, 132, 255));
        jButtonlink_identidadesPFDR.setCursor(new Cursor(Cursor.HAND_CURSOR));
        jButtonlink_identidadesPFDR.setHorizontalAlignment(SwingConstants.LEFT);
        jButtonlink_identidadesPFDR.setContentAreaFilled(false);
        jButtonlink_identidadesPFDR.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                administrarIdentidadesPFDR();
            }
        });

        jPanelUpdate.setLayout(new MigLayout("align left"));
        jPanelUpdate.add(jLabelUpdate);
        jPanelUpdate.add(jProgressBarUpdate);
        jLabelUpdate.setForeground(new Color(0, 0, 214));

        jButtonFirmar.setText(PDFirma.resourceBundle.getString("BOTON_FIRMAR"));
        jButtonFirmar.addActionListener(CursorController.createListener(this, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jButtonFirmar_actionPerformed(e);
            }
        }));
        jButtonCancelar.setText(PDFirma.resourceBundle.getString("BOTON_CANCELAR"));
        jButtonCancelar.addActionListener(CursorController.createListener(this, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jButtonCancelar_actionPerformed(e);
            }
        }));

        scrlTable.getViewport().setBackground(Color.LIGHT_GRAY);
        scrlTable.getViewport().add(tabla_certs);

        this.getContentPane().add(scrlTable, "width 100%, height 100%, growx, push, span, wrap");

        this.getContentPane().add(jPanelUpdate, "width 100%, height 3%");

        this.getContentPane().add(jButtonlink_identidadesPFDR, "align left");
        this.getContentPane().add(jButtonCancelar, "align right");
        this.getContentPane().add(jButtonFirmar, "align right");

        /* Se pone a funcionar un timer que cada tantos ... segundos vuelve a cargar los
         * certificados, para contemplar el caso que el token se conecta una vez que
         * la Interface Gráfica ya había cargado */
        this.Timer_refrescoCertificados();

        /* Se agrega un listner para que estando con el foco en la tabla de seleccion de certificados
         * permita parar el servidor y salir de la aplicacion con la combinacion de teclas CTRL+C */
        Object mapCTRL_C = "ctrlc";

        ActionMap actionMapScrlTablePane = tabla_certs.getActionMap();
        InputMap inputMapScrlTablePane = tabla_certs.getInputMap(tabla_certs.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        inputMapScrlTablePane.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), mapCTRL_C);
        actionMapScrlTablePane.put(mapCTRL_C, new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (JOptionPane.showConfirmDialog(PDFirma.ffirma,
                        PDFirma.resourceBundle.getString("TXT_CIERRAAPLICACION") + "\n" +
                                PDFirma.resourceBundle.getString("TXT_SERVERSTOP"),
                        "ADVERTENCIA", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == 0) {
                    esperarProcesoUpdate();
                    wsse.cerrarSesion("Cierre de programa");
                    System.exit(0);
                    System.out.println("Se detiene servidor de WebSocket - Detiene el funcionamiento del Firmador Digital");
                }
            }
        });

        /* Se lanza hilo para comprobar, descargar e instalar actualizaciones */
        threadupdate.start();
    }

    private void jButtonFirmar_actionPerformed(ActionEvent e) {
        try {
            this.setAlwaysOnTop(false);
            if (tabla_certs.getSelectedRow() >= 0 && !(signCertificates.isEmpty() && PDFirma.lista_identidadesremotas.isEmpty())) {
                timer.stop();
                int fila_seleccionada = tabla_certs.getSelectedRow();
                String tipo_certificado = (String) tabla_certs.getModel().getValueAt(fila_seleccionada, 5);

                if (tipo_certificado.compareTo("local") == 0) {
                    /* Si se esta trabajando con un certificado en keystore local */
                    // Obtener la cadena de certificación completa directamente
                    chain = signCertificates.get(fila_seleccionada);

                    // El primer certificado de la cadena es el del firmante
                    X509Certificate cert = (X509Certificate) chain[0];

                    // Obtener la clave privada asociada al certificado (ahora por cadena)
                    key = obtenerClavePrivada(chain);

                    /* Se verifica que se haya escogido un certificado, que tenga clave privada asociada y que no este fuera de su fecha de vigencia */
                    if (cert != null && key != null && chain != null && chain.length > 0) {

                        /* Se verifica caducidad del Certificado */
                        if (verificarCertificado(cert)) {
                            /* Inicia un hilo de firma local */
                            ThreadFirma firmaseleccionados = new ThreadFirma(chain, key, wsse.getImagen_Estampa(), wsse);
                            firmaseleccionados.setPriority(Thread.MIN_PRIORITY);
                            firmaseleccionados.start();
                        }
                    }
                } else {
                    String cuil = (String) tabla_certs.getModel().getValueAt(fila_seleccionada, 1);

                    for (IdentidadRemota identidad : PDFirma.lista_identidadesremotas) {
                        if (identidad.getCuil().compareTo(cuil) == 0) {
                            identidadremota = new IdentidadRemota(identidad);
                            break;
                        }
                    }
                    if (identidadremota != null) {
                        /* Inicia un hilo de firma remota */
                        ThreadFirma firmaseleccionados = new ThreadFirma(identidadremota, wsse);
                        firmaseleccionados.setPriority(Thread.MIN_PRIORITY);
                        firmaseleccionados.start();
                    }
                }
            }
        } catch (CanceloFirmaException ex) {
            /* Se cancela el proceso de firma porque asi lo requirio el usuario */
            PDFirma.loguearExcepcion(ex, "ERROR_073");
        } catch (ParametroIncorrectoException ex) {
            PDFirma.loguearExcepcion(ex, "ERROR_068");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_068"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jButtonCancelar_actionPerformed(ActionEvent e) {
        esperarProcesoUpdate();
        wsse.cerrarSesion(PDFirma.resourceBundle.getString("TXT_FIRMACANCELADA"));
        this.dispose();

    }


    @Override
    public void dispose() {
        super.dispose();
        timer.stop();
    }

    private void getKestore() {
        /* Se carga el keysotre de windows, lo que incluye la mayor parte de dispositivos PKC11 si estan debidamente configurados */
        //todo prever fallback a levantar los certificados usando el driver PKC11 especifico
        try {
            // Se carga el KeyStore de Windows
            ks = KeyStore.getInstance("Windows-MY");
            ks.load(null, null);

            // Se obtiene  todos los certificados del KeyStore
            signCertificates.clear();
            java.util.Enumeration<String> aliases = ks.aliases();

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                try {
                    // Se filtran solo las entradas que tiene clave privada
                    if (!ks.isKeyEntry(alias)) continue;

                    java.security.cert.Certificate[] certChain = ks.getCertificateChain(alias);
                    if (certChain == null || certChain.length == 0) continue;

                    // Se filtran solo las entradas que son del tipo X509 end-entity (no CA)
                    if (!(certChain[0] instanceof X509Certificate)) continue;
                    X509Certificate cert = (X509Certificate) certChain[0];

                    // Se excluyen certificados que sean CA/intermedias (basicConstraints >= 0)
                    if (cert.getBasicConstraints() != -1) {
                        continue;
                    }

                    // Se exlcuyen certificados autocertificados (probables CA raíz) donde sujeto == emisor
                    if (cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                        continue;
                    }

                    // Si existe KeyUsage, se exige que permita digitalSignature o nonRepudiation,
                    // y se excluyen los que tengan keyCertSign (uso para firmar certificados)
                    boolean[] keyUsage = cert.getKeyUsage();
                    if (keyUsage != null) {
                        // keyUsage[0] = digitalSignature, [1] = nonRepudiation, [5] = keyCertSign
                        if (keyUsage.length > 5 && keyUsage[5]) {
                            // diseñado para firmar certificados (CA)
                            continue;
                        }

                        boolean allowsSignature = false;
                        if (keyUsage.length > 0 && keyUsage[0]) allowsSignature = true; // digitalSignature
                        if (!allowsSignature && keyUsage.length > 1 && keyUsage[1])
                            allowsSignature = true; // nonRepudiation
                        if (!allowsSignature) {
                            // No permite firma digital
                            continue;
                        }
                    }

                    // Se verifica  que la clave privada sea accesible (puede lanzar excepciones segun proveedor)
                    try {
                        java.security.Key key = ks.getKey(alias, null);
                        if (!(key instanceof PrivateKey)) continue; // no hay clave privada utilizable
                    } catch (Exception exKey) {
                        // Si no podemos acceder a la clave, no incluir esta entrada
                        PDFirma.loguearExcepcion(exKey, "WARN_KEY_NOT_ACCESSIBLE");
                        continue;
                    }

                    // Todo ok: añadimos la cadena completa para uso posterior
                    signCertificates.add(certChain);

                } catch (Exception innerEx) {
                    // No interrumpir la carga si un alias falla; registrar y continuar
                    PDFirma.loguearExcepcion(innerEx, "WARN_LOAD_ALIAS");
                }
            }
        } catch (KeyStoreException e) {
            PDFirma.loguearExcepcion(e, "ERROR_023");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_023") +
                            ": KeyStore no disponible", "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        } catch (NoSuchAlgorithmException e) {
            PDFirma.loguearExcepcion(e, "ERROR_023");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_023") +
                            ": Algoritmo no disponible", "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        } catch (java.io.IOException e) {
            PDFirma.loguearExcepcion(e, "ERROR_023");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_023") +
                            ": Error al cargar KeyStore", "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        } catch (CertificateException e) {
            // todo ver como tratar esta excepcion
            throw new RuntimeException(e);
        }
    }

    private Object[][] getFilas() {

        Object[][] data;

        if (!(signCertificates.isEmpty() && PDFirma.lista_identidadesremotas.isEmpty())) {
            int size = signCertificates.size() + PDFirma.lista_identidadesremotas.size();
            data = new Object[size][6];
            int i = 0;

            if (!signCertificates.isEmpty()) {
                // Iterar sobre las cadenas de certificados
                for (Certificate[] cadena : signCertificates) {
                    if (cadena.length > 0) {
                        // Obtener el primer certificado de la cadena (el del firmante)
                        X509Certificate cert = (X509Certificate) cadena[0];
                        String DN;
                        String CN;

                        /* Se cargan datos del sujeto */
                        DN = cert.getSubjectDN().toString();
                        int x = DN.indexOf("CN=");
                        int y = DN.indexOf(',', x);
                        y = y >= 0 ? y : DN.length();
                        CN = new String(DN.substring(x + 3, y));
                        data[i][0] = CN;

                        /* Se carga el SN */
                        x = DN.indexOf("SERIALNUMBER");
                        String SN;
                        if (x > 0) {
                            y = DN.indexOf(',', x);
                            y = y >= 0 ? y : DN.length();
                            SN = new String(DN.substring(x + 13, y));
                        } else {
                            SN = cert.getSerialNumber().toString();
                        }
                        data[i][1] = SN;

                        /* Se cargan los datos del emisor */
                        DN = cert.getIssuerX500Principal().getName();
                        x = DN.indexOf("CN=");
                        y = DN.indexOf(',', x);
                        y = y >= 0 ? y : DN.length();
                        CN = new String(DN.substring(x + 3, y));
                        data[i][2] = CN;

                        /* Se cargan las fechas de vigencia */
                        Format formatter = new SimpleDateFormat("dd MMMM yyyy");
                        data[i][3] = formatter.format(cert.getNotBefore());
                        data[i][4] = formatter.format(cert.getNotAfter());

                        /* Se indica que el certificado esta almacenado en un keystore local o
                         * repositorio por software local o dispositivo criptografico local */
                        data[i][5] = "local";

                        ++i;
                    }
                }
            }
            if (!PDFirma.lista_identidadesremotas.isEmpty()) {
                for (IdentidadRemota identidadremota : PDFirma.lista_identidadesremotas) {
                    data[i][0] = identidadremota.getApenomb();
                    data[i][1] = identidadremota.getCuil();
                    data[i][2] = PDFirma.resourceBundle.getString("TXT_PFDRMINISTERIODEMODERNIZACION");
                    data[i][5] = PDFirma.resourceBundle.getString("TXT_REMOTA");
                    ++i;
                }
            }
            return data;
        } else {
            /* Almacen de certificados vacio */
            String[] columnNames = {""};
            data = new Object[1][1];
            data[0][0] = new String(PDFirma.resourceBundle.getString("TXT_NOSEENCUENTRANCERTIFICADOS"));
            return data;
        }
    }

    private String[] getColumnas() {
        /* La columna tipo de firma remite a determinar si se realizara una
         * local  - firma local: el certificado esta en un keystore local o dispositivo criptografico local
         * remota - firma remota: el certificado esta en el HSM de la PFDR del Ministerio de Modernizacion */
        String[] columnNames = {
                PDFirma.resourceBundle.getString("TXT_EMITIDO_PARA"), PDFirma.resourceBundle.getString("TXT_NRODESERIE"),
                PDFirma.resourceBundle.getString("TXT_EMITIDO_POR"), PDFirma.resourceBundle.getString("TXT_VALIDO_DESDE"),
                PDFirma.resourceBundle.getString("TXT_VALIDOHASTA"), PDFirma.resourceBundle.getString("TXT_TIPO_FIRMA")
        };
        return columnNames;
    }

    public boolean verificarCertificado(X509Certificate cert) {

        try {
            Date hoy = new Date();
            Date no_antes = cert.getNotBefore();
            Date no_despues = cert.getNotAfter();

            /* Se obtiene el CN del emisor del Certificado desde el DN del emisor */
            String issuerCN = "";
            X500Name issuer = new X500Name(cert.getIssuerX500Principal().getName());
            RDN[] rdns = issuer.getRDNs(BCStyle.CN);
            if (rdns != null && rdns.length > 0) {
                issuerCN = rdns[0].getFirst().getValue().toString();
            }

            /* Se verifica la caducidad-periodo de vigencia del Certificado escogido */
            if (hoy.after(no_antes) && hoy.before(no_despues)) {
           
                /* El Certificado escogido esta vigente 
                /* Se evalua si el certificado esta por vencer para dar aviso al firmante 
                 * se considera un periodo de dias antes de la fecha de vencimiento y
                 * de ahi en mas se avisa cuantos dias le faltan para que expire. La cantidad de 
                 * dias esta tomada del parametro cantdias_preavisovencimientocertificado seteado
                 * en el documento parametros.properties*/

                final long MILLSECS_PER_DAY = 24 * 60 * 60 * 1000; //Milisegundos al d?a 
                long dias_restantes = Math.abs((hoy.getTime() - no_despues.getTime()) / MILLSECS_PER_DAY);


                if (dias_restantes <= PDFirma.parametros.getcantdias_preavisovencimientocertificado()) {
                    JOptionPane.showMessageDialog(this,
                            PDFirma.resourceBundle.getString("TXT_RESTAN") + " " + dias_restantes + " " +
                                    PDFirma.resourceBundle.getString("TXT_DIASVENCIMIENTOCERTIFICADO") + "\n\n" +
                                    PDFirma.resourceBundle.getString("TXT_RECORDATORIORENOVACION") + " " + issuerCN,
                                    PDFirma.resourceBundle.getString("TXT_ADVERTENCIA"), JOptionPane.WARNING_MESSAGE);
                }
                /* Todo ok. se avanza en el proceso de firma */
                return true;
            }
            else if (hoy.after(no_despues)) {
                /* Certificado Expirado*/
                JOptionPane.showMessageDialog(this,
                        PDFirma.resourceBundle.getString("ERROR_024") + "\n" +
                                PDFirma.resourceBundle.getString("TXT_DEBERENOVARCERTIFICADO") + "\n" + issuerCN,
                        "ERROR",
                        JOptionPane.ERROR_MESSAGE);
                PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
                PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_024"));
            } else if (hoy.before(no_antes)) {
                /* Certificado aun no entra en vigencia*/
                JOptionPane.showMessageDialog(this,
                        PDFirma.resourceBundle.getString("ERROR_025") + "\n" +
                                PDFirma.resourceBundle.getString("TXT_VERIFIQUEPERIODOVALIDEZ"),
                        "ERROR",
                        JOptionPane.ERROR_MESSAGE);
                PDFirma.filelog.print(PDFirma.fecha_fileLog + " ");
                PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_025"));
            }
        } catch (ParametroIncorrectoException e) {
            PDFirma.loguearExcepcion(e, e.getMessage());
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "ERROR", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }


    /* Se coloca un timer (hilo) que refresca la tabla de certificados
     * cada 5 segundos, con un delay inicial de 15 segundos. Esto se hace
     * a fin de actualizar la tabla cuando el usuario conecta o desconecta tokens. */
    public void Timer_refrescoCertificados() {
        timer = new Timer(5000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getKestore();
                actualizarTabla();
            }
        });
        timer.setRepeats(true);
        timer.setInitialDelay(15);
        timer.start();
    }


    public void administrarIdentidadesPFDR() {
        IdentidadRemotaDialog identidadesPFDRDialog = new IdentidadRemotaDialog(this, PDFirma.resourceBundle.getString("TXT_IDENTIDADES_PFDR"), true);
        identidadesPFDRDialog.setVisible(true);
    }

    public void actualizarTabla_agregar(IdentidadRemota identidadremota) {

        String data[] = new String[6];

        data[0] = identidadremota.getApenomb();
        data[1] = identidadremota.getCuil();
        data[2] = PDFirma.resourceBundle.getString("TXT_PFDRMINISTERIODEMODERNIZACION");
        data[5] = PDFirma.resourceBundle.getString("TXT_REMOTA");

        DefaultTableModel dtm = (DefaultTableModel) tabla_certs.getModel();

        dtm.addRow(data);
        this.tabla_certs.repaint();
        this.tabla_certs.updateUI();
    }

    public void actualizarTabla() {
        int selectedRow = tabla_certs.getSelectedRow();
        DefaultTableModel table_model = new DefaultTableModel(getFilas(), getColumnas());
        table_model.fireTableDataChanged();
        tabla_certs.setModel(table_model);
        tabla_certs.changeSelection(selectedRow, 0, false, true);
        this.tabla_certs.repaint();
        this.tabla_certs.updateUI();
    }

    public void setjlabelUpdate(String mensaje) {
        jLabelUpdate.setText(mensaje);
    }

    public void setjProgressBarUpdate(int valor) {
        jProgressBarUpdate.setValue(valor);
    }

    public void ocultarInfoUpdate() {
        jLabelUpdate.setVisible(false);
        jProgressBarUpdate.setVisible(false);
        jPanelUpdate.setVisible(false);
        this.getContentPane().remove(jPanelUpdate);
    }

    public void esperarProcesoUpdate() {
        try {
            threadupdate.join();
        } catch (InterruptedException e) {
            PDFirma.loguearExcepcion(e, "ERROR_072");
        }
    }

    /**
     * Obtiene la clave privada asociada a un certificado desde el KeyStore de Windows
     * Ahora recibe la cadena completa Certificate[] y busca la clave privada comparando
     * el certificado firmante (primer elemento) con las entradas del KeyStore.
     */
    private PrivateKey obtenerClavePrivada(Certificate[] chain) {

        PrivateKey privateKey = null;

        try {
            if (chain != null && chain.length > 0) {
                Certificate signerCert = chain[0];
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                byte[] signerFingerprint = sha256.digest(signerCert.getEncoded());

                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements() && privateKey == null) {
                    String alias = aliases.nextElement();
                    if (ks.isKeyEntry(alias)) {
                        Certificate candidate = ks.getCertificate(alias);
                        if (candidate != null) {
                            byte[] candidateFingerprint = sha256.digest(candidate.getEncoded());
                            if (Arrays.equals(signerFingerprint, candidateFingerprint)) {
                                Key key = ks.getKey(alias, null);
                                if (key instanceof PrivateKey pk) {
                                    privateKey = pk;
                                }
                            }
                        }
                    }
                }
            }
        } catch (GeneralSecurityException e) {
            PDFirma.loguearExcepcion(e, "ERROR_094");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_094"),
                    "ERROR", JOptionPane.ERROR_MESSAGE);
        }
        return privateKey;
    }
}
