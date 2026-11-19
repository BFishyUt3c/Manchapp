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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CustomerFlowTest {
    private JSONObject details()throws Exception{return new JSONObject().put("customer","Cliente de prueba").put("phone","900000000").put("fulfillment","pickup").put("payment","cash");}
    private static Object field(Object target,String name)throws Exception{Field value=target.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(target);}
    private static void set(Object target,String name,Object value)throws Exception{Field member=target.getClass().getDeclaredField(name);member.setAccessible(true);member.set(target,value);}
    private static void render(CustomerActivity activity)throws Exception{Method method=CustomerActivity.class.getDeclaredMethod("render");method.setAccessible(true);method.invoke(activity);ShadowLooper.idleMainLooper();}
    private void shot(View view,String name)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));view.layout(0,0,720,1280);Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));java.nio.file.Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v080");Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}

    @Test public void queuedRequestWaitsForSellerAndTracksStatus()throws Exception{
        Context context=RuntimeEnvironment.getApplication();String filename="customer-flow-"+System.nanoTime()+".json";CustomerStore store=new CustomerStore(context,filename);
        int before=store.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock");store.quantity("papa",1);JSONObject pending=store.placePracticeOrder(details());assertEquals("device_pending",pending.getString("sendState"));assertEquals(before,store.snapshot().getJSONObject("catalog").getJSONArray("products").getJSONObject(0).getInt("stock"));
        store=new CustomerStore(context,filename);assertEquals(1,store.pending().length());
        JSONObject accepted=CustomerStore.copy(pending).put("status","registered").put("sendState","sent").put("version",1);store.acceptOrder(pending.getString("id"),new JSONObject().put("order",accepted));
        JSONObject sent=store.snapshot().getJSONArray("orders").getJSONObject(0);assertEquals("sent",sent.getString("sendState"));assertEquals("registered",sent.getString("status"));
        JSONObject preparing=CustomerStore.copy(sent).put("status","preparing").put("version",sent.getInt("version")+1);store.acceptOrder(sent.getString("id"),new JSONObject().put("order",preparing));assertEquals("preparing",store.snapshot().getJSONArray("orders").getJSONObject(0).getString("status"));
    }

    @Test public void changedCatalogRequiresReviewAndDropsRemovedProductsFromCart()throws Exception{
        CustomerStore store=new CustomerStore(RuntimeEnvironment.getApplication(),"customer-review-"+System.nanoTime()+".json");JSONObject original=store.snapshot().getJSONObject("catalog");int displayed=original.getInt("revision");store.quantity("papa",1);JSONObject changed=CustomerStore.copy(original).put("revision",displayed+1);store.acceptDeviceCatalog(changed);
        try{store.placeReviewedOrder(details(),displayed);fail("A changed catalog must require a fresh review");}catch(Exception expected){assertTrue(expected.getMessage().contains("catálogo cambió"));}
        assertEquals(1,store.count());assertEquals(0,store.snapshot().getJSONArray("orders").length());store.acceptDeviceCatalog(original);assertEquals(displayed,store.snapshot().getJSONObject("catalog").getInt("revision"));assertEquals("device_pending",store.placeReviewedOrder(details(),displayed).getString("sendState"));
        CustomerStore empty=new CustomerStore(RuntimeEnvironment.getApplication(),"customer-prune-"+System.nanoTime()+".json");empty.quantity("papa",1);JSONObject without=CustomerStore.copy(empty.snapshot().getJSONObject("catalog"));JSONArray products=without.getJSONArray("products");for(int i=products.length()-1;i>=0;i--)if(products.getJSONObject(i).getString("id").equals("papa"))products.remove(i);empty.acceptDeviceCatalog(without);assertEquals(0,empty.count());
    }

    @Test public void actualCheckoutButtonSendsAndShowsCanonicalOrder()throws Exception{
        Context context=RuntimeEnvironment.getApplication();CustomerStore store=new CustomerStore(context,"customer-ui-"+System.nanoTime()+".json");store.quantity("causa",1);JSONObject contact=details();store.details(contact);
        try(ActivityController<CustomerActivity> controller=Robolectric.buildActivity(CustomerActivity.class).setup().visible()){
            CustomerActivity activity=controller.get();set(activity,"store",store);set(activity,"details",contact);set(activity,"screen","food");render(activity);shot((View)field(activity,"root"),"customer-food");
            set(activity,"screen","checkout");set(activity,"step",0);set(activity,"details",details().put("fulfillment","delivery").put("zoneId","cercana").put("address","Mz A Lt 4").put("reference","Al pie de las escaleras"));render(activity);shot((View)field(activity,"root"),"customer-destination");
            set(activity,"details",contact);set(activity,"step",2);render(activity);shot((View)field(activity,"root"),"customer-review");ViewGroup footer=(ViewGroup)field(activity,"footer");assertTrue(footer.getChildAt(0).performClick());
            long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end&&(Boolean)field(activity,"submittingOrder")){Thread.sleep(30);ShadowLooper.idleMainLooper();}assertFalse((Boolean)field(activity,"submittingOrder"));assertEquals("device_pending",store.snapshot().getJSONArray("orders").getJSONObject(0).getString("sendState"));assertEquals("receipt",field(activity,"screen"));shot((View)field(activity,"root"),"customer-received");
        }
    }

    @Test public void customerReceiptShowsStageTimelineForCookingPickup()throws Exception{
        Context context=RuntimeEnvironment.getApplication();CustomerStore store=new CustomerStore(context,"customer-stages-"+System.nanoTime()+".json");store.quantity("causa",1);
        JSONObject pending=store.placePracticeOrder(details().put("fulfillment","pickup"));
        JSONObject sent=CustomerStore.copy(pending).put("status","cooking").put("sendState","sent").put("version",1);
        store.acceptOrder(pending.getString("id"),new JSONObject().put("order",sent));
        try(ActivityController<CustomerActivity> controller=Robolectric.buildActivity(CustomerActivity.class).setup().visible()){
            CustomerActivity activity=controller.get();set(activity,"store",store);set(activity,"selectedId",pending.getString("id"));set(activity,"screen","receipt");render(activity);
            View root=(View)field(activity,"root");
            assertNotNull(root.findViewWithTag("stage:received"));
            assertNotNull(root.findViewWithTag("stage:accepted"));
            assertNotNull(root.findViewWithTag("stage:preparing"));
            assertNotNull(root.findViewWithTag("stage:cooking"));
            assertNotNull(root.findViewWithTag("stage:ready"));
            assertNotNull(root.findViewWithTag("stage:delivered"));
            assertEquals("Pedido listo para recoger",((android.widget.TextView)root.findViewWithTag("stage:ready")).getText().toString());
        }
    }

    @Test public void cancelReasonIsDisplayedInCustomerReceipt()throws Exception{
        Context context=RuntimeEnvironment.getApplication();CustomerStore store=new CustomerStore(context,"customer-cancel-"+System.nanoTime()+".json");store.quantity("causa",1);
        JSONObject pending=store.placePracticeOrder(details());
        JSONObject sent=CustomerStore.copy(pending).put("status","cancelled").put("sendState","sent").put("version",1).put("cancelReason","Se acabó la causa");
        store.acceptOrder(pending.getString("id"),new JSONObject().put("order",sent));
        try(ActivityController<CustomerActivity> controller=Robolectric.buildActivity(CustomerActivity.class).setup().visible()){
            CustomerActivity activity=controller.get();set(activity,"store",store);set(activity,"selectedId",pending.getString("id"));set(activity,"screen","receipt");render(activity);
            View note=((View)field(activity,"root")).findViewWithTag("cancel:reason");
            assertNotNull(note);
            assertTrue(((android.widget.TextView)note).getText().toString().contains("Se acabó la causa"));
        }
    }
}
