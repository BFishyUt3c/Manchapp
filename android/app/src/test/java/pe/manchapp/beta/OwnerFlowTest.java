package pe.manchapp.beta;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OwnerFlowTest {
    private JSONObject details()throws Exception{return new JSONObject().put("customer","Pedido de prueba").put("phone","900000000").put("fulfillment","pickup").put("payment","cash").put("source","whatsapp");}
    private static Object field(Object target,String name)throws Exception{Field value=target.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(target);}
    private void shot(View view,String name)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));view.layout(0,0,720,1280);Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));java.nio.file.Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v080");Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}

    @Test public void freshInstallStartsWithEmptyPersonalData()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        LocalStore store=new LocalStore(context,"owner-fresh-"+System.nanoTime()+".json");
        JSONObject settings=store.snapshot().getJSONObject("settings");
        assertEquals("",settings.optString("name"));
        assertEquals("",settings.optString("pickup"));
        assertEquals("",settings.optString("phone"));
        assertEquals(-12.150786,settings.optDouble("latitude"),0);
        assertEquals(-76.889562,settings.optDouble("longitude"),0);
        assertEquals("Invalid date: "+settings,"",SaleSchedule.invalid(settings));
        assertEquals(3,settings.getJSONArray("zones").length());
    }

    @Test public void localOrderAndPendingQueueSurviveReopen()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String filename="owner-local-"+System.nanoTime()+".json";LocalStore store=new LocalStore(context,filename);
        JSONObject product=store.snapshot().getJSONArray("products").getJSONObject(0);
        int before=product.getInt("stock");
        JSONObject order=store.manualOrder(details(),new JSONObject().put("papa",2));
        assertEquals(1,store.snapshot().getJSONArray("pending").length());
        assertEquals(before-2,store.snapshot().getJSONArray("products").getJSONObject(0).getInt("stock"));
        assertTrue(store.hasPending(order.getString("id")));
        assertTrue(store.canEditUnsent(order.getString("id")));
        String operation=store.snapshot().getJSONArray("pending").getJSONObject(0).getString("id");
        store.markAttempted(operation);
        assertFalse(store.canEditUnsent(order.getString("id")));
        LocalStore reopened=new LocalStore(context,filename);
        assertEquals(order.getString("id"),reopened.snapshot().getJSONArray("orders").getJSONObject(0).getString("id"));
        assertEquals(1,reopened.snapshot().getJSONArray("pending").length());
        assertEquals(store.snapshot().getString("storeId"),reopened.snapshot().getString("storeId"));
    }

    @Test public void ownerStageChainIncludesPreparationAndCooking()throws Exception{
        assertEquals("confirmed",SaleSchedule.next("registered",false));
        assertEquals("preparing",SaleSchedule.next("confirmed",false));
        assertEquals("cooking",SaleSchedule.next("preparing",false));
        assertEquals("sale_day",SaleSchedule.next("cooking",false));
        assertEquals("delivering",SaleSchedule.next("sale_day",true));
        assertEquals("pickup_available",SaleSchedule.next("sale_day",false));
        assertEquals("delivered",SaleSchedule.next("delivering",false));
        assertEquals("delivered",SaleSchedule.next("pickup_available",false));
    }

    @Test public void destinationDefaultsAndFindsWrittenAddress()throws Exception{
        JSONObject details=DeliveryDestination.normalized(new JSONObject().put("address","Mz. B lote 4").put("reference","Portón verde"));
        assertEquals("home",details.getString("destinationType"));
        assertEquals("Trabajo",DeliveryDestination.typeLabel("work"));
        String address=DeliveryDestination.addressGeo(details);
        assertTrue(address.startsWith("geo:0,0?q="));
        assertTrue(address.contains("Mz.%20B"));
        assertTrue(address.contains("Port%C3%B3n%20verde"));
    }

    @Test public void newDeviceOrderPostsANotification()throws Exception{
        Context context=RuntimeEnvironment.getApplication();NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        OrderNotifier.newOrder(context,new JSONObject().put("id","pedido-notify").put("customer","Ana").put("total",12.5).put("items",new JSONArray()));
        assertEquals(1,Shadows.shadowOf(manager).getAllNotifications().size());
    }

    @Test public void damagedFilesAreNeverReplacedWithSeed()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String filename="owner-damaged-"+System.nanoTime()+".json";new LocalStore(context,filename);
        for(String suffix:new String[]{"",".recovery"})try(OutputStream out=context.openFileOutput(filename+suffix,Context.MODE_PRIVATE)){out.write("{broken".getBytes("UTF-8"));}
        try{new LocalStore(context,filename);fail("Damaged primary and recovery must not reset data");}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("conservaron"));}
        assertEquals("{broken",new String(Files.readAllBytes(context.getFileStreamPath(filename).toPath()),"UTF-8"));
    }

    @Test public void screensAndPosterVariantsRenderWithoutWindows()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();AppShell shell=(AppShell)field(activity,"shell");
            shell.store().manualOrder(details(),new JSONObject().put("causa",1));
            for(String screen:new String[]{"home","products","summary","orders","publications"}){shell.screen(screen);shell.render();ShadowLooper.idleMainLooper();shot(shell.root(),"seller-"+screen);}
            JSONObject data=shell.store().snapshot(),product=data.getJSONArray("products").getJSONObject(5);NativeUi ui=new NativeUi(context);java.nio.file.Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v080");Files.createDirectories(dir);
            for(int style=0;style<3;style++)for(boolean square:new boolean[]{false,true}){Bitmap bitmap=FlyerRenderer.render(product,data.getJSONObject("settings"),"Reserva para nuestra próxima venta",style,square,ui);assertEquals(720,bitmap.getWidth());assertEquals(square?720:1280,bitmap.getHeight());try(OutputStream out=Files.newOutputStream(dir.resolve("flyer-"+style+(square?"-square":"-vertical")+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}
        }
    }
}
