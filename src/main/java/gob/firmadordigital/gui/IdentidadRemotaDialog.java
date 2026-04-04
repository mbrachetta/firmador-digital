package gob.firmadordigital.gui;


import gob.firmadordigital.PDFirma;
import gob.firmadordigital.pfdr.IdentidadRemota;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.regex.Pattern;


public class IdentidadRemotaDialog extends JDialog {

    /* JTabbed y sus componentes */
    private JTabbedPane jTabbedPane1 = new JTabbedPane();
    private JPanel JPanelFirma_Remota = new JPanel();

    /* Componenetes Panel Firma Remota*/
    private JPanel jPanelNuevaIdentidadRemota = new JPanel();
    private JLabel jLabel_apenombPFDR = new JLabel();
    private JLabel jLabel_cuilPFDR = new JLabel();
    private JLabel jLabel_formatoCuil = new JLabel();
    private JTextField jTextField_apenombPFDR = new JTextField();
    private JTextField jTextField_cuilPFDR = new JTextField();
    private JButton jButton_AgregarNuevaIdentidadRemota = new JButton();
    private JPanel jPanelArbolIdentidadesRemotas = new JPanel();
    private JTree arbol_identidadesRemotas = new JTree();
    private JScrollPane scrlDir_arbol_identidadesRemotas = new JScrollPane();
    private JPopupMenu menuContextual = new JPopupMenu();
    private JMenuItem jMenuItem_Eliminar = new JMenuItem();
    private MouseListener ml;

    private Dimension screenSize;
    private boolean apenomb_ok = false;
    private boolean cuil_ok = false;

    public IdentidadRemotaDialog() {
        this(null, "", false);
    }

    public IdentidadRemotaDialog(Frame parent, String title, boolean modal) {
        super(parent, title, modal);
        try {
            jbInit();
        } catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_043");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_043") + "\n" +
                            e.getMessage(), "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void jbInit() throws Exception {

        screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setSize(screenSize.width / 2, (int) (screenSize.height * 0.4));
        MigLayout layout = new MigLayout();
        this.setLayout(layout);
        this.setResizable(false);

        Dimension frameSize = this.getSize();
        this.setLocation((screenSize.width - frameSize.width) / 2,
                (screenSize.height - frameSize.height) / 2);

        jTabbedPane1.setBackground(new Color(231, 231, 231));

        crearPanelFirmaRemota();
        jTabbedPane1.addTab(PDFirma.resourceBundle.getString("TXT_FIRMAREMOTA"), JPanelFirma_Remota);

