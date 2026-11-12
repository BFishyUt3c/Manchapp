package pe.manchapp.beta;

import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;

final class SafeJson {
    private final AtomicFile primary,recovery;
    private final String required;
    private String lastValid;
    SafeJson(File file,String required){primary=new CrossFile(file);recovery=new CrossFile(new File(file.getPath()+".recovery"));this.required=required;}
    JSONObject load(JSONObject seed)throws Exception{
        boolean exists=primary.getBaseFile().exists()||new File(primary.getBaseFile().getPath()+".bak").exists();
        if(!exists&&!recovery.getBaseFile().exists()){lastValid=seed.toString();write(recovery,lastValid);write(primary,lastValid);return new JSONObject(lastValid);}
        try{JSONObject value=parse(primary);lastValid=value.toString();return value;}
        catch(Exception invalid){
            if(primary.getBaseFile().exists())try(InputStream in=new FileInputStream(primary.getBaseFile());OutputStream out=new FileOutputStream(new File(primary.getBaseFile().getPath()+".damaged-"+System.currentTimeMillis()))){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);}
            try{JSONObject value=parse(recovery);lastValid=value.toString();write(primary,lastValid);return value;}
            catch(Exception lost){throw new IOException("No pudimos recuperar tus datos. Los archivos se conservaron para revisarlos; no se reemplazaron por un catálogo nuevo.",lost);}
        }
    }
    private JSONObject parse(AtomicFile file)throws Exception{JSONObject value=new JSONObject(read(file.openRead()));if(value.opt(required)==null)throw new IOException("Datos incompletos.");JSONObject catalog=required.equals("catalog")?value.optJSONObject("catalog"):value;if(catalog==null||catalog.optJSONObject("settings")==null||catalog.optJSONArray("products")==null||value.optJSONArray("orders")==null)throw new IOException("Datos incompletos.");if(required.equals("catalog")&&(value.optJSONObject("cart")==null||value.optJSONObject("details")==null))throw new IOException("Datos incompletos.");return value;}
    static String read(InputStream stream)throws IOException{try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);return out.toString("UTF-8");}}
    void save(JSONObject value)throws IOException{String next=value.toString();if(lastValid!=null)write(recovery,lastValid);write(primary,next);lastValid=next;}
    private static void write(AtomicFile file,String value)throws IOException{FileOutputStream out=null;try{out=file.startWrite();out.write(value.getBytes("UTF-8"));file.finishWrite(out);}catch(IOException error){if(out!=null)file.failWrite(out);throw error;}}
}
