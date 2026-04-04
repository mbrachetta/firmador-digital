package gob.firmadordigital.model;

public class DocumentoAEnviar {

    private DocumentoFirmado documentoFirmado;
    private String url_callback;

    public DocumentoAEnviar(DocumentoFirmado doc, String url) {
        documentoFirmado = doc;
        url_callback = url;
    }

    public DocumentoFirmado getDocumentoFirmado() {
        return documentoFirmado;
    }

    public String getUrlCallback() {
        return url_callback;
    }
}
