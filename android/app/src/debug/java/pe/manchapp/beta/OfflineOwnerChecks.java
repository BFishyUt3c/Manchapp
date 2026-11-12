package pe.manchapp.beta;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.util.UUID;

public final class OfflineOwnerChecks extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);this.arguments=arguments;start();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onStart(){Bundle result=new Bundle();String filename="qa-owner-connected-"+System.nanoTime()+".json";Context context=getTargetContext();
        try{
            String endpoint=arguments.getString("endpoint"),key=arguments.getString("adminKey");LocalStore store=new LocalStore(context,filename);store.accept(HttpApi.call(endpoint,"/api/admin/state",key,null,null),"");
            JSONObject details=new JSONObject().put("customer","Registro manual de prueba").put("phone","900000000").put("fulfillment","pickup").put("payment","cash").put("source","whatsapp");JSONObject created=store.manualOrder(details,new JSONObject().put("papa",2));String id=created.getString("id");
            for(JSONObject patch:new JSONObject[]{new JSONObject().put("paid",true),new JSONObject().put("status","sale_day"),new JSONObject().put("status","pickup_available"),new JSONObject().put("status","delivered")}){
                JSONObject current=null;JSONArray all=store.snapshot().getJSONArray("orders");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(id))current=all.getJSONObject(i);patch.put("id",id).put("version",current.getInt("version"));store.enqueue("order",patch);
            }
            LocalStore reopened=new LocalStore(context,filename);check(reopened.snapshot().getJSONArray("pending").length()==5,"Manual creation and four offline changes survive reopen");
            JSONObject summary=OrderSummary.build(reopened.snapshot(),created.getString("saleDate"));check(summary.getInt("unconfirmed")==1,"Summary distinguishes local requests from accepted orders");
            JSONArray operations=reopened.snapshot().getJSONArray("pending");JSONObject first=operations.getJSONObject(0);
            for(int i=0;i<operations.length();i++){JSONObject operation=operations.getJSONObject(i);reopened.markAttempted(operation.getString("id"));reopened.accept(HttpApi.call(endpoint,"/api/admin/operations",key,null,operation),operation.getString("id"));}
            check(reopened.snapshot().getJSONArray("pending").length()==0,"Queued operations synchronize in version order");
            JSONObject remote=HttpApi.call(endpoint,"/api/admin/state",key,null,null);JSONObject order=null;JSONArray orders=remote.getJSONArray("orders");for(int i=0;i<orders.length();i++)if(orders.getJSONObject(i).getString("id").equals(id))order=orders.getJSONObject(i);
            check(order!=null&&order.getString("status").equals("delivered")&&order.getBoolean("paid")&&order.getInt("version")==5,"Canonical manual order retains all offline changes");
            int count=orders.length();JSONObject retried=HttpApi.call(endpoint,"/api/admin/operations",key,null,first);check(retried.getJSONArray("orders").length()==count,"Manual retry cannot duplicate");
            try(OutputStream out=context.openFileOutput(filename,Context.MODE_PRIVATE)){out.write("{broken".getBytes("UTF-8"));}
            LocalStore recovered=new LocalStore(context,filename);check(recovered.snapshot().has("storeId"),"Damaged primary recovers the previous valid state");JSONArray retry=recovered.snapshot().getJSONArray("pending");for(int i=0;i<retry.length();i++){JSONObject operation=retry.getJSONObject(i);recovered.accept(HttpApi.call(endpoint,"/api/admin/operations",key,null,operation),operation.getString("id"));}check(recovered.snapshot().getJSONArray("pending").length()==0,"Recovery safely retries an already accepted operation");
            JSONObject product=new JSONObject().put("id","merienda-combo").put("name","Combo merienda con 3 cachangas y 2 cafés").put("portion","3 cachangas + 2 cafés").put("category","Combos").put("saleUnit","combo").put("price",7);JSONObject settings=remote.getJSONObject("settings");NativeUi ui=new NativeUi(context);File dir=new File(context.getFilesDir(),"qa-new-flyers");dir.mkdirs();
            for(int style=0;style<3;style++)for(boolean square:new boolean[]{false,true}){Bitmap flyer=FlyerRenderer.render(product,settings,"Reserva para la próxima venta",style,square,ui);check(flyer.getWidth()==720&&flyer.getHeight()==(square?720:1280),"Poster export dimensions");try(OutputStream out=new FileOutputStream(new File(dir,"template-"+style+(square?"-square":"-vertical")+".png"))){flyer.compress(Bitmap.CompressFormat.PNG,100,out);}flyer.recycle();}
            result.putString("stream","PASS: manual WhatsApp order, offline version chain, summary, reconnect, recovery without reset and six poster variants.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}finally{context.deleteFile(filename);context.deleteFile(filename+".recovery");}
    }
}
