package pe.manchapp.beta;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class DispatchFlowTest {
    private static final String DATE = "2026-11-23";

    private JSONObject order(String id, String date, String fulfillment) throws Exception {
        return new JSONObject().put("id", id).put("saleDate", date).put("fulfillment", fulfillment)
                .put("status", "registered").put("version", 1).put("number", 4)
                .put("customer", "María").put("phone", "900000000").put("address", "Calle Lima 10")
                .put("zone", "Manchay").put("reference", "Puerta azul").put("total", 15)
                .put("items", new JSONArray());
    }

    @Test public void assignmentIsAtomicAndSurvivesReopenWithoutChangingOrderStatus() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String filename = "dispatch-" + System.nanoTime() + ".json";
        LocalStore store = new LocalStore(context, filename);
        store.enqueue("manualOrder", order("first", DATE, "delivery"));
        store.enqueue("manualOrder", order("second", DATE, "delivery"));
        store.enqueue("manualOrder", order("other-day", "2026-11-24", "delivery"));
        Map<String, Integer> selected = new LinkedHashMap<>();
        selected.put("first", 1);
        selected.put("other-day", 1);
        JSONObject before = store.snapshot();
        try {
            store.assignDispatch(DATE, "Lucía", selected);
            fail("Mixed delivery dates must be rejected");
        } catch (IOException expected) {
            assertEquals(before.toString(), store.snapshot().toString());
        }
        selected.remove("other-day");
        selected.put("second", 1);
        store.assignDispatch(DATE, "Lucía", selected);
        LocalStore reopened = new LocalStore(context, filename);
        assertEquals("Lucía", reopened.deviceOrder("first").getString("driver"));
        assertEquals("Lucía", reopened.deviceOrder("second").getString("driver"));
        assertEquals("registered", reopened.deviceOrder("first").getString("status"));
        assertEquals("", reopened.deviceOrder("other-day").optString("driver"));
    }

    @Test public void dispatchMessageIncludesOnlyProvidedDeliveriesAndUsesConfirmedPoint() throws Exception {
        JSONObject delivery = order("first", DATE, "delivery").put("status", "confirmed")
                .put("items", new JSONArray().put(new JSONObject().put("quantity", 2).put("name", "Causa")));
        delivery.put("deliveryLocation", DeliveryDestination.point(-12.1, -76.8, 5, 1));
        DeliveryDestination.confirm(delivery, DATE);
        delivery.put("deliveryExtras", new JSONArray().put(new JSONObject().put("extraAmount", 4.50)
                .put("items", new JSONArray().put(new JSONObject().put("quantity", 1).put("name", "Mazamorra")))));
        String text = DispatchScreen.format(DATE, "Lucía", Collections.singletonList(delivery));
        assertTrue(text.contains("Reparto de Lucía"));
        assertTrue(text.contains("Calle Lima 10"));
        assertTrue(text.contains("900000000"));
        assertTrue(text.contains("query=-12.1%2C-76.8"));
        assertTrue(text.contains("Saldo pendiente"));
        assertFalse(text.contains("Puerta azul"));
        assertFalse(text.contains("Causa"));
        assertTrue(text.contains("1 × Mazamorra"));
        assertTrue(text.contains("El pago de los extras se coordina aparte."));
        String single = DispatchScreen.formatOrder(DATE, delivery);
        assertTrue(single.contains("Puerta azul"));
        assertTrue(single.contains("2 × Causa"));
        assertTrue(single.contains("1 × Mazamorra"));
        assertTrue(single.contains("query=-12.1%2C-76.8"));
        assertFalse(DispatchScreen.eligible(order("pickup", DATE, "pickup")));
        assertFalse(DispatchScreen.eligible(delivery.put("status", "cancelled")));
    }

    @Test public void demoBatchAddsTenLocalDeliveriesWithoutUsingStockOrSyncQueue() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String filename = "dispatch-demo-" + System.nanoTime() + ".json";
        LocalStore store = new LocalStore(context, filename);
        JSONObject before = store.snapshot();
        String date = store.addDemoDeliveries();
        JSONArray orders = store.snapshot().getJSONArray("orders");
        int start = before.getJSONArray("orders").length();
        assertEquals(start + 10, orders.length());
        int paid = 0, extraordinary = 0;
        for (int i = start; i < orders.length(); i++) {
            JSONObject order = orders.getJSONObject(i);
            assertTrue(order.getBoolean("demoOrder"));
            assertTrue(DispatchScreen.eligible(order));
            assertEquals(date, order.getString("saleDate"));
            if (order.getBoolean("paid")) {
                assertEquals("confirmed", order.getString("status"));
                paid++;
            }
            if ("registered".equals(order.getString("status"))) {
                assertFalse(order.getBoolean("paid"));
                assertFalse(order.getString("demoReviewReason").isEmpty());
                extraordinary++;
            }
        }
        assertTrue(paid > 0);
        assertEquals(3, extraordinary);
        JSONObject first = orders.getJSONObject(start);
        String id = first.getString("id");
        store.assignDispatch(date, "Lucía", Collections.singletonMap(id, first.getInt("version")));
        assertEquals("Lucía", store.deviceOrder(id).getString("driver"));
        store.enqueue("order", OrderScreen.orderChange(store.deviceOrder(id)).put("status", "cancelled"));
        LocalStore reopened = new LocalStore(context, filename);
        assertEquals("cancelled", reopened.deviceOrder(id).getString("status"));
        assertEquals(before.getJSONArray("products").toString(), reopened.snapshot().getJSONArray("products").toString());
        assertEquals(before.getJSONArray("pending").toString(), reopened.snapshot().getJSONArray("pending").toString());
    }
}