        this.getContentPane().setBackground(new Color(247, 247, 247));
        this.getContentPane().add(jTabbedPane1, "growx, growy, push,span,wrap");
    }

    private void crearPanelFirmaRemota() {
        JPanelFirma_Remota.setBackground(new Color(237, 237, 237));
        JPanelFirma_Remota.setLayout(new MigLayout("aligny top"));

        jPanelNuevaIdentidadRemota.setLayout(new MigLayout("gapy 0,fill", "", ""));
        jPanelNuevaIdentidadRemota.setBorder(BorderFactory.createTitledBorder(PDFirma.resourceBundle.getString("TXT_NUEVAIDENTIDADPFDR")));

        jLabel_apenombPFDR.setText(PDFirma.resourceBundle.getString("LABEL_APENOMBPDFR"));
        jLabel_cuilPFDR.setText(PDFirma.resourceBundle.getString("LABEL_CUILPFDR"));
        jLabel_formatoCuil.setText("99-99999999-9");
        jLabel_formatoCuil.setFont(jLabel_formatoCuil.getFont().deriveFont(9f));
        jLabel_formatoCuil.setForeground(Color.gray);

        /* Se valida el formato de ingreso de nombre y apellido */
        jTextField_apenombPFDR.setInputVerifier(new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                String apenomb_pattern = "[A-Za-z\\s]{2,50}$";
                Pattern pattern = Pattern.compile(apenomb_pattern);
                if (pattern.matcher(((JTextField) input).getText()).matches()) {
                    input.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                    apenomb_ok = true;
                    if (apenomb_ok && cuil_ok) jButton_AgregarNuevaIdentidadRemota.setEnabled(true);
                    return true;
                } else {
                    input.setBorder(BorderFactory.createLineBorder(Color.RED, 1));
                    apenomb_ok = false;
                    jButton_AgregarNuevaIdentidadRemota.setEnabled(false);
                    return false;
                }
            }
        });

        /* Se valida el formato de ingreso de cuil */
        jTextField_cuilPFDR.setInputVerifier(new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                String cuil_pattern = "(20|23|24|27)-[0-9]{8}-[0-9]";
                Pattern pattern = Pattern.compile(cuil_pattern);
                if (pattern.matcher(((JTextField) input).getText()).matches()) {
                    input.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                    cuil_ok = true;
                    if (apenomb_ok && cuil_ok) jButton_AgregarNuevaIdentidadRemota.setEnabled(true);
                    return true;
                } else {
                    input.setBorder(BorderFactory.createLineBorder(Color.RED, 1));
                    cuil_ok = false;
                    jButton_AgregarNuevaIdentidadRemota.setEnabled(false);
                    return false;
                }
            }
        });

        jTextField_cuilPFDR.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                String cuil_pattern = "(20|23|24|27)-[0-9]{8}-[0-9]";
                Pattern pattern = Pattern.compile(cuil_pattern);
                if (pattern.matcher(jTextField_cuilPFDR.getText()).matches()) {
                    jTextField_cuilPFDR.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                    System.out.println(jTextField_cuilPFDR.getText());
                    cuil_ok = true;
                }
                if (apenomb_ok && cuil_ok) {
                    jButton_AgregarNuevaIdentidadRemota.setEnabled(true);
                }
            }
        });


        jButton_AgregarNuevaIdentidadRemota.setText(PDFirma.resourceBundle.getString("BOTON_AGREGARNUEVAIDENTIDADREMOTA"));
        jButton_AgregarNuevaIdentidadRemota.setToolTipText(PDFirma.resourceBundle.getString("TXT_AGREGANUEVAIDENTIDADPFDR"));
        jButton_AgregarNuevaIdentidadRemota.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                agregarIdentidadRemota();
            }
        });
        jButton_AgregarNuevaIdentidadRemota.setEnabled(false);
        jButton_AgregarNuevaIdentidadRemota.addFocusListener(new FocusListener() {

            @Override
            public void focusGained(FocusEvent e) {
                if (apenomb_ok && cuil_ok) jButton_AgregarNuevaIdentidadRemota.setEnabled(true);
                else jButton_AgregarNuevaIdentidadRemota.setEnabled(false);

            }

            @Override
            public void focusLost(FocusEvent e) {
                if (apenomb_ok && cuil_ok) jButton_AgregarNuevaIdentidadRemota.setEnabled(true);
                else jButton_AgregarNuevaIdentidadRemota.setEnabled(false);
            }
        });

        jPanelNuevaIdentidadRemota.add(jLabel_apenombPFDR, "span 2");
        jPanelNuevaIdentidadRemota.add(jLabel_cuilPFDR, "shrink x,split 2");
        jPanelNuevaIdentidadRemota.add(jLabel_formatoCuil, "align left, wrap");
        jPanelNuevaIdentidadRemota.add(jTextField_apenombPFDR, "span 2,growx,push");
        jPanelNuevaIdentidadRemota.add(jTextField_cuilPFDR, "span 2,growx,push");
        jPanelNuevaIdentidadRemota.add(jButton_AgregarNuevaIdentidadRemota, "span,al right,wrap");

        arbol_identidadesRemotas.setCellRenderer(new IdentidadesRemotasCellRenderer());
        arbol_identidadesRemotas.setRowHeight(arbol_identidadesRemotas.getFontMetrics(arbol_identidadesRemotas.getFont()).getHeight());

        cargarIdentidadesRemotas();

        scrlDir_arbol_identidadesRemotas.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrlDir_arbol_identidadesRemotas.getViewport().add(arbol_identidadesRemotas);

        jMenuItem_Eliminar.setIcon(new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/boton_eliminar.png")));
        jMenuItem_Eliminar.setText(PDFirma.resourceBundle.getString("MENU_ITEM_ELIMINAR"));
        jMenuItem_Eliminar.setToolTipText(PDFirma.resourceBundle.getString("TXT_OPCIONBORRARIDENTIDADPFDR"));
        jMenuItem_Eliminar.addActionListener(CursorController.createListener(this, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                borrarIdentidadRemota();
            }
        }));

        ml = new MouseListener() {
            public void mouseClicked(MouseEvent e) {
            }

            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menuContextual(e);
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menuContextual(e);
                }
            }

            public void mouseEntered(MouseEvent e) {
            }

            public void mouseExited(MouseEvent e) {
            }
        };

        arbol_identidadesRemotas.addMouseListener(ml);
        menuContextual.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        menuContextual.add(jMenuItem_Eliminar);

        jPanelArbolIdentidadesRemotas.setLayout(new MigLayout("insets 5"));
        jPanelArbolIdentidadesRemotas.setBorder(BorderFactory.createTitledBorder(PDFirma.resourceBundle.getString("TXT_IDENTIDADES_PFDR")));
        jPanelArbolIdentidadesRemotas.add(scrlDir_arbol_identidadesRemotas, "growx, growy, push");

        JPanelFirma_Remota.add(this.jPanelNuevaIdentidadRemota, "width 100%, wrap");
        JPanelFirma_Remota.add(this.jPanelArbolIdentidadesRemotas, "width 100%");
        this.jTextField_apenombPFDR.grabFocus();
    }

    public void cargarIdentidadesRemotas() {
        DefaultMutableTreeNode nodoOrigen = new DefaultMutableTreeNode(PDFirma.resourceBundle.getString("TXT_IDENTIDADES_PFDR"));
        nodoOrigen.removeAllChildren();

        DefaultMutableTreeNode curridentidad;

        try {
            /* Se agregan identidades remotas */
            for (IdentidadRemota identidad_remota : PDFirma.lista_identidadesremotas) {
                curridentidad = new DefaultMutableTreeNode(identidad_remota);
                nodoOrigen.add(curridentidad);
            }
        } catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_044");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_044") + "\n" +
                            e.getMessage(), "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            arbol_identidadesRemotas.setModel(new DefaultTreeModel(nodoOrigen));
            arbol_identidadesRemotas.expandRow(0);
        }
    }

    private void menuContextual(MouseEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) arbol_identidadesRemotas.getLastSelectedPathComponent();
        if (node != null && node.getLevel() > 0) {
            menuContextual.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    public void borrarIdentidadRemota() {
        if (arbol_identidadesRemotas.getSelectionCount() > 0) {

            /* Se arma un Vector con las identidades pfdr a eliminar*/
            DefaultMutableTreeNode node, padre;
            TreePath[] tp_seleccionados = arbol_identidadesRemotas.getSelectionPaths();

            for (int i = 0; i < tp_seleccionados.length; i++) {
                node = (DefaultMutableTreeNode) tp_seleccionados[i].getLastPathComponent();
                padre = (DefaultMutableTreeNode) node.getParent();

                if (node.isLeaf() && (node.getUserObject() instanceof IdentidadRemota)) {
                    IdentidadRemota identidadremota = (IdentidadRemota) node.getUserObject();
                    PDFirma.lista_identidadesremotas.remove(identidadremota);
                    IdentidadesRemotasSerializer cs = new IdentidadesRemotasSerializer();

                    try {
                        cs.encode((ArrayList) PDFirma.lista_identidadesremotas);
                    } catch (Exception e) {
                        PDFirma.loguearExcepcion(e, "ERROR_045");
                        JOptionPane.showMessageDialog(this,
                                PDFirma.resourceBundle.getString("ERROR_045") + "\n" +
                                        e.getMessage(), "ERROR",
                                JOptionPane.ERROR_MESSAGE);
                    } finally {
                        cargarIdentidadesRemotas();
                        PDFirma.ffirma.actualizarTabla();
                    }
                }
            }
        }
    }

    public void agregarIdentidadRemota() {

        try {
            if (apenomb_ok && cuil_ok) {
                IdentidadRemota nueva_identidadremota = new IdentidadRemota(jTextField_apenombPFDR.getText(), jTextField_cuilPFDR.getText());
                PDFirma.lista_identidadesremotas.add(nueva_identidadremota);
                if (PDFirma.lista_identidadesremotas.size() > 0 &&
                        PDFirma.lista_identidadesremotas.get(0).getApenomb().compareTo(PDFirma.resourceBundle.getString("TXT_NOHAYIDENTIDADESPFDR")) == 0)
                    PDFirma.lista_identidadesremotas.remove(0);
                IdentidadesRemotasSerializer cs = new IdentidadesRemotasSerializer();
                cs.encode((ArrayList) PDFirma.lista_identidadesremotas);
                PDFirma.ffirma.actualizarTabla_agregar(nueva_identidadremota);
                jTextField_apenombPFDR.setText("");
                jTextField_cuilPFDR.setText("");
                jTextField_apenombPFDR.requestFocus();
            } else {
                JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_048"), "ERROR",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_046");
            JOptionPane.showMessageDialog(this, PDFirma.resourceBundle.getString("ERROR_046") + "\n" +
                            e.getMessage(), "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            cargarIdentidadesRemotas();
        }
    }
}