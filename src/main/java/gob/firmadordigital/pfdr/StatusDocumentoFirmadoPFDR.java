package gob.firmadordigital.pfdr;

public class StatusDocumentoFirmadoPFDR {
    private Boolean success;
      
    public StatusDocumentoFirmadoPFDR(Boolean estado){
        this.success = estado;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
    public Boolean getSuccess() {
        return this.success;
    }
}