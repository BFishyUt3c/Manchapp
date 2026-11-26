package pe.manchapp.beta;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.util.UUID;

final class SettingsScreen {

    static void showSettings(AppShell shell) {
        JSONObject s = shell.data().optJSONObject("settings");
        String name = s.optString("name").isEmpty() ? "Tu emprendimiento" : s.optString("name");
        shell.title("Configuración", "Datos que aparecen en productos, pedidos y afiches.");
        LinearLayout c = shell.card();
        shell.add(c, shell.text(name, 22, NativeUi.WINE, true), 0);
        shell.add(c, shell.text("Emprendimiento de la olla común", 14, NativeUi.MUTED, false), 6);
        shell.add(shell.content(), c, 18);
        shell.menu("Datos de " + name, "Nombre, punto de recojo, contacto y referencia", "products", () -> shell.go("profile", ""));
        shell.menu("Reparto cercano", "Cobertura por distancia y tarifas", "truck", () -> shell.go("delivery", ""));
        shell.menu("Pagos y QR", "Yape, Plin y confirmación manual", "pay", () -> shell.go("payments", ""));
        shell.menu("Guardar copia de seguridad", "Conserva productos, pedidos y cambios pendientes en un archivo", "orders", () -> exportBackup(shell));
        shell.menu("Ayuda para tus fotos", "Consejos sencillos antes de editar", "help", () -> shell.go("help", ""));
    }

