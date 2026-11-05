package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;

/** Checkout views use a separate store; real orders, cart and address remain untouched. */
public final class CustomerUiChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private static Field field(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field;}
    private static void render(Object owner)throws Exception{Method method=owner.getClass().getDeclaredMethod("render");method.setAccessible(true);method.invoke(owner);}
    private void frame(Activity activity,String name)throws Exception{View view=activity.getWindow().getDecorView();Bitmap shot=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(shot));File dir=new File(getTargetContext().getFilesDir(),"qa-ui");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    @Override public void onStart(){Context context=getTargetContext();String file="qa-ui-customer-"+System.nanoTime()+".json";Bundle result=new Bundle();Activity activity=null;
        try{CustomerStore store=new CustomerStore(context,file);store.quantity("papa",1);JSONObject details=new JSONObject().put("customer","Cliente de prueba").put("phone","900000000").put("payment","cash").put("fulfillment","delivery").put("zoneId","cercana").put("zone","Zona cercana").put("address","Sector de prueba, Mz A Lt 4").put("reference","Casa verde, al pie de las escaleras");details.put("deliveryLocation",DeliveryDestination.point(-12.13,-76.86,20,System.currentTimeMillis()));DeliveryDestination.confirm(details,store.snapshot().getJSONObject("catalog").getJSONObject("settings").getString("saleDate"));store.details(details);
            activity=startActivitySync(new Intent(context,CustomerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity active=activity;
            runOnMainSync(()->{try{field(active,"store").set(active,store);field(active,"details").set(active,details);field(active,"screen").set(active,"checkout");field(active,"step").setInt(active,0);render(active);}catch(Exception error){throw new IllegalStateException(error);}});waitForIdleSync();
            runOnMainSync(()->{try{((android.widget.ScrollView)field(active,"scroll").get(active)).scrollTo(0,Math.round(145*active.getResources().getDisplayMetrics().density));frame(active,"delivery-fields");field(active,"step").setInt(active,2);render(active);}catch(Exception error){throw new IllegalStateException(error);}});waitForIdleSync();runOnMainSync(()->{try{frame(active,"delivery-review");}catch(Exception error){throw new IllegalStateException(error);}});
            if(!new CustomerStore(context,file).snapshot().getJSONObject("details").has("deliveryLocation"))throw new AssertionError("Destination persistence missing");
            runOnMainSync(()->active.finish());waitForIdleSync();result.putString("stream","PASS: actual delivery form and review with confirmed optional point, isolated from user data.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}finally{if(activity!=null&&!activity.isFinishing()){final Activity active=activity;runOnMainSync(()->active.finish());waitForIdleSync();}context.deleteFile(file);}
    }
}
