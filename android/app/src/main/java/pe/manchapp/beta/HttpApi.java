package pe.manchapp.beta;

import org.json.JSONObject;
import java.io.*;
import java.net.*;

final class HttpApi {
    static final class Failure extends IOException {
        final int status;
        Failure(int status,String message){super(message);this.status=status;}
        boolean retryable(){return status==0||status>=500||status==408||status==429;}
    }
    static String endpoint(String value)throws Exception{
        String result=value.trim().replaceAll("/+$","");URI uri=new URI(result);
        if(!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath()==null||uri.getPath().isEmpty()))throw new IOException("Escribe la dirección completa de la laptop, por ejemplo http://192.168.1.20:4173.");
        return result;
    }
    static JSONObject call(String base,String path,String adminKey,String orderToken,JSONObject body)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint(base)+path).openConnection();
        try{
            connection.setConnectTimeout(4500);connection.setReadTimeout(8000);connection.setRequestProperty("Accept","application/json");
            if(adminKey!=null&&!adminKey.isEmpty())connection.setRequestProperty("X-Manchapp-Key",adminKey);
            if(orderToken!=null&&!orderToken.isEmpty())connection.setRequestProperty("X-Order-Token",orderToken);
            if(body!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=body.toString().getBytes("UTF-8");connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
            int code=connection.getResponseCode();InputStream input=code>=400?connection.getErrorStream():connection.getInputStream();if(input==null)throw new Failure(code,"La laptop no devolvió una respuesta válida.");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream in=input){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1){if(buffer.size()+count>12*1024*1024)throw new Failure(413,"El catálogo es demasiado grande. Reduce el tamaño de las fotos.");buffer.write(bytes,0,count);}}
            JSONObject result;try{result=new JSONObject(buffer.toString("UTF-8"));}catch(Exception invalid){throw new Failure(code>=400?code:502,"La dirección no responde como un servidor de ManchApp.");}
            if(code>=400)throw new Failure(code,result.optString("error","No pudimos completar la solicitud."));return result;
        }catch(SocketTimeoutException|ConnectException|UnknownHostException error){throw new Failure(0,"Sin conexión con la laptop. Tus datos siguen guardados en este celular.");}
        finally{connection.disconnect();}
    }
}
