package gob.firmadordigital.pfdr;


import java.util.Map;

public class JSON_DocumentoFirmadoPFDR {
    private StatusDocumentoFirmadoPFDR status;
    private Map<String, String> metadata;
    private String msg;
    private String documento;

    public StatusDocumentoFirmadoPFDR getStatus() {
        return this.status;
    }

    public void setStatus(StatusDocumentoFirmadoPFDR status) {
        this.status = status;
    }

    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getDocumento() {
        return this.documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getMsg() {
        return this.msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}