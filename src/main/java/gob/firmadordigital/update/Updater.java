package gob.firmadordigital.update;

import gob.firmadordigital.PDFirma;
import jupar.objects.Instruction;
import jupar.objects.Modes;
import jupar.parsers.UpdateXMLParser;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;

public class Updater {

    public void update(String instructionsxml, String tmp, Modes mode, String proxyHost, int proxyPort, String usuarioProxy, String passwordProxy) throws SAXException,
            FileNotFoundException, IOException, InterruptedException {

        UpdateXMLParser parser = new UpdateXMLParser();
        ArrayList<Instruction> archivosaactualizar = parser.parse(tmp + File.separator + instructionsxml, mode, proxyHost, proxyPort, usuarioProxy, passwordProxy);
        int cant_archivos = archivosaactualizar.size();
        int contador_procesados = 0;
        Iterator<Instruction> iterator = archivosaactualizar.iterator();
        Instruction instruction;

        while (iterator.hasNext()) {
            instruction = (Instruction) iterator.next();
            switch (instruction.getAction()) {
                case MOVE:
                    copy(tmp + File.separator + instruction.getFilename(), instruction.getDestination());
                    break;
                case DELETE:
                    delete(instruction.getDestination());
                    break;
                case EXECUTE:
                    Runtime.getRuntime().exec("java -jar " + tmp + File.separator + instruction.getFilename());
                    break;
            }
            PDFirma.ffirma.setjProgressBarUpdate((++contador_procesados) * 100 / cant_archivos);
        }
    }

    public void update(JProgressBar jprogressbar, String instructionsxml, String tmp, String dstdir, Modes mode, String proxyHost, int proxyPort, String usuarioProxy, String passwordProxy) throws SAXException,
            FileNotFoundException, IOException, InterruptedException {

        UpdateXMLParser parser = new UpdateXMLParser();
        ArrayList<Instruction> archivosaactualizar = parser.parse(tmp + File.separator + instructionsxml, mode, proxyHost, proxyPort, usuarioProxy, passwordProxy);
        int cant_archivos = archivosaactualizar.size();
        int contador_procesados = 0;
        Iterator<Instruction> iterator = archivosaactualizar.iterator();
        Instruction instruction;

        while (iterator.hasNext()) {
            instruction = (Instruction) iterator.next();
            switch (instruction.getAction()) {
                case MOVE:
                    copy(tmp + File.separator + instruction.getFilename(), dstdir + File.separator + instruction.getDestination());
                    break;
                case DELETE:
                    delete(dstdir + File.separator + instruction.getDestination());
                    break;
                case EXECUTE:
                    Runtime.getRuntime().exec("java -jar " + tmp + File.separator + instruction.getFilename());
                    break;
            }
            jprogressbar.setValue((++contador_procesados) * 100 / cant_archivos);
        }
    }

    private void copy(String source, String destination) throws FileNotFoundException, IOException {
        File srcfile = new File(source);
        File dstfile = new File(destination);
        if (dstfile.isDirectory()) {
            dstfile = new File(destination + File.separator + srcfile.getName());
        } else {
            if (dstfile.getParentFile() != null && !dstfile.getParentFile().exists()) {
                dstfile.getParentFile().mkdirs();
            }
        }

        InputStream in = new FileInputStream(srcfile);
        OutputStream out = new FileOutputStream(dstfile);

        byte[] buffer = new byte[512];
        int length;

        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }

        in.close();
        out.close();
    }

    private void delete(String filename) {
        File file = new File(filename);
        file.delete();
    }
}
