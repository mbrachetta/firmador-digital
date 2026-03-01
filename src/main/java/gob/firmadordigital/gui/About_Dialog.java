package gob.firmadordigital.gui;

import gob.firmadordigital.PDFirma;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import net.miginfocom.swing.MigLayout;

public class About_Dialog extends JDialog {
    
    private JLabel jLabel1 = new JLabel();
    private JLabel jLabel2 = new JLabel();
    private JLabel jLabel3 = new JLabel();
    private Dimension screenSize;

    public About_Dialog() {
        this(null, "", false);
    }

    public About_Dialog(Frame parent, String title, boolean modal) {
        super(parent, title, modal);
        try {
            jbInit();
        } 
        catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_066");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_066"), 
                                          "ERROR", 
                                          JOptionPane.ERROR_MESSAGE);
        }
    }

    private void jbInit() throws Exception {
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        MigLayout layout = new MigLayout("insets 10 10 30 40", //Layout constraints - insets top - left - bottom - right 
                                         "", //Column constraints
                                         "");  //Row constraints 
        this.setLayout(layout);
        ImageIcon iconofirma = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconoaplicacion.png"));
        this.setIconImage(iconofirma.getImage());

        jLabel1.setIcon(escalarImagen(new ImageIcon(About_Dialog.class.getClassLoader().getResource("images/marcafirmador.png"))));
        
        jLabel2.setText(convertToMultiline(PDFirma.resourceBundle.getString("LABEL_TITLE") + " " +
                                           PDFirma.parametros.getVersion() + " " + PDFirma.parametros.getRelease() + "\n" + PDFirma.resourceBundle.getString("LABEL_ORGANISMO") + "\n" +
                                           PDFirma.resourceBundle.getString("LABEL_ANIO")));
        jLabel2.setForeground(new Color(112, 51, 56));
        jLabel3.setIcon(escalarImagen(new ImageIcon(About_Dialog.class.getClassLoader().getResource("images/logo_organismo.png"))));

        this.getContentPane().setBackground(Color.WHITE);
        /* Descomentar esta línea si se quiere agregar una marca
         * propia del producto 
        this.getContentPane().add(jLabel1, "align center"); */
        this.getContentPane().add(jLabel2, "align center, wrap");
        this.getContentPane().add(jLabel3, "align center");
        this.pack();
        Dimension frameSize = this.getSize();
        this.setLocation((screenSize.width - frameSize.width) / 2, 
                         (screenSize.height - frameSize.height) / 2);

    }
    
   private ImageIcon escalarImagen(ImageIcon icono){
        Image imagen = icono.getImage();
        double ancho_original = imagen.getWidth(this);
        double alto_original = imagen.getHeight(this);
        double factor_horizontal = (ancho_original*100)/1280;
        double factor_vertical = (alto_original*100)/768;
        double ancho_escala = (factor_horizontal * screenSize.width)/100;
        double alto_escala = (factor_vertical * screenSize.height)/100;
        return new ImageIcon(icono.getImage().getScaledInstance((int)ancho_escala, (int)alto_escala,Image.SCALE_SMOOTH));
    }
    
    public static String convertToMultiline(String orig)    {
        return "<html>" + orig.replaceAll("\n", "<br>");
    }
}
