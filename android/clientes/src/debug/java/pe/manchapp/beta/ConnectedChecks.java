package pe.manchapp.beta;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import org.json.*;
import java.util.UUID;

public final class ConnectedChecks extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);this.arguments=arguments;start();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onStart(){Bundle result=new Bundle();String filename="qa-connected-"+System.nanoTime()+".json";Context context=getTargetContext();
        try{
            String endpoint=arguments.getString("endpoint"),key=arguments.getString("adminKey");CustomerStore store=new CustomerStore(context,filename);
            store.acceptCatalog(HttpApi.call(endpoint,"/api/catalog",null,null,null),endpoint);int initial=store.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock");
            store.quantity("papa",1);JSONObject details=new JSONObject().put("customer","Cliente de comprobación").put("phone","900000000").put("fulfillment","pickup").put("payment","cash");
            JSONObject pending=store.placePracticeOrder(details);check(pending.optString("sendState").equals("pending"),"Offline request waits for confirmation");check(store.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock")==initial,"Pending request cannot claim a shared slot");
            CustomerStore reopened=new CustomerStore(context,filename);check(reopened.pending().length()==1,"Pending request survives process recreation");
            JSONObject accepted=HttpApi.call(endpoint,"/api/orders",null,null,pending),again=HttpApi.call(endpoint,"/api/orders",null,null,pending);check(accepted.getJSONObject("order").getString("id").equals(again.getJSONObject("order").getString("id")),"Repeated HTTP request creates only one order");
            reopened.acceptOrder(pending.getString("id"),accepted);JSONObject sent=reopened.snapshot().getJSONArray("orders").getJSONObject(0);check(sent.getString("sendState").equals("sent")&&!sent.optString("token").isEmpty(),"Canonical order and private token persist");
            JSONObject change=new JSONObject().put("id",sent.getString("id")).put("version",sent.getInt("version")).put("paid",true);
            HttpApi.call(endpoint,"/api/admin/operations",key,null,new JSONObject().put("id",UUID.randomUUID().toString()).put("type","order").put("data",change));
            reopened.acceptOrder(sent.getString("id"),HttpApi.call(endpoint,"/api/orders/"+sent.getString("id"),null,sent.getString("token"),null));check(reopened.snapshot().getJSONArray("orders").getJSONObject(0).getBoolean("paid"),"Customer receives seller's payment update");
            reopened.acceptCatalog(HttpApi.call(endpoint,"/api/catalog",null,null,null),endpoint);check(reopened.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock")==initial-1,"Customer receives canonical shared stock");
            result.putString("stream","PASS: Android HTTP, pending persistence, shared stock, retry deduplication, private tracking and seller-to-customer update.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}finally{context.deleteFile(filename);context.deleteFile(filename+".recovery");}
    }
}
