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
        assertFalse(OrderScreen.matchesFilters(order, "active", "unpaid"));
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

    @Test public void normalDeviceOrderConfirmsAndRetriesWithoutReservingTwice() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        LocalStore store = new LocalStore(context, "owner-confirm-" + System.nanoTime() + ".json");
        JSONObject product = store.snapshot().getJSONArray("products").getJSONObject(0);
        int stock = product.getInt("stock");
        long now = System.currentTimeMillis();
        JSONObject request = OrderDraft.build(store.snapshot(), details(),
                new JSONObject().put(product.getString("id"), stock), false, now);
        JSONObject stale = LocalStore.copy(request);
        stale.getJSONObject("expected").put("total", 0);
        try {
            store.receiveDeviceOrder(stale);
            fail("Changed total must be reviewed");
        } catch (IOException expected) {
            assertEquals(stock, store.snapshot().getJSONArray("products").getJSONObject(0).getInt("stock"));
        }
        JSONObject accepted = store.receiveDeviceOrder(request);
        assertEquals("confirmed", accepted.getString("status"));
        assertFalse(accepted.getBoolean("paid"));
        assertEquals(0, store.snapshot().getJSONArray("products").getJSONObject(0).getInt("stock"));
        assertEquals(accepted.getString("id"), store.receiveDeviceOrder(request).getString("id"));
        assertEquals(1, store.snapshot().getJSONArray("orders").length());
    }

    @Test public void cancellationOnlyRestoresStockForTheReservedSale() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        LocalStore store = new LocalStore(context, "owner-cancel-" + System.nanoTime() + ".json");
        JSONObject product = store.snapshot().getJSONArray("products").getJSONObject(0);
        JSONObject quantities = new JSONObject().put(product.getString("id"), 1);
        int initialStock = product.getInt("stock");
        JSONObject current = store.manualOrder(details(), quantities);
        store.enqueue("order", OrderScreen.orderChange(current).put("status", "cancelled"));
        assertEquals(initialStock, store.snapshot().getJSONArray("products").getJSONObject(0).getInt("stock"));

        JSONObject old = store.manualOrder(details(), quantities);
        JSONObject nextSale = LocalStore.copy(store.snapshot().getJSONArray("products").getJSONObject(0));
        nextSale.put("saleDate", "2030-02-15").put("pickupFrom", "2030-02-15T16:00")
                .put("pickupUntil", "2030-02-15T19:00").put("stock", 10);
        store.enqueue("product", nextSale);
        store.enqueue("order", OrderScreen.orderChange(old).put("status", "cancelled"));
        assertEquals(10, store.snapshot().getJSONArray("products").getJSONObject(0).getInt("stock"));
    }

    @Test public void extraRequestCanBeReviewedWithoutChargingOrChangingPublicStock() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        DeviceBridgeProvider bridge = new DeviceBridgeProvider();
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "pe.manchapp.beta.bridge";
        bridge.attachInfo(context, info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority, bridge);
        LocalStore store = LocalStore.open(context);
        JSONObject base = store.receiveDeviceOrder(OrderDraft.build(store.snapshot(), details(),
                new JSONObject().put("mazamorra", 1), false, System.currentTimeMillis()));
        JSONObject before = store.snapshot();
        JSONObject request = details().put("requestId", "extra-demo-1")
                .put("saleDate", base.getString("saleDate")).put("baseRequestId", base.getString("requestId"))
                .put("items", new JSONArray().put(new JSONObject().put("productId", "mazamorra").put("quantity", 1)));
        JSONObject pending = DeviceBridgeClient.requestMore(context, request);
        String id = pending.getString("id");
        assertEquals("pending", pending.getString("status"));
        try {
            store.resolveExtraRequest(id, true, 4.555, true);
            fail("The amount must have at most two decimals");
        } catch (IOException expected) {
            assertEquals("pending", DeviceBridgeClient.extraRequest(context, id).getString("status"));
        }
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            AppShell shell = (AppShell) field(controller.get(), "shell");
            Method review = OrderScreen.class.getDeclaredMethod("reviewExtraRequest", AppShell.class, JSONObject.class);
            review.setAccessible(true);
            review.invoke(null, shell, pending);
            android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
            ShadowLooper.idleMainLooper();
            dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            java.util.ArrayList<View> labels = new java.util.ArrayList<>();
            dialog.getWindow().getDecorView().findViewsWithText(labels, "Monto total de los extras", View.FIND_VIEWS_WITH_TEXT);
            assertEquals(1, labels.size());
            ViewGroup form = (ViewGroup) labels.get(0).getParent();
            android.widget.EditText amount = (android.widget.EditText) form.getChildAt(1);
            assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
            assertNotNull(amount.getError());
            amount.setText("4,50");
            ((android.widget.CheckBox) form.getChildAt(2)).setChecked(true);
            assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
            assertFalse(dialog.isShowing());
        }
        JSONObject approved = DeviceBridgeClient.extraRequest(context, id);
        assertEquals("approved", approved.getString("status"));
        assertEquals(4.50, approved.getDouble("extraAmount"), .001);
        assertEquals(base.getString("id"), approved.getString("sameDeliveryOrderId"));
        assertEquals(base.getInt("number"), approved.getInt("sameDeliveryOrderNumber"));
        assertEquals(4.50, DeviceBridgeClient.requestMore(context, request).getDouble("extraAmount"), .001);
        assertEquals(id, LocalStore.deliveryExtras(store.snapshot(), base.getString("id")).getJSONObject(0).getString("id"));
        assertEquals(before.getJSONArray("restockRequests").length() + 1,
                store.snapshot().getJSONArray("restockRequests").length());
        JSONObject second = DeviceBridgeClient.requestMore(context, request.put("requestId", "extra-demo-2"));
        store.resolveExtraRequest(second.getString("id"), false, 0, false);
        JSONObject rejected = DeviceBridgeClient.extraRequest(context, second.getString("id"));
        assertEquals("rejected", rejected.getString("status"));
        assertFalse(rejected.has("extraAmount"));
        request.remove("baseRequestId");
        JSONObject standalone = DeviceBridgeClient.requestMore(context, request.put("requestId", "extra-demo-3"));
        try {
            store.resolveExtraRequest(standalone.getString("id"), true, 0, true);
            fail("An extra-only request cannot attach to a delivery");
        } catch (IOException expected) {
            assertEquals("pending", store.extraRequest(standalone.getString("id")).getString("status"));
        }
        store.resolveExtraRequest(standalone.getString("id"), true, 0, false);
        JSONObject free = DeviceBridgeClient.extraRequest(context, standalone.getString("id"));
        assertEquals(0, free.getDouble("extraAmount"), .001);
        assertFalse(free.has("sameDeliveryOrderNumber"));
        assertEquals(before.getJSONArray("products").toString(), store.snapshot().getJSONArray("products").toString());
        assertEquals(before.getJSONArray("orders").toString(), store.snapshot().getJSONArray("orders").toString());
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
