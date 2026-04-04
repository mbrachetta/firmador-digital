package gob.firmadordigital.model;

import java.util.List;

public class CierreLote {
    private String filtro;
    private int cantdoc; //Cantidad de documentos del lote
    private int cantrecibidos; //en el cliente
    private int cantprocesados; //que se intentan firmar en el cliente
    private int cantfirmados;  //efectivamente firmados sin error
    private int cantfallaron;  //fallo al firmar
    private int cantenviados; //desde el cliente
    private List<DocumentoStatus> resumenlote;

    public CierreLote(String filtro, int cantdoc, int cantrecibidos, int cantprocesados, int cantfirmados, int cantfallaron, int cantenviados, List<DocumentoStatus> resumenlote) {
        this.filtro = filtro;
        this.cantdoc = cantdoc;
        this.cantrecibidos = cantrecibidos;
        this.cantprocesados = cantprocesados;
        this.cantfirmados = cantfirmados;
        this.cantfallaron = cantfallaron;
        this.cantenviados = cantenviados;
        this.resumenlote = resumenlote;
    }

    public String getFiltro() {
        return filtro;
    }

    public void setFiltro(String filtro) {
        this.filtro = filtro;
    }

    public int getCantdoc() {
        return cantdoc;
    }

    public void setCantdoc(int cantdoc) {
        this.cantdoc = cantdoc;
    }

    public int getCantrecibidos() {
        return cantrecibidos;
    }

    public void setCantrecibidos(int cantrecibidos) {
        this.cantrecibidos = cantrecibidos;
    }

    public int getCantprocesados() {
        return cantprocesados;
    }

    public void setCantprocesados(int cantprocesados) {
        this.cantprocesados = cantprocesados;
    }

    public int getCantfirmados() {
        return cantfirmados;
    }

    public void setCantfirmados(int cantfirmados) {
        this.cantfirmados = cantfirmados;
    }

    public int getCantfallaron() {
        return cantfallaron;
    }

    public void setCantfallaron(int cantfallaron) {
        this.cantfallaron = cantfallaron;
    }

    public int getCantenviados() {
        return cantenviados;
    }

    public void setCantenviados(int cantenviados) {
        this.cantenviados = cantenviados;
    }

    public List<DocumentoStatus> getResumenlote() {
        return resumenlote;
    }

    public void setResumenlote(List<DocumentoStatus> resumenlote) {
        this.resumenlote = resumenlote;
    }
}