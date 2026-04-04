package gob.firmadordigital.update;

import gob.firmadordigital.PDFirma;
import jupar.objects.Modes;
import jupar.parsers.DownloaderXMLParser;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;

public class Downloader {

    public void download(String filesxml, String destinationdir, Modes mode, String proxyHost, int proxyPort, String usuarioProxy, String passwordProxy) throws SAXException,
            FileNotFoundException, IOException, InterruptedException {

        DownloaderXMLParser parser = new DownloaderXMLParser();
        ArrayList<String> archivosadescargar = parser.parse(filesxml, mode, proxyHost, proxyPort, usuarioProxy, passwordProxy);
        int cant_archivos = archivosadescargar.size();
        int contador_procesados = 0;
        Iterator iterator = archivosadescargar.iterator();
        java.net.URL url;

        File dir = new File(destinationdir);
        if (!dir.exists()) {
            dir.mkdir();
        }

        while (iterator.hasNext()) {
            url = new java.net.URL((String) iterator.next());
            wget(url, destinationdir + File.separator + new File(url.getFile()).getName(), proxyHost, proxyPort, usuarioProxy, passwordProxy);
            PDFirma.ffirma.setjProgressBarUpdate((++contador_procesados) * 100 / cant_archivos);
        }
    }

    private void wget(java.net.URL url, String destination, String proxyHost, int proxyPort, String usuarioProxy, String passwordProxy) throws MalformedURLException, IOException {

        URLConnection conn;
        if (proxyHost != null) {
            Proxy proxy;
            if (usuarioProxy != null) {
                proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
                Authenticator authenticator = new Authenticator() {
                    public PasswordAuthentication getPasswordAuthentication() {
                        return (new PasswordAuthentication(usuarioProxy, passwordProxy.toCharArray()));
                    }
                };
                Authenticator.setDefault(authenticator);
            } else {
                proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            }
            conn = url.openConnection(proxy);
        } else {
            conn = url.openConnection();
        }
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 ( compatible ) ");
        conn.setRequestProperty("Accept", "*/*");
        /* Se pasa el usuario y password para acceder al directorio protegido*/
        String authStr = Base64.getEncoder().encodeToString("updatefirmador:1QQ2ww3ee4rr".getBytes());
        conn.setRequestProperty("Authorization", "Basic " + authStr);
        java.io.InputStream in = conn.getInputStream();

        File dstfile = new File(destination);
        OutputStream out = new FileOutputStream(dstfile);

        byte[] buffer = new byte[512];
        int length;

        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
        in.close();
        out.close();
    }
}
