package gob.firmadordigital.gui;


import gob.firmadordigital.PDFirma;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;


public class ProgresoFirmaDialog extends JDialog {

    private JButton jButton1 = new JButton();
    private JProgressBar jProgressBar1 = new JProgressBar(0, 100);
    private JLabel jLabel1 = new JLabel();
    private JLabel jLabel2 = new JLabel();
    private boolean cancelado = false;
    private int cant_firmasOK;
    private int cant_firmasERROR;


    private Dimension screenSize;

    public ProgresoFirmaDialog(String title) {
        super(PDFirma.ffirma, title, false);
        cant_firmasOK = 0;
        cant_firmasERROR = 0;
        try {
            /* Se redefine el comportamiento al cerrar la ventana, para que se cierre adecuadamente la conexion de websocket
             * y se avise al client. */
            addWindowListener(new java.awt.event.WindowAdapter() {
                public void windowClosing(java.awt.event.WindowEvent e) {
                    cancelado = true;
                }
            });
            jbInit();
        } catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_041");
        }
    }

    private void jbInit() throws Exception {
        PDFirma.ffirma.disable();
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setSize(screenSize.width / 2, screenSize.height / 6);
        MigLayout layout = new MigLayout("wrap 2,fill", //Layout constraints
                "[][]", //Column constraints
                "");
        this.setLayout(layout);
        this.setBackground(Color.WHITE);

        Dimension frameSize = this.getSize();
        this.setLocation((screenSize.width - frameSize.width) / 2,
                (screenSize.height - frameSize.height) / 2);


        jButton1.setText(PDFirma.resourceBundle.getString("BOTON_CANCELAR"));
        jButton1.addActionListener(CursorController.createListener(this, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cancelado = true;
            }
        }));
        jButton1.addMouseListener(new MouseListener() {
            public void mouseClicked(MouseEvent e) {
            }

            public void mousePressed(MouseEvent e) {
            }

            public void mouseReleased(MouseEvent e) {
            }

            public void mouseEntered(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }

            public void mouseExited(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        });
        jProgressBar1.setStringPainted(true);
        jLabel1.setVisible(false);
        jLabel2.setVisible(false);
        this.getContentPane().add(jProgressBar1, "growx, push");
        this.getContentPane().add(jButton1, "wrap");
        this.getContentPane().add(jLabel1, "span 2, wrap");
        this.getContentPane().add(jLabel2, "span 2, wrap");
        this.getContentPane().setBackground(Color.WHITE);
    }

    ;


    public void setProgressBarValue(int valor) {
        jProgressBar1.setValue(valor);
        Toolkit.getDefaultToolkit().beep();
        this.repaint();
    }

    public void setMensajeFirmaOk(int cant_documentos) {
        ++cant_firmasOK;
        ImageIcon iconook = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconook.gif"));
        jLabel1.setIcon(iconook);
        if (cant_documentos > 1)
            jLabel1.setText(PDFirma.resourceBundle.getString("TXT_DOCUMENTOSFIRMADOS") + " " + cant_firmasOK + " " +
                    PDFirma.resourceBundle.getString("TXT_DE") + " " + cant_documentos);
        else
            jLabel1.setText(PDFirma.resourceBundle.getString("TXT_DOCUMENTOSFIRMADOS") + " " + cant_firmasOK);
        jLabel1.setVisible(true);
        this.repaint();
    }

    public void setMensajeFirmaFalla(int cant_documentos) {
        ++cant_firmasERROR;
        ImageIcon iconoerror = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconofalla.gif"));
        jLabel2.setIcon(iconoerror);
        if (cant_documentos > 1)
            jLabel2.setText(PDFirma.resourceBundle.getString("TXT_DOCUMENTOSCONERROR") + " " + cant_firmasERROR + " " +
                    PDFirma.resourceBundle.getString("TXT_DE") + " " + cant_documentos);
        else
            jLabel2.setText(PDFirma.resourceBundle.getString("TXT_DOCUMENTOSCONERROR") + " " + cant_firmasERROR);
        jLabel2.setForeground(Color.RED);
        jLabel2.setVisible(true);
        this.repaint();
        Toolkit.getDefaultToolkit().beep();
    }


    public boolean getCancelado() {
        return cancelado;
    }

    public int getCant_firmasOK() {
        return cant_firmasOK;
    }

    public int getCant_firmasERROR() {
        return cant_firmasERROR;
    }
}