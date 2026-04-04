package gob.firmadordigital.pfdr;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class DocumentoAFirmarPFDR {
    private String docName;
    private String document;
    private String pin;

    public DocumentoAFirmarPFDR(int ndoc, byte[] documento, String pin) {
        this.docName = "documento" + ndoc + ".pdf";
        this.document = encodeFileToBase64(documento);
        this.pin = pin;
    }

    public String getDocName() {
        return this.docName;
    }

    public void setDocName(int ndoc) {
        this.docName = "documento" + ndoc + ".pdf";
    }

    public String getDocument() {
        return this.document;
    }

    public void setDocument(byte[] documento) {
        this.document = encodeFileToBase64(documento);
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    private String encodeFileToBase64(byte[] bytearchivo) {
        return new String(Base64.getEncoder().encode(bytearchivo), StandardCharsets.US_ASCII);
    }
}