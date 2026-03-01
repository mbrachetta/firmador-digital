package gob.firmadordigital.gui;

import es.mityc.javasign.pkstore.CertStoreException;
import es.mityc.javasign.pkstore.DefaultPassStoreKS;
import es.mityc.javasign.pkstore.IPKStoreManager;
import es.mityc.javasign.pkstore.mscapi.MSCAPIStore;
import es.mityc.javasign.pkstore.pkcs11.ConfigMultiPKCS11;
import es.mityc.javasign.pkstore.pkcs11.DefaultPassStoreP11;
import es.mityc.javasign.pkstore.pkcs11.MultiPKCS11Store;

import gob.firmadordigital.pfdr.IdentidadRemota;
import gob.firmadordigital.PDFirma;
import gob.firmadordigital.ThreadFirma;
import gob.firmadordigital.excepciones.CanceloFirmaException;
import gob.firmadordigital.excepciones.ParametroIncorrectoException;
import gob.firmadordigital.update.ThreadUpdate;
import gob.firmadordigital.websocket.WebSocketServerEndpoint;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.text.Format;
import java.text.SimpleDateFormat;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

import net.miginfocom.swing.MigLayout;


public class FrameFirma extends JFrame {
  
    private static IPKStoreManager ks;
    private List<X509Certificate> signCertificates;
    private Certificate [] certificate;
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
    private JProgressBar jProgressBarUpdate = new JProgressBar(0,100);
    private JLabel jLabelUpdate = new JLabel();

    private Dimension screenSize;
    private WebSocketServerEndpoint wsse;
    private ThreadUpdate threadupdate = new ThreadUpdate();
    private Timer timer;

