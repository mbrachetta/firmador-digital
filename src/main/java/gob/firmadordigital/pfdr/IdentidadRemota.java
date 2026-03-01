package gob.firmadordigital.pfdr;


public class IdentidadRemota {
    private String apenomb;
    private String cuil;
    
    public IdentidadRemota(){}
    
    public IdentidadRemota(String apenomb, String cuil){
        this.apenomb = apenomb;
        this.cuil = cuil;
    }
    
    public IdentidadRemota(IdentidadRemota identidad){
        this.apenomb = identidad.getApenomb();
        this.cuil = identidad.getCuil();
    }
    
    public String getApenomb(){return apenomb;}
    public void setApenomb(String apenomb){this.apenomb = apenomb;}
    
    public String getCuil(){return cuil;}
    public void setCuil(String cuil){this.cuil=cuil;}

    public String toString(){
        return apenomb;
    }
}
