package gob.firmadordigital.model;

import gob.firmadordigital.PDFirma;

import java.util.Map;

public class DocumentoAFirmar {

    private int ndoc;
    private int cantdoc;
    private String tipo_documento; //PDF o XML
    private String documento;
    private Map<String, String> metadata;
    private String cargo;
    private String razon;
    private String localidad;
    private String ultima_firma; //Determina si es la última firma del documento para cerrarlo con TSA
    private String url_callback;

    public DocumentoAFirmar(int ndoc, int cantdoc, String tipo, String documento, Map<String, String> metadata,
                            String cargo, String razon, String localidad, String ultima_firma, String url_callback) {
        this.ndoc = ndoc;
        this.cantdoc = cantdoc;
        this.tipo_documento = tipo;
        this.documento = documento;
        this.metadata = metadata;
        this.cargo = cargo;
        this.razon = razon;
        this.localidad = localidad;
        this.ultima_firma = ultima_firma;
        this.url_callback = url_callback;
    }

    public int getNdoc() {
        return ndoc;
    }

    public void setNdoc(int ndoc) {
        this.ndoc = ndoc;
    }

    public int getCantdoc() {
        return cantdoc;
    }

    public void setCantdoc(int cantdoc) {
        this.cantdoc = cantdoc;
    }

    public String getTipodocumento() {
        return tipo_documento;
    }

    public void setTipodocumento(String tipo) {
        this.tipo_documento = tipo;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public Map<String, String> getMetadata() {
        PDFirma.filelog.print(PDFirma.fecha_fileLog);
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_GETMETADATA"));
        metadata.forEach((k, v) -> {
            PDFirma.filelog.print(PDFirma.fecha_fileLog);
            PDFirma.filelog.println("INFO Key: " + k + ": Value: " + v);
        });
        return metadata;
    }

    public void setMetadata(Map<String, String> data) {
        this.metadata = data;
        PDFirma.filelog.print(PDFirma.fecha_fileLog);
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_SETMETADATA"));
        metadata.forEach((k, v) -> {
            PDFirma.filelog.print(PDFirma.fecha_fileLog);
            PDFirma.filelog.println("INFO Key: " + k + ": Value: " + v);
        });
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public String getRazon() {
        return razon;
    }

    public void setRazon(String razon) {
        this.razon = razon;
    }

    public String getLocalidad() {
        return localidad;
    }

    public void setLocalidad(String localidad) {
        this.localidad = localidad;
    }

    public String getUltima_firma() {
        return ultima_firma;
    }

    public void setUltima_firma(String ultima_firma) {
        this.ultima_firma = ultima_firma;
    }

    public String getUrlCallback() {
        return url_callback;
    }

    public void setUrlCallback(String url) {
        this.url_callback = url;
    }
}
