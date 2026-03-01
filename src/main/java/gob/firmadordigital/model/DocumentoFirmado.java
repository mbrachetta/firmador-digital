package gob.firmadordigital.model;


import gob.firmadordigital.PDFirma;
import java.util.Map;

public class DocumentoFirmado {

        private int ndoc;
        private int cantdoc;
        private String tipo_documento;
        private String documento;
        private Map<String,String> metadata;
        private Boolean estado;
        private Integer codigo;
        
        public DocumentoFirmado(int ndoc, int cantdoc, String tipo, String documento, Map<String,String> metadata, boolean estado, int codigo){
            this.ndoc=ndoc;
            this.cantdoc=cantdoc;
            this.tipo_documento=tipo;
            this.documento=documento;
            this.metadata = metadata;
            this.estado = estado;
            this.codigo = codigo;
        }
    
	public int getNdoc() {return ndoc;}
	public void setNdoc(int ndoc) {
		this.ndoc = ndoc;
	}

	public int getCantdoc() {return cantdoc;}
	public void setCantdoc(int cantdoc) {
		this.cantdoc = cantdoc;
	}
    
        public String getTipodocumento(){return tipo_documento;}
        public void setTipodocumento (String tipo){
            this.tipo_documento = tipo;
        }
   
	public String getDocumento() {return documento;}
	public void setDocumento(String documento) {
		this.documento = documento;
	}
	
	public Map<String,String> getMetadata() {
        PDFirma.filelog.print(PDFirma.fecha_fileLog);
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_GETMETADATA"));
		metadata.forEach((k,v) -> {
            PDFirma.filelog.print(PDFirma.fecha_fileLog);
            PDFirma.filelog.println("INFO Key: " + k + ": Value: " + v);
		});
		return metadata;
	}
	public void setMetadata(Map<String,String> data) {
		this.metadata = data;
        PDFirma.filelog.print(PDFirma.fecha_fileLog);
        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_SETMETADATA"));
		metadata.forEach((k,v) -> {
            PDFirma.filelog.print(PDFirma.fecha_fileLog);
            PDFirma.filelog.println("INFO Key: " + k + ": Value: " + v);
		});		
	}
		
	public boolean getEstado() {return estado;}
	public void setEstado(boolean estado) {this.estado = estado;}
	
	public int getCodigo() {return codigo;}
	public void setCodigo(int codigo) {this.codigo=codigo;}

}
