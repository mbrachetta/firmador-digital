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
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

import java.util.regex.Pattern;


public class OtpPinDialog extends JDialog {
    private String pin;
    private String otp;
    private Dimension screenSize;
    private JLabel jLabel_pin = new JLabel();
    private JPasswordField jPasswordField_pin = new JPasswordField();
    private JLabel jLabel_otp = new JLabel();
    private JPasswordField jPasswordField_otp = new JPasswordField();
    private JButton jButton_Aceptar = new JButton();
    private JButton jButton_Cancelar = new JButton();
    
    private boolean pin_ok=false;
    private boolean otp_ok=false;

    
    public OtpPinDialog() {
        this("", false);
    }

    public OtpPinDialog(String title, boolean modal) {
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
        
        pin="";
        otp="";
        jLabel_pin.setText("Ingrese PIN");
        jLabel_otp.setText("Ingrese OTP");
        
        /* Se valida el formato de ingreso del pin. Minimo 8 caracteres
         * con mayusculas, minusculas y numeros */    
        jPasswordField_pin.setInputVerifier(new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                String pin_pattern = "[A-Za-z0-9]{8,20}$";
                Pattern pattern = Pattern.compile(pin_pattern);
                if (pattern.matcher(((JTextField)input).getText()).matches()){
                    input.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                    pin_ok=true;
                    if (pin_ok && otp_ok)jButton_Aceptar.setEnabled(true);
                    return true;
                }
                else{
                    input.setBorder(BorderFactory.createLineBorder(Color.RED, 1));
                    pin_ok=false;
                    jButton_Aceptar.setEnabled(false);
                    return false;
                }
            }
        });

        /* Se valida el formato de ingreso del otp */    
        jPasswordField_otp.setInputVerifier(new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                String otp_pattern = "[0-9]{6}";
                Pattern pattern = Pattern.compile(otp_pattern);
                if (pattern.matcher(((JTextField)input).getText()).matches()){
                    input.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                    otp_ok=true;
                    if (pin_ok && otp_ok)jButton_Aceptar.setEnabled(true);
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
                if (pin_ok && otp_ok){
                    jButton_Aceptar.setEnabled(true);            
                    jButton_Aceptar.requestFocus();
                }
            }
        });

        
        jButton_Aceptar.setText("Aceptar");
        jButton_Aceptar.addFocusListener(new FocusListener(){

            @Override
            public void focusGained(FocusEvent e) {
                if (pin_ok && otp_ok){
                    jButton_Aceptar.setEnabled(true);
                }
                else jButton_Aceptar.setEnabled(false);

            }

            @Override
            public void focusLost(FocusEvent e) {
                if (pin_ok && otp_ok)jButton_Aceptar.setEnabled(true);
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

        this.getContentPane().add(jLabel_pin);
        this.getContentPane().add(jPasswordField_pin,"growx,wrap");
        this.getContentPane().add(jLabel_otp);
        this.getContentPane().add(jPasswordField_otp,"growx,wrap");
        this.getContentPane().add(jButton_Cancelar,"align right");
        this.getContentPane().add(jButton_Aceptar,"align right");
        this.pack();
    }
        
    public String getPin(){
        return pin;    
    }

    public String getOTP(){
        return otp;
    }

    private void jButton_Aceptar_actionPerformed(ActionEvent e) {
            if (!jPasswordField_otp.getText().isEmpty()&& !jPasswordField_pin.getText().isEmpty()){
                otp = jPasswordField_otp.getText();
                pin = jPasswordField_pin.getText();
                this.dispose();
            }
    }

    private void jButton_Cancelar_actionPerformed(ActionEvent e) {
        this.dispose();
    }
}
