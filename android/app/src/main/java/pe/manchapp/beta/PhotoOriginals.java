package pe.manchapp.beta;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.security.MessageDigest;
import org.json.JSONObject;

final class PhotoOriginals {
    private static File file(Context context,String id)throws Exception{File dir=new File(context.getFilesDir(),"original-photos");dir.mkdirs();byte[] hash=MessageDigest.getInstance("SHA-256").digest(id.getBytes("UTF-8"));StringBuilder name=new StringBuilder();for(byte value:hash)name.append(String.format(java.util.Locale.US,"%02x",value&255));return new File(dir,name+".txt");}
    static boolean has(Context context,String id){try{return !id.isEmpty()&&file(context,id).isFile();}catch(Exception error){return false;}}
    static void remember(Context context,String id,String encoded)throws Exception{if(!id.isEmpty()&&!has(context,id))replace(context,id,encoded);}
    static void replace(Context context,String id,String encoded)throws Exception{if(id.isEmpty())return;AtomicFile file=new CrossFile(file(context,id));FileOutputStream out=null;try{out=file.startWrite();out.write(encoded.getBytes("UTF-8"));file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);throw error;}}
    static String read(Context context,String id)throws Exception{return SafeJson.read(new CrossFile(file(context,id)).openRead());}
    static String digest(String value)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));StringBuilder hash=new StringBuilder();for(byte part:bytes)hash.append(String.format(java.util.Locale.US,"%02x",part&255));return hash.toString();}
    static String composition(Context context,String id,String image){try{if(id.isEmpty())return "";JSONObject link=new JSONObject(SafeJson.read(new CrossFile(new File(file(context,id).getPath()+".composition.json")).openRead()));return link.optString("hash").equals(digest(image))?link.optString("compositionId"):"";}catch(Exception error){return "";}}
    static void linkComposition(Context context,String id,String image,String compositionId)throws Exception{if(id.isEmpty()||compositionId.isEmpty())return;AtomicFile atomic=new CrossFile(new File(file(context,id).getPath()+".composition.json"));FileOutputStream out=null;try{JSONObject link=new JSONObject().put("compositionId",compositionId).put("hash",digest(image));out=atomic.startWrite();out.write(link.toString().getBytes("UTF-8"));atomic.finishWrite(out);}catch(Exception error){if(out!=null)atomic.failWrite(out);throw error;}}
}
