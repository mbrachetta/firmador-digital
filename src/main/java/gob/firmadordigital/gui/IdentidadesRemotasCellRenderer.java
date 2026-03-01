package gob.firmadordigital.gui;

import gob.firmadordigital.PDFirma;
import gob.firmadordigital.pfdr.IdentidadRemota;

import java.awt.Component;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

public class IdentidadesRemotasCellRenderer extends DefaultTreeCellRenderer {

    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
                                                  int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        Icon iconogrupo = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconogrupos.png"));
        Icon iconocontacto = new ImageIcon(FrameFirma.class.getClassLoader().getResource("images/iconocontactos.png"));

        if (row == 0) {
            this.setIcon(iconogrupo);
            if (leaf)
                setText(PDFirma.resourceBundle.getString("TXT_NOHAYIDENTIDADESPFDR"));
        } else {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;

            if (node.getUserObject() instanceof IdentidadRemota) {
                setIcon(iconocontacto);
                IdentidadRemota identidad_remota = (IdentidadRemota) node.getUserObject();
                setText(identidad_remota.getApenomb());
            }
        }
        return this;
    }
}
