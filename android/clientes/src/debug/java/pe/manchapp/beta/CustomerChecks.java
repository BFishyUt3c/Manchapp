package pe.manchapp.beta;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import org.json.*;
import android.graphics.Bitmap;

/** Debug-only checks use their own file and never modify the user's cart. */
public class CustomerChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void rejected(CheckedAction action,String message)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,message);}
    private interface CheckedAction{void run()throws Exception;}
    @Override public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();String filename="qa-customer-"+System.nanoTime()+".json";
        try{
            check(SaleRules.deliveryFee(12.99,3,13)==3,"Below threshold keeps fee");check(SaleRules.deliveryFee(13,3,13)==0,"Exact threshold is free");check(SaleRules.deliveryFee(13,3,0)==3,"Zero disables offer");
            check(SaleRules.validPhone("900000000")&&!SaleRules.validPhone("123"),"Phone validation");
            JSONObject legacy=DeliveryDestination.normalized(new JSONObject().put("address","  Mz A Lt 4  ").put("reference","Casa verde").put("meeting","Al pie de las escaleras"));
            check(legacy.getString("address").equals("Mz A Lt 4")&&legacy.getString("reference").contains("Al pie de las escaleras")&&!legacy.has("meeting"),"Legacy directions merge without losing access instructions");
            check(!DeliveryDestination.validPoint(new JSONObject().put("latitude",100).put("longitude",0).put("accuracy",10).put("capturedAt",1)),"Invalid coordinates rejected");
            CustomerStore store=new CustomerStore(context,filename);
            rejected(()->store.quantity("social",1),"Social menu excluded");rejected(()->store.quantity("papa",21),"Overselling rejected");rejected(()->store.quantity("papa",-1),"Negative quantity rejected");
            store.quantity("papa",2);check(store.subtotal()==13&&store.count()==2,"Prices come from catalog");
            JSONObject details=new JSONObject().put("customer","Prueba técnica").put("phone","900000000").put("payment","cash").put("fulfillment","delivery").put("zoneId","cercana").put("address","Pasaje de prueba").put("reference","Junto a la bodega");
            store.details(details);CustomerStore restored=new CustomerStore(context,filename);check(restored.count()==2&&restored.snapshot().getJSONObject("details").getString("address").equals("Pasaje de prueba"),"Cart and fields survive reopen");
            final JSONObject invalid=CustomerStore.copy(details).put("zoneId","intermedia");rejected(()->restored.placePracticeOrder(invalid),"Disabled sector rejected");
            final JSONObject badPhone=CustomerStore.copy(details).put("phone","123");rejected(()->restored.placePracticeOrder(badPhone),"Invalid contact rejected");
            JSONObject schedule=restored.snapshot().getJSONObject("catalog").getJSONObject("settings");
            rejected(()->restored.placePracticeOrder(details,SaleSchedule.parse(schedule.optString("reserveUntil"),false)),"Final submit rejects cutoff");
            check(restored.count()==2&&restored.snapshot().getJSONArray("orders").length()==0,"Closed submit preserves cart and stock");
            JSONObject point=DeliveryDestination.point(-12.13,-76.86,20,System.currentTimeMillis());details.put("deliveryLocation",point);DeliveryDestination.confirm(details,schedule.getString("saleDate"));
            JSONObject wrongAddress=CustomerStore.copy(details).put("address","Otra dirección");rejected(()->restored.placePracticeOrder(wrongAddress),"Changed address requires GPS confirmation");
            JSONObject wrongDate=CustomerStore.copy(details);wrongDate.getJSONObject("deliveryLocation").put("saleDate","2000-01-01");rejected(()->restored.placePracticeOrder(wrongDate),"Changed sale date requires GPS confirmation");
            JSONObject order=restored.placePracticeOrder(details);check(order.getJSONObject("deliveryLocation").getDouble("latitude")==-12.13,"Confirmed destination belongs to saved reservation");
            point.put("latitude",0);check(restored.snapshot().getJSONArray("orders").getJSONObject(0).getJSONObject("deliveryLocation").getDouble("latitude")==-12.13,"Subsequent location edits do not change the prior destination");
            check(order.getString("saleDate").equals(schedule.getString("saleDate"))&&order.getString("pickupFrom").equals(schedule.getString("pickupFrom"))&&order.getString("pickup").equals(schedule.getString("pickup")),"Order freezes agreed schedule and pickup");check(order.getDouble("total")==16&&order.getDouble("fee")==3,"Delivery total correct");check(!order.getBoolean("paid")&&order.getString("status").equals("local"),"Local order never implies payment or sending");check(restored.count()==0,"Cart cleared atomically");
            rejected(()->restored.placePracticeOrder(details),"Repeated submit cannot duplicate empty cart");
            CustomerStore persisted=new CustomerStore(context,filename);check(persisted.snapshot().getJSONArray("orders").length()==1,"Order persisted once");
            JSONObject later=persisted.snapshot();later.getJSONObject("catalog").getJSONObject("settings").put("saleDate","2026-11-20").put("pickup","Otro lugar");
            try(java.io.FileOutputStream out=context.openFileOutput(filename,Context.MODE_PRIVATE)){out.write(later.toString().getBytes("UTF-8"));}
            CustomerStore newSale=new CustomerStore(context,filename);JSONObject oldOrder=newSale.snapshot().getJSONArray("orders").getJSONObject(0);check(oldOrder.getString("saleDate").equals(order.getString("saleDate"))&&oldOrder.getString("pickup").equals(order.getString("pickup")),"Next sale cannot change prior reservation");
            check(persisted.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock")==20,"Pending requests do not consume confirmed stock");
            persisted.quantity("papa",1);JSONObject pickup=CustomerStore.copy(details).put("fulfillment","pickup");JSONObject collected=persisted.placePracticeOrder(pickup);check(!collected.has("deliveryLocation")&&!collected.has("address")&&!collected.has("reference"),"Pickup does not store delivery coordinates or home address");
            persisted.quantity("causa",1);int stockBeforeRefresh=persisted.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock");int priorOrders=persisted.snapshot().getJSONArray("orders").length();persisted.loadBundledCatalog(context);check(persisted.count()==1&&persisted.snapshot().getJSONArray("orders").length()==priorOrders,"Explicit catalog refresh keeps cart and old reservations");check(persisted.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock")==stockBeforeRefresh,"Refreshing a selling day preserves authoritative stock");
            for(String id:new String[]{"papa","causa","cachanga","cafe","merienda-combo","mazamorra"}){Bitmap photo=ImageTools.productPhoto(context,new JSONObject().put("id",id),960);check(photo!=null,"Customer food photo: "+id);photo.recycle();}
            result.putString("stream","PASS: destination persistence, GPS validation, pickup privacy, legacy references, cutoff, frozen reservation date, pricing, stock, social separation, fields, persistence, disabled coverage, duplicate submission and six food photos.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}finally{context.deleteFile(filename);}
    }
}
