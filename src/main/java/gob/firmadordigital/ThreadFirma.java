package gob.firmadordigital;

import com.google.gson.Gson;

import com.itextpdf.layout.element.Image;
import gob.firmadordigital.excepciones.CanceloFirmaException;
import gob.firmadordigital.excepciones.ErrorEnURLCallbackException;
import gob.firmadordigital.excepciones.FiltroException;
import gob.firmadordigital.excepciones.Url_CierreLoteException;
import gob.firmadordigital.excepciones.Url_ServicioDelOrganismoException;
import gob.firmadordigital.gui.FinProcesoDialog;
import gob.firmadordigital.gui.OtpPinDialog;
import gob.firmadordigital.gui.ProgresoFirmaDialog;
import gob.firmadordigital.model.CierreLote;
import gob.firmadordigital.model.DocumentoAEnviar;
import gob.firmadordigital.model.DocumentoAFirmar;
import gob.firmadordigital.model.DocumentoFirmado;
import gob.firmadordigital.model.DocumentoStatus;
import gob.firmadordigital.pfdr.IdentidadRemota;
import gob.firmadordigital.websocket.WebSocketServerEndpoint;

import java.awt.Component;
import java.awt.Cursor;

import java.security.PrivateKey;
import java.security.cert.Certificate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;


public class ThreadFirma extends Thread {

    private ProgresoFirmaDialog progresofirmadialog;
    
    /* Vinculo al WebSocketServerEndpoint */
    private WebSocketServerEndpoint wsse;
    private String filtro;
    
    /* Arreglo donde se carga el resumen de resultados del proceso */
    private List<DocumentoStatus> resumen = new ArrayList<DocumentoStatus>();
       
    /* Cola de documentos a firmar y cola de documentos firmados a enviar */
    private final BlockingQueue<DocumentoAFirmar> cola_DocumentosAFirmar=new LinkedBlockingDeque<DocumentoAFirmar>();
    private final BlockingQueue<DocumentoAEnviar> cola_DocumentosFirmados_aEnviar=new LinkedBlockingDeque<DocumentoAEnviar>();
    
    private final ExecutorService executor=Executors.newFixedThreadPool(3);
    private final ExecutorService executor_firmador=Executors.newFixedThreadPool(3);
    private Runnable requerirDocumentosAFirmar;
    private Runnable firmarDocumentos;
    private Runnable enviarDocumentosFirmados;
    
    private boolean proceso_cancelado = false;
    private boolean proceso_bloqueado = false;
   
    /* Se lleva cuenta de la cantidad de documentos del lote, de la cantidad de recibidos,
     * de la cantidad de procesados (firmados ok + erroneos) y la cantidad que  
     * de enviados a la url de callback del organismo. A partir de estos atributos se controla la barra de progreso. */
    private int cant_documentosLote=1; 
    private int cant_aprocesar=1;
    private int cant_aenviar=1;
    private int contador_recibidos=0;
    private int contador_procesados=0;
    private int contador_enviados=0; 
    
    private PDFSigner pdfsigner;
    // Reutilizar un único cliente JAX-RS para todo el hilo evita crear y no cerrar recursos repetidamente
    private final Client restClient = ClientBuilder.newBuilder().build();

    
    /* Constructor para un hilo de firmas locales */
    public ThreadFirma (Certificate [] cert, PrivateKey privkey, Image img_estampaFirma, String datos_selloFirma, WebSocketServerEndpoint wsse){
        super("ThreadFirma");
        this.wsse=wsse;
        pdfsigner = new PDFSignerLocal(cert, privkey,img_estampaFirma,datos_selloFirma,this);
    }

