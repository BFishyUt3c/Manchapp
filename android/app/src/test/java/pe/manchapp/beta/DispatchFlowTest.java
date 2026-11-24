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
        JSONObject delivery = order("first", DATE, "delivery");
        delivery.put("deliveryLocation", DeliveryDestination.point(-12.1, -76.8, 5, 1));
        DeliveryDestination.confirm(delivery, DATE);
        String text = DispatchScreen.format(DATE, "Lucía", Collections.singletonList(delivery));
        assertTrue(text.contains("Reparto de Lucía"));
        assertTrue(text.contains("Calle Lima 10"));
        assertTrue(text.contains("900000000"));
        assertTrue(text.contains("query=-12.1%2C-76.8"));
        assertTrue(text.contains("Saldo pendiente"));
        assertFalse(DispatchScreen.eligible(order("pickup", DATE, "pickup")));
        assertFalse(DispatchScreen.eligible(delivery.put("status", "cancelled")));
    }
}
