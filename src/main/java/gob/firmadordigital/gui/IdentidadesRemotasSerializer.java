package gob.firmadordigital.gui;

import gob.firmadordigital.PDFirma;
import gob.firmadordigital.pfdr.IdentidadRemota;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class IdentidadesRemotasSerializer {

    public static void encode(ArrayList<IdentidadRemota> obj) throws Exception {
        XMLEncoder e = new XMLEncoder(
                new BufferedOutputStream(
                        new FileOutputStream(PDFirma.fdefault_dir + "/pfdr.xml")));
        e.writeObject(obj);
        e.close();
    }

    public static List<IdentidadRemota> decode() {
        List<IdentidadRemota> result = null;
        try {
            XMLDecoder d = new XMLDecoder(
                    new BufferedInputStream(
                            new FileInputStream(PDFirma.fdefault_dir + "/pfdr.xml")));
            result = (List<IdentidadRemota>) d.readObject();
            d.close();
        } catch (Exception e) {
            PDFirma.loguearExcepcion(e, "ERROR_076");
            result = new ArrayList<IdentidadRemota>();
        } finally {
            return result;
        }
    }
}