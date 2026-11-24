package pe.manchapp.beta;

import android.os.Build;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** AtomicFile with a replace step that also works on Windows for local tests. */
final class CrossFile extends AtomicFile {
    private final File base;
    private final Map<FileOutputStream,File> pending=new HashMap<>();

    CrossFile(File base){super(base);this.base=base;}

    @Override public FileOutputStream startWrite()throws IOException{
        File parent=base.getParentFile();
        if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs()&&!parent.isDirectory())throw new IOException("No pudimos preparar la carpeta.");
        File temp=new File(base.getPath()+".new-"+UUID.randomUUID());
        FileOutputStream out=new FileOutputStream(temp);
        synchronized(pending){pending.put(out,temp);}
        return out;
    }

    @Override public void finishWrite(FileOutputStream out){
        File temp=null;
        synchronized(pending){temp=pending.remove(out);}
        if(temp==null){super.finishWrite(out);return;}
        try{out.flush();out.getFD().sync();}catch(IOException ignored){}finally{try{out.close();}catch(IOException ignored){}}
        try{replace(temp,base);}
        catch(IOException error){temp.delete();throw new IllegalStateException("No pudimos guardar el archivo.",error);}
    }

    @Override public void failWrite(FileOutputStream out){
        File temp=null;
        synchronized(pending){temp=pending.remove(out);}
        if(temp==null){super.failWrite(out);return;}
        try{out.close();}catch(IOException ignored){}
        temp.delete();
    }

    private static void replace(File temp,File base)throws IOException{
        if(Build.VERSION.SDK_INT>=26){
            try{Files.move(temp.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);return;}
            catch(Exception ignored){}
            try{Files.move(temp.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING);return;}
            catch(Exception error){throw new IOException("No pudimos reemplazar el archivo.",error);}
        }
        if(base.exists()&&!base.delete())throw new IOException("No pudimos reemplazar el archivo.");
        if(!temp.renameTo(base))throw new IOException("No pudimos guardar el archivo.");
    }
}
