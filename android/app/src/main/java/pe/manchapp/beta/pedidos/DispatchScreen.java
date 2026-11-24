package pe.manchapp.beta;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputFilter;
import android.widget.CheckBox;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

final class DispatchScreen {
    static boolean eligible(JSONObject order) {
        return order != null && "delivery".equals(order.optString("fulfillment"))
                && !"delivered".equals(order.optString("status"))
                && !"cancelled".equals(order.optString("status"))
                && SaleSchedule.parse(order.optString("saleDate"), true) >= 0
                && !order.optString("address").trim().isEmpty();
    }

    private static List<JSONObject> deliveries(JSONObject state, String date) {
        List<JSONObject> result = new ArrayList<>();
        JSONArray orders = state.optJSONArray("orders");
        for (int i = 0; orders != null && i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (eligible(order) && (date.isEmpty() || date.equals(order.optString("saleDate")))) {
                result.add(order);
            }
        }
        result.sort(Comparator.comparing((JSONObject order) -> order.optString("zone"), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(order -> order.optInt("number")));
        return result;
    }

    static void show(AppShell shell, String requestedDate) {
        shell.title("Organizar reparto", "Elige el día y marca los pedidos para cada repartidor.");
        TreeSet<String> dates = new TreeSet<>();
        for (JSONObject order : deliveries(shell.data(), "")) dates.add(order.optString("saleDate"));
        if (dates.isEmpty()) {
            shell.note("No hay entregas pendientes con fecha y dirección.");
            return;
        }
        String today = SaleSchedule.date(System.currentTimeMillis());
        String upcoming = dates.ceiling(today);
        String date = dates.contains(requestedDate) ? requestedDate : upcoming == null ? dates.last() : upcoming;
        LinearLayout content = shell.content();
        List<String> dateOptions = new ArrayList<>(dates);
        String[] labels = new String[dateOptions.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = SaleSchedule.display(dateOptions.get(i), true);
        shell.add(content, shell.button("Fecha: " + SaleSchedule.display(date, true), false, () ->
                new AlertDialog.Builder(shell.activity()).setTitle("Día de reparto")
                        .setSingleChoiceItems(labels, dateOptions.indexOf(date), (dialog, index) -> {
                            dialog.dismiss();
                            if (index != dateOptions.indexOf(date)) shell.go("dispatch", dateOptions.get(index));
                        }).setNegativeButton("Volver", null).show()), 12);

        List<JSONObject> orders = deliveries(shell.data(), date);
        Map<String, Integer> drivers = new LinkedHashMap<>();
        for (JSONObject order : orders) {
            String name = order.optString("driver").trim();
            if (!name.isEmpty()) drivers.put(name, drivers.getOrDefault(name, 0) + 1);
        }
        if (!drivers.isEmpty()) {
            shell.add(content, shell.text("Repartos asignados", 18, NativeUi.INK, true), 12);
            drivers.forEach((name, count) -> shell.menu(name, count + " entrega(s) · Ver lista y compartir",
                    "orders", () -> preview(shell, date, name)));
        }

        shell.add(content, shell.text("Selecciona entregas", 18, NativeUi.INK, true), 12);
        Map<String, Integer> selected = new LinkedHashMap<>();
        String lastZone = null;
        for (JSONObject order : orders) {
            String zone = order.optString("zone");
            if (!zone.equals(lastZone)) {
                shell.add(content, shell.text(zone.isEmpty() ? "Sin sector" : zone, 15, NativeUi.WINE, true), 10);
                lastZone = zone;
            }
            String driver = order.optString("driver").trim();
            String id = order.optString("id");
            CheckBox check = new CheckBox(shell.activity());
            check.setMinHeight(shell.dp(48));
            check.setTextColor(NativeUi.INK);
            check.setText("Pedido " + order.optInt("number") + " · " + order.optString("customer")
                    + "\n" + order.optString("address") + (driver.isEmpty() ? "" : "\nReparte: " + driver));
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.put(id, order.optInt("version"));
                else selected.remove(id);
            });
            shell.add(content, check, 6);
        }
        shell.primary("Asignar seleccionados", () -> assign(shell, date, selected, drivers));
    }

