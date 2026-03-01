package gob.firmadordigital.gui;

import gob.firmadordigital.PDFirma;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class CursorController {
    public static final Cursor busyCursor = new Cursor(Cursor.WAIT_CURSOR);
    public static final Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
    public static final int delay = 500; // en milisegundos

    private CursorController() {}
    
    public static ActionListener createListener(final Component component, final ActionListener mainActionListener) {
        ActionListener actionListener = new ActionListener() {
            public void actionPerformed(final ActionEvent ae) {
                
                    final Component glass = PDFirma.ffirma.getGlassPane();
                    glass.setCursor(busyCursor);
                    glass.setVisible(true);
                    component.setCursor(busyCursor);
                    
                    mainActionListener.actionPerformed(ae);

                    glass.setCursor(defaultCursor);
                    glass.setVisible(false);
                    component.setCursor(defaultCursor);
                }
        };
        return actionListener;
    }
}