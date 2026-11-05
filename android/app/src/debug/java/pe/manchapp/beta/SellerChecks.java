package pe.manchapp.beta;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import org.json.*;
import android.graphics.Bitmap;

public class SellerChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();String filename="qa-seller-"+System.nanoTime()+".json";
        try{
            JSONObject sale=new JSONObject().put("saleDate","2026-10-23").put("pickupFrom","2026-10-23T16:00").put("pickupUntil","2026-10-23T19:00").put("reserveUntil","2026-10-22T20:00").put("salesOpen",true);
            check(SaleSchedule.invalid(sale).isEmpty(),"Valid selling window");
            check(SaleSchedule.parse("2026-02-30",true)<0&&SaleSchedule.parse("2026-10-23T25:00",false)<0,"Impossible dates and times rejected");
            check(SaleSchedule.unavailable(sale,SaleSchedule.parse("2026-10-19T09:00",false)).isEmpty(),"Monday can reserve for Friday");
            check(!SaleSchedule.unavailable(sale,SaleSchedule.parse("2026-10-22T20:00",false)).isEmpty(),"Cutoff is inclusive");
            JSONObject paused=LocalStore.copy(sale).put("salesOpen",false);check(!SaleSchedule.unavailable(paused,SaleSchedule.parse("2026-10-19T09:00",false)).isEmpty(),"Owner can pause reservations");
            check(!SaleSchedule.invalid(LocalStore.copy(sale).put("pickupUntil","2026-10-23T15:00")).isEmpty(),"Reverse window rejected");
            check(!SaleSchedule.invalid(LocalStore.copy(sale).put("reserveUntil","2026-10-23T17:00")).isEmpty(),"Cutoff after pickup starts rejected");
            check(SaleSchedule.invalid(LocalStore.copy(sale).put("pickupUntil","2026-10-24T10:00")).isEmpty(),"Pickup interval may end on another date");
            boolean early=false;try{SaleSchedule.checkTransition(sale,"sale_day",SaleSchedule.parse("2026-10-19T09:00",false));}catch(Exception expected){early=true;}check(early,"Cannot mark sale day on Monday");
            SaleSchedule.checkTransition(sale,"sale_day",SaleSchedule.parse("2026-10-23T09:00",false));
            SaleSchedule.checkTransition(sale,"pickup_available",SaleSchedule.parse("2026-10-23T16:00",false));
            early=false;try{SaleSchedule.checkTransition(sale,"pickup_available",SaleSchedule.parse("2026-10-23T15:59",false));}catch(Exception expected){early=true;}check(early,"Pickup cannot start before its announced window");
            check(SaleSchedule.next("registered",true).equals("sale_day")&&SaleSchedule.next("sale_day",true).equals("delivering")&&SaleSchedule.next("sale_day",false).equals("pickup_available"),"Scheduled fulfillment branches without preparation stages");
            JSONObject old=new JSONObject().put("settings",new JSONObject().put("pickup","Mi dirección acordada")).put("products",new JSONArray().put(new JSONObject().put("id","merienda-combo").put("portion","Mi combo especial")));
            SaleSchedule.upgrade(old,sale);check(old.getJSONObject("settings").getString("pickup").equals("Mi dirección acordada")&&old.getJSONArray("products").getJSONObject(0).getString("portion").equals("Mi combo especial"),"Migration preserves owner edits");
            LocalStore store=new LocalStore(context,filename);JSONObject remote=store.snapshot();JSONObject product=LocalStore.copy(remote.getJSONArray("products").getJSONObject(0));
            product.put("name","Prueba inicial");store.enqueue("product",product);product.put("name","Última edición");store.enqueue("product",product);
            check(store.snapshot().getJSONArray("pending").length()==1,"Unsent edits coalesce");check(store.canEditUnsent("papa"),"Never-sent draft remains editable");
            LocalStore restored=new LocalStore(context,filename);check(restored.snapshot().getJSONArray("products").getJSONObject(0).getString("name").equals("Última edición"),"Edit persisted");
            String op=restored.snapshot().getJSONArray("pending").getJSONObject(0).getString("id");restored.markAttempted(op);check(!restored.canEditUnsent("papa"),"Ambiguous network attempt stays protected");
            restored.accept(remote,op);check(!restored.hasPending("papa"),"Acknowledged operation removed");
            boolean failed=false;try{restored.enqueueCombo(product,null);}catch(Exception expected){failed=true;}
            check(failed&&restored.snapshot().getJSONArray("pending").length()==0,"Failed combo rolls back both operations");check(restored.snapshot().getJSONArray("products").getJSONObject(0).getString("name").equals(remote.getJSONArray("products").getJSONObject(0).getString("name")),"Failed batch cannot alter product");
            for(String id:new String[]{"papa","causa","cachanga","cafe","merienda-combo","mazamorra"}){JSONObject p=new JSONObject().put("id",id);Bitmap photo=ImageTools.productPhoto(context,p,960);check(photo!=null&&photo.getWidth()>100&&photo.getHeight()>100,"Bundled food photo: "+id);photo.recycle();}
            check(!ImageTools.hasProductPhoto(new JSONObject().put("id","custom-combo").put("illustration","cachanga")),"A cachanga photo must not stand in for a whole combo");
            Bitmap qr=Bitmap.createBitmap(8,12,Bitmap.Config.ARGB_8888);qr.eraseColor(android.graphics.Color.WHITE);qr.setPixel(1,1,android.graphics.Color.BLACK);String encoded=ImageTools.encodeQr(qr);qr.recycle();Bitmap decoded=ImageTools.decode(encoded);check(encoded.startsWith("data:image/png;base64,")&&decoded.getWidth()==8&&decoded.getHeight()==12,"QR keeps PNG format and dimensions");decoded.recycle();
            JSONObject settings=restored.snapshot().getJSONObject("settings");settings.put("paymentImage",encoded);restored.enqueue("settings",settings);LocalStore qrRestored=new LocalStore(context,filename);check(qrRestored.snapshot().getJSONObject("settings").getString("paymentImage").equals(encoded),"Owner QR persists after reopening");
            result.putString("stream","PASS: scheduled reservations, cutoff, pickup interval, day guards, migration, offline edits, queue persistence, attempted-operation protection, combo rollback, six photos and QR persistence.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}finally{context.deleteFile(filename);}
    }
}
