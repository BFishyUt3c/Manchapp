package pe.manchapp.beta;

import android.app.AlertDialog;
import android.text.TextUtils;
import android.widget.LinearLayout;
import org.json.*;
import java.util.ArrayList;

final class ScheduleScreen {

    static void openSchedule(AppShell shell, boolean forOrder) {
        JSONObject current = forOrder ? shell.find("orders", shell.currentId()) : null;
        JSONObject saleDraft = SaleSchedule.editable(current != null && current.has("saleDate")
                ? current : shell.data().optJSONObject("settings"));
        if (!forOrder) AppShell.put(saleDraft, "productIds", new JSONArray());
        shell.setSaleDraft(saleDraft);
        shell.go(forOrder ? "orderSchedule" : "schedule", shell.currentId());
    }

    static JSONArray scheduledProductIds(AppShell shell) {
        JSONObject saleDraft = shell.saleDraft();
        if (saleDraft == null) return new JSONArray();
        JSONArray ids = saleDraft.optJSONArray("productIds");
        return ids == null ? new JSONArray() : ids;
    }

    static void chooseScheduleProducts(AppShell shell) {
        MainActivity activity = shell.activity();
        JSONArray products = shell.data().optJSONArray("products");
        JSONArray selected = scheduledProductIds(shell);
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product.optString("line").equals("commercial")) {
                ids.add(product.optString("id"));
                labels.add(product.optString("name"));
            }
        }
        if (ids.isEmpty()) {
            shell.toast("Primero agrega un producto.");
            return;
        }
        boolean[] checked = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            for (int j = 0; j < selected.length(); j++) {
                if (ids.get(i).equals(selected.optString(j))) checked[i] = true;
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Productos de esta venta")
                .setMultiChoiceItems(labels.toArray(new String[0]), checked, null)
                .setNegativeButton("Volver", null)
                .setPositiveButton("Usar productos", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(button -> {
            JSONArray chosen = new JSONArray();
            for (int i = 0; i < checked.length; i++) {
                if (dialog.getListView().isItemChecked(i)) chosen.put(ids.get(i));
            }
            if (chosen.length() == 0) {
                shell.toast("Elige al menos un producto.");
                return;
            }
            JSONObject saleDraft = shell.saleDraft();
            if (selected.length() == 0) {
                JSONObject first = shell.find("products", chosen.optString(0));
                JSONObject schedule = SaleSchedule.forProduct(first, shell.data().optJSONObject("settings"));
                for (String key : SaleSchedule.KEYS) {
                    if (schedule.has(key)) AppShell.put(saleDraft, key, schedule.opt(key));
                }
            }
            AppShell.put(saleDraft, "productIds", chosen);
            dialog.dismiss();
            shell.render();
        }));
        dialog.show();
    }

    static void showSchedule(AppShell shell, boolean forOrder) {
        if (forOrder && shell.find("orders", shell.currentId()) == null) {
            shell.go("orders", "");
            return;
        }
        JSONObject saleDraft = shell.saleDraft();
        if (saleDraft == null) {
            saleDraft = SaleSchedule.editable(shell.data().optJSONObject("settings"));
            shell.setSaleDraft(saleDraft);
        }
        LinearLayout content = shell.content();
        NativeUi ui = shell.ui();
        if (!forOrder) {
            JSONArray ids = scheduledProductIds(shell);
            shell.add(content, ui.button("Elegir productos (" + ids.length() + ")", true, "products",
                    () -> chooseScheduleProducts(shell)), 0);
            if (ids.length() > 0) {
                ArrayList<String> names = new ArrayList<>();
                for (int i = 0; i < ids.length(); i++) {
                    JSONObject product = shell.find("products", ids.optString(i));
                    if (product != null) names.add(product.optString("name"));
                }
                shell.add(content, ui.message("Productos de esta jornada", TextUtils.join(" · ", names), NativeUi.GOLD), 12);
            }
        }
        ScheduleEditor.draw(shell.activity(), ui, content, saleDraft, shell::render);
        if (forOrder) shell.note("Estos cambios solo corresponden a este pedido. Acuerda las fechas con el cliente.");
        else shell.note("Los cupos de los productos se editan en Productos. Cambiar de fecha no repone ni aumenta cantidades.");
        final JSONObject activeDraft = saleDraft;
        shell.primary(forOrder ? "Guardar fechas del pedido" : "Guardar jornada", () -> {
            String error = SaleSchedule.invalid(activeDraft);
            if (!error.isEmpty()) {
                shell.toast(error);
                return;
            }
            if (activeDraft.optBoolean("salesOpen") && SaleSchedule.parse(activeDraft.optString("reserveUntil"), false) <= System.currentTimeMillis()) {
                shell.toast("El plazo para reservar debe quedar en el futuro o pausa las reservas.");
                return;
            }
            if (activeDraft.optBoolean("salesOpen") && SaleSchedule.parse(activeDraft.optString("pickupUntil"), false) <= System.currentTimeMillis()) {
                shell.toast("Elige un intervalo de recojo futuro o pausa las reservas.");
                return;
            }
            if (!forOrder) {
                try {
                    shell.store().saveSchedule(scheduledProductIds(shell), activeDraft);
                    shell.setSaleDraft(null);
                    shell.screen("home");
                    shell.render();
                    shell.toast("Jornada guardada para los productos elegidos.");
                } catch (Exception errorSave) {
                    shell.toast(errorSave.getMessage());
                }
                return;
            }
            JSONObject change = OrderScreen.orderChange(shell.find("orders", shell.currentId()));
            for (String key : SaleSchedule.KEYS) {
                try {
                    change.put(key, activeDraft.get(key));
                } catch (JSONException fatal) {
                    throw new IllegalStateException(fatal);
                }
            }
            AppShell.put(change, "pickup", activeDraft.optString("pickup", shell.data().optJSONObject("settings").optString("pickup")));
            if (shell.enqueue("order", change, "order")) shell.setSaleDraft(null);
        });
    }
}
