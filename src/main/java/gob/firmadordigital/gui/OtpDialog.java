package gob.firmadordigital.gui;

import gob.firmadordigital.PDFirma;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.BorderFactory;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import net.miginfocom.swing.MigLayout;

import java.util.regex.Pattern;


public class OtpDialog extends JDialog {
    private String otp;
    private Dimension screenSize;
    private JLabel jLabel_otp = new JLabel();
    private JPasswordField jPasswordField_otp = new JPasswordField();
    private JButton jButton_Aceptar = new JButton();
    private JButton jButton_Cancelar = new JButton();
    private JLabel jLabel_otpInvalido = new JLabel();
    
    private boolean otp_ok=false;

    public OtpDialog() {
        this("", false);
    }

    public OtpDialog(String title, boolean modal) {
        super(PDFirma.ffirma, title, modal);
        jbInit();
    }

    private void jbInit() {
        
        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setSize((int)(screenSize.width*0.2),screenSize.height/6);

        this.setBackground(Color.LIGHT_GRAY);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension frameSize = this.getSize();
        this.setLocation((screenSize.width - frameSize.width) / 2, 
                         (screenSize.height - frameSize.height) / 2);
        
        MigLayout layout = new MigLayout("insets 10 10 10 20");  
        this.setLayout(layout);
        
        otp="";
        jLabel_otp.setText("Ingrese OTP");
        jPasswordField_otp.setInputVerifier(new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                /* Mínimo de 8 caracteres debiendo contener minusculas, 
                 * mayusculas y numeros. */
                //String otp_pattern = "[A-Za-z0-9\\s]{8,15}$";
                String otp_pattern = "[0-9]{6}$";
                Pattern pattern = Pattern.compile(otp_pattern);
                if (pattern.matcher(((JPasswordField)input).getText()).matches()){
                    input.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                    otp_ok=true;
                    if (otp_ok)jButton_Aceptar.setEnabled(true);
                    return true;
                }
                else{
                    input.setBorder(BorderFactory.createLineBorder(Color.RED, 1));
                    otp_ok=false;
                    jButton_Aceptar.setEnabled(false);
                    return false;
                }
            }
        });
        jPasswordField_otp.addKeyListener(new KeyListener(){
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                String otp_pattern = "[0-9]{6}";
                Pattern pattern = Pattern.compile(otp_pattern);
                if (pattern.matcher(jPasswordField_otp.getText()).matches()){
                    jPasswordField_otp.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                    otp_ok=true;
                }
                if (otp_ok){
                    jButton_Aceptar.setEnabled(true);  
                    jButton_Aceptar.requestFocus();
                }
            }
        });


        jButton_Aceptar.setText("Aceptar");
        jButton_Aceptar.addFocusListener(new FocusListener(){

            @Override
            public void focusGained(FocusEvent e) {
                if (otp_ok)jButton_Aceptar.setEnabled(true);
                else jButton_Aceptar.setEnabled(false);
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (otp_ok)jButton_Aceptar.setEnabled(true);
                else jButton_Aceptar.setEnabled(false);
            }
        });
        jButton_Aceptar.addActionListener(CursorController.createListener(this,new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    jButton_Aceptar_actionPerformed(e);
                }
            }));
        jButton_Aceptar.setEnabled(false);

        jButton_Cancelar.setText("Cancelar");
        jButton_Cancelar.addActionListener(CursorController.createListener(this,new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    jButton_Cancelar_actionPerformed(e);
                }
            }));
        jLabel_otpInvalido.setForeground(Color.RED);
        jLabel_otpInvalido.setVisible(false);

        this.getContentPane().add(jLabel_otp);
        this.getContentPane().add(jPasswordField_otp,"growx,wrap");
        this.getContentPane().add(jButton_Cancelar,"align right,push");
        this.getContentPane().add(jButton_Aceptar,"align right,wrap");
        this.getContentPane().add(jLabel_otpInvalido,"span,alignx center");
        this.pack();
    }
        
    public String getOTP(){
        return otp;
    }

    private void jButton_Aceptar_actionPerformed(ActionEvent e) {
            if (!jPasswordField_otp.getText().isEmpty()){
                otp = jPasswordField_otp.getText();
                this.dispose();
            }
    }

    private void jButton_Cancelar_actionPerformed(ActionEvent e) {
       
        this.dispose();
    }
    
    public void setMensaje_ultimoIntento(){
        jLabel_otpInvalido.setText(PDFirma.resourceBundle.getString("TXT_OTPINVALIDO")+" " +
                                   PDFirma.resourceBundle.getString("TXT_ULTIMOINTENTO"));
        jLabel_otpInvalido.setVisible(true);
        this.pack();
    }
}
