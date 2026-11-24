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

    @Test public void freshInstallAlwaysHasAValidDemoSale()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        LocalStore store=new LocalStore(context,"owner-fresh-"+System.nanoTime()+".json");
        JSONObject snapshot=store.snapshot(),settings=snapshot.getJSONObject("settings");
        assertEquals("Invalid date: "+settings,"",SaleSchedule.invalid(settings));
        assertTrue("Reserva vencida",SaleSchedule.parse(settings.optString("reserveUntil"),false)>System.currentTimeMillis());
        assertTrue(settings.has("latitude"));
        assertTrue(settings.has("longitude"));
        JSONArray zones=settings.getJSONArray("zones");
        assertEquals(3,zones.length());
        for(int i=0;i<zones.length();i++)assertTrue("Zona sin cobertura",zones.getJSONObject(i).optDouble("maxKm",0)>0);
        assertTrue(store.snapshot().getString("storeId").length()>10);
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

    @Test public void ownerConfirmsOrderBeforeSchedulingSaleDay()throws Exception{
        assertEquals("confirmed",SaleSchedule.next("registered",false));
        assertEquals("confirmed",SaleSchedule.next("registered",true));
        assertEquals("sale_day",SaleSchedule.next("confirmed",false));
        assertEquals("sale_day",SaleSchedule.next("confirmed",true));
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

    @Test public void manualOrderQuantityButtonsKeepTheProductListInPlace() throws Exception {
        try (ActivityController<ManualOrderActivity> controller =
                     Robolectric.buildActivity(ManualOrderActivity.class).setup().visible()) {
            ManualOrderActivity activity = controller.get();
            android.widget.ScrollView scroll = (android.widget.ScrollView) field(activity, "scroll");
            View root = activity.findViewById(android.R.id.content);
            root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 720, 800);
            ShadowLooper.idleMainLooper();
            scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
            int position = scroll.getScrollY();
            assertTrue(position > 0);
            java.util.ArrayList<View> buttons = new java.util.ArrayList<>();
            scroll.findViewsWithText(buttons, "+", View.FIND_VIEWS_WITH_TEXT);
            View plus = buttons.get(buttons.size() - 1);
            ViewGroup row = (ViewGroup) plus.getParent();
            android.widget.TextView count = (android.widget.TextView) row.getChildAt(1);
            assertTrue(plus.performClick());
            assertEquals("1", count.getText().toString());
            assertTrue(row.getChildAt(0).performClick());
            assertEquals("0", count.getText().toString());
            ShadowLooper.idleMainLooper();
            assertSame(scroll, field(activity, "scroll"));
            assertEquals(position, scroll.getScrollY());
        }
    }

    @Test public void orderAndPaymentFiltersAreIndependent() throws Exception {
        JSONObject order = new JSONObject().put("status", "registered").put("paid", false);
        assertTrue(OrderScreen.matchesFilters(order, "registered", "all"));
        assertTrue(OrderScreen.matchesFilters(order, "active", "unpaid"));
        assertFalse(OrderScreen.matchesFilters(order, "registered", "paid"));
        order.put("paid", true);
        assertTrue(OrderScreen.matchesFilters(order, "registered", "paid"));
        order.put("status", "delivered").put("paid", false);
        assertTrue(OrderScreen.matchesFilters(order, "finished", "unpaid"));
        assertFalse(OrderScreen.matchesFilters(order, "active", "unpaid"));
        order.put("status", "cancelled");
        assertTrue(OrderScreen.matchesFilters(order, "finished", "all"));
        assertFalse(OrderScreen.matchesFilters(order, "all", "unpaid"));
    }

    @Test public void savingManualOrderReturnsToSalesAndMakesTheNewOrderVisible() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            AppShell shell = (AppShell) field(activity, "shell");
            JSONObject order = shell.store().manualOrder(details(), new JSONObject().put("causa", 1));
            shell.orderDate("2000-01-01");
            shell.orderFilter("finished");
            shell.orderPayment("paid");
            shell.orderDelivery("delivery");
            activity.onActivityResult(85, android.app.Activity.RESULT_OK,
                    new android.content.Intent().putExtra("orderId", order.getString("id")));
            assertEquals("orders", shell.screen());
            assertEquals(order.getString("saleDate"), shell.orderDate());
            assertTrue(OrderScreen.visibleOrder(shell, order));
            assertTrue(OrderScreen.matchesFilters(order, shell.orderFilter(), shell.orderPayment()));

            shell.go("order", order.getString("id"));
            View firstCard = shell.content().getChildAt(2);
            java.util.ArrayList<View> confirm = new java.util.ArrayList<>();
            firstCard.findViewsWithText(confirm, "Confirmar pedido", View.FIND_VIEWS_WITH_TEXT);
            assertEquals(1, confirm.size());
        }
    }
}
