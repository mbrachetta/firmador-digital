package gob.firmadordigital;

import gob.firmadordigital.model.DocumentoAFirmar;
import gob.firmadordigital.model.DocumentoFirmado;

public abstract class Signer {

    protected ThreadFirma hilofirma;

    public Signer(ThreadFirma hilo) {
        this.hilofirma = hilo;
    }

    public abstract DocumentoFirmado firmar(DocumentoAFirmar documentoAFirmar);

}