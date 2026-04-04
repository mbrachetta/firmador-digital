package gob.firmadordigital.gui;

import gob.firmadordigital.PDFirma;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FinProcesoDialog extends JDialog {

    private JLabel jLabel_recibidos = new JLabel();
    private JLabel jLabel_procesados = new JLabel();
    private JLabel jLabel_firmadosok = new JLabel();
    private JLabel jLabel_erroneos = new JLabel();
    private JLabel jLabel_enviados = new JLabel();
    private JButton jButton1 = new JButton();

    private boolean estado; //true: completado, false: cancelado
    private int cant_documentosLote;
    private int cant_recibidos;
    private int cant_procesados;
    private int cant_firmadosok;
    private int cant_erroneos;
    private int cant_enviados;


    private Dimension screenSize;

    public FinProcesoDialog(Frame parent, String title, boolean estado, int cant_documentosLote, int cant_recibidos, int cant_procesados, int cant_firmadosok, int cant_erroneos, int cant_enviados) {
        super(parent, title, false);
        this.estado = estado;
        this.cant_documentosLote = cant_documentosLote;
        this.cant_recibidos = cant_recibidos;
        this.cant_procesados = cant_procesados;
        this.cant_firmadosok = cant_firmadosok;
        this.cant_erroneos = cant_erroneos;
        this.cant_enviados = cant_enviados;

        try {
            jbInit();
        } catch (Exception e) {
            e.printStackTrace();
            PDFirma.loguearExcepcion(e, "ERROR_092");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_092"),
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void jbInit() throws Exception {
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setSize(screenSize.width / 6, (int) (screenSize.height / 4.2f));

        this.getRootPane().setBorder(BorderFactory.createMatteBorder(1, 1, 2, 2, Color.LIGHT_GRAY));

        this.setLayout(new MigLayout("insets 20 60 10 10", "[grow]", "[][][][][]15[]"));

        Dimension frameSize = this.getSize();
        this.setLocation((screenSize.width - frameSize.width) / 2,
                (screenSize.height - frameSize.height) / 2);

        ImageIcon icono;
        if (estado) icono = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconoaplicacion.png"));
        else icono = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconoadvertencia.jpg"));
        this.setIconImage(icono.getImage());

        /* Se crea el panel de resumen con los datos de cierre del proceso de firma */
        this.getContentPane().setBackground(Color.WHITE);

        jLabel_recibidos.setText(PDFirma.resourceBundle.getString("TXT_RECIBIDOS") + " " + cant_recibidos + " " +
                PDFirma.resourceBundle.getString("TXT_DE") + " " + cant_documentosLote);
        jLabel_procesados.setText(PDFirma.resourceBundle.getString("TXT_PROCESADOS") + " " + cant_procesados);
        jLabel_firmadosok.setIcon(new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconook.gif")));
        jLabel_firmadosok.setText(PDFirma.resourceBundle.getString("TXT_FIRMADOS") + " " + cant_firmadosok);
        jLabel_erroneos.setIcon(new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconofalla.gif")));
        jLabel_erroneos.setText(PDFirma.resourceBundle.getString("TXT_ERRONEOS") + " " + cant_erroneos);
        jLabel_enviados.setText(PDFirma.resourceBundle.getString("TXT_ENVIADOS") + " " + cant_enviados);
        jButton1.setText(PDFirma.resourceBundle.getString("BOTON_TERMINAR"));
        jButton1.addActionListener(CursorController.createListener(this, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                PDFirma.ffirma.dispose();
            }
        }));

        this.getContentPane().add(jLabel_recibidos, "wrap");
        this.getContentPane().add(jLabel_procesados, "wrap");
        this.getContentPane().add(jLabel_firmadosok, "wrap");
        this.getContentPane().add(jLabel_erroneos, "wrap");
        this.getContentPane().add(jLabel_enviados, "wrap");
        this.getContentPane().add(jButton1, "align right");
    }
}

