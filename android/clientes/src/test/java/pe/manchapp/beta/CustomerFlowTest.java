package pe.manchapp.beta;

import android.app.*;
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
import java.nio.file.Path;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CustomerFlowTest {
    private String endpoint(){return "http://127.0.0.1:4181";}
    private String key()throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(System.getProperty("manchapp.root"),"tmp","qa-connection.json")),"UTF-8")).getString("adminKey");}
    @Before public void configureClock()throws Exception{JSONObject config=new JSONObject(new String(Files.readAllBytes(Paths.get(System.getProperty("manchapp.root"),"tmp","qa-connection.json")),"UTF-8"));Files.write(Paths.get(System.getProperty("manchapp.root"),"tmp","qa-clock.txt"),String.valueOf(config.getLong("beforeSale")).getBytes("UTF-8"));}
    private JSONObject details()throws Exception{return new JSONObject().put("customer","Cliente de prueba").put("phone","900000000").put("fulfillment","pickup").put("payment","cash");}
    private static Object field(Object target,String name)throws Exception{Field value=target.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{Field member=target.getClass().getDeclaredField(name);member.setAccessible(true);member.set(target,value);}
    private static void render(CustomerActivity activity)throws Exception{Method method=CustomerActivity.class.getDeclaredMethod("render");method.setAccessible(true);method.invoke(activity);ShadowLooper.idleMainLooper();}
    private void shot(View view,String name)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));view.layout(0,0,720,1280);Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v070");Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}

    @Test public void queuedRequestRetryAndTrackingUseActualHttp()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String filename="customer-flow-"+System.nanoTime()+".json";CustomerStore store=new CustomerStore(context,filename);store.acceptCatalog(HttpApi.call(endpoint(),"/api/catalog",null,null,null),endpoint());int before=store.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock");store.quantity("papa",1);JSONObject pending=store.placePracticeOrder(details());assertEquals("pending",pending.getString("sendState"));assertEquals(before,store.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock"));
        store=new CustomerStore(context,filename);assertEquals(1,store.pending().length());JSONObject response=HttpApi.call(endpoint(),"/api/orders",null,null,pending),retried=HttpApi.call(endpoint(),"/api/orders",null,null,pending);assertEquals(response.getJSONObject("order").getString("id"),retried.getJSONObject("order").getString("id"));store.acceptOrder(pending.getString("id"),response);
        JSONObject sent=store.snapshot().getJSONArray("orders").getJSONObject(0);assertEquals("sent",sent.getString("sendState"));assertFalse(sent.getString("token").isEmpty());JSONObject change=new JSONObject().put("id",sent.getString("id")).put("version",sent.getInt("version")).put("paid",true);HttpApi.call(endpoint(),"/api/admin/operations",key(),null,new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("type","order").put("data",change));
        store.acceptOrder(sent.getString("id"),HttpApi.call(endpoint(),"/api/orders/"+sent.getString("id"),null,sent.getString("token"),null));assertTrue(store.snapshot().getJSONArray("orders").getJSONObject(0).getBoolean("paid"));store.acceptCatalog(HttpApi.call(endpoint(),"/api/catalog",null,null,null),endpoint());assertEquals(before-1,store.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock"));
    }

    @Test public void changedCatalogRequiresReviewBeforePreparingRequest()throws Exception{
        CustomerStore store=new CustomerStore(RuntimeEnvironment.getApplication(),"customer-review-"+System.nanoTime()+".json");JSONObject original=HttpApi.call(endpoint(),"/api/catalog",null,null,null);store.acceptCatalog(original,endpoint());store.quantity("papa",1);int displayed=original.getInt("revision");JSONObject changed=CustomerStore.copy(original).put("revision",displayed+1);store.acceptCatalog(changed,endpoint());
        try{store.placeReviewedOrder(details(),displayed);fail("A changed catalog must require a fresh review");}catch(Exception expected){assertTrue(expected.getMessage().contains("catálogo cambió"));}
        assertEquals(1,store.count());assertEquals(0,store.snapshot().getJSONArray("orders").length());store.acceptCatalog(original,endpoint());assertEquals(displayed+1,store.snapshot().getJSONObject("catalog").getInt("revision"));assertEquals("pending",store.placeReviewedOrder(details(),displayed+1).getString("sendState"));
    }

    @Test public void actualCheckoutButtonSendsAndShowsCanonicalOrder()throws Exception{
        Context context=RuntimeEnvironment.getApplication();CustomerStore store=new CustomerStore(context,"customer-ui-"+System.nanoTime()+".json");store.acceptCatalog(HttpApi.call(endpoint(),"/api/catalog",null,null,null),endpoint());store.quantity("causa",1);JSONObject contact=details();store.details(contact);
        try(ActivityController<CustomerActivity> controller=Robolectric.buildActivity(CustomerActivity.class).setup().visible()){
            CustomerActivity activity=controller.get();set(activity,"store",store);set(activity,"details",contact);set(activity,"screen","food");render(activity);shot((View)field(activity,"root"),"customer-food");
            set(activity,"screen","checkout");set(activity,"step",0);set(activity,"details",details().put("fulfillment","delivery").put("zoneId","cercana").put("address","Mz A Lt 4").put("reference","Al pie de las escaleras"));render(activity);shot((View)field(activity,"root"),"customer-destination");
            set(activity,"details",contact);set(activity,"step",2);render(activity);shot((View)field(activity,"root"),"customer-review");ViewGroup footer=(ViewGroup)field(activity,"footer");assertTrue(footer.getChildAt(0).performClick());
            long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end&&(Boolean)field(activity,"syncing")){Thread.sleep(30);ShadowLooper.idleMainLooper();}assertFalse((Boolean)field(activity,"syncing"));assertEquals("sent",store.snapshot().getJSONArray("orders").getJSONObject(0).getString("sendState"));assertEquals("receipt",field(activity,"screen"));shot((View)field(activity,"root"),"customer-received");
        }
    }
}