    private static void assign(AppShell shell, String date, Map<String, Integer> selected, Map<String, Integer> drivers) {
        if (selected.isEmpty()) {
            shell.toast("Marca al menos un pedido para repartir.");
            return;
        }
        AutoCompleteTextView name = new AutoCompleteTextView(shell.activity());
        name.setAdapter(new ArrayAdapter<>(shell.activity(), android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>(drivers.keySet())));
        name.setThreshold(1);
        name.setSingleLine(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        name.setHint("Nombre del repartidor");
        LinearLayout form = shell.column();
        form.setPadding(shell.dp(20), 0, shell.dp(20), 0);
        form.addView(name);
        AlertDialog dialog = new AlertDialog.Builder(shell.activity())
                .setTitle("Asignar " + selected.size() + " entrega(s)")
                .setMessage("Si alguno ya tiene repartidor, se reasignará a la persona que indiques.")
                .setView(form).setNegativeButton("Volver", null).setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String driver = name.getText().toString().trim();
            if (driver.length() < 2) {
                name.setError("Escribe el nombre del repartidor.");
                return;
            }
            if (shell.syncing()) {
                shell.toast("Espera a que termine la actualización.");
                return;
            }
            try {
                shell.store().assignDispatch(date, driver, selected);
            } catch (Exception error) {
                dialog.dismiss();
                shell.render();
                shell.toast(error.getMessage());
                return;
            }
            dialog.dismiss();
            shell.render();
            preview(shell, date, driver);
        }));
        dialog.show();
    }

    private static void preview(AppShell shell, String date, String driver) {
        List<JSONObject> orders = deliveries(shell.store().snapshot(), date);
        orders.removeIf(order -> !driver.equals(order.optString("driver").trim()));
        if (orders.isEmpty()) {
            shell.toast("Este repartidor ya no tiene entregas pendientes para ese día.");
            shell.render();
            return;
        }
        String message = format(date, driver, orders);
        new AlertDialog.Builder(shell.activity()).setTitle("Lista para " + driver).setMessage(message)
                .setNegativeButton("Cerrar", null).setPositiveButton("Abrir WhatsApp", (dialog, ignored) -> {
                    Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message);
                    FlyerStudioScreen.openWhatsAppOrChooser(shell.activity(), share, "Compartir lista de reparto", true);
                }).show();
    }

    static String format(String date, String driver, List<JSONObject> orders) {
        StringBuilder message = new StringBuilder("Reparto de ").append(driver).append("\n")
                .append(SaleSchedule.display(date, true)).append(" · ").append(orders.size()).append(" entrega(s)");
        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("es", "PE"));
        for (JSONObject order : orders) {
            message.append("\n\nPedido ").append(order.optInt("number")).append(" · ").append(order.optString("customer"));
            appendLine(message, "Teléfono", order.optString("phone"));
            appendLine(message, "Sector", order.optString("zone"));
            appendLine(message, "Dirección", order.optString("address"));
            appendLine(message, "Referencia", order.optString("reference"));
            appendLine(message, "Horario", order.optString("deliveryWindow"));
            JSONObject point = order.optJSONObject("deliveryLocation");
            String query = point != null && DeliveryDestination.problem(order, date).isEmpty()
                    ? point.optDouble("latitude") + "," + point.optDouble("longitude")
                    : (order.optString("address") + " " + order.optString("zone")).trim();
            appendLine(message, "Mapa", "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query));
            JSONArray items = order.optJSONArray("items");
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) message.append("\n- ").append(item.optInt("quantity")).append(" × ").append(item.optString("name"));
            }
            message.append(order.optBoolean("paid") ? "\nPago: pagado" :
                    "\nSaldo pendiente: " + currency.format(order.optDouble("total", 0)));
        }
        return message.toString();
    }

    private static void appendLine(StringBuilder message, String label, String value) {
        if (!value.trim().isEmpty()) message.append("\n").append(label).append(": ").append(value.trim());
    }
}
