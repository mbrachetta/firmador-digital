package gob.firmadordigital.pfdr;

public class StatusDocumentoFirmadoPFDR {
    private Boolean success;

    public StatusDocumentoFirmadoPFDR(Boolean estado) {
        this.success = estado;
    }

    public Boolean getSuccess() {
        return this.success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}