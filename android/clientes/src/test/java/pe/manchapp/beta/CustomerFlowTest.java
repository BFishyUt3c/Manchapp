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
            long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end&&(Boolean)field(activity,"submittingOrder")){Thread.sleep(30);ShadowLooper.idleMainLooper();}assertFalse((Boolean)field(activity,"submittingOrder"));assertEquals("device_pending",store.snapshot().getJSONArray("orders").getJSONObject(0).getString("sendState"));assertEquals("orders",field(activity,"screen"));shot((View)field(activity,"root"),"customer-received");
        }
    }

    @Test public void catalogAndCartKeepQuantityChangesInPlace() throws Exception {
        CustomerStore store = new CustomerStore(RuntimeEnvironment.getApplication(),
                "customer-shopping-" + System.nanoTime() + ".json");
        try (ActivityController<CustomerActivity> controller =
                     Robolectric.buildActivity(CustomerActivity.class).setup().visible()) {
            CustomerActivity activity = controller.get();
            set(activity, "store", store);
            render(activity);
            View catalog = (View) field(activity, "root");
            View catalogScroll = (View) field(activity, "scroll");
            View categories = catalog.findViewWithTag("catalog:categories");
            assertTrue(catalog.findViewWithTag("category:Salados").performClick());
            assertSame(catalog, field(activity, "root"));
            assertSame(catalogScroll, field(activity, "scroll"));
            assertSame(categories, catalog.findViewWithTag("catalog:categories"));
            assertTrue(catalog.findViewWithTag("category:Salados").isSelected());
            assertNotNull(catalog.findViewWithTag("shopping:product:papa"));
            assertNull(catalog.findViewWithTag("shopping:product:merienda-combo"));

            assertTrue(catalog.findViewWithTag("shopping:add:papa").performClick());
            assertSame(catalog, field(activity, "root"));
            android.widget.TextView shortcut = catalog.findViewWithTag("shopping:cart");
            assertTrue(shortcut.isShown());
            assertTrue(shortcut.getText().toString().contains("Ver carrito · 1"));
            shot(catalog, "customer-catalog");

            assertTrue(shortcut.performClick());
            assertEquals("cart", field(activity, "screen"));
            View basket = (View) field(activity, "root");
            shot(basket, "customer-cart");
            android.widget.ScrollView scroll = (android.widget.ScrollView) field(activity, "scroll");
            scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
            int position = scroll.getScrollY();
            assertTrue(position > 0);
            android.widget.TextView subtotal = basket.findViewWithTag("shopping:subtotal");
            String one = subtotal.getText().toString();
            assertTrue(basket.findViewWithTag("shopping:plus:papa").performClick());
            assertEquals(2, store.count());
            assertNotEquals(one, subtotal.getText().toString());
            assertTrue(basket.findViewWithTag("shopping:minus:papa").performClick());
            assertEquals(1, store.count());
            assertEquals(one, subtotal.getText().toString());
            assertSame(scroll, field(activity, "scroll"));
            assertEquals(position, scroll.getScrollY());

            assertTrue(basket.findViewWithTag("shopping:checkout").performClick());
            assertEquals("checkout", field(activity, "screen"));

            ViewGroup checkout = (ViewGroup) field(activity, "root");
            ViewGroup navigation = (ViewGroup) checkout.getChildAt(checkout.getChildCount() - 1);
            assertTrue(navigation.getChildAt(1).performClick());
            View offers = (View) field(activity, "root");
            assertNotNull(offers.findViewWithTag("shopping:product:merienda-combo"));
            assertNull(offers.findViewWithTag("shopping:product:papa"));
        }
    }

    @Test public void outdatedCartLetsTheBuyerReduceQuantities() throws Exception {
        CustomerStore store = new CustomerStore(RuntimeEnvironment.getApplication(),
                "customer-old-cart-" + System.nanoTime() + ".json");
        store.quantity("papa", 2);
        JSONObject changed = store.snapshot().getJSONObject("catalog");
        JSONObject product = changed.getJSONArray("products").getJSONObject(0);
        product.put("saleDate", "2030-02-15").put("pickupFrom", "2030-02-15T16:00")
                .put("pickupUntil", "2030-02-15T19:00").put("reserveUntil", "2030-02-15T15:00");
        store.acceptDeviceCatalog(changed);
        assertTrue(store.cartNeedsReview());
        assertEquals(1, store.adjustQuantity("papa", -1).getInt("papa"));
        assertEquals(0, store.adjustQuantity("papa", -1).length());
        assertFalse(store.cartNeedsReview());
    }

    @Test public void fiveInStockAndOneExtraBecomeTwoIndependentRecords() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String filename = "customer-extra-" + System.nanoTime() + ".json";
        CustomerStore store = new CustomerStore(context, filename);
        JSONObject catalog = store.snapshot().getJSONObject("catalog");
        JSONArray products = catalog.getJSONArray("products");
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.getJSONObject(i);
            if (product.getString("id").equals("mazamorra")) product.put("stock", 5);
        }
        store.acceptDeviceCatalog(catalog);
        store.quantity("mazamorra", 5);
        double subtotal = store.subtotal();
        store.changeExtra("mazamorra", 1);
        assertEquals(6, store.shoppingCount());
        assertEquals(subtotal, store.subtotal(), .001);
        JSONObject order = store.placeReviewedShopping(details(), catalog.getInt("revision"), true);
        assertEquals(5, order.getJSONArray("items").getJSONObject(0).getInt("quantity"));
        assertEquals(subtotal, order.getDouble("total"), .001);
        CustomerStore reopened = new CustomerStore(context, filename);
        JSONObject extra = reopened.extraRequestsToSync().getJSONObject(0);
        assertEquals(order.getString("extraRequestId"), extra.getString("id"));
        assertEquals(order.getString("requestId"), extra.getString("baseRequestId"));
        assertEquals(1, extra.getJSONArray("items").getJSONObject(0).getInt("quantity"));
        assertFalse(extra.has("total"));
        reopened.acceptOrder(order.getString("id"), CustomerStore.copy(order).put("status", "confirmed"));
        assertEquals(extra.getString("id"), reopened.snapshot().getJSONArray("orders").getJSONObject(0).getString("extraRequestId"));
        reopened.acceptExtraReply(extra.getString("id"), new JSONObject().put("status", "approved"));
        assertEquals(0, reopened.extraRequestsToSync().length());
        assertEquals("approved", reopened.snapshot().getJSONArray("extraRequests").getJSONObject(0).getString("status"));
    }

    @Test public void deliveryFeeUsesCoordinatesAndIgnoresTheReportedSector() throws Exception {
        CustomerStore store = new CustomerStore(RuntimeEnvironment.getApplication(),
                "customer-zone-" + System.nanoTime() + ".json");
        JSONObject catalog = store.snapshot().getJSONObject("catalog");
        JSONObject settings = catalog.getJSONObject("settings");
        settings.getJSONArray("zones").getJSONObject(1).put("enabled", true);
        store.acceptDeviceCatalog(catalog);
        store.quantity("papa", 1);
        JSONObject destination = details().put("fulfillment", "delivery").put("zoneId", "cercana")
                .put("address", "Calle de prueba 100").put("reference", "Puerta verde")
                .put("deliveryLocation", DeliveryDestination.point(settings.getDouble("latitude") - .027,
                        settings.getDouble("longitude"), 10, System.currentTimeMillis()));
        DeliveryDestination.confirm(destination, store.currentSale().getString("saleDate"));
        assertEquals(5, store.fee(destination), .001);
        JSONObject order = store.placePracticeOrder(destination);
        assertEquals("intermedia", order.getString("zoneId"));
        assertEquals(5, order.getDouble("fee"), .001);
    }

    @Test public void checkoutShowsGpsBeforeAddressAndAnInAppBackButton() throws Exception {
        CustomerStore store = new CustomerStore(RuntimeEnvironment.getApplication(),
                "customer-steps-" + System.nanoTime() + ".json");
        store.quantity("papa", 1);
        try (ActivityController<CustomerActivity> controller =
                     Robolectric.buildActivity(CustomerActivity.class).setup().visible()) {
            CustomerActivity activity = controller.get();
            set(activity, "store", store);
            set(activity, "screen", "checkout");
            set(activity, "step", 2);
            set(activity, "details", details().put("fulfillment", "delivery").put("address", "Calle de prueba 100"));
            render(activity);
            View root = (View) field(activity, "root");
            assertTrue(root.findViewWithTag("checkout:back").performClick());
            assertEquals(1, field(activity, "step"));
            root = (View) field(activity, "root");
            assertTrue(root.findViewWithTag("checkout:back").performClick());
            assertEquals(0, field(activity, "step"));
            ViewGroup content = (ViewGroup) field(activity, "content");
            View gps = content.findViewWithTag("checkout:gps");
            assertNotNull(gps);
            java.util.Map<?, ?> fields = (java.util.Map<?, ?>) field(activity, "fields");
            assertTrue(content.indexOfChild(gps) < content.indexOfChild((View) fields.get("address")));
            assertEquals("Calle de prueba 100", ((android.widget.EditText) fields.get("address")).getText().toString());
        }
    }

    @Test public void paymentQrOnlyAppearsForConfirmedWalletOrder() throws Exception {
        CustomerStore store = new CustomerStore(RuntimeEnvironment.getApplication(),
                "customer-qr-" + System.nanoTime() + ".json");
        JSONObject catalog = store.snapshot().getJSONObject("catalog");
        Bitmap fixture = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        try {
            fixture.eraseColor(Color.WHITE);
            catalog.getJSONObject("settings").put("cashOnly", false).put("paymentMode", "yape")
                    .put("paymentImage", ImageTools.encodeQr(fixture));
        } finally {
            fixture.recycle();
        }
        store.acceptDeviceCatalog(catalog);
        store.quantity("papa", 1);
        JSONObject order = store.placePracticeOrder(details().put("payment", "yape"));
        try (ActivityController<CustomerActivity> controller =
                     Robolectric.buildActivity(CustomerActivity.class).setup().visible()) {
            CustomerActivity activity = controller.get();
            set(activity, "store", store);
            set(activity, "screen", "receipt");
            set(activity, "selectedId", order.getString("requestId"));
            store.acceptOrder(order.getString("id"), CustomerStore.copy(order).put("status", "registered").put("id", "canonical-qr"));
            render(activity);
            assertNull(((View) field(activity, "root")).findViewWithTag("receipt:qr"));
            store.acceptOrder("canonical-qr", CustomerStore.copy(order).put("status", "confirmed").put("id", "canonical-qr"));
            render(activity);
            View qr = ((View) field(activity, "root")).findViewWithTag("receipt:qr");
            assertNotNull(qr);
            assertTrue(qr.performClick());
            AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());
            dialog.dismiss();
        }
    }

    @Test public void soldOutProductOffersAnExplicitRequestWithoutAnOrderCharge() throws Exception {
        CustomerStore store = new CustomerStore(RuntimeEnvironment.getApplication(),
                "customer-sold-out-" + System.nanoTime() + ".json");
        JSONObject catalog = store.snapshot().getJSONObject("catalog");
        catalog.getJSONArray("products").getJSONObject(0).put("stock", 0);
        catalog.getJSONArray("products").getJSONObject(1).put("stock", 0);
        store.acceptDeviceCatalog(catalog);
        try (ActivityController<CustomerActivity> controller =
                     Robolectric.buildActivity(CustomerActivity.class).setup().visible()) {
            CustomerActivity activity = controller.get();
            set(activity, "store", store);
            render(activity);
            View root = (View) field(activity, "root");
            android.widget.TextView request = root.findViewWithTag("shopping:add:papa");
            assertEquals("Solicitar", request.getText().toString());
            assertTrue(request.performClick());
            assertEquals(0, store.shoppingCount());
            AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            ShadowLooper.idleMainLooper();
            assertEquals(1, store.extraCount());
            assertTrue(root.findViewWithTag("shopping:plus:papa").performClick());
            assertEquals(2, store.extraCount());
            assertSame(dialog, org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());

            assertTrue(root.findViewWithTag("shopping:add:causa").performClick());
            AlertDialog other = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotSame(dialog, other);
            assertEquals(2, store.extraCount());
            assertTrue(other.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            ShadowLooper.idleMainLooper();
            assertTrue(root.findViewWithTag("shopping:plus:causa").performClick());
            assertEquals(4, store.extraCount());
            assertSame(other, org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
            assertEquals(0, store.count());
            assertEquals(0, store.subtotal(), .001);
            assertTrue(root.findViewWithTag("shopping:cart").performClick());
            android.widget.TextView subtotal = ((View) field(activity, "root")).findViewWithTag("shopping:subtotal");
            assertEquals("Sin cobro", subtotal.getText().toString());
        }
        JSONObject extra = store.placeReviewedShopping(details(), catalog.getInt("revision"), true);
        assertTrue(extra.getBoolean("extra"));
        assertTrue(extra.getLong("createdAt") > 0);
        assertEquals(0, store.pending().length());
        assertEquals(1, store.extraRequestsToSync().length());
    }

    @Test public void extraSubmissionAndStatusButtonWorkEvenWithAnUnavailableCatalog() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        CustomerStore store = new CustomerStore(context, "customer-lookup-" + System.nanoTime() + ".json");
        store.changeExtra("papa", 1);
        JSONObject request = store.placeReviewedShopping(details(),
                store.snapshot().getJSONObject("catalog").getInt("revision"), true);

        try (ActivityController<CustomerActivity> controller =
                     Robolectric.buildActivity(CustomerActivity.class).setup().visible()) {
            CustomerActivity activity = controller.get();
            waitForSync(activity);
            ExtraReplyProvider bridge = new ExtraReplyProvider();
            android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
            info.authority = "pe.manchapp.beta.bridge";
            bridge.attachInfo(context, info);
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority, bridge);

            set(activity, "store", store);
            set(activity, "screen", "extras");
            set(activity, "selectedId", request.getString("id"));
            render(activity);
            Method sync = CustomerActivity.class.getDeclaredMethod("syncDevice", boolean.class);
            sync.setAccessible(true);
            sync.invoke(activity, true);
            waitForSync(activity);
            assertEquals(1, bridge.submissions);
            android.widget.TextView button = (android.widget.TextView) ((ViewGroup) field(activity, "footer")).getChildAt(0);
            assertEquals("Consultar respuesta", button.getText().toString());
            assertTrue(button.performClick());
            waitForSync(activity);
            assertEquals(1, bridge.lookups);
            assertEquals("pending", store.extraRequestsToSync().getJSONObject(0).getString("status"));

            bridge.status = "approved";
            button = (android.widget.TextView) ((ViewGroup) field(activity, "footer")).getChildAt(0);
            assertTrue(button.performClick());
            waitForSync(activity);
            assertEquals(1, bridge.submissions);
            assertEquals(2, bridge.lookups);
            JSONObject updated = store.snapshot().getJSONArray("extraRequests").getJSONObject(0);
            assertEquals("approved", updated.getString("status"));
            assertEquals("sent", updated.getString("sendState"));
            assertEquals(4.50, updated.getDouble("extraAmount"), .001);
            assertEquals(12, updated.getInt("sameDeliveryOrderNumber"));
            View content = (View) field(activity, "content");
            java.util.ArrayList<View> quote = new java.util.ArrayList<>();
            content.findViewsWithText(quote, "Monto adicional", View.FIND_VIEWS_WITH_TEXT);
            assertEquals(1, quote.size());
            String amount = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("es", "PE")).format(4.50);
            assertEquals("Monto adicional: " + amount, ((android.widget.TextView) quote.get(0)).getText().toString());
            java.util.ArrayList<View> delivery = new java.util.ArrayList<>();
            content.findViewsWithText(delivery, "Se entrega junto al pedido #12", View.FIND_VIEWS_WITH_TEXT);
            assertEquals(1, delivery.size());
            assertEquals("Volver a mis pedidos",
                    ((android.widget.TextView) ((ViewGroup) field(activity, "footer")).getChildAt(0)).getText().toString());
        }
    }

    private static void waitForSync(CustomerActivity activity) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while ((Boolean) field(activity, "syncing") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            ShadowLooper.idleMainLooper();
        }
        assertFalse((Boolean) field(activity, "syncing"));
    }

    private static final class ExtraReplyProvider extends android.content.ContentProvider {
        volatile String status = "pending";
        volatile int submissions, lookups;
        @Override public boolean onCreate() { return true; }
        @Override public android.os.Bundle call(String method, String id, android.os.Bundle input) {
            if (method.equals("requestMore")) submissions++;
            else if (method.equals("extraRequest")) lookups++;
            else throw new AssertionError("Unexpected bridge method: " + method);
            android.os.Bundle reply = new android.os.Bundle();
            reply.putBoolean("ok", true);
            reply.putString("json", "approved".equals(status)
                    ? "{\"status\":\"approved\",\"extraAmount\":4.50,\"sameDeliveryOrderNumber\":12}"
                    : "{\"status\":\"" + status + "\"}");
            return reply;
        }
        @Override public android.database.Cursor query(android.net.Uri uri, String[] projection,
                                                       String selection, String[] args, String sort) { return null; }
        @Override public String getType(android.net.Uri uri) { return null; }
        @Override public android.net.Uri insert(android.net.Uri uri, android.content.ContentValues data) { return null; }
        @Override public int delete(android.net.Uri uri, String selection, String[] args) { return 0; }
        @Override public int update(android.net.Uri uri, android.content.ContentValues data,
                                    String selection, String[] args) { return 0; }
    }
}
