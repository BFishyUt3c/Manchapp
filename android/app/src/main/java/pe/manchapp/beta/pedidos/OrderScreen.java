package pe.manchapp.beta;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

final class OrderScreen {
    private static final int CONFIRM_INK = 0xff794900, CONFIRM_BG = 0xffffe3a3;
    private static final int PAYMENT_INK = 0xff254d83, PAYMENT_BG = 0xffe1edff;
    private static final int PAID_BG = 0xffe6f4ea;

    private static int statusColor(String state) {
        if (state.equals("registered")) return CONFIRM_INK;
        if (state.equals("delivered")) return NativeUi.GREEN;
        if (state.equals("cancelled")) return NativeUi.MUTED;
        return NativeUi.WINE;
    }

    private static int statusBackground(String state) {
        if (state.equals("registered")) return CONFIRM_BG;
        if (state.equals("delivered")) return PAID_BG;
        if (state.equals("cancelled")) return NativeUi.LINE;
        return NativeUi.SOFT;
    }

    static boolean matchesFilters(JSONObject order, String statusFilter, String paymentFilter) {
        String state = order.optString("status");
        boolean cancelled = state.equals("cancelled");
        boolean finished = cancelled || state.equals("delivered");
        if ((statusFilter.equals("active") && finished) || (statusFilter.equals("finished") && !finished)
                || (statusFilter.equals("registered") && !state.equals("registered"))) return false;
        if (paymentFilter.equals("paid")) return order.optBoolean("paid");
        if (paymentFilter.equals("unpaid")) return !cancelled && !order.optBoolean("paid");
        return true;
    }

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
        shell.title("Ventas", "Gestiona pedidos, cobros y entregas.");
        JSONArray orders = shell.data().optJSONArray("orders");
        if (shell.orderDate().isEmpty()) {
            String date = shell.data().optJSONObject("settings").optString("saleDate");
            shell.orderDate(date.isEmpty() ? "*" : date);
        }
        int unconfirmed = 0, unpaid = 0;
        for (int i = 0; orders != null && i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null || !visibleOrder(shell, order)) continue;
            if (matchesFilters(order, "registered", "all")) unconfirmed++;
            if (matchesFilters(order, "all", "unpaid")) unpaid++;
        }

        LinearLayout stats = new LinearLayout(activity);
        stats.setPadding(0, shell.dp(6), 0, shell.dp(6));
        LinearLayout confirmBox = ui.message(String.valueOf(unconfirmed), "Por confirmar", CONFIRM_BG);
        LinearLayout pendingBox = ui.message(String.valueOf(unpaid), "Pago pendiente", PAYMENT_BG);
        ui.buttonRole(confirmBox);
        confirmBox.setOnClickListener(view -> {
            shell.orderFilter("registered");
            shell.orderPayment("all");
            shell.render();
        });
        ui.buttonRole(pendingBox);
        pendingBox.setOnClickListener(view -> {
            shell.orderFilter("all");
            shell.orderPayment("unpaid");
            shell.render();
        });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, 1);
        left.rightMargin = shell.dp(10);
        stats.addView(confirmBox, left);
        stats.addView(pendingBox, new LinearLayout.LayoutParams(0, -1, 1));
        shell.add(content, stats, 14);

        LinearLayout quickActions = new LinearLayout(activity);
        TextView addOrder = ui.button("+ Pedido", true, null, () -> startManual(shell, null));
        TextView summaryBtn = ui.button("Resumen", false, null, () -> shell.go("summary", ""));
        addOrder.setTextSize(15);
        summaryBtn.setTextSize(15);
        LinearLayout.LayoutParams actLeft = new LinearLayout.LayoutParams(0, -2, 1);
        actLeft.rightMargin = shell.dp(10);
        quickActions.addView(addOrder, actLeft);
        quickActions.addView(summaryBtn, new LinearLayout.LayoutParams(0, -2, 1));
        shell.add(content, quickActions, 12);
        shell.add(content, shell.button("Organizar reparto", false, () -> shell.go("dispatch", "")), 12);

        showOrderFilters(shell);
        int count = 0;
        for (int i = orders == null ? -1 : orders.length() - 1; i >= 0; i--) {
            JSONObject o = orders.optJSONObject(i);
            if (o == null || !visibleOrder(shell, o) || !matchesFilters(o, shell.orderFilter(), shell.orderPayment())) continue;
            count++;

            LinearLayout c = shell.card();
            LinearLayout header = new LinearLayout(activity);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(
                    shell.text("Pedido " + String.format(Locale.US, "%03d", o.optInt("number")) + " · " + o.optString("customer"),
                            17, NativeUi.INK, true),
                    new LinearLayout.LayoutParams(0, -2, 1));
            header.addView(shell.text("›", 24, NativeUi.MUTED, false));
            c.addView(header);

            LinearLayout badges = new LinearLayout(activity);
            String status = o.optString("status");
            TextView statusChip = ui.chip(shell.status(status), statusColor(status), statusBackground(status));
            statusChip.setTextSize(11);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
            chipLp.rightMargin = shell.dp(8);
            badges.addView(statusChip, chipLp);

            boolean paid = o.optBoolean("paid");
            if (paid || !status.equals("cancelled")) {
                TextView paidChip = ui.chip(paid ? "Pagado" : "Pago pendiente",
                        paid ? NativeUi.GREEN : PAYMENT_INK, paid ? PAID_BG : PAYMENT_BG);
                paidChip.setTextSize(11);
                badges.addView(paidChip);
            }
            shell.add(c, badges, 8);

            shell.add(c, shell.text("Venta: " + SaleSchedule.day(o), 14, NativeUi.INK, true), 8);
            String mode = o.optString("fulfillment").equals("delivery")
                    ? "Delivery · " + o.optString("zone")
                    : "Para recoger";
            shell.add(c, shell.text(mode + " · " + shell.cash(o.optDouble("total")), 14, NativeUi.MUTED, false), 4);

            c.setFocusable(true);
            c.setOnClickListener(v -> shell.go("order", o.optString("id")));
            shell.add(content, c, 12);
        }
        if (count == 0) {
            LinearLayout c = shell.card();
            shell.add(c, shell.text("No hay pedidos con estos filtros", 18, NativeUi.INK, true), 0);
            shell.add(c, shell.text("Cambia los filtros o registra un pedido nuevo.", 14, NativeUi.MUTED, false), 8);
            shell.add(content, c, 18);
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
        String state = order.optString("status");
        boolean delivery = order.optString("fulfillment").equals("delivery");
        LinearLayout statusCard = shell.card();
        shell.add(statusCard, ui.chip(shell.status(state), statusColor(state), statusBackground(state)), 0);
        shell.add(statusCard, shell.text("Venta: " + SaleSchedule.day(order), 15, NativeUi.INK, true), 8);
        if (state.equals("registered")) {
            shell.add(statusCard, shell.text("Confirma el pedido para avisar al cliente que lo aceptaste.",
                    14, NativeUi.MUTED, false), 6);
        }
        String next = SaleSchedule.next(state, delivery);
        String label = next.equals("confirmed") ? "Confirmar pedido"
                : next.equals("sale_day") ? "Marcar día de venta"
                : next.equals("delivering") ? "El delivery salió"
                : next.equals("pickup_available") ? "Habilitar recojo" : "Marcar como entregado";
        if (!next.isEmpty()) {
            final String target = next;
            shell.add(statusCard, ui.button(label, true, null, () -> {
                if (shell.checkPending(shell.currentId())) return;
                try {
                    SaleSchedule.checkTransition(order, target, System.currentTimeMillis());
                } catch (Exception error) {
                    shell.toast(error.getMessage());
                    return;
                }
                if (target.equals("delivering")) {
                    LinearLayout form = dialogContainer(shell);
                    EditText name = styledInput(shell, form, "Responsable del reparto", "Ej. Juan Pérez", 80, true);
                    name.setText(order.optString("driver"));
                    AlertDialog dialog = new AlertDialog.Builder(activity)
                            .setTitle("¿Quién lo entrega?")
                            .setView(form)
                            .setNegativeButton("Volver", null)
                            .setPositiveButton("Confirmar salida", null)
                            .create();
                    dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
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
            }), 12);
        }
        shell.add(content, statusCard, 12);

        LinearLayout payment = shell.card();
        String method = order.optString("paymentMethodConfirmed", order.optString("payment"));
        shell.add(payment, shell.text("PAGO · " + methodLabel(method), 12, NativeUi.WINE, true), 0);
        boolean paid = order.optBoolean("paid");
        if (paid || !state.equals("cancelled")) {
            shell.add(payment, ui.chip(paid ? "Pagado" : "Pago pendiente",
                    paid ? NativeUi.GREEN : PAYMENT_INK, paid ? PAID_BG : PAYMENT_BG), 7);
        }
        if (order.optBoolean("paid") && order.optLong("paidAt", 0) > 0) {
            shell.add(payment, shell.text("Confirmado: " + formatPaidAt(order.optLong("paidAt")), 13, NativeUi.INK, false), 4);
        }
        if (!order.optString("paymentNote").isEmpty()) {
            shell.add(payment, shell.text("Nota: " + order.optString("paymentNote"), 13, NativeUi.MUTED, false), 3);
        }
        shell.add(payment, shell.text("Total " + shell.cash(order.optDouble("total")), 19, NativeUi.INK, true), 7);
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

        LinearLayout food = shell.card();
        shell.add(food, shell.text("PRODUCTOS DEL PEDIDO", 12, NativeUi.WINE, true), 0);
        JSONArray items = order.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            shell.add(food, shell.text(item.optInt("quantity") + " × " + item.optString("name") + " · "
                    + shell.cash(item.optDouble("price") * item.optInt("quantity")), 16, NativeUi.INK, false), 10);
        }
        shell.add(food, shell.text("Entrega: " + (order.optString("fulfillment").equals("delivery")
                ? (order.optBoolean("deliveryOffer") ? "Gratis por promoción" : shell.cash(order.optDouble("fee")))
                : "Recojo sin costo"), 14, NativeUi.MUTED, false), 12);
        shell.add(content, food, 12);

        if (SaleRules.validPhone(order.optString("phone"))) {
            shell.add(content, ui.button("Llamar al cliente", false, null, () -> {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + order.optString("phone"))));
                } catch (Exception error) {
                    shell.toast("No hay una aplicación de llamadas disponible.");
                }
            }), 12);
        }

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
        if (!delivery) {
            shell.add(arrival, shell.text(SaleSchedule.window(order), 14, NativeUi.INK, false), 8);
        }

        JSONObject deliveryPoint = order.optJSONObject("deliveryLocation");
        if (delivery) {
            boolean exact = DeliveryDestination.validPoint(deliveryPoint);
            String addressQuery = DeliveryDestination.addressGeo(order);
            if (exact || !order.optString("address").isEmpty()) {
                if (exact) {
                    shell.add(arrival, shell.text("Punto confirmado · precisión aproximada: "
                            + Math.round(deliveryPoint.optDouble("accuracy")) + " m", 13, NativeUi.MUTED, false), 8);
                }
                shell.add(arrival, ui.button(exact ? "Abrir ubicación" : "Buscar dirección en Maps", false, "pin", () -> {
                    try {
                        String uri = exact ? DeliveryDestination.geo(deliveryPoint) : addressQuery;
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                    } catch (Exception error) {
                        shell.toast("No hay una aplicación de mapas disponible. La dirección escrita se conserva.");
                    }
                }), 10);
            }
        }

        shell.add(content, arrival, 12);
        if (!Arrays.asList("delivering", "delivered", "cancelled", "pickup_available").contains(state)) {
            shell.menu("Fecha y recojo de este pedido", "Revisar o editar la fecha acordada", "clock",
                    () -> ScheduleScreen.openSchedule(shell, true));
        }

        if (shell.store().hasPending(shell.currentId())) {
            shell.add(content, ui.message("Cambios guardados",
                    "La aplicación de clientes los verá al actualizar este pedido en el mismo celular.", NativeUi.GOLD), 12);
        }

        if (!Arrays.asList("delivering", "delivered", "cancelled").contains(state)) {
            shell.add(content, shell.button("Cancelar este pedido", false, () -> new AlertDialog.Builder(activity)
                    .setTitle("¿Cancelar el pedido?")
                    .setMessage("Revisa con el cliente antes de cancelar. Los cupos volverán a quedar disponibles.")
                    .setNegativeButton("Conservar pedido", null)
                    .setPositiveButton("Sí, cancelar", (d, w) -> {
                        if (shell.checkPending(shell.currentId())) return;
                        JSONObject change = orderChange(order);
                        AppShell.put(change, "status", "cancelled");
                        shell.enqueue("order", change, "orders");
                    }).show()), 16);
        }
    }

    private static LinearLayout dialogContainer(AppShell shell) {
        LinearLayout form = shell.column();
        form.setBackgroundColor(NativeUi.BG);
        form.setPadding(shell.dp(20), shell.dp(14), shell.dp(20), shell.dp(12));
        return form;
    }

    private static EditText styledInput(AppShell shell, LinearLayout parent, String label, String hint, int maxLen, boolean singleLine) {
        shell.add(parent, shell.text(label, 14, NativeUi.INK, true), parent.getChildCount() == 0 ? 0 : 12);
        EditText e = new EditText(shell.activity());
        e.setHint(hint);
        e.setSingleLine(singleLine);
        if (!singleLine) e.setMinLines(2);
        e.setTextSize(16);
        e.setTypeface(shell.regular());
        e.setTextColor(NativeUi.INK);
        e.setHintTextColor(NativeUi.MUTED);
        e.setPadding(shell.dp(13), shell.dp(11), shell.dp(13), shell.dp(11));
        e.setBackground(shell.bg(NativeUi.CARD, NativeUi.LINE, 12));
        e.setMinHeight(shell.dp(48));
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        shell.add(parent, e, 6);
        return e;
    }

    static void editDispatch(AppShell shell, JSONObject order) {
        MainActivity activity = shell.activity();
        LinearLayout form = dialogContainer(shell);
        EditText driver = styledInput(shell, form, "Responsable del reparto", "Nombre de quien reparte", 80, true);
        driver.setText(order.optString("driver"));
        EditText window = styledInput(shell, form, "Franja de entrega acordada", "Ej. De 5:00 p. m. a 6:00 p. m.", 100, false);
        window.setText(order.optString("deliveryWindow"));
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
        LinearLayout quickFilters = new LinearLayout(shell.activity());
        addOrderFilter(shell, quickFilters, "Estado del pedido",
                new String[]{"Todos los estados", "En curso", "Por confirmar", "Terminados"},
                new String[]{"all", "active", "registered", "finished"}, shell.orderFilter(), shell::orderFilter);
        addOrderFilter(shell, quickFilters, "Estado del pago",
                new String[]{"Todos los pagos", "Pago pendiente", "Pagado"},
                new String[]{"all", "unpaid", "paid"}, shell.orderPayment(), shell::orderPayment);
        shell.add(shell.content(), quickFilters, 10);

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

    private static void addOrderFilter(AppShell shell, LinearLayout row, String title, String[] labels,
                                       String[] values, String selected, java.util.function.Consumer<String> change) {
        int index = Math.max(0, Arrays.asList(values).indexOf(selected));
        TextView button = shell.button(labels[index], false, () ->
                new AlertDialog.Builder(shell.activity()).setTitle(title)
                        .setSingleChoiceItems(labels, index, (dialog, selectedIndex) -> {
                            dialog.dismiss();
                            change.accept(values[selectedIndex]);
                            shell.render();
                        }).setNegativeButton("Volver", null).show());
        button.setTextSize(14);
        button.setContentDescription(title + ": " + labels[index]);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        if (row.getChildCount() == 0) params.rightMargin = shell.dp(8);
        row.addView(button, params);
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
        LinearLayout form = dialogContainer(shell);
        shell.add(form, shell.text("¿Cómo pagó el cliente?", 14, NativeUi.INK, true), 0);
        Spinner methodPicker = shell.spinner(new String[]{"Efectivo", "Yape", "Plin"},
                "yape".equals(order.optString("payment")) ? 1 : "plin".equals(order.optString("payment")) ? 2 : 0);
        shell.add(form, methodPicker, 6);
        EditText note = styledInput(shell, form, "Referencia o nota", "Nro. operación o nota (opcional)", 80, true);
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