    public FrameFirma(WebSocketServerEndpoint wsse){
            this.wsse = wsse;
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
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setSize((int)(screenSize.width*0.6),screenSize.height/4);
        this.setBackground(Color.LIGHT_GRAY);
        try {
            this.setTitle(PDFirma.resourceBundle.getString("LABEL_TITLE") + " " + PDFirma.parametros.getVersion());
            
            
        } 
        catch (ParametroIncorrectoException e) {
            /* Corresponde aqui un manejo particular del log de la excepcion*/
            PDFirma.loguearExcepcion(e, e.getMessage());
            JOptionPane.showMessageDialog(this, e.getMessage(),
                                          "ERROR", JOptionPane.ERROR_MESSAGE);
        }

        ImageIcon iconofirma = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconoaplicacion.png"));
        this.setIconImage(iconofirma.getImage());

        Dimension frameSize = this.getSize();
        this.setLocation((screenSize.width - frameSize.width) / 2, 
                         (screenSize.height - frameSize.height) / 2);

        MigLayout layout = new MigLayout();  
        this.setLayout(layout);

        /* Descomentar para levantar los Certificados de Windows y de las Tokens
         * usando el proveedor MSCapi */      
          this.getKestore_ie();
        
        /* Descomentar para levantar los Certificados del Token directamente 
        this.getKeystore_pkcs11(); */
          
        DefaultTableModel tabla_certsModel = new DefaultTableModel(getFilas(),getColumnas());
        tabla_certsModel.fireTableDataChanged();

        tabla_certs=new JTable(tabla_certsModel){ public boolean isCellEditable(int rowIndex, int vColIndex) {
                 return false;}};
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
        tabla_certs.setRowHeight(frameSize.height/10);
        
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
        jButtonFirmar.addActionListener(CursorController.createListener(this,new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    jButtonFirmar_actionPerformed(e);
                }
            }));
        jButtonCancelar.setText(PDFirma.resourceBundle.getString("BOTON_CANCELAR"));
        jButtonCancelar.addActionListener(CursorController.createListener(this,new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    jButtonCancelar_actionPerformed(e);
                }
            }));
        
        scrlTable.getViewport().setBackground(Color.LIGHT_GRAY);
        scrlTable.getViewport().add(tabla_certs);

        this.getContentPane().add(scrlTable,"width 100%, height 100%, growx, push, span, wrap");

        this.getContentPane().add(jPanelUpdate,"width 100%, height 3%");
       
        this.getContentPane().add(jButtonlink_identidadesPFDR,"align left"); 
        this.getContentPane().add(jButtonCancelar,"align right");
        this.getContentPane().add(jButtonFirmar,"align right");
        
        /* Se pone a funcionar un timer que cada tantos ... segundos vuelve a cargar los
         * certificados, para contemplar el caso que el token se conecta una vez que 
         * la Interface Gráfica ya había cargado */
        this.Timer_refrescoCertificados();
        
        /* Se agrega un listner para que estando con el foco en la tabla de seleccion de certificados
         * permita parar el servidor y salir de la aplicacion con la combinacion de teclas CTRL+C */
        Object mapCTRL_C = "ctrlc";
        
        ActionMap actionMapScrlTablePane = tabla_certs.getActionMap();
        InputMap inputMapScrlTablePane = tabla_certs.getInputMap(tabla_certs.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        inputMapScrlTablePane.put(KeyStroke.getKeyStroke(KeyEvent.VK_C,KeyEvent.CTRL_DOWN_MASK), mapCTRL_C);  
        actionMapScrlTablePane.put(mapCTRL_C, new AbstractAction(){
            public void actionPerformed(ActionEvent e) {
                PDFirma.filelog.println("Y VOY A TERMINAR");
                    if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                  PDFirma.resourceBundle.getString("TXT_CIERRAAPLICACION") + "\n" +
                                                  PDFirma.resourceBundle.getString("TXT_SERVERSTOP"),               
                                                     "ADVERTENCIA",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)==0) {
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
            if (tabla_certs.getSelectedRow()>=0  && !(signCertificates.isEmpty() && PDFirma.lista_identidadesremotas.isEmpty())){
                timer.stop();
                int fila_seleccionada = tabla_certs.getSelectedRow();
                String tipo_certificado = (String) tabla_certs.getModel().getValueAt(fila_seleccionada, 5);

                if (tipo_certificado.compareTo("local")==0){
                    /* Si se esta trabajando con un certificado en keystore local */  
                    X509Certificate cert = signCertificates.get(fila_seleccionada);
                    certificate = new Certificate[1];
                    certificate[0]= cert;
                    key = ks.getPrivateKey(cert);

                    /* Se verifica que se haya escogido un certificado, que tenga clave privada asociada y que no este fuera de su fecha de vigencia */
                    if (cert !=null && key != null){

                    /* Se verifica caducidad del Certificado */
                        if (verificarCertificado(cert)){
                            /* Inicia un hilo de firma local */
                            ThreadFirma firmaseleccionados = new ThreadFirma(certificate,key,wsse.getImagen_Estampa(),obtenerDatosFirmante(cert),wsse);
                            firmaseleccionados.setPriority(Thread.MIN_PRIORITY);
                            firmaseleccionados.start(); 
                        }
                    }
                }
                else{
                    String cuil = (String) tabla_certs.getModel().getValueAt(fila_seleccionada, 1);
                    
                    for(IdentidadRemota identidad : PDFirma.lista_identidadesremotas){
                        if (identidad.getCuil().compareTo(cuil)==0){
                            identidadremota = new IdentidadRemota(identidad);
                            break;
                        }
                    }
                    if(identidadremota!=null){
                        /* Inicia un hilo de firma remota */
                        ThreadFirma firmaseleccionados = new ThreadFirma(identidadremota,wsse);
                        firmaseleccionados.setPriority(Thread.MIN_PRIORITY);
                        firmaseleccionados.start();
                    }
                }
            }   
        }
        catch (CanceloFirmaException ex){
            /* Se cancela el proceso de firma porque asi lo requirio el usuario */
            PDFirma.loguearExcepcion(ex, "ERROR_073");
        }
        catch (ParametroIncorrectoException ex){
            PDFirma.loguearExcepcion(ex, "ERROR_068");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_068"), 
                                          "ERROR", 
                                          JOptionPane.ERROR_MESSAGE);
        }
        catch (CertStoreException ex) {
            PDFirma.loguearExcepcion(ex, "ERROR_029");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_029"), 
                                          "ERROR", 
                                          JOptionPane.ERROR_MESSAGE);
        }
        //TODO Borrar
        catch (Exception ex) {
            ex.printStackTrace();
        }
        //todo borrar
        catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    private void jButtonCancelar_actionPerformed(ActionEvent e) {
        esperarProcesoUpdate();
        wsse.cerrarSesion(PDFirma.resourceBundle.getString("TXT_FIRMACANCELADA"));
        this.dispose();
        
    }
    
    
    @Override
    public void dispose(){
        super.dispose();
        timer.stop();
    }

        
    private void getKeystore_pkcs11(){
        String provider_dll = "C:/Windows/System32/eToken.dll";
        ConfigMultiPKCS11 config = new ConfigMultiPKCS11();
        try {
           config.addSunProvider("ePass", provider_dll);
           ks = new MultiPKCS11Store(config, new DefaultPassStoreP11());    
           signCertificates = ks.getSignCertificates();
       } 
        catch (NoSuchProviderException e) {
            PDFirma.loguearExcepcion(e, "ERROR_021");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_021"), "ERROR", 
                                          JOptionPane.ERROR_MESSAGE);
        }
        catch (CertStoreException e) {
            PDFirma.loguearExcepcion(e, "ERROR_022");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_022"), "ERROR", 
                                          JOptionPane.ERROR_MESSAGE);
        }
    }

    private void getKestore_ie(){
        try {
            ks = new MSCAPIStore(new DefaultPassStoreKS());
            signCertificates = ks.getSignCertificates();
        }
        catch (CertStoreException e) {
            PDFirma.loguearExcepcion(e, "ERROR_023");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_023"), "ERROR", 
                                         JOptionPane.ERROR_MESSAGE);
        }
    }

    private Object[][] getFilas()  {

         Object [][] data;

         if(!(signCertificates.isEmpty() && PDFirma.lista_identidadesremotas.isEmpty())){
             int size = signCertificates.size() + PDFirma.lista_identidadesremotas.size();
             data = new Object[size][6]; 
             int i=0;

             if (!signCertificates.isEmpty()){
                Iterator iterator = signCertificates.iterator();
                X509Certificate cert;
                String DN;
                String CN;
         
                /* Se carga la tabla con los datos de los Certificados */
                while (iterator.hasNext()){
                     cert = (X509Certificate)iterator.next();
                                  
                     /* Se cargan datos del sujeto */
                     DN = cert.getSubjectDN().toString();
                     int x = DN.indexOf("CN=");
                     int y = DN.indexOf(',', x);
                     y = y >= 0 ? y : DN.length();
                     CN = new String(DN.substring(x+3,y));
                     data[i][0]=CN;
               
                     /* Se carga el SN */
                     x = DN.indexOf("SERIALNUMBER");
                     String SN;
                     if (x>0) {
                        y = DN.indexOf(',',x);
                        y = y >= 0 ? y : DN.length();
                        SN = new String(DN.substring(x+13,y));
                     }
                     else {
                        SN=cert.getSerialNumber().toString();
                     }
                     data[i][1]=SN;
                 
                     /* Se cargan los datos del emisor */
                     DN = cert.getIssuerX500Principal().getName();
                     x = DN.indexOf("CN=");
                     y = DN.indexOf(',', x);
                     y = y >= 0 ? y : DN.length();
                     CN = new String(DN.substring(x+3,y));
                     data[i][2]=CN;

                     /* Se cargan las fechas de vigencia */
                     Format formatter = new SimpleDateFormat("dd MMMM yyyy");
                     data[i][3]=formatter.format(cert.getNotBefore());
                     data[i][4]=formatter.format(cert.getNotAfter());
                    
                    /* Se indica que el certificado esta almacenado en un keystore local o 
                     * repositorio por software local o dispositivo criptografico local */
                    data[i][5]="local";
                    
                     ++i;
                 }
             }
             if (!PDFirma.lista_identidadesremotas.isEmpty()){
                for(IdentidadRemota identidadremota : PDFirma.lista_identidadesremotas){
                    data[i][0]=identidadremota.getApenomb();
                    data[i][1]=identidadremota.getCuil();
                    data[i][2] = PDFirma.resourceBundle.getString("TXT_PFDRMINISTERIODEMODERNIZACION");
                    data[i][5] = PDFirma.resourceBundle.getString("TXT_REMOTA");
                    ++i;
                }                
             }
             return data;
         }    
         else{ 
              /* Almacen de certificados vacio */
             String[] columnNames = {""};            
             data = new Object[1][1];
             data [0][0]= new String(PDFirma.resourceBundle.getString("TXT_NOSEENCUENTRANCERTIFICADOS"));
             return data;
        }
    }

    private String[] getColumnas()  {
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
    
    
    public String obtenerDatosFirmante(X509Certificate cert) {

        /* Se obtienen datos del Certificado para armar el sello que se incluira en la estampa de firma*/
        /* Se obtienen los datos del firmante y se arma el texto a colocar en la estampa de firma. */
        String digitallySignedBy = "";
        try {
            // Intentamos extraer email primero desde el DN (EMAILADDRESS=)
            String DN = cert.getSubjectX500Principal().getName();
            String email = null;
            int x = DN.toUpperCase().indexOf("EMAILADDRESS=");
            if (x >= 0) {
                int y = DN.indexOf(',', x);
                y = y >= 0 ? y : DN.length();
                email = DN.substring(x + 13, y).trim();
            } else {
                // Si no esta en el DN, se busca el mail en el campo SubjectAlternativeName
                Collection<List<?>> sans = null;
                try {
                    sans = cert.getSubjectAlternativeNames();
                } catch (Exception e) {
                    // ignore, fallamos silenciosamente a la busqueda por SAN
                }
                if (sans != null) {
                    for (Object sanObj : sans) {
                        @SuppressWarnings("unchecked")
                        List<?> san = (List<?>) sanObj;
                        if (san.size() >= 2 && san.get(1) instanceof String) {
                            String candidate = (String) san.get(1);
                            if (candidate.contains("@")) {
                                email = candidate;
                                break;
                            }
                        }
                    }
                }
            }

            // Usamos BouncyCastle para parsear el Subject y obtener CN, T, O, OU
            String cn = null;
            String t = null;
            String o = null;
            String ou = null;
            try {
                org.bouncycastle.asn1.x500.X500Name x500name = org.bouncycastle.asn1.x500.X500Name.getInstance(cert.getSubjectX500Principal().getEncoded());
                org.bouncycastle.asn1.x500.RDN[] rdns;

                rdns = x500name.getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.CN);
                if (rdns != null && rdns.length > 0) cn = org.bouncycastle.asn1.x500.style.IETFUtils.valueToString(rdns[0].getFirst().getValue());

                rdns = x500name.getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.T);
                if (rdns != null && rdns.length > 0) t = org.bouncycastle.asn1.x500.style.IETFUtils.valueToString(rdns[0].getFirst().getValue());

                rdns = x500name.getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.O);
                if (rdns != null && rdns.length > 0) o = org.bouncycastle.asn1.x500.style.IETFUtils.valueToString(rdns[0].getFirst().getValue());

                rdns = x500name.getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.OU);
                if (rdns != null && rdns.length > 0) ou = org.bouncycastle.asn1.x500.style.IETFUtils.valueToString(rdns[0].getFirst().getValue());
            } catch (Exception e) {
                // Si por alguna razon falla BC, caemos a parseo manual simple del DN
                try {
                    String subjectDN = cert.getSubjectX500Principal().getName();
                    // CN
                    int ix = subjectDN.indexOf("CN=");
                    if (ix >= 0) {
                        int iy = subjectDN.indexOf(',', ix);
                        iy = iy >= 0 ? iy : subjectDN.length();
                        cn = subjectDN.substring(ix + 3, iy).trim();
                    }
                    // O
                    ix = subjectDN.indexOf("O=");
                    if (ix >= 0) {
                        int iy = subjectDN.indexOf(',', ix);
                        iy = iy >= 0 ? iy : subjectDN.length();
                        o = subjectDN.substring(ix + 2, iy).trim();
                    }
                    // OU
                    ix = subjectDN.indexOf("OU=");
                    if (ix >= 0) {
                        int iy = subjectDN.indexOf(',', ix);
                        iy = iy >= 0 ? iy : subjectDN.length();
                        ou = subjectDN.substring(ix + 3, iy).trim();
                    }
                    // T (Title)
                    ix = subjectDN.indexOf("T=");
                    if (ix >= 0) {
                        int iy = subjectDN.indexOf(',', ix);
                        iy = iy >= 0 ? iy : subjectDN.length();
                        t = subjectDN.substring(ix + 2, iy).trim();
                    }
                } catch (Exception ex) {
                    // ignore fallback failures
                }
            }

            // Armamos la cadena resultante manteniendo el formato previo
            StringBuilder sb = new StringBuilder();
            sb.append(PDFirma.resourceBundle.getString("TXT_FIRMADOPOR")).append("\n");
            sb.append("      ").append(cn != null ? cn : "").append("\n");
            if (t != null && !t.isEmpty()) sb.append("      ").append(t).append("\n");
            if (o != null && !o.isEmpty()) sb.append("      ").append(o).append("\n");
            if (ou != null && !ou.isEmpty()) sb.append("      ").append(ou).append("\n");
            // Si hay email, lo agregamos (opcional)
            if (email != null && !email.isEmpty()) sb.append("      ").append(email).append("\n");

            digitallySignedBy = sb.toString();

        } catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_040");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_040"),
                    "ERROR", JOptionPane.ERROR_MESSAGE);
        }
        return digitallySignedBy;
    }

    private String getIssuerCN(X509Certificate cert){
        try{
            org.bouncycastle.asn1.x500.X500Name x500name = org.bouncycastle.asn1.x500.X500Name.getInstance(cert.getIssuerX500Principal().getEncoded());
            org.bouncycastle.asn1.x500.RDN[] rdns = x500name.getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.CN);
            if (rdns != null && rdns.length > 0) return org.bouncycastle.asn1.x500.style.IETFUtils.valueToString(rdns[0].getFirst().getValue());
            return cert.getIssuerX500Principal().getName();
        } catch(Exception e){
            try{ return cert.getIssuerX500Principal().getName(); } catch(Exception ex){ return ""; }
        }
    }

    public boolean verificarCertificado(X509Certificate cert){
        
        try {
            Date hoy = new Date();
            Date no_antes = cert.getNotBefore();
            Date no_despues = cert.getNotAfter();
        
            /* Se verifica la caducidad-periodo de vigencia del Certificado escogido */
            if (hoy.after(no_antes) && hoy.before(no_despues)){
           
                /* El Certificado escogido esta vigente 
                /* Se evalua si el certificado esta por vencer para dar aviso al firmante 
                 * se considera un periodo de dias antes de la fecha de vencimiento y
                 * de ahi en mas se avisa cuantos dias le faltan para que expire. La cantidad de 
                 * dias esta tomada del parametro cantdias_preavisovencimientocertificado seteado
                 * en el documento parametros.properties*/
            
                final long MILLSECS_PER_DAY = 24 * 60 * 60 * 1000; //Milisegundos al d?a 
                long dias_restantes = Math.abs(( hoy.getTime() - no_despues.getTime() )/MILLSECS_PER_DAY); 
            
                if (dias_restantes <= PDFirma.parametros.getcantdias_preavisovencimientocertificado()){
                    JOptionPane.showMessageDialog(this,
                                                  PDFirma.resourceBundle.getString("TXT_RESTAN") + " " + dias_restantes + " " +
                                                  PDFirma.resourceBundle.getString("TXT_DIASVENCIMIENTOCERTIFICADO") + "\n\n" +
                                                  PDFirma.resourceBundle.getString("TXT_RECORDATORIORENOVACION") + " " +
                                                  getIssuerCN(cert),
                                                  PDFirma.resourceBundle.getString("TXT_ADVERTENCIA"), JOptionPane.WARNING_MESSAGE);
                }
                /* Todo ok. se avanza en el proceso de firma */
                return true;
            }
            else if (hoy.after(no_despues)){
                /* Certificado Expirado*/
                JOptionPane.showMessageDialog(this,
                                              PDFirma.resourceBundle.getString("ERROR_024") + "\n" +
                                              PDFirma.resourceBundle.getString("TXT_DEBERENOVARCERTIFICADO")+"\n"+
                                              getIssuerCN(cert),
                                              "ERROR",
                                              JOptionPane.ERROR_MESSAGE);
                PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_024"));
            }
            else if (hoy.before(no_antes)){
                /* Certificado aun no entra en vigencia*/
                JOptionPane.showMessageDialog(this,
                                              PDFirma.resourceBundle.getString("ERROR_025")+"\n" +
                                              PDFirma.resourceBundle.getString("TXT_VERIFIQUEPERIODOVALIDEZ"),                                    
                                              "ERROR", 
                                              JOptionPane.ERROR_MESSAGE);
                PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_025"));
            }
        }
        catch (ParametroIncorrectoException e){
            PDFirma.loguearExcepcion(e,e.getMessage());
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
                getKestore_ie();
                actualizarTabla();
            }
        });
        timer.setRepeats(true);
        timer.setInitialDelay(15);
        timer.start();
      }
    

    public void administrarIdentidadesPFDR(){
        IdentidadRemotaDialog identidadesPFDRDialog = new IdentidadRemotaDialog(this, PDFirma.resourceBundle.getString("TXT_IDENTIDADES_PFDR"), true);
        identidadesPFDRDialog.setVisible(true);
    }
    
    public void actualizarTabla_agregar(IdentidadRemota identidadremota){
        
        String data [] = new String[6];
        
        data[0]=identidadremota.getApenomb();
        data[1]=identidadremota.getCuil();
        data[2] = PDFirma.resourceBundle.getString("TXT_PFDRMINISTERIODEMODERNIZACION");
        data[5] = PDFirma.resourceBundle.getString("TXT_REMOTA");
       
        DefaultTableModel dtm = (DefaultTableModel)tabla_certs.getModel();

        dtm.addRow(data);
        this.tabla_certs.repaint();
        this.tabla_certs.updateUI();
    }
    
    public void actualizarTabla(){
        int selectedRow = tabla_certs.getSelectedRow();
        DefaultTableModel table_model = new DefaultTableModel(getFilas(),getColumnas());
        table_model.fireTableDataChanged();
        tabla_certs.setModel(table_model);
        tabla_certs.changeSelection(selectedRow, 0, false, true);
        this.tabla_certs.repaint();
        this.tabla_certs.updateUI();
    }
    
    public void setjlabelUpdate(String mensaje){
        jLabelUpdate.setText(mensaje);
    }
    
    public void setjProgressBarUpdate(int valor){
        jProgressBarUpdate.setValue(valor);
    }
    
    public void ocultarInfoUpdate(){
       jLabelUpdate.setVisible(false);
       jProgressBarUpdate.setVisible(false);
       jPanelUpdate.setVisible(false);
       this.getContentPane().remove(jPanelUpdate);
    }
    
    public void esperarProcesoUpdate(){
        try {
            threadupdate.join();
        } 
        catch (InterruptedException e) {
            PDFirma.loguearExcepcion(e,"ERROR_072");
        }
    }
}
