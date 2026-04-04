package gob.firmadordigital;

import gob.firmadordigital.model.DocumentoAFirmar;
import gob.firmadordigital.model.DocumentoFirmado;

public abstract class PDFSigner {

    protected ThreadFirma hilofirma;

    public PDFSigner(ThreadFirma hilo) {
        this.hilofirma = hilo;
    }

    public abstract DocumentoFirmado firmar(DocumentoAFirmar documentoAFirmar);

}