package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.UUID;

final class MaskEditSession {
    final File directory;
    private MaskEditSession(Context context,String id)throws IOException{if(id==null||!id.matches("[a-fA-F0-9-]{36}"))throw new IOException("Recorte no válido.");directory=new File(new File(context.getFilesDir(),"mask-edits"),id);}
    static MaskEditSession create(Context context,Bitmap source,Bitmap cutout)throws Exception{if(source.getWidth()!=cutout.getWidth()||source.getHeight()!=cutout.getHeight())throw new IOException("Las imágenes del recorte no coinciden.");MaskEditSession session=new MaskEditSession(context,UUID.randomUUID().toString());if(!session.directory.mkdirs())throw new IOException();session.write("source.png",source);session.write("initial.png",cutout);session.write("current.png",cutout);return session;}
    static MaskEditSession open(Context context,String id)throws IOException{return new MaskEditSession(context,id);}
    String id(){return directory.getName();}
    Bitmap read(String name)throws IOException{if(!name.matches("(source|initial|current)\\.png"))throw new IOException();Bitmap bitmap;try(InputStream in=new CrossFile(new File(directory,name)).openRead()){bitmap=BitmapFactory.decodeStream(in);}if(bitmap==null)throw new IOException("No pudimos recuperar este recorte.");return bitmap;}
    void write(String name,Bitmap bitmap)throws Exception{AtomicFile file=new CrossFile(new File(directory,name));FileOutputStream out=null;try{out=file.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);throw error;}}
}
