package pe.manchapp.beta;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.io.*;

public final class DeviceBridgeProvider extends ContentProvider {
    private LocalStore store;
    @Override public boolean onCreate(){store=LocalStore.open(getContext());return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"catalog".equals(uri.getLastPathSegment()))throw new FileNotFoundException();
        File directory=new File(getContext().getCacheDir(),"device-bridge");directory.mkdirs();File catalog=new File(directory,"catalog.json");
        try(FileOutputStream out=new FileOutputStream(catalog)){out.write(store.publicCatalog().toString().getBytes("UTF-8"));}catch(Exception error){throw new FileNotFoundException(error.getMessage());}
        return ParcelFileDescriptor.open(catalog,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Bundle call(String method,String argument,Bundle extras){Bundle result=new Bundle();try{if("placeOrder".equals(method)){JSONObject order=store.receiveDeviceOrder(new JSONObject(extras.getString("json","{}")));result.putString("json",order.toString());}else if("order".equals(method)){result.putString("json",store.deviceOrder(argument).toString());}else if("requestMore".equals(method)){result.putString("json",store.requestMore(new JSONObject(extras.getString("json","{}"))).toString());}else throw new IllegalArgumentException("Opción no disponible");result.putBoolean("ok",true);}catch(Exception error){result.putBoolean("ok",false);result.putString("error",error.getMessage()==null?"No pudimos intercambiar los datos.":error.getMessage());}return result;}
    @Override public String getType(Uri uri){return "application/json";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){return 0;}
}
