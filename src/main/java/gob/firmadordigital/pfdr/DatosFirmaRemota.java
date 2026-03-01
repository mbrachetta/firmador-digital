package gob.firmadordigital.pfdr;

import gob.firmadordigital.PDFirma;
import gob.firmadordigital.excepciones.ParametroIncorrectoException;

import java.nio.charset.StandardCharsets;

import java.util.Base64;
import java.util.Map;

public class DatosFirmaRemota {
    private String cuil;
    private String documento;
    private Map<String,String> metadata;
    private String type; 
    private String urlRedirect;

    public DatosFirmaRemota(String cuil,int ndoc,byte [] documento,Map<String,String> metadata,String type) throws ParametroIncorrectoException {
        
        this.cuil = cuil;
        /* Cargo el PDF y lo codifico como un String en BASE64*/
        this.documento = encodeFileToBase64(documento);
        this.metadata=metadata;
        this.metadata.put("cuil",cuil);
        this.metadata.put("ndoc", Integer.toString(ndoc));
        this.type=type;
        this.urlRedirect = PDFirma.parametros.getUrl_redirectfirmaremota();
    }
    
    public String getCuil(){return cuil;}
    public void setCuil(String cuil){this.cuil = cuil;}

    public String getDocumento(){return documento;}
    public void setDocumento(byte [] documento){this.documento = encodeFileToBase64(documento);}
    
    public Map<String,String> getMetadata(){return metadata;}
    public void setMetadata(int ndoc,Map<String,String> metadata){
        this.metadata = metadata;
        this.metadata.put("cuil", this.cuil);
        this.metadata.put("ndoc",Integer.toString(ndoc));
    }
    
    public String getType(){return type;}
    public void setType(String type){this.type = type;}
    
    public String getUrlRedirect(){return urlRedirect;}
    public void setUrlRedirect(String url){this.urlRedirect = url;}
    
    private String encodeFileToBase64(byte [] bytearchivo) {
        return new String(Base64.getEncoder().encode(bytearchivo),StandardCharsets.US_ASCII); 
    }
 }