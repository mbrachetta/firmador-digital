package gob.firmadordigital.model;

public class DocumentoStatus {
	
	private Integer ndoc;
	private Boolean status;
	private String errorcode;
	
	
	public DocumentoStatus(int ndoc, boolean status, String errorcode) {
		this.ndoc = ndoc;
		this.status = status;
		this.errorcode = errorcode;
	}


	public Integer getNdoc() {
		return ndoc;
	}


	public void setNdoc(int ndoc) {
		this.ndoc = ndoc;
	}


	public Boolean isStatus() {
		return status;
	}


	public void setStatus(boolean status) {
		this.status = status;
	}


	public String getErrorcode() {
		return errorcode;
	}


	public void setErrorcode(String errorcode) {
		this.errorcode = errorcode;
	}
}