    /* Constructor para un hilo de firmas remotas */
    public ThreadFirma (IdentidadRemota identidad, WebSocketServerEndpoint wsse)throws CanceloFirmaException{
        super("ThreadFirma");
        
        /* Se requiere OTP y Pin de firma */ 
        OtpPinDialog otpPinDialog = new OtpPinDialog("OTP",true);
        otpPinDialog.setVisible(true);
        String pin=otpPinDialog.getPin();
        String otp=otpPinDialog.getOTP();
        if (!pin.isEmpty()&& !otp.isEmpty()){
            this.wsse=wsse;
            pdfsigner = new PDFSignerPFDR(identidad,pin,otp,this);
        }
        else {
            throw new CanceloFirmaException(PDFirma.resourceBundle.getString("TXT_PROCESODEFIRMACANCELADO"));
        }
    }

    public void run() {

        final Cursor busyCursor = new Cursor(Cursor.WAIT_CURSOR);
        final Component glass = PDFirma.ffirma.getGlassPane();
        glass.setCursor(busyCursor);
        glass.setVisible(true);
        PDFirma.ffirma.setCursor(busyCursor);
            
        progresofirmadialog = new ProgresoFirmaDialog(PDFirma.resourceBundle.getString("TXT_AVANCEPROCESOFIRMA"));
        progresofirmadialog.setVisible(true);
       
                          
        /* se abre el hilo que requiere los documentos a firmar
         * al sistema corporativo y los agrega a la Cola de Documentos a Firmar */
        requerirDocumentosAFirmar = new Runnable() {

            public void run() {

                try{
                    String url = wsse.getUrl_serviciodelorganismo();
                    System.out.println(url);
                    filtro = wsse.getFiltro();
                    DocumentoAFirmar documentoAFirmar=null;
                    int contador = 0;

                    /* Se itera requiriendo los documentos a firmar y encolandolos
                     * en una cola de documentos a firmar */

                    while (contador<cant_documentosLote && !proceso_cancelado && !progresofirmadialog.getCancelado()){
                        micropausa();
                        if (!proceso_bloqueado) {
                            ++contador;
                            WebTarget target = restClient.target(url);
                            Response response = target.request().post(Entity.entity(filtro, MediaType.TEXT_PLAIN));

                            switch (response.getStatus()){
                                case 200:
                                case 201://Ok - respuesta satisfactoria, se obtuvo el documento a firmar
                                        String json = response.readEntity(String.class);
                                        Gson parser = new Gson();
                                        documentoAFirmar = parser.fromJson(json, DocumentoAFirmar.class);
                                        cola_DocumentosAFirmar.add(documentoAFirmar);
                                        ++contador_recibidos;
                                        if (contador_recibidos == 1) {
                                            cant_documentosLote = documentoAFirmar.getCantdoc();
                                            cant_aprocesar = cant_documentosLote;
                                            cant_aenviar = cant_documentosLote;
                                        }
                                        break;
                                case 400: //error - bad request.
                                case 401: //error - unauthorized
                                case 403: //error - forbidden
                                case 404: //error - not found
                                        proceso_bloqueado=true;
                                        --cant_aprocesar; --cant_aenviar;
                                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_030"));
                                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " +
                                                                response.getStatus());
                                        if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                                  PDFirma.resourceBundle.getString("ERROR_030") + "\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+" " +
                                                                         response.getStatus() +"\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),
                                                                         "ERROR",JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                                                /* El usuario decidio cancelar el proceso */
                                                proceso_cancelado=true;
                                        }
                                        proceso_bloqueado=false;
                                        break;
                                case 500: //error - Internal Server Error.
                                case 502: //error - Bad Gateway
                                case 503: //error - Service Unavailable
                                case 504: //error - Gateway timeout
                                        proceso_bloqueado=true;
                                        --cant_aprocesar; --cant_aenviar;
                                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_020"));
                                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " +
                                                                response.getStatus());
                                        if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                                  PDFirma.resourceBundle.getString("ERROR_020") + "\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+" " +
                                                                         response.getStatus() +"\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),"ERROR",JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                                            /* El usuario decidio cancelar el proceso */
                                            proceso_cancelado=true;
                                        }
                                        proceso_bloqueado=false;
                                        break;
                                default:
                                        proceso_bloqueado=true;
                                        --cant_aprocesar; --cant_aenviar;
                                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_031"));
                                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " +
                                                                response.getStatus());
                                        if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                                  PDFirma.resourceBundle.getString("ERROR_031") + "\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+" " +
                                                                         response.getStatus() +"\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),"ERROR",JOptionPane.YES_NO_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                                            /* El usuario decidio cancelar el proceso */
                                            proceso_cancelado=true;
                                        }
                                        proceso_bloqueado=false;
                            }
                        } //fin bloqueo
                    }//fin while
                }
                catch (Url_ServicioDelOrganismoException e){
                    if (!proceso_cancelado){
                        proceso_bloqueado=true;
                        --cant_aprocesar; --cant_aenviar;
                        PDFirma.loguearExcepcion(e,"ERROR_013");
                        JOptionPane.showMessageDialog(PDFirma.ffirma, e.getMessage()+ "\n" +
                                                      PDFirma.resourceBundle.getString("TXT_NOPODRAUSARFIRMADOR"),
                                                      "ERROR", JOptionPane.ERROR_MESSAGE);
                        wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_013"));
                        proceso_cancelado=true;
                        proceso_bloqueado=false;
                    }
                }
                catch (FiltroException e){
                    if(!proceso_cancelado){
                        --cant_aprocesar; --cant_aenviar;
                        proceso_bloqueado=true;
                        PDFirma.loguearExcepcion(e,"ERROR_070");
                        JOptionPane.showMessageDialog(PDFirma.ffirma, e.getMessage()+ "\n" +
                                                      PDFirma.resourceBundle.getString("TXT_NOPODRAUSARFIRMADOR"),
                                                      "ERROR", JOptionPane.ERROR_MESSAGE);
                        wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_070"));
                        proceso_bloqueado=false;
                        proceso_cancelado=true;
                    }
                }
                catch (Exception e){
                    if(!proceso_cancelado){
                        proceso_bloqueado=true;
                        --cant_aprocesar; --cant_aenviar;
                        PDFirma.loguearExcepcion(e,"ERROR_039");
                        JOptionPane.showMessageDialog(PDFirma.ffirma,
                                                      PDFirma.resourceBundle.getString("ERROR_039") +"\n " +
                                                      e.getMessage(), "ERROR",
                                                      JOptionPane.ERROR_MESSAGE);
                        wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_039"));
                        proceso_cancelado=true;
                        proceso_bloqueado=false;
                    }
                }
            }
        };

        /* Se abre el hilo que firma los documentos en espera
         * en la cola de Documentos a Firmar */
        firmarDocumentos = new Runnable() {
            public void run() {

                int contador=0;
                contador_procesados=0;

                while (contador<cant_aprocesar && !proceso_cancelado && !progresofirmadialog.getCancelado()){
                    micropausa();
                    if (!proceso_bloqueado){
                        try {
                            /* Se intenta recuperar un elemento de la cola_documentosAFirmar, esperando como maximo 5 segundos a 
                             * que se cargue algo si esta vacia. */
                            final DocumentoAFirmar documentoAFirmar = cola_DocumentosAFirmar.poll(5,TimeUnit.SECONDS);
                            if (documentoAFirmar != null){
                                ++contador;  
                                
                                /* Se firma el documento y se agrega a la cola de documentos a enviar
                                 * para que sean enviados a la url de callback definida por el organismo */
                                CompletableFuture.supplyAsync(() -> {
                                    return pdfsigner.firmar(documentoAFirmar);
                                },executor_firmador)
                                .thenAccept(documentoFirmado -> {
                                    if (documentoFirmado != null){
                                        DocumentoAEnviar documentoaEnviar = new DocumentoAEnviar(documentoFirmado,documentoAFirmar.getUrlCallback());
                                        cola_DocumentosFirmados_aEnviar.add(documentoaEnviar);
                                        /* Se actualiza el contador de documentos procesados. No se tiene en cuenta aqui
                                         * si el documento se firmo con exito o no. Solo interesa que cantidad de
                                         * documentos han sido obtenidos de la cola y tratados. */
                                    }
                                    else {
                                        /* En este caso el proceso de firma retorno un null, lo que denota un problema 
                                         * excepcional en el proceso de firma para ese documento. Lo informamos como
                                         * documento con firma erronea. */
                                           
                                        documentoFirmado = new DocumentoFirmado(documentoAFirmar.getNdoc(),documentoAFirmar.getCantdoc(),documentoAFirmar.getTipodocumento(),
                                                                 PDFirma.resourceBundle.getString("ERROR_037"),documentoAFirmar.getMetadata(),false,Integer.parseInt("037"));
                                        DocumentoAEnviar documentoaEnviar = new DocumentoAEnviar(documentoFirmado,documentoAFirmar.getUrlCallback());
                                        cola_DocumentosFirmados_aEnviar.add(documentoaEnviar);
                                        /* Se actualiza el contador de documentos procesados. No se tiene en cuenta aqui
                                         * si el documento se firmo con exito o no. Solo interesa que cantidad de
                                         * documentos han sido obtenidos de la cola y tratados. */
                                    } 
                                    ++contador_procesados;
                                });
                            }
                        }
                        catch (InterruptedException e){
                            if (!proceso_cancelado){
                                proceso_bloqueado=true;
                                --cant_aenviar;
                                PDFirma.loguearExcepcion(e,"ERROR_081");                      
                                progresofirmadialog.setMensajeFirmaFalla(cant_documentosLote);
                                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                                  PDFirma.resourceBundle.getString("ERROR_081")+"\n" + 
                                                                 e.getMessage() +"\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),               
                                                                "ERROR",JOptionPane.OK_CANCEL_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                                    proceso_cancelado=true;
                                }
                                wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_081"));
                                proceso_bloqueado=false;
                            }
                        }
                        catch (RejectedExecutionException e){
                            /* Esta excepcion se produce eventualmente cuando luego de
                             * haber realizado el shutdownNow del executor_firmador en el
                             * metodo cancelarProcesoFirma se intenta completar alguna tarea pendiente. 
                             * Solo debe loguearse, no conviene hacer nada mas aqui. */
                            proceso_bloqueado=true;
                            --cant_aenviar;
                            PDFirma.loguearExcepcion(e,"ERROR_037");                        
                            proceso_bloqueado=false;
                        }
                        catch (Exception e) {
                            if (!proceso_cancelado){
                                proceso_bloqueado=true;
                                --cant_aenviar;
                                PDFirma.loguearExcepcion(e,"ERROR_037");                        
                                progresofirmadialog.setMensajeFirmaFalla(cant_documentosLote);
                                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                                  PDFirma.resourceBundle.getString("ERROR_037")+"\n" + 
                                                                 e.getMessage() +"\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),               
                                                                "ERROR",JOptionPane.OK_CANCEL_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                                    proceso_cancelado=true;
                                }
                                wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_037"));
                                proceso_bloqueado=false;
                            }
                        }
                    }//fin if bloqueado
                }//fin while
            }//fin run
        }; //cierra Runnable

        enviarDocumentosFirmados = new Runnable() {
            public void run() {

                DocumentoAEnviar documentoaEnviar=null;
                DocumentoFirmado documentoFirmado=null;
                int contador=0;
                while (contador<cant_aenviar && !proceso_cancelado && !progresofirmadialog.getCancelado()){
                    micropausa();
                    if (!proceso_bloqueado){
                        try {
                            /* Se intenta recuperar un elemento de la cola_DocumentosFirmados_aEnviar, esperando como maximo 10 segundos a 
                             * que se cargue algo si esta vacia. */
                            documentoaEnviar = cola_DocumentosFirmados_aEnviar.poll(5,TimeUnit.SECONDS);                       
                            if (documentoaEnviar != null){
                                ++contador;
                                documentoFirmado = documentoaEnviar.getDocumentoFirmado();
                                /* Se envía el documento firmado a la url de callback definida por el cliente */
                                Gson gson = new Gson();
                                String documentofirmado_json = gson.toJson(documentoFirmado);
                                PDFirma.filelog.print(PDFirma.fecha_fileLog);
                        
                                WebTarget target = restClient.target(documentoaEnviar.getUrlCallback());

                                Response response = target.request().post(Entity.entity(documentofirmado_json, MediaType.APPLICATION_JSON));

                                PDFirma.filelog.print(PDFirma.fecha_fileLog);
                                PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_ESTADORESPUESTADIRECCIONDECALLBACK") + " " +
                                                        response.getStatus());
                                switch (response.getStatus()){
                                    case 200:
                                    case 201: //Ok - respuesta satisfactoria, se envio correctamente el documento firmado
                                            ++contador_enviados;
                                            PDFirma.filelog.print(PDFirma.fecha_fileLog);
                                            PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_DOCUMENTOENVIADOADIRECCIONDECALLBACK"));
                                            DocumentoStatus docstatus = new DocumentoStatus(documentoFirmado.getNdoc(),true,"sin error");
                                            if (documentoFirmado.getEstado()==false){
                                                docstatus.setStatus(false);
                                                docstatus.setErrorcode(documentoFirmado.getDocumento());
                                            }
                                            resumen.add(docstatus);
                                            break;
                                    case 400: //error - bad request.
                                    case 401: //error - unauthorized
                                    case 403: //error - forbidden
                                    case 404: //error - not found
                                            PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                            PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_049") + " " +
                                                    PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " +
                                                                    response.getStatus());
                                              throw new ErrorEnURLCallbackException(PDFirma.resourceBundle.getString("ERROR_049") + " " +
                                                                          PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                                              response.getStatus());
                                    case 500: //error - Internal Server Error.
                                    case 502: //error - Bad Gateway
                                    case 503: //error - Service Unavailable
                                    case 504: //error - Gateway timeout
                                    PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                    PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_060") + " " +
                                                            PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                                    response.getStatus());
                                            throw new ErrorEnURLCallbackException(PDFirma.resourceBundle.getString("ERROR_060") + " " +
                                                                          PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                                                  response.getStatus());
                                    default:
                                    PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                                    PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_061") + " " +
                                                            PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                                    response.getStatus());
                                            throw new ErrorEnURLCallbackException(PDFirma.resourceBundle.getString("ERROR_061") + " " +
                                                                          PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                                                  response.getStatus());
                                }
                            }
                        }
                        catch (ErrorEnURLCallbackException ex){
                            if (!proceso_cancelado){
                                proceso_bloqueado=true;
                                /* Esta excepcion tiene un tratamiento diferente en cuanto al log del error
                                 * porque las causas son diferentes y se loguearon cuando se creo la excepcion */
                                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,ex.getMessage() +"\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),               
                                                                 "ERROR",JOptionPane.OK_CANCEL_OPTION,JOptionPane.ERROR_MESSAGE)!=0){
                                    /* El usuario decidio NO continuar el proceso de firma, cancelo el proceso*/
                                    proceso_cancelado=true;
                                    proceso_bloqueado=false;
                                    break;
                                }
                                proceso_bloqueado=false;
                            }
                        }
                        catch (InterruptedException e){
                            if (!proceso_cancelado){
                                proceso_bloqueado=true;
                                PDFirma.loguearExcepcion(e,"ERROR_082");                        
                                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                                  PDFirma.resourceBundle.getString("ERROR_082")+"\n" + 
                                                                 e.getMessage() +"\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),               
                                                               "ERROR",JOptionPane.OK_CANCEL_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                                    proceso_cancelado=true;
                                }
                                wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_082"));
                                proceso_bloqueado=false;
                            }
                        }
                        catch (Exception e) {
                            if (!proceso_cancelado){
                                proceso_bloqueado=true;
                                PDFirma.loguearExcepcion(e,"ERROR_047");                        
                                if(JOptionPane.showConfirmDialog(PDFirma.ffirma,
                                                                  PDFirma.resourceBundle.getString("ERROR_047")+"\n" + 
                                                                 e.getMessage() +"\n" +
                                                                  PDFirma.resourceBundle.getString("TXT_DESEACONTINUARPROCESOFIRMA"),               
                                                                 "ERROR",JOptionPane.OK_CANCEL_OPTION,JOptionPane.ERROR_MESSAGE)!=0) {
                                    proceso_cancelado=true;
                                }
                                wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_047"));
                                proceso_bloqueado=false;
                            }
                        }
                        finally {
                            /* Avanza la barra de progreso solo si obtuvo algun documento para enviar */
                            if (documentoaEnviar != null){
                                if (documentoaEnviar.getDocumentoFirmado().getEstado()){
                                    progresofirmadialog.setMensajeFirmaOk(cant_documentosLote);
                                }
                                else {
                                    progresofirmadialog.setMensajeFirmaFalla(cant_documentosLote);
                                }
                                progresofirmadialog.setProgressBarValue((contador_enviados) * 100 / cant_documentosLote);              
                                progresofirmadialog.repaint();
                                PDFirma.ffirma.repaint();
                            }
                        }
                    } //fin if bloqueado
                } //fin while
                /* No se permite cerrar la ventana ni los procesos hasta que no haya terminado el
                 * proceso de Update. */
                PDFirma.ffirma.esperarProcesoUpdate();

                /* Se controla que antes de mostrar el dialogo de cierre y terminar el
                 * no haya algun otro dialogo previo abierto */
                while (proceso_bloqueado){micropausa();}

                /* Si se ha definido la url, se envian los datos de fin de proceso, para cerrar el lote al endpoint de la aplicacion corporativa */
                try {        

                    /* Se obtiene la url de cierre de lote, lo que valida que sea correcta
                     * antes de intentar iniciar el proceso. Si no fuera correcta, probablemente porque
                     * no se recibio en el mensaje, se obtiene una excepcion Error_084 y entonces 
                     * no se envia el cierre de lote. No obstante no se interrumpe el normal cierre del
                     * proceso de firma. Es decir, que el informe de cierre de lote se envia si se
                     * tiene una direccion correcta a donde hacerlo y si no, no se envia pero sin
                     * generar ningun tipo de error en el proceso. */
                    String url_cierreLote = wsse.getUrl_cierrelote();
                    
                    /* se ordeno el arreglo con el resumen */
                    Comparator<DocumentoStatus> comp = (DocumentoStatus a, DocumentoStatus b) -> {
                        return a.getNdoc().compareTo(b.getNdoc());
                    };

                    Collections.sort(resumen, comp);
                    CierreLote cierreLote = new CierreLote(filtro,cant_documentosLote,contador_recibidos,contador_procesados,progresofirmadialog.getCant_firmasOK(),progresofirmadialog.getCant_firmasERROR(),contador_enviados,resumen);
                    
                    Gson gson = new Gson();
                    String cierreLote_json = gson.toJson(cierreLote);
                    PDFirma.filelog.print(PDFirma.fecha_fileLog);
                
                    WebTarget target = restClient.target(url_cierreLote);
                    Response response = target.request().post(Entity.entity(cierreLote_json, MediaType.APPLICATION_JSON));


                    PDFirma.filelog.print(PDFirma.fecha_fileLog);
                    PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_ESTADORESPUESTADIRECCIONDECALLBACK") + " " +
                                            response.getStatus());

                    switch (response.getStatus()){
                        case 200:
                        case 201: //Ok - respuesta satisfactoria, se envio correctamente la informacion de cierre de lote
                        PDFirma.filelog.print(PDFirma.fecha_fileLog);
                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("INFO_DOCUMENTOENVIADOADIRECCIONDECALLBACK"));
                            response.close(); 
                            break;
                        case 400: //error - bad request.
                        case 401: //error - unauthorized
                        case 403: //error - forbidden
                        case 404: //error - not found
                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_085") + " " +
                                                PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                    response.getStatus());
                            throw new ErrorEnURLCallbackException(PDFirma.resourceBundle.getString("ERROR_085") + " " +
                                                              PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                                  response.getStatus());
                        case 500: //error - Internal Server Error.
                        case 502: //error - Bad Gateway
                        case 503: //error - Service Unavailable
                        case 504: //error - Gateway timeout
                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_086") + " " +
                                                PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                    response.getStatus());
                            throw new ErrorEnURLCallbackException(PDFirma.resourceBundle.getString("ERROR_086") + " " +
                                                              PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                                  response.getStatus());
                        default:
                        PDFirma.filelog.print (PDFirma.fecha_fileLog + " ");
                        PDFirma.filelog.println(PDFirma.resourceBundle.getString("ERROR_087") + " " +
                                                PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                    response.getStatus());
                            throw new ErrorEnURLCallbackException(PDFirma.resourceBundle.getString("ERROR_087") + " " +
                                                              PDFirma.resourceBundle.getString("TXT_RESPUESTASERVIDORCODIGO")+ " " + 
                                                                  response.getStatus());
                    }
                }
                catch(Url_CierreLoteException ex){
                    PDFirma.loguearExcepcion(ex,"ERROR_084");                      
                    
                    /*
                    JOptionPane.showMessageDialog(PDFirma.ffirma,PDFirma.resourceBundle.getString("ERROR_084"),
                                                  "ERROR", JOptionPane.ERROR_MESSAGE);
                    wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_084")); */
                }                
                catch (ErrorEnURLCallbackException ex){
                    PDFirma.loguearExcepcion(ex,"ERROR_089");                      
                    JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_089"),
                                                  "ERROR", JOptionPane.ERROR_MESSAGE);
                    wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_089"));
                } 
                catch (Exception ex){
                    PDFirma.loguearExcepcion(ex,"ERROR_090");                      
                    JOptionPane.showMessageDialog(PDFirma.ffirma, PDFirma.resourceBundle.getString("ERROR_090"),
                                                  "ERROR", JOptionPane.ERROR_MESSAGE);
                    wsse.sendTextMessage(PDFirma.resourceBundle.getString("ERROR_090"));
                }

                /* Se finaliza el proceso */
                JDialog finprocesoDialog;
                if (!proceso_cancelado && !progresofirmadialog.getCancelado()){                                        
                    /* Se completo el proceso para un lote de documentos */
                    
                    if (cant_documentosLote>0){
                    
                        /* Si el proceso de firma termino normalmente */
                        /* Muestro los datos de fin de proceso al cliente*/
                        progresofirmadialog.dispose();
                        finprocesoDialog = new FinProcesoDialog(PDFirma.ffirma,
                                                 PDFirma.resourceBundle.getString("TXT_MENSAJEPROCESOCOMPLETADO"),true,cant_documentosLote,contador_recibidos,contador_procesados,progresofirmadialog.getCant_firmasOK(),progresofirmadialog.getCant_firmasERROR(),contador_enviados);
                        finprocesoDialog.setVisible(true);
                
                        /* Mando los datos de fin de proceso a la aplicacion web desde donde se invoco al firmador */
                        wsse.sendTextMessage(PDFirma.resourceBundle.getString("TXT_PROCESODEFIRMACOMPLETADO")+ "\n\n" +
                                             PDFirma.resourceBundle.getString("TXT_RECIBIDOS") + " " + contador_recibidos + " " + PDFirma.resourceBundle.getString("TXT_DE")+" " + cant_documentosLote +"\n" +
                                             PDFirma.resourceBundle.getString("TXT_PROCESADOS") + " " + contador_procesados + "\n" +
                                             PDFirma.resourceBundle.getString("TXT_FIRMADOS") +  " " + progresofirmadialog.getCant_firmasOK() +"\n" +
                                             PDFirma.resourceBundle.getString("TXT_ERRONEOS") +  " " + progresofirmadialog.getCant_firmasERROR() + "\n" +
                                             PDFirma.resourceBundle.getString("TXT_ENVIADOS") +  " " + contador_enviados);
                        wsse.cerrarSesion(PDFirma.resourceBundle.getString("TXT_PROCESODEFIRMACOMPLETADO"));
                    }
                    else{
                        
                        /* No se encontraron documentos a la firma*/
                        JOptionPane.showMessageDialog(PDFirma.ffirma,
                                                      PDFirma.resourceBundle.getString("TXT_NODOCUMENTS"),
                                                      PDFirma.resourceBundle.getString("TXT_INFORMACION"),
                                                      JOptionPane.INFORMATION_MESSAGE);
                        wsse.sendTextMessage(PDFirma.resourceBundle.getString("TXT_NODOCUMENTS"));
                        wsse.cerrarSesion(PDFirma.resourceBundle.getString("TXT_NODOCUMENTS"));
                        PDFirma.ffirma.dispose();
                    }
                }
                else {
                    /* El proceso fue cancelado por el usuario o se produjo una excepcion irrecuperable */
                    executor_firmador.shutdownNow();
                    progresofirmadialog.dispose(); 
                    finprocesoDialog = new FinProcesoDialog(PDFirma.ffirma,
                                             PDFirma.resourceBundle.getString("TXT_PROCESODEFIRMACANCELADO"),false,cant_documentosLote,contador_recibidos,contador_procesados,progresofirmadialog.getCant_firmasOK(),progresofirmadialog.getCant_firmasERROR(),contador_enviados);
                    finprocesoDialog.setVisible(true);

                    wsse.sendTextMessage(PDFirma.resourceBundle.getString("TXT_PROCESODEFIRMACANCELADO") + "\n" +
                                         PDFirma.resourceBundle.getString("TXT_RECIBIDOS") + " " + contador_recibidos + " " + PDFirma.resourceBundle.getString("TXT_DE")+" " + cant_documentosLote +"\n" + PDFirma.resourceBundle.getString("TXT_PROCESADOS") + " " + contador_procesados + "\n" + PDFirma.resourceBundle.getString("TXT_FIRMADOS") +  " " + progresofirmadialog.getCant_firmasOK() +"\n" +
                                         PDFirma.resourceBundle.getString("TXT_ERRONEOS") +  " " + progresofirmadialog.getCant_firmasERROR() + "\n" +
                                         PDFirma.resourceBundle.getString("TXT_ENVIADOS") +  " " + contador_enviados);
                    wsse.cerrarSesion(PDFirma.resourceBundle.getString("TXT_PROCESODEFIRMACANCELADO"));
                }
            }//fin run
        }; //cierra Runnable
        executor.execute(requerirDocumentosAFirmar);
        executor.execute(firmarDocumentos);
        executor.execute(enviarDocumentosFirmados);
        executor.shutdown();
    }

    public BlockingQueue<DocumentoAEnviar> getCola_DocumentosFirmados_aEnviar(){
        return cola_DocumentosFirmados_aEnviar;
    }
    
    public ProgresoFirmaDialog getProgresoFirmaDialog(){
        return progresofirmadialog;
    }
    
    public int getCant_documentosLote(){
        return cant_documentosLote;
    }
            
    public void ocultarBarraProgreso(){
        progresofirmadialog.setVisible(false);
    }
    
    public void mostrarBarraProgreso(){
        progresofirmadialog.setVisible(true);
    }
    
    public void cancelarProcesoFirma(){
        /* Se cancela el proceso de firma porque asi lo requirio el usuario */;
        executor_firmador.shutdownNow();
        proceso_cancelado=true;
    }
    
    /*
     * Pausar la ejecución de un trhead durante 1 segundo, para 
     * que otro proceso tome el contro y la variable proceso_bloqueado
     * se pueda desbloquear
     */
     public static void micropausa(){
        try {
          /* pausa de 100 milisegundos */  
          Thread.sleep(100);
        } 
        catch (Exception e) {
            PDFirma.loguearExcepcion(e,"ERROR_091");                        
        }
    } 
     
     public void bloquear(){
         proceso_bloqueado=true;
     }
     
     public void desbloquear(){
         proceso_bloqueado=false;
     }
}



