package pe.manchapp.beta;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

final class OrderScreen {

    static JSONObject orderChange(JSONObject o) {
        JSONObject value = new JSONObject();
        AppShell.put(value, "id", o.optString("id"));
        AppShell.put(value, "version", o.optInt("version"));
        return value;
    }

    static void startManual(AppShell shell, JSONObject restore) {
        if (shell.syncing()) {
            shell.toast("Espera a que termine la actualización.");
            return;
        }
        Intent intent = new Intent(shell.activity(), ManualOrderActivity.class);
        if (restore != null) intent.putExtra("restoreDraft", restore.toString());
        shell.activity().startActivityForResult(intent, 85);
    }

    static void showOrders(AppShell shell) {
        MainActivity activity = shell.activity();
        NativeUi ui = shell.ui();
        LinearLayout content = shell.content();
        shell.title("Gestionar ventas", "Pedidos, cupos y entregas en un solo lugar.");
        JSONArray products = shell.data().optJSONArray("products");
        JSONArray orders = shell.data().optJSONArray("orders");
        int units = 0, attention = 0;
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product.optString("line").equals("commercial") && product.optBoolean("available")) {
                units += product.optInt("stock");
            }
        }
        for (int i = 0; orders != null && i < orders.length(); i++) {
            if (!Arrays.asList("delivered", "cancelled").contains(orders.optJSONObject(i).optString("status"))) {
                attention++;
            }
        }

        LinearLayout stats = new LinearLayout(activity);
        stats.setPadding(0, shell.dp(8), 0, shell.dp(8));
        LinearLayout stock = ui.message(String.valueOf(units), "Cupos", NativeUi.CARD);
        LinearLayout pendingBox = ui.message(String.valueOf(attention), "Pendientes", NativeUi.GOLD);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, 1);
        left.rightMargin = shell.dp(10);
        stats.addView(stock, left);
        stats.addView(pendingBox, new LinearLayout.LayoutParams(0, -1, 1));
        shell.add(content, stats, 16);

        LinearLayout quickActions = new LinearLayout(activity);
        quickActions.addView(ui.button("Registrar Pedido", true, "plus", () -> startManual(shell, null)),
                new LinearLayout.LayoutParams(0, -2, 1));
        View spacer = new View(activity);
        quickActions.addView(spacer, new LinearLayout.LayoutParams(shell.dp(12), 1));
        quickActions.addView(ui.button("Resumen", false, "orders", () -> shell.go("summary", "")),
                new LinearLayout.LayoutParams(0, -2, 1));
        shell.add(content, quickActions, 12);

        showOrderFilters(shell);
        shell.segmented(new String[]{"Por atender", "Terminados"}, shell.orderFilter().equals("active") ? 0 : 1, i -> {
            shell.orderFilter(i == 0 ? "active" : "finished");
            shell.render();
        });
        int count = 0;
        for (int i = 0; orders != null && i < orders.length(); i++) {
            JSONObject o = orders.optJSONObject(i);
            if (!visibleOrder(shell, o)) continue;
            boolean ended = o.optString("status").equals("delivered") || o.optString("status").equals("cancelled");
            if (ended != shell.orderFilter().equals("finished")) continue;
            count++;
            LinearLayout c = shell.card();
            shell.add(c, shell.text("Pedido " + String.format(Locale.US, "%03d", o.optInt("number")) + " · " + o.optString("customer"), 18, NativeUi.INK, true), 0);
            shell.add(c, shell.text(shell.status(o.optString("status")), 14, NativeUi.WINE, true), 7);
            shell.add(c, shell.text("Venta: " + SaleSchedule.day(o), 14, NativeUi.INK, true), 7);
            shell.add(c, shell.text((o.optString("fulfillment").equals("delivery") ? "Delivery · " + o.optString("zone") : "Para recoger") + " · " + shell.cash(o.optDouble("total")), 14, NativeUi.MUTED, false), 7);
            shell.add(c, shell.text(o.optBoolean("paid") ? "Pago confirmado" : "Pago pendiente", 12, o.optBoolean("paid") ? NativeUi.GREEN : 0xff8a6425, true), 6);
            shell.add(c, shell.button("Ver pedido  →", false, () -> shell.go("order", o.optString("id"))), 13);
            shell.add(content, c, 14);
        }
        if (count == 0) {
            LinearLayout c = shell.card();
            shell.add(c, shell.text("Todavía no hay pedidos aquí", 19, NativeUi.INK, true), 0);
            shell.add(c, shell.text("Cuando recibas un pedido, aquí verás su fecha de venta y podrás registrar la salida del delivery o habilitar el recojo.", 15, NativeUi.MUTED, false), 10);
            shell.add(content, c, 22);
        }
    }

    static void showOrder(AppShell shell) {
        MainActivity activity = shell.activity();
        NativeUi ui = shell.ui();
        LinearLayout content = shell.content();
        JSONObject order = shell.find("orders", shell.currentId());
        if (order == null) {
            shell.go("orders", "");
            return;
        }
        shell.title("Pedido " + String.format(Locale.US, "%03d", order.optInt("number")),
                order.optString("customer") + " · " + order.optString("phone"));
        LinearLayout food = shell.card();
        shell.add(food, shell.text("ESTADO DEL PEDIDO", 12, NativeUi.WINE, true), 0);
        shell.add(food, ui.heading(shell.status(order.optString("status")), 24), 6);
        if (order.optString("status").equals("cancelled") && !order.optString("cancelReason").isEmpty()) {
            shell.add(food, shell.text("Motivo de la cancelación: " + order.optString("cancelReason"), 14, NativeUi.MUTED, false), 4);
        }
        JSONArray items = order.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            shell.add(food, shell.text(item.optInt("quantity") + " × " + item.optString("name") + " · "
                    + shell.cash(item.optDouble("price") * item.optInt("quantity")), 16, NativeUi.INK, false), 10);
        }
        shell.add(food, shell.text("Entrega: " + (order.optString("fulfillment").equals("delivery")
                ? (order.optBoolean("deliveryOffer") ? "Gratis por promoción" : shell.cash(order.optDouble("fee")))
                : "Recojo sin costo"), 14, NativeUi.MUTED, false), 12);
        shell.add(food, shell.text("Total " + shell.cash(order.optDouble("total")), 24, NativeUi.WINE, true), 10);
        shell.add(content, food, 16);

        shell.add(content, ui.message("DÍA DE VENTA", SaleSchedule.day(order), NativeUi.GOLD), 14);
        if (order.optString("status").equals("registered")) {
            shell.add(content, ui.message("FALTA CONFIRMAR",
                    "Acepta el pedido para avisarle al cliente que la olla lo recibió. Después podrás marcar el día de venta.", NativeUi.SOFT), 12);
        } else if (order.optString("status").equals("confirmed")) {
            shell.add(content, ui.message("PEDIDO ACEPTADO",
                    "El cliente ya puede ver la confirmación. El botón de día de venta habilita recojo o reparto el día acordado.", NativeUi.SOFT), 12);
        }
        if (!order.optString("fulfillment").equals("delivery")) {
            shell.add(content, ui.message("PUEDES RECOGER", SaleSchedule.window(order), NativeUi.CARD), 12);
        }
        if (!Arrays.asList("delivering", "delivered", "cancelled", "pickup_available").contains(order.optString("status"))) {
            shell.menu("Fecha y recojo de este pedido", "Revisar o agregar fechas acordadas con el cliente", "clock",
                    () -> ScheduleScreen.openSchedule(shell, true));
        }

        LinearLayout payment = shell.card();
        String method = order.optString("paymentMethodConfirmed", order.optString("payment"));
        shell.add(payment, shell.text("PAGO · " + methodLabel(method), 12, NativeUi.WINE, true), 0);
        shell.add(payment, shell.text(order.optBoolean("paid") ? "Pago confirmado" : "Pago pendiente", 21, order.optBoolean("paid") ? NativeUi.GREEN : NativeUi.WINE, true), 7);
        if (order.optBoolean("paid") && order.optLong("paidAt", 0) > 0) {
            shell.add(payment, shell.text("Confirmado: " + formatPaidAt(order.optLong("paidAt")), 13, NativeUi.INK, false), 4);
        }
        if (!order.optString("paymentNote").isEmpty()) {
            shell.add(payment, shell.text("Nota: " + order.optString("paymentNote"), 13, NativeUi.MUTED, false), 3);
        }
        shell.add(payment, shell.text("El estado del pedido y el pago se confirman por separado.", 14, NativeUi.MUTED, false), 7);
        boolean pending = shell.store().hasPending(shell.currentId());
        String state = order.optString("status");
        if (!state.equals("cancelled")) {
            shell.add(payment, shell.button(order.optBoolean("paid") ? "Corregir estado del pago" : "Confirmar dinero recibido", false,
                    () -> {
                        if (order.optBoolean("paid")) {
                            new AlertDialog.Builder(activity)
                                    .setTitle("¿Marcar pago pendiente?")
                                    .setMessage("Se quitará la confirmación de cobro de este pedido.")
                                    .setNegativeButton("Volver", null)
                                    .setPositiveButton("Confirmar", (d, w) -> {
                                        if (shell.checkPending(shell.currentId())) return;
                                        JSONObject change = orderChange(order);
                                        AppShell.put(change, "paid", false);
                                        AppShell.put(change, "paidAt", 0L);
                                        AppShell.put(change, "paymentMethodConfirmed", "");
                                        AppShell.put(change, "paymentNote", "");
                                        shell.enqueue("order", change, "order");
                                    }).show();
                        } else {
                            confirmPaymentDialog(shell, order);
                        }
                    }), 12);
        }
        shell.add(content, payment, 12);
        if (SaleRules.validPhone(order.optString("phone"))) {
            shell.add(content, ui.button("Llamar al cliente", false, null, () -> {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + order.optString("phone"))));
                } catch (Exception error) {
                    shell.toast("No hay una aplicación de llamadas disponible.");
                }
            }), 12);
        }

        boolean delivery = order.optString("fulfillment").equals("delivery");
        String place = delivery
                ? DeliveryDestination.typeLabel(order.optString("destinationType", "home")) + " · " + order.optString("zone")
                        + "\n" + order.optString("address") + "\nReferencia: " + order.optString("reference")
                : order.optString("pickup", shell.data().optJSONObject("settings").optString("pickup"));
        LinearLayout arrival = ui.message(delivery ? "REPARTO A COORDINAR" : "RECOJO EN LA OLLA", place, NativeUi.CARD);
        if (!order.optString("driver").isEmpty()) {
            shell.add(arrival, shell.text("Reparte: " + order.optString("driver"), 16, NativeUi.GREEN, true), 10);
        }
        if (!order.optString("deliveryWindow").isEmpty()) {
            shell.add(arrival, shell.text("Horario acordado: " + order.optString("deliveryWindow"), 15, NativeUi.INK, true), 8);
        }
        if (delivery && !Arrays.asList("delivered", "cancelled").contains(state)) {
            shell.add(arrival, shell.button("Organizar reparto", false, () -> editDispatch(shell, order)), 12);
        }
        shell.add(content, arrival, 12);

        JSONObject deliveryPoint = order.optJSONObject("deliveryLocation");
        if (delivery) {
            boolean exact = DeliveryDestination.validPoint(deliveryPoint);
            String addressQuery = DeliveryDestination.addressGeo(order);
            if (exact || !order.optString("address").isEmpty()) {
                LinearLayout point = ui.message(exact ? "UBICACIÓN DE ENTREGA" : "DIRECCIÓN DE ENTREGA",
                        exact ? "Punto confirmado al reservar · precisión aproximada: " + Math.round(deliveryPoint.optDouble("accuracy")) + " m"
                                : DeliveryDestination.typeLabel(order.optString("destinationType", "home")) + " · " + order.optString("zone")
                                        + "\n" + order.optString("address") + "\nReferencia: " + order.optString("reference"),
                        NativeUi.CARD);
                shell.add(point, ui.button(exact ? "Abrir ubicación" : "Buscar dirección en Maps", false, "pin", () -> {
                    try {
                        String uri = exact ? DeliveryDestination.geo(deliveryPoint) : addressQuery;
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                    } catch (Exception error) {
                        shell.toast("No hay una aplicación de mapas disponible. La dirección escrita se conserva.");
                    }
                }), 10);
                shell.add(content, point, 12);
            }
        }

        if (pending) {
            shell.add(content, ui.message("Cambios guardados",
                    "La aplicación de clientes los verá al actualizar este pedido en el mismo celular.", NativeUi.GOLD), 12);
        }

        String next = SaleSchedule.next(state, delivery);
        String label = next.equals("confirmed") ? "Aceptar pedido"
                : next.equals("preparing") ? "Iniciar preparación"
                : next.equals("cooking") ? "Poner a cocinar"
                : next.equals("sale_day") ? "Marcar día de venta"
                : next.equals("delivering") ? "El delivery salió"
                : next.equals("pickup_available") ? "Habilitar recojo" : "Marcar como entregado";
        if (!next.isEmpty()) {
            final String target = next;
            shell.primary(label, () -> {
                if (shell.checkPending(shell.currentId())) return;
                try {
                    SaleSchedule.checkTransition(order, target, System.currentTimeMillis());
                } catch (Exception error) {
                    shell.toast(error.getMessage());
                    return;
                }
                if (target.equals("delivering")) {
                    EditText name = new EditText(activity);
                    name.setHint("Nombre de quien hará el reparto");
                    AlertDialog dialog = new AlertDialog.Builder(activity)
                            .setTitle("¿Quién lo entrega?")
                            .setView(name)
                            .setNegativeButton("Volver", null)
                            .setPositiveButton("Confirmar salida", null)
                            .create();
                    dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(view -> {
                        String value = name.getText().toString().trim();
                        if (value.length() < 2) {
                            name.setError("Escribe el nombre de quien reparte.");
                            return;
                        }
                        JSONObject change = orderChange(order);
                        AppShell.put(change, "status", target);
                        AppShell.put(change, "driver", value);
                        if (shell.enqueue("order", change, "order")) dialog.dismiss();
                    }));
                    dialog.show();
                } else {
                    JSONObject change = orderChange(order);
                    AppShell.put(change, "status", target);
                    shell.enqueue("order", change, "order");
                }
            });
        }
        if (!Arrays.asList("delivering", "delivered", "cancelled").contains(state)) {
            shell.add(content, shell.button("Cancelar este pedido", false, () -> {
                EditText reason = new EditText(activity);
                reason.setHint("Motivo de la cancelación");
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setTitle("¿Cancelar el pedido?")
                        .setMessage("Revisa con el cliente antes de cancelar. Los cupos volverán a quedar disponibles.")
                        .setView(reason)
                        .setNegativeButton("Conservar pedido", null)
                        .setPositiveButton("Sí, cancelar", null)
                        .create();
                dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(view -> {
                    if (shell.checkPending(shell.currentId())) return;
                    String value = reason.getText().toString().trim();
                    if (value.length() < 4) {
                        reason.setError("Escribe el motivo de la cancelación.");
                        return;
                    }
                    JSONObject change = orderChange(order);
                    AppShell.put(change, "status", "cancelled");
                    AppShell.put(change, "cancelReason", value);
                    if (shell.enqueue("order", change, "orders")) dialog.dismiss();
                }));
                dialog.show();
            }), 16);
        }
    }

    static void editDispatch(AppShell shell, JSONObject order) {
        MainActivity activity = shell.activity();
        LinearLayout form = shell.column();
        form.setPadding(shell.dp(20), shell.dp(10), shell.dp(20), shell.dp(8));
        EditText driver = new EditText(activity), window = new EditText(activity);
        driver.setText(order.optString("driver"));
        driver.setHint("Nombre de quien reparte");
        driver.setSingleLine(true);
        driver.setTextSize(17);
        driver.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        window.setText(order.optString("deliveryWindow"));
        window.setHint("Horario acordado, por ejemplo de 5 a 6 p. m.");
        window.setTextSize(17);
        window.setMinLines(2);
        window.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        shell.add(form, shell.text("Responsable del reparto", 15, NativeUi.INK, true), 0);
        shell.add(form, driver, 5);
        shell.add(form, shell.text("Franja de entrega acordada", 15, NativeUi.INK, true), 14);
        shell.add(form, window, 5);
        new AlertDialog.Builder(activity)
                .setTitle("Organizar reparto")
                .setView(form)
                .setNegativeButton("Volver", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    JSONObject change = orderChange(order);
                    AppShell.put(change, "driver", driver.getText().toString().trim());
                    AppShell.put(change, "deliveryWindow", window.getText().toString().trim());
                    shell.enqueue("order", change, "order");
                }).show();
    }

    static void showOrderFilters(AppShell shell) {
        if (shell.orderDate().isEmpty()) {
            shell.orderDate(shell.data().optJSONObject("settings").optString("saleDate"));
        }
        List<String> dates = shell.sellingDays(), ids = new ArrayList<>(), names = new ArrayList<>();
        ids.add("*");
        names.add("Todas las fechas");
        for (String date : dates) {
            ids.add(date);
            names.add(SaleSchedule.display(date, true));
        }
        shell.chooseFilter("Día de venta", names, ids, shell.orderDate(), value -> {
            shell.orderDate(value);
            shell.render();
        });
        List<String> deliveryIds = new ArrayList<>(Arrays.asList("all", "pickup", "delivery"));
        List<String> deliveryNames = new ArrayList<>(Arrays.asList("Todos los pedidos", "Solo recojo", "Todo el delivery"));
        JSONArray zones = shell.data().optJSONObject("settings").optJSONArray("zones");
        for (int i = 0; zones != null && i < zones.length(); i++) {
            JSONObject zone = zones.optJSONObject(i);
            deliveryIds.add("zone:" + zone.optString("id"));
            deliveryNames.add("Delivery · " + zone.optString("name"));
        }
        shell.chooseFilter("Modalidad o sector", deliveryNames, deliveryIds, shell.orderDelivery(), value -> {
            shell.orderDelivery(value);
            shell.render();
        });
    }

    static boolean visibleOrder(AppShell shell, JSONObject order) {
        String orderDate = shell.orderDate();
        String orderDelivery = shell.orderDelivery();
        if (!orderDate.equals("*") && !order.optString("saleDate").equals(orderDate)) return false;
        if (orderDelivery.equals("all")) return true;
        if (orderDelivery.equals("pickup") || orderDelivery.equals("delivery")) {
            return order.optString("fulfillment").equals(orderDelivery);
        }
        if (orderDelivery.startsWith("zone:")) {
            String id = orderDelivery.substring(5);
            if (order.optString("zoneId").equals(id)) return true;
            JSONArray zones = shell.data().optJSONObject("settings").optJSONArray("zones");
            for (int i = 0; zones != null && i < zones.length(); i++) {
                if (zones.optJSONObject(i).optString("id").equals(id)
                        && zones.optJSONObject(i).optString("name").equals(order.optString("zone"))) {
                    return true;
                }
            }
        }
        return false;
    }

    static void showSummary(AppShell shell) {
        NativeUi ui = shell.ui();
        LinearLayout content = shell.content();
        shell.title("Así va tu venta", "Cantidades, pagos y entregas de la jornada.");
        List<String> days = shell.sellingDays();
        if (shell.summaryDate().isEmpty()) {
            shell.summaryDate(shell.data().optJSONObject("settings").optString("saleDate"));
        }
        List<String> labels = new ArrayList<>();
        for (String day : days) labels.add(SaleSchedule.display(day, true));
        shell.chooseFilter("Día de venta", labels, days, shell.summaryDate(), value -> {
            shell.summaryDate(value);
            shell.render();
        });
        try {
            JSONObject summary = OrderSummary.build(shell.data(), shell.summaryDate());
            LinearLayout confirmed = ui.message(summary.optInt("orders") + " pedidos confirmados",
                    "Recojos: " + summary.optInt("pickups") + " · Repartos: " + summary.optInt("deliveries")
                            + "\nEntregados: " + summary.optInt("completed") + " · Pagos pendientes: " + summary.optInt("unpaid"),
                    NativeUi.GOLD);
            shell.add(content, confirmed, 16);
            if (summary.optInt("unconfirmed") > 0) {
                shell.add(content, ui.message(summary.optInt("unconfirmed")
                        + (summary.optInt("unconfirmed") == 1 ? " solicitud local" : " solicitudes locales"),
                        "Fueron registradas manualmente en este celular.", NativeUi.SOFT), 12);
            }
            shell.add(content, ui.heading("Conciliación de caja", 22), 22);
            LinearLayout cashCard = shell.card();
            shell.add(cashCard, shell.text("Cobrado: " + shell.cash(summary.optDouble("totalConfirmed"))
                    + " (" + summary.optInt("paidCount") + " pagos)", 19, NativeUi.GREEN, true), 0);
            shell.add(cashCard, shell.text("Pendiente por cobrar: " + shell.cash(summary.optDouble("totalPending")),
                    15, summary.optDouble("totalPending") > 0 ? NativeUi.WINE : NativeUi.MUTED, true), 6);
            shell.add(cashCard, shell.text("Efectivo: " + shell.cash(summary.optDouble("cashTotal"))
                    + " · Yape: " + shell.cash(summary.optDouble("yapeTotal"))
                    + " · Plin: " + shell.cash(summary.optDouble("plinTotal")), 14, NativeUi.INK, false), 8);
            shell.add(content, cashCard, 12);

            JSONArray history = summary.optJSONArray("paymentHistory");
            if (history != null && history.length() > 0) {
                shell.add(content, ui.heading("Historial de pagos", 19), 18);
                for (int i = 0; i < history.length(); i++) {
                    JSONObject entry = history.optJSONObject(i);
                    LinearLayout row = shell.card();
                    shell.add(row, shell.text("Pedido " + String.format(Locale.US, "%03d", entry.optInt("number"))
                            + " · " + entry.optString("customer"), 16, NativeUi.INK, true), 0);
                    String detail = methodLabel(entry.optString("method")) + " · " + shell.cash(entry.optDouble("total"));
                    if (entry.optLong("paidAt", 0) > 0) detail += " · " + formatPaidAt(entry.optLong("paidAt"));
                    shell.add(row, shell.text(detail, 14, NativeUi.GREEN, true), 4);
                    if (!entry.optString("note").isEmpty()) {
                        shell.add(row, shell.text("Nota: " + entry.optString("note"), 13, NativeUi.MUTED, false), 3);
                    }
                    row.setOnClickListener(v -> shell.go("order", entry.optString("id")));
                    shell.add(content, row, 10);
                }
            }

            shell.add(content, ui.heading("Cantidades reservadas", 22), 22);
            JSONArray products = summary.optJSONArray("products");
            if (products.length() == 0) shell.note("Todavía no hay cantidades registradas para esta jornada.");
            for (int i = 0; i < products.length(); i++) {
                JSONObject product = products.optJSONObject(i);
                LinearLayout row = shell.card();
                shell.add(row, shell.text(product.optString("name"), 18, NativeUi.INK, true), 0);
                shell.add(row, shell.text("Cantidad confirmada: " + product.optInt("confirmed"), 16, NativeUi.WINE, true), 6);
                if (product.optInt("pending") > 0) {
                    shell.add(row, shell.text(product.optInt("pending") + " por confirmar", 14, NativeUi.MUTED, false), 5);
                }
                shell.add(content, row, 12);
            }
            shell.add(content, ui.heading("Cupos disponibles", 21), 22);
            JSONArray available = shell.data().optJSONArray("products");
            for (int i = 0; available != null && i < available.length(); i++) {
                JSONObject product = available.optJSONObject(i);
                if (product.optString("line").equals("commercial")
                        && product.optBoolean("available")
                        && product.optString("saleDate").equals(shell.summaryDate())) {
                    shell.add(content, shell.text(product.optString("name") + " · " + product.optInt("stock"), 16, NativeUi.INK, false), 8);
                }
            }
            shell.primary("Ver pedidos de esta jornada", () -> {
                shell.orderDate(shell.summaryDate());
                shell.orderDelivery("all");
                shell.go("orders", "");
            });
        } catch (Exception error) {
            shell.note("No pudimos preparar el resumen. Tus pedidos se conservan.");
        }
    }

    private static String methodLabel(String method) {
        if ("yape".equalsIgnoreCase(method)) return "YAPE";
        if ("plin".equalsIgnoreCase(method)) return "PLIN";
        return "EFECTIVO";
    }

    private static String formatPaidAt(long timestamp) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd/MM HH:mm", new Locale("es", "PE"));
        fmt.setTimeZone(SaleSchedule.LIMA);
        return fmt.format(new Date(timestamp));
    }

    private static void confirmPaymentDialog(AppShell shell, JSONObject order) {
        MainActivity activity = shell.activity();
        LinearLayout form = shell.column();
        form.setPadding(shell.dp(20), shell.dp(10), shell.dp(20), shell.dp(8));
        Spinner methodPicker = shell.spinner(new String[]{"Efectivo", "Yape", "Plin"},
                "yape".equals(order.optString("payment")) ? 1 : "plin".equals(order.optString("payment")) ? 2 : 0);
        EditText note = new EditText(activity);
        note.setHint("Nro. operación o nota (opcional)");
        note.setSingleLine(true);
        note.setTextSize(16);
        note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        shell.add(form, shell.text("¿Cómo pagó el cliente?", 15, NativeUi.INK, true), 0);
        shell.add(form, methodPicker, 6);
        shell.add(form, shell.text("Referencia o nota", 15, NativeUi.INK, true), 14);
        shell.add(form, note, 6);
        new AlertDialog.Builder(activity)
                .setTitle("Confirmar " + shell.cash(order.optDouble("total")))
                .setView(form)
                .setNegativeButton("Volver", null)
                .setPositiveButton("Confirmar recibido", (d, w) -> {
                    if (shell.checkPending(shell.currentId())) return;
                    String[] codes = {"cash", "yape", "plin"};
                    String confirmedMethod = codes[methodPicker.getSelectedItemPosition()];
                    JSONObject change = orderChange(order);
                    AppShell.put(change, "paid", true);
                    AppShell.put(change, "paidAt", System.currentTimeMillis());
                    AppShell.put(change, "paymentMethodConfirmed", confirmedMethod);
                    AppShell.put(change, "payment", confirmedMethod);
                    AppShell.put(change, "paymentNote", note.getText().toString().trim());
                    shell.enqueue("order", change, "order");
                }).show();
    }
}
