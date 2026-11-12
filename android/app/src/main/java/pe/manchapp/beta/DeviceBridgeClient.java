package pe.manchapp.beta;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.InputStream;

final class DeviceBridgeClient {
    private static final Uri CATALOG=Uri.parse("content://pe.manchapp.beta.bridge/catalog");
    static JSONObject catalog(Context context)throws Exception{try(InputStream in=context.getContentResolver().openInputStream(CATALOG)){if(in==null)throw new Exception("Instala también ManchApp Emprendedora en este celular.");return new JSONObject(SafeJson.read(in));}}
    static JSONObject placeOrder(Context context,JSONObject order)throws Exception{Bundle extras=new Bundle();extras.putString("json",order.toString());return result(context.getContentResolver().call(CATALOG,"placeOrder","",extras));}
    static JSONObject order(Context context,String id)throws Exception{return result(context.getContentResolver().call(CATALOG,"order",id,null));}
    private static JSONObject result(Bundle bundle)throws Exception{if(bundle==null)throw new Exception("Instala también ManchApp Emprendedora en este celular.");if(!bundle.getBoolean("ok"))throw new Exception(bundle.getString("error","No pudimos intercambiar los datos."));return new JSONObject(bundle.getString("json","{}"));}
}