    static void showProfile(AppShell shell) {
        JSONObject s = shell.data().optJSONObject("settings");
        boolean isFirstSetup = s.optString("name").isEmpty() || s.optString("pickup").isEmpty() || s.optString("phone").isEmpty();
        shell.title(
                isFirstSetup ? "Configura tu olla" : "Datos de tu olla",
                "Estos datos aparecen en el catálogo, pedidos y afiches."
        );
        shell.field("name", "Nombre del emprendimiento", s.optString("name"), "Ej. Olla Común Las Flores", false);
        shell.field("pickup", "Lugar de recojo y referencia", s.optString("pickup"), "Indica un punto fácil de reconocer", false);

        if (!s.optString("pickup").isEmpty()) {
            LinearLayout location = shell.ui().message("UBICACIÓN DE REFERENCIA", s.optString("pickup"), NativeUi.GOLD);
            double latitude = s.optDouble("latitude", Double.NaN), longitude = s.optDouble("longitude", Double.NaN);
            if (Double.isFinite(latitude) && Double.isFinite(longitude)) {
                shell.add(location, shell.ui().button("Abrir en la app de mapas", false, "pin", () -> {
                    try {
                        String label = Uri.encode(s.optString("name", "Punto de recojo"));
                        shell.activity().startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude + "(" + label + ")")));
                    } catch (Exception error) {
                        shell.toast("No hay una app de mapas disponible. La referencia escrita se conserva.");
                    }
                }), 10);
            }
            shell.add(shell.content(), location, 12);
        }

        shell.field("phone", "Celular de contacto (WhatsApp)", s.optString("phone"), "Ej. 932 232 123", false)
                .setInputType(InputType.TYPE_CLASS_PHONE);
        shell.field("hours", "Información de atención (opcional)", s.optString("hours"), "Ej. Sábados y domingos de 12 a 5 p. m.", false);

        shell.primary(isFirstSetup ? "Guardar y continuar" : "Guardar datos", () -> {
            if (shell.value("name").length() < 2) {
                shell.editorError("name", "Escribe el nombre del emprendimiento.");
                return;
            }
            if (shell.value("pickup").length() < 4) {
                shell.editorError("pickup", "Indica el lugar de recojo y una referencia.");
                return;
            }
            String cleanPhone = shell.value("phone").replaceAll("\\D+", "");
            if (!cleanPhone.matches("9[0-9]{8}")) {
                shell.editorError("phone", "Escribe 9 dígitos empezando con 9 (ej. 932 232 123).");
                return;
            }
            JSONObject settings = shell.data().optJSONObject("settings");
            AppShell.put(settings, "name", shell.value("name"));
            AppShell.put(settings, "pickup", shell.value("pickup"));
            AppShell.put(settings, "phone", cleanPhone);
            AppShell.put(settings, "hours", shell.value("hours"));
            shell.enqueue("settings", settings, isFirstSetup ? "home" : "settings");
        });
    }

    static void showDelivery(AppShell shell) {
        JSONObject s = shell.data().optJSONObject("settings");
        LinearLayout content = shell.content();
        shell.title("Delivery cercano", "Activa los sectores donde reparten y ajusta su tarifa y distancia.");
        JSONArray zones = s.optJSONArray("zones");
        for (int i = 0; zones != null && i < zones.length(); i++) {
            JSONObject z = zones.optJSONObject(i);
            final int index = i;
            String coverage = "Hasta " + z.optDouble("maxKm", 0) + " km";
            shell.menu(z.optString("name") + " · " + shell.cash(z.optDouble("fee")),
                    (z.optBoolean("enabled") ? "Habilitado" : "Sin cobertura") + " · " + coverage,
                    "pin", () -> editZone(shell, index));
        }
        shell.note("Activa un sector y ajusta su distancia. El cliente solo podrá usar su ubicación dentro de la cobertura habilitada.");
        shell.add(content, shell.ui().heading("Promoción de reparto", 21), 25);
        shell.note("Si desean ofrecer delivery gratis desde un monto, pueden configurarlo aquí.");
        shell.field("deliveryFreeFrom", "Compra mínima para delivery gratis (S/)",
                String.valueOf(s.optDouble("deliveryFreeFrom", 0)), "0 para desactivar", true);
        shell.primary("Guardar promoción", () -> {
            try {
                double minimum = Double.parseDouble(shell.value("deliveryFreeFrom").replace(',', '.'));
                if (!Double.isFinite(minimum) || minimum < 0 || minimum > 10000) throw new Exception();
                JSONObject settings = shell.data().optJSONObject("settings");
                AppShell.put(settings, "deliveryFreeFrom", minimum);
                shell.enqueue("settings", settings, "delivery");
            } catch (Exception error) {
                shell.editorError("deliveryFreeFrom", "Escribe un monto entre 0 y 10000.");
            }
        });
    }

    private static EditText styledDialogField(AppShell shell, LinearLayout parent, String label, String value, String hint, boolean numeric) {
        shell.add(parent, shell.text(label, 14, NativeUi.INK, true), 12);
        EditText e = new EditText(shell.activity());
        e.setTextSize(17);
        e.setTypeface(shell.regular());
        e.setTextColor(NativeUi.INK);
        e.setHintTextColor(NativeUi.MUTED);
        e.setHint(hint);
        e.setPadding(shell.dp(13), shell.dp(12), shell.dp(13), shell.dp(12));
        e.setBackground(shell.bg(NativeUi.CARD, NativeUi.LINE, 12));
        e.setMinHeight(shell.dp(50));
        e.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(value);
        shell.add(parent, e, 6);
        return e;
    }

    static void editZone(AppShell shell, int index) {
        MainActivity activity = shell.activity();
        JSONObject settings = shell.data().optJSONObject("settings");
        JSONArray zones = settings.optJSONArray("zones");
        JSONObject current = index >= 0 && zones != null ? zones.optJSONObject(index) : new JSONObject();
        final JSONObject z = current == null ? new JSONObject() : current;
        final String zoneName = z.optString("name", "Sector de reparto");

        ScrollView wrapper = new ScrollView(activity);
        wrapper.setBackgroundColor(NativeUi.BG);
        LinearLayout form = shell.column();
        form.setPadding(shell.dp(20), shell.dp(14), shell.dp(20), shell.dp(16));
        wrapper.addView(form);

        EditText fee = styledDialogField(shell, form, "Tarifa de reparto (S/)",
                z.has("fee") ? z.optString("fee") : "", "Ej. 3.00", true);
        EditText maxKm = styledDialogField(shell, form, "Cobertura máxima (km)",
                z.has("maxKm") ? z.optString("maxKm") : "2", "Ej. 2", true);
        EditText reference = styledDialogField(shell, form, "Referencia o punto de encuentro (opcional)",
                z.optString("note"), "Ej. Hasta el paradero principal", false);

        Switch enabled = new Switch(activity);
        enabled.setText("Sector habilitado para reparto");
        enabled.setTextSize(15);
        enabled.setTextColor(NativeUi.INK);
        enabled.setTypeface(shell.regular());
        enabled.setChecked(z.optBoolean("enabled", false));
        shell.add(form, enabled, 16);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(zoneName)
                .setView(wrapper)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                double amount = Double.parseDouble(fee.getText().toString().trim().replace(',', '.'));
                double distance = Double.parseDouble(maxKm.getText().toString().trim().replace(',', '.'));
                if (!Double.isFinite(amount) || amount < 0 || amount >= 500) {
                    fee.setError("Escribe una tarifa válida entre 0 y 499.");
                    return;
                }
                if (!Double.isFinite(distance) || distance <= 0 || distance > 50) {
                    maxKm.setError("Escribe una cobertura entre 0.1 y 50 km.");
                    return;
                }
                JSONObject change = LocalStore.copy(z);
                change.put("id", z.optString("id", UUID.randomUUID().toString()))
                        .put("name", zoneName)
                        .put("fee", amount)
                        .put("maxKm", distance)
                        .put("note", reference.getText().toString().trim())
                        .put("enabled", enabled.isChecked());
                JSONObject latest = shell.data().optJSONObject("settings");
                JSONArray copy = new JSONArray(latest.optJSONArray("zones") == null ? "[]" : latest.optJSONArray("zones").toString());
                if (index < 0) copy.put(change);
                else copy.put(index, change);
                latest.put("zones", copy);
                if (shell.enqueue("settings", latest, "delivery")) dialog.dismiss();
            } catch (Exception error) {
                fee.setError("Revisa la tarifa y la cobertura.");
            }
        }));
        dialog.show();
    }

    static void showPayments(AppShell shell) {
        LinearLayout content = shell.content();
        NativeUi ui = shell.ui();
        JSONObject settings = shell.data().optJSONObject("settings");
        String encoded = settings.optString("paymentImage");
        String mode = settings.optString("paymentMode", settings.optBoolean("cashOnly", false) && encoded.isEmpty() ? "cash" : "both");
        if (mode.isEmpty()) mode = "both";
        String[] modes = {"both", "yape", "plin", "cash"};
        int modeIndex = Math.max(0, java.util.Arrays.asList(modes).indexOf(mode));
        boolean paymentReady = !encoded.isEmpty() || settings.optBoolean("cashOnly", false)
                || SaleRules.validPhone(settings.optString("walletPhone"));

        shell.title("Pagos a tu manera", "Yape, Plin o efectivo. Tú confirmas lo recibido.");
        shell.add(content, shell.text("Billeteras y medios habilitados", 15, NativeUi.INK, true), 10);
        shell.segmented(new String[]{"Yape + Plin", "Yape", "Plin", "Efectivo"}, modeIndex, i -> {
            JSONObject s2 = shell.data().optJSONObject("settings");
            AppShell.put(s2, "paymentMode", modes[i]);
            AppShell.put(s2, "cashOnly", "cash".equals(modes[i]));
            shell.enqueue("settings", s2, "payments");
        });

        if (!"cash".equals(mode)) {
            final String activeMode = mode;
            shell.field("walletPhone", "Celular Yape / Plin",
                    settings.optString("walletPhone", settings.optString("phone")),
                    "Ej. 932 232 123", false).setInputType(InputType.TYPE_CLASS_PHONE);
            shell.field("walletHolder", "Titular del QR o cuenta",
                    settings.optString("walletHolder", settings.optString("name")),
                    "Nombre que verá el cliente al transferir", false);
            shell.add(content, shell.button("Guardar número y titular", false, () -> {
                String cleanPhone = shell.value("walletPhone").replaceAll("\\D+", "");
                if (!cleanPhone.isEmpty() && !SaleRules.validPhone(cleanPhone)) {
                    shell.editorError("walletPhone", "Escribe 9 dígitos empezando con 9 (ej. 932 232 123).");
                    return;
                }
                JSONObject s2 = shell.data().optJSONObject("settings");
                AppShell.put(s2, "walletPhone", cleanPhone);
                AppShell.put(s2, "walletHolder", shell.value("walletHolder"));
                AppShell.put(s2, "paymentMode", activeMode);
                AppShell.put(s2, "cashOnly", false);
                shell.enqueue("settings", s2, "payments");
            }), 10);
        }

        if (encoded.isEmpty()) {
            shell.add(content, ui.message("Agrega tu QR interoperable",
                    "Sube la imagen de tu QR (Yape o Plin). También puedes cobrar solo con número o efectivo.", NativeUi.GOLD), 16);
        } else {
            JSONObject photo = new JSONObject();
            AppShell.put(photo, "image", encoded);
            ImageView preview = ProductScreen.image(shell, photo, 360);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preview.setPadding(shell.dp(15), shell.dp(15), shell.dp(15), shell.dp(15));
            preview.setBackground(shell.bg(Color.WHITE, NativeUi.LINE, 18));
            preview.setContentDescription("QR de pago guardado por la olla");
            shell.add(content, preview, 18);
            preview.getLayoutParams().height = shell.dp(265);
            shell.add(content, shell.text("Tu QR está guardado", 17, NativeUi.GREEN, true), 12);
        }
        shell.add(content, ui.button(encoded.isEmpty() ? "Agregar mi QR" : "Cambiar mi QR", true, "qr",
                () -> shell.choosePhoto("qr", false)), 16);
        if (shell.qrDraftFile().exists()) {
            shell.add(content, shell.button("Revisar QR pendiente", false, () -> shell.go("qr", "")), 10);
        }
        if (encoded.isEmpty() && !"cash".equals(mode)) {
            shell.add(content, shell.button("Por ahora, solo usaré efectivo", false, () -> {
                JSONObject s2 = shell.data().optJSONObject("settings");
                AppShell.put(s2, "paymentMode", "cash");
                AppShell.put(s2, "cashOnly", true);
                shell.enqueue("settings", s2, shell.history().isEmpty() ? "home" : "settings");
            }), 10);
        }
        shell.note("El QR y número guardado se muestran al cliente en su pedido. Revisa siempre el efectivo o el abono antes de confirmar el pago.");

        if (paymentReady) {
            shell.primary("Listo, ir al inicio", () -> shell.go("home", ""));
        }
    }

    static void showQrPreview(AppShell shell) {
        if (shell.pendingQr().isEmpty()) {
            try {
                shell.pendingQr(LocalStore.read(new CrossFile(shell.qrDraftFile()).openRead()));
            } catch (Exception error) {
                shell.go("payments", "");
                return;
            }
        }
        LinearLayout content = shell.content();
        shell.title("¿Es el QR de tu negocio?", "Revisa que esté completo y que corresponda a quien recibirá el pago.");
        JSONObject photo = new JSONObject();
        AppShell.put(photo, "image", shell.pendingQr());
        ImageView preview = ProductScreen.image(shell, photo, 480);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setPadding(shell.dp(16), shell.dp(16), shell.dp(16), shell.dp(16));
        preview.setBackground(shell.bg(Color.WHITE, NativeUi.LINE, 18));
        preview.setContentDescription("Vista previa del QR seleccionado, todavía sin guardar");
        shell.add(content, preview, 20);
        preview.getLayoutParams().height = shell.dp(315);
        shell.add(content, shell.button("Elegir otra imagen", false, () -> shell.choosePhoto("qr", false)), 14);
        shell.note("Guardar esta imagen habilita el pago con QR si tu venta estaba configurada solo en efectivo.");
        shell.primary("Guardar mi QR", () -> {
            JSONObject settings = shell.data().optJSONObject("settings");
            AppShell.put(settings, "paymentImage", shell.pendingQr());
            if (settings.optBoolean("cashOnly") || settings.optString("paymentMode").equals("cash")) {
                AppShell.put(settings, "paymentMode", "both");
                AppShell.put(settings, "cashOnly", false);
            }
            if (shell.enqueue("settings", settings, "payments")) {
                shell.pendingQr("");
                shell.qrDraftFile().delete();
                shell.render();
            }
        });
    }

    static void exportBackup(AppShell shell) {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "ManchApp-respaldo-" + SaleSchedule.date(System.currentTimeMillis()) + ".json");
            shell.activity().startActivityForResult(intent, 86);
        } catch (Exception error) {
            shell.toast("No hay una aplicación disponible para guardar el archivo.");
        }
    }
}
