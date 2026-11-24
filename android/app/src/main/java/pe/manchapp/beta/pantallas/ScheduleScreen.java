package pe.manchapp.beta;

import android.widget.CheckBox;
import android.widget.LinearLayout;
import org.json.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

final class ScheduleScreen {

    static void openSchedule(AppShell shell, boolean forOrder) {
        JSONObject settings = shell.data().optJSONObject("settings");
        JSONObject current = forOrder ? shell.find("orders", shell.currentId()) : null;
        JSONObject saleDraft = SaleSchedule.editable(current != null && current.has("saleDate")
                ? current : settings);

        if (!forOrder) {
            JSONArray preselected = new JSONArray();
            String currentSaleDate = saleDraft.optString("saleDate");
            JSONArray products = shell.data().optJSONArray("products");
            if (!currentSaleDate.isEmpty() && products != null) {
                for (int i = 0; i < products.length(); i++) {
                    JSONObject p = products.optJSONObject(i);
                    if (p != null && p.optString("line").equals("commercial")
                            && currentSaleDate.equals(p.optString("saleDate"))) {
                        preselected.put(p.optString("id"));
                    }
                }
            }
            AppShell.put(saleDraft, "productIds", preselected);
        }

        shell.setSaleDraft(saleDraft);
        shell.go(forOrder ? "orderSchedule" : "schedule", shell.currentId());
    }

    static JSONArray scheduledProductIds(AppShell shell) {
        JSONObject saleDraft = shell.saleDraft();
        if (saleDraft == null) return new JSONArray();
        JSONArray ids = saleDraft.optJSONArray("productIds");
        return ids == null ? new JSONArray() : ids;
    }

    private static Set<String> selectedSet(JSONObject saleDraft) {
        Set<String> set = new LinkedHashSet<>();
        JSONArray ids = saleDraft.optJSONArray("productIds");
        for (int i = 0; ids != null && i < ids.length(); i++) {
            set.add(ids.optString(i));
        }
        return set;
    }

    private static void saveSet(JSONObject saleDraft, Set<String> set) {
        JSONArray arr = new JSONArray();
        for (String id : set) arr.put(id);
        AppShell.put(saleDraft, "productIds", arr);
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
        final JSONObject activeDraft = saleDraft;
        LinearLayout content = shell.content();
        NativeUi ui = shell.ui();

        shell.title(
                forOrder ? "Fechas del pedido" : "Programar una venta",
                forOrder ? "Ajusta el día y horario solo para este pedido."
                        : "Define cuándo cocinas y elige qué productos salen ese día."
        );

        // 1. Primero: fechas, horarios y estado de reservas
        ScheduleEditor.draw(shell.activity(), ui, content, activeDraft, shell::render);

        // 2. Segundo: selección directa de productos (sin panel blanco)
        if (!forOrder) {
            shell.add(content, ui.heading("Productos de esta jornada", 20), 22);
            JSONArray products = shell.data().optJSONArray("products");
            ArrayList<JSONObject> commercial = new ArrayList<>();
            for (int i = 0; products != null && i < products.length(); i++) {
                JSONObject p = products.optJSONObject(i);
                if (p != null && p.optString("line").equals("commercial")) {
                    commercial.add(p);
                }
            }

            if (commercial.isEmpty()) {
                shell.add(content, ui.message("Sin productos aún",
                        "Agrega primero tus productos en la pestaña Productos para incluirlos en la venta.", NativeUi.SOFT), 10);
            } else {
                Set<String> selected = selectedSet(activeDraft);
                shell.pair(content,
                        "Marcar todos", () -> {
                            Set<String> all = new LinkedHashSet<>();
                            for (JSONObject p : commercial) all.add(p.optString("id"));
                            saveSet(activeDraft, all);
                            shell.render();
                        },
                        "Limpiar selección", () -> {
                            saveSet(activeDraft, new LinkedHashSet<>());
                            shell.render();
                        });

                LinearLayout listCard = shell.card();
                for (int i = 0; i < commercial.size(); i++) {
                    JSONObject p = commercial.get(i);
                    String id = p.optString("id");
                    CheckBox box = new CheckBox(shell.activity());
                    box.setText(p.optString("name") + " · " + shell.cash(p.optDouble("price"))
                            + " (" + p.optInt("stock") + " cupos)");
                    box.setTextSize(15);
                    box.setTextColor(NativeUi.INK);
                    box.setTypeface(shell.regular());
                    box.setMinHeight(shell.dp(48));
                    box.setChecked(selected.contains(id));
                    box.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        Set<String> current = selectedSet(activeDraft);
                        if (isChecked) current.add(id);
                        else current.remove(id);
                        saveSet(activeDraft, current);
                    });
                    shell.add(listCard, box, i == 0 ? 0 : 4);
                }
                shell.add(content, listCard, 12);
            }
        }

        if (forOrder) {
            shell.note("Estos cambios solo corresponden a este pedido. Acuerda las fechas con el cliente.");
        } else {
            shell.note("Los cupos se editan en Productos. Cambiar de fecha no repone ni aumenta cantidades.");
        }

        shell.primary(forOrder ? "Guardar fechas del pedido" : "Guardar jornada", () -> {
            String error = SaleSchedule.invalid(activeDraft);
            if (!error.isEmpty()) {
                shell.toast(error);
                return;
            }
            if (activeDraft.optBoolean("salesOpen")
                    && SaleSchedule.parse(activeDraft.optString("reserveUntil"), false) <= System.currentTimeMillis()) {
                shell.toast("El plazo para reservar debe quedar en el futuro o pausa las reservas.");
                return;
            }
            if (activeDraft.optBoolean("salesOpen")
                    && SaleSchedule.parse(activeDraft.optString("pickupUntil"), false) <= System.currentTimeMillis()) {
                shell.toast("Elige un intervalo de recojo futuro o pausa las reservas.");
                return;
            }
            if (!forOrder) {
                JSONArray chosen = scheduledProductIds(shell);
                if (chosen.length() == 0) {
                    shell.toast("Elige al menos un producto para esta jornada.");
                    return;
                }
                try {
                    shell.store().saveSchedule(chosen, activeDraft);
                    shell.setSaleDraft(null);
                    shell.screen("home");
                    shell.render();
                    shell.toast("Jornada guardada para " + chosen.length()
                            + (chosen.length() == 1 ? " producto." : " productos."));
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
            AppShell.put(change, "pickup", activeDraft.optString("pickup",
                    shell.data().optJSONObject("settings").optString("pickup")));
            if (shell.enqueue("order", change, "order")) shell.setSaleDraft(null);
        });
    }
}