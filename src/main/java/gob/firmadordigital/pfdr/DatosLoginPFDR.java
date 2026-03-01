package gob.firmadordigital.pfdr;


public class DatosLoginPFDR {
    private String cuil;
    private String pass;
    
    public DatosLoginPFDR(String cuil, String pass) {
        this.cuil = cuil;
        this.pass = pass;
    }
    
    public String getCuil(){return this.cuil;}
    public void setCuil(String cuil){this.cuil = cuil;}

    public String getPass(){return this.pass;}
    public void setPass(String pass){this.pass = pass;}
}
