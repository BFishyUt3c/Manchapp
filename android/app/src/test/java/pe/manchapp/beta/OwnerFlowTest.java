package pe.manchapp.beta;

import android.app.*;
import android.content.*;
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
import java.nio.file.Path;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OwnerFlowTest {
    private JSONObject config()throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(System.getProperty("manchapp.root"),"tmp","qa-connection.json")),"UTF-8"));}
    @Before public void configureClock()throws Exception{Files.write(Paths.get(System.getProperty("manchapp.root"),"tmp","qa-clock.txt"),String.valueOf(config().getLong("duringSale")).getBytes("UTF-8"));}
    private String endpoint(){return "http://127.0.0.1:4181";}
    private JSONObject details()throws Exception{return new JSONObject().put("customer","Pedido de prueba").put("phone","900000000").put("fulfillment","pickup").put("payment","cash").put("source","whatsapp");}
    private static Object field(Object target,String name)throws Exception{Field value=target.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{Field member=target.getClass().getDeclaredField(name);member.setAccessible(true);member.set(target,value);}
    private static Object call(Object target,String name,Class<?>[] types,Object...arguments)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,arguments);}
    private void shot(View view,String name)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));view.layout(0,0,720,1280);Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v070");Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}

    @Test public void offlineChainRecoveryAndSharedInventory()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String filename="owner-flow-"+System.nanoTime()+".json";LocalStore store=new LocalStore(context,filename);String key=config().getString("adminKey");
        store.accept(HttpApi.call(endpoint(),"/api/admin/state",key,null,null),"");assertEquals("Invalid date: "+store.snapshot().getJSONObject("settings").toString(),"",SaleSchedule.invalid(store.snapshot().getJSONObject("settings")));JSONObject created=store.manualOrder(details(),new JSONObject().put("papa",2));String id=created.getString("id");
        for(JSONObject patch:new JSONObject[]{new JSONObject().put("paid",true),new JSONObject().put("status","sale_day"),new JSONObject().put("status","pickup_available"),new JSONObject().put("status","delivered")}){
            JSONArray orders=store.snapshot().getJSONArray("orders");JSONObject current=null;for(int i=0;i<orders.length();i++)if(orders.getJSONObject(i).getString("id").equals(id))current=orders.getJSONObject(i);assertNotNull(current);patch.put("id",id).put("version",current.getInt("version"));store.enqueue("order",patch);
        }
        store=new LocalStore(context,filename);assertEquals(5,store.snapshot().getJSONArray("pending").length());assertEquals(1,OrderSummary.build(store.snapshot(),created.getString("saleDate")).getInt("unconfirmed"));JSONArray operations=store.snapshot().getJSONArray("pending");
        for(int i=0;i<operations.length();i++){JSONObject operation=operations.getJSONObject(i);store.markAttempted(operation.getString("id"));store.accept(HttpApi.call(endpoint(),"/api/admin/operations",key,null,operation),operation.getString("id"));}
        assertEquals(0,store.snapshot().getJSONArray("pending").length());JSONObject found=null;JSONArray received=store.snapshot().getJSONArray("orders");for(int i=0;i<received.length();i++)if(received.getJSONObject(i).getString("id").equals(id))found=received.getJSONObject(i);assertNotNull(found);assertEquals("delivered",found.getString("status"));assertTrue(found.getBoolean("paid"));assertEquals(5,found.getInt("version"));assertFalse(found.optBoolean("localOnly"));
        try(OutputStream out=context.openFileOutput(filename,Context.MODE_PRIVATE)){out.write("{broken".getBytes("UTF-8"));}LocalStore recovered=new LocalStore(context,filename);assertEquals(store.snapshot().getString("storeId"),recovered.snapshot().getString("storeId"));JSONArray retry=recovered.snapshot().getJSONArray("pending");for(int i=0;i<retry.length();i++){JSONObject operation=retry.getJSONObject(i);recovered.accept(HttpApi.call(endpoint(),"/api/admin/operations",key,null,operation),operation.getString("id"));}assertEquals(0,recovered.snapshot().getJSONArray("pending").length());
    }

    @Test public void damagedFilesAreNeverReplacedWithSeed()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String filename="owner-damaged-"+System.nanoTime()+".json";new LocalStore(context,filename);
        for(String suffix:new String[]{"",".recovery"})try(OutputStream out=context.openFileOutput(filename+suffix,Context.MODE_PRIVATE)){out.write("{broken".getBytes("UTF-8"));}
        try{new LocalStore(context,filename);fail("Damaged primary and recovery must not reset data");}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("conservaron"));}
        assertEquals("{broken",new String(Files.readAllBytes(context.getFileStreamPath(filename).toPath()),"UTF-8"));
    }

    @Test public void screensAndPosterVariantsRenderWithoutWindows()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String key=config().getString("adminKey");LocalStore store=new LocalStore(context);store.accept(HttpApi.call(endpoint(),"/api/admin/state",key,null,null),"");store.manualOrder(details(),new JSONObject().put("causa",1));
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();set(activity,"store",store);
            for(String screen:new String[]{"home","summary","orders","publications"}){set(activity,"screen",screen);call(activity,"render",new Class<?>[0]);ShadowLooper.idleMainLooper();shot((View)field(activity,"root"),"seller-"+screen);}
        }
        try(ActivityController<ManualOrderActivity> controller=Robolectric.buildActivity(ManualOrderActivity.class).setup().visible()){
            ManualOrderActivity activity=controller.get();shot(activity.findViewById(android.R.id.content),"manual-products");set(activity,"details",details().put("fulfillment","delivery").put("zoneId","cercana"));set(activity,"step",1);call(activity,"draw",new Class<?>[0]);ShadowLooper.idleMainLooper();shot(activity.findViewById(android.R.id.content),"manual-destination");
        }
        JSONObject data=store.snapshot(),product=data.getJSONArray("products").getJSONObject(5);NativeUi ui=new NativeUi(context);Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v070");Files.createDirectories(dir);
        for(int style=0;style<3;style++)for(boolean square:new boolean[]{false,true}){Bitmap bitmap=FlyerRenderer.render(product,data.getJSONObject("settings"),"Reserva para nuestra próxima venta",style,square,ui);assertEquals(720,bitmap.getWidth());assertEquals(square?720:1280,bitmap.getHeight());try(OutputStream out=Files.newOutputStream(dir.resolve("flyer-"+style+(square?"-square":"-vertical")+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}
    }
}
