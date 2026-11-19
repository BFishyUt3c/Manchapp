package pe.manchapp.beta;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.ArrayList;

final class FlyerStudioScreen {

    private static void renderProductChips(AppShell shell, LinearLayout content, JSONObject activeFirst) {
        JSONArray products = shell.data().optJSONArray("products");
        if (products == null || products.length() == 0) return;
        String activeSale = SaleSchedule.key(SaleSchedule.forProduct(activeFirst, shell.data().optJSONObject("settings")));
        HorizontalScrollView strip = new HorizontalScrollView(shell.activity());
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(shell.activity());
        for (int i = 0; i < products.length(); i++) {
            JSONObject prod = products.optJSONObject(i);
            if (!prod.optString("line").equals("commercial")) continue;
            String id = prod.optString("id");
            boolean on = shell.flyerIds.contains(id);
            TextView chip = shell.ui().chip((on ? "✓ " : "+ ") + prod.optString("name"),
                    on ? NativeUi.CARD : NativeUi.WINE, on ? NativeUi.WINE : NativeUi.CARD);
            chip.setPadding(shell.dp(12), shell.dp(8), shell.dp(12), shell.dp(8));
            chip.setTextSize(13);
            chip.setOnClickListener(v -> {
                shell.publicationMessage(shell.value("message"));
                if (on) {
                    if (shell.flyerIds.size() <= 1) { shell.toast("El afiche necesita al menos 1 producto."); return; }
                    shell.flyerIds.remove(id);
                } else {
                    if (shell.flyerIds.size() >= 4) { shell.toast("Elige hasta 4 productos para que se vean claros."); return; }
                    String prodSale = SaleSchedule.key(SaleSchedule.forProduct(prod, shell.data().optJSONObject("settings")));
                    if (!activeSale.equals(prodSale)) { shell.toast("Para un mismo afiche, elige productos de la misma fecha de venta."); return; }
                    shell.flyerIds.add(id);
                }
                shell.currentId(shell.flyerIds.get(0));
                shell.render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = shell.dp(8);
            row.addView(chip, lp);
        }
        strip.addView(row);
        shell.add(content, strip, 12);
    }

    static void openNextSalePromo(AppShell shell) {
        JSONArray products = shell.data().optJSONArray("products");
        ArrayList<String> chosen = new ArrayList<>();
        String firstSale = "";
        for (int i = 0; products != null && i < products.length() && chosen.size() < 4; i++) {
            JSONObject p = products.optJSONObject(i);
            if (!p.optString("line").equals("commercial") || !p.optBoolean("available")) continue;
            String key = SaleSchedule.key(SaleSchedule.forProduct(p, shell.data().optJSONObject("settings")));
            if (firstSale.isEmpty()) firstSale = key;
            if (key.equals(firstSale)) chosen.add(p.optString("id"));
        }
        if (chosen.isEmpty()) {
            shell.toast("Primero agrega o habilita un producto para anunciar tu venta.");
            return;
        }
        shell.flyerIds.clear();
        shell.flyerIds.addAll(chosen);
        shell.flyerStyle(2);
        shell.flyerFormat(2);
        shell.publicationMessage("¡Reserva con anticipación para nuestra próxima venta!");
        shell.go("publish", chosen.get(0));
    }

    static JSONArray flyerProducts(AppShell shell) {
        JSONArray result = new JSONArray(), catalog = shell.data().optJSONArray("products");
        if (shell.flyerIds.isEmpty() && !shell.currentId().isEmpty()) shell.flyerIds.add(shell.currentId());
        for (String id : shell.flyerIds) {
            for (int i = 0; catalog != null && i < catalog.length(); i++) {
                JSONObject product = catalog.optJSONObject(i);
                if (product.optString("id").equals(id) && product.optString("line").equals("commercial")) {
                    result.put(LocalStore.copy(product));
                    break;
                }
            }
        }
        return result;
    }

    static void chooseFlyerProducts(AppShell shell) {
        if (shell.screen().equals("publish")) shell.publicationMessage(shell.value("message"));
        JSONArray products = shell.data().optJSONArray("products");
        ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product.optString("line").equals("commercial")) {
                ids.add(product.optString("id"));
                names.add(product.optString("name") + " · " + shell.cash(product.optDouble("price"))
                        + " · " + SaleSchedule.display(product.optString("saleDate"), true));
            }
        }
        if (ids.isEmpty()) {
            shell.toast("Primero agrega un producto para vender.");
            return;
        }
        ArrayList<String> chosen = new ArrayList<>();
        boolean[] checked = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            if (shell.flyerIds.contains(ids.get(i))) {
                checked[i] = true;
                chosen.add(ids.get(i));
            }
        }
        if (chosen.isEmpty()) {
            checked[0] = true;
            chosen.add(ids.get(0));
        }
        AlertDialog dialog = new AlertDialog.Builder(shell.activity())
                .setTitle("Productos del afiche · máximo 4")
                .setMultiChoiceItems(names.toArray(new String[0]), checked, (d, index, on) -> {
                    if (on && chosen.size() == 4) {
                        ((AlertDialog) d).getListView().setItemChecked(index, false);
                        shell.toast("Elige hasta 4 para que se vean claros.");
                        return;
                    }
                    if (on) chosen.add(ids.get(index));
                    else chosen.remove(ids.get(index));
                })
                .setNegativeButton("Volver", null)
                .setPositiveButton("Usar productos", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (chosen.isEmpty()) {
                shell.toast("Elige al menos un producto.");
                return;
            }
            String sale = "";
            for (String id : chosen) {
                JSONObject product = shell.find("products", id);
                JSONObject schedule = SaleSchedule.forProduct(product, shell.data().optJSONObject("settings"));
                if (sale.isEmpty()) sale = SaleSchedule.key(schedule);
                else if (!sale.equals(SaleSchedule.key(schedule))) {
                    shell.toast("Para un mismo afiche, elige productos de la misma fecha de venta.");
                    return;
                }
            }
            shell.flyerIds.clear();
            shell.flyerIds.addAll(chosen);
            shell.currentId(chosen.get(0));
            shell.screen("publish");
            dialog.dismiss();
            shell.render();
        }));
        dialog.show();
    }

    static void showPublish(AppShell shell) {
        JSONArray selected = flyerProducts(shell);
        JSONObject p = selected.optJSONObject(0);
        if (p == null || !p.optString("line").equals("commercial")) {
            shell.go("products", "");
            return;
        }
        LinearLayout content = shell.content();
        MainActivity activity = shell.activity();
        shell.title("Tu afiche", selected.length() == 1
                ? "Elige un estilo y revisa tu publicación."
                : "Cada producto con su foto y precio.");
        renderProductChips(shell, content, p);
        shell.add(content, shell.button("Elegir productos (" + selected.length() + ")", false,
                () -> chooseFlyerProducts(shell)), 10);
        shell.segmented(selected.length() > 1
                ? new String[]{"Catálogo", "Destacados", "Próxima venta"}
                : new String[]{"Producto", "Combo", "Próxima venta"}, shell.flyerStyle(), i -> {
            shell.publicationMessage(shell.value("message"));
            shell.flyerStyle(i);
            shell.render();
        });
        shell.segmented(new String[]{"Story 9:16", "Cuadrada 1:1", "Story Flash"}, shell.flyerFormat(), i -> {
            shell.publicationMessage(shell.value("message"));
            shell.flyerFormat(i);
            shell.render();
        });
        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(shell.bg(0xffeee7de, 0, 18));
        shell.add(content, preview, 18);
        preview.getLayoutParams().height = shell.dp(390);
        String message = shell.publicationMessage();
        int style = shell.flyerStyle();
        int format = shell.flyerFormat();
        JSONObject settings = SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"));
        shell.worker().execute(() -> {
            try {
                Bitmap bitmap = FlyerRenderer.render(selected, settings, message, style, format, shell.ui());
                activity.runOnUiThread(() -> {
                    if (shell.screen().equals("publish") && preview.getWindowToken() != null) {
                        preview.setImageBitmap(bitmap);
                    } else {
                        bitmap.recycle();
                    }
                });
            } catch (Exception | OutOfMemoryError error) {
                activity.runOnUiThread(() -> shell.toast("No pudimos preparar la vista del afiche."));
            }
        });
        shell.field("message", "Mensaje para acompañar", shell.publicationMessage(),
                "Ejemplo: ¡Reserva para nuestra próxima venta!", false);
        shell.add(content, shell.button("Actualizar vista previa", false, () -> {
            shell.publicationMessage(shell.value("message"));
            shell.render();
        }), 12);
        if (settings.optString("paymentImage").isEmpty()) {
            shell.note("Puedes agregar el QR en Ajustes → Pagos y QR para incluirlo en el afiche.");
        }
        shell.note("En WhatsApp se abrirá directo para elegir a quién enviarlo. Puedes preparar o descargar el afiche sin internet.");
        shell.pair(content, "Descargar PNG", () -> {
            shell.publicationMessage(shell.value("message"));
            downloadFlyer(shell);
        }, "Otra app", () -> {
            if (!validatePublish(shell, settings)) return;
            createFlyer(shell, p, shell.value("message"), false);
        });
        shell.primary("Enviar afiche por WhatsApp", () -> {
            if (!validatePublish(shell, settings)) return;
            createFlyer(shell, p, shell.value("message"), true);
        });
    }

    private static boolean validatePublish(AppShell shell, JSONObject settings) {
        if (!SaleRules.validPhone(settings.optString("phone")) || settings.optString("pickup").trim().length() < 4) {
            shell.toast("Completa el teléfono de contacto y el lugar de recojo antes de compartir.");
            shell.go("profile", "");
            return false;
        }
        if (!SaleSchedule.invalid(settings).isEmpty()) {
            shell.toast("Programa la fecha de venta antes de compartir.");
            return false;
        }
        return true;
    }

    static String storeSlug(JSONObject settings) {
        String raw = settings == null ? "" : settings.optString("name", "manka-kusi");
        String slug = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "manka-kusi" : slug;
    }

    static String catalogDeepLink(JSONObject settings) {
        String customUrl = settings == null ? "" : settings.optString("catalogUrl", "").trim();
        String deepLink = "manchapp://catalog/" + storeSlug(settings);
        return customUrl.isEmpty() ? deepLink : customUrl + " · " + deepLink;
    }

    static void shareCatalogWhatsApp(AppShell shell) {
        JSONObject data = shell.data();
        JSONObject settings = data.optJSONObject("settings");
        if (!SaleRules.validPhone(settings.optString("phone")) || settings.optString("pickup").trim().length() < 4) {
            shell.toast("Completa el teléfono de contacto y el lugar de recojo antes de compartir.");
            shell.go("profile", "");
            return;
        }
        JSONArray products = data.optJSONArray("products");
        StringBuilder body = new StringBuilder();
        body.append("*").append(settings.optString("name", "ManchApp")).append("* · Catálogo disponible\n\n");
        int count = 0;
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (!p.optString("line").equals("commercial") || !p.optBoolean("available") || p.optInt("stock") <= 0) continue;
            count++;
            JSONObject schedule = SaleSchedule.forProduct(p, settings);
            body.append("• *").append(p.optString("name")).append("* — ")
                    .append(shell.cash(p.optDouble("price"))).append(" ")
                    .append(ProductScreen.unit(p))
                    .append(" (").append(p.optInt("stock")).append(" cupos · ")
                    .append(SaleSchedule.display(schedule.optString("saleDate"), true)).append(")\n");
        }
        if (count == 0) {
            shell.toast("Agrega o habilita al menos un producto con cupos antes de compartir el catálogo.");
            return;
        }
        body.append("\nRecojo: ").append(settings.optString("pickup"));
        body.append("\nPedidos al: ").append(settings.optString("phone"));
        body.append("\nVer catálogo: ").append(catalogDeepLink(settings));

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, body.toString());
        openWhatsAppOrChooser(shell.activity(), share, "Compartir catálogo", true);
    }

    static void downloadFlyer(AppShell shell) {
        MainActivity activity = shell.activity();
        JSONArray selected = flyerProducts(shell);
        JSONObject p = selected.optJSONObject(0);
        if (p == null) { shell.toast("Elige al menos un producto."); return; }
        if (Build.VERSION.SDK_INT >= 29) {
            JSONObject settings = SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"));
            String message = shell.publicationMessage();
            int style = shell.flyerStyle();
            int format = shell.flyerFormat();
            shell.toast("Guardando afiche en galería…");
            shell.worker().execute(() -> {
                try {
                    Bitmap bitmap = FlyerRenderer.render(selected, settings, message, style, format, shell.ui());
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, "manchapp-afiche-" + System.currentTimeMillis() + ".png");
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ManchApp");
                    Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IOException();
                    try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                        if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException();
                    } finally {
                        bitmap.recycle();
                    }
                    activity.runOnUiThread(() -> shell.toast("Afiche guardado en Pictures/ManchApp."));
                } catch (Exception | OutOfMemoryError error) {
                    activity.runOnUiThread(() -> shell.toast("No pudimos guardar el afiche en la galería."));
                }
            });
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_TITLE, "manchapp-afiche-" + SaleSchedule.date(System.currentTimeMillis()) + ".png");
            activity.startActivityForResult(intent, 88);
        } catch (Exception e) {
            shell.toast("No pudimos abrir el selector para guardar el PNG.");
        }
    }

    static void saveFlyerToUri(AppShell shell, Uri uri) {
        MainActivity activity = shell.activity();
        JSONArray selected = flyerProducts(shell);
        JSONObject p = selected.optJSONObject(0);
        if (p == null) return;
        JSONObject settings = SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"));
        String message = shell.publicationMessage();
        int style = shell.flyerStyle();
        int format = shell.flyerFormat();
        shell.worker().execute(() -> {
            try {
                Bitmap bitmap = FlyerRenderer.render(selected, settings, message, style, format, shell.ui());
                try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                    if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException();
                } finally {
                    bitmap.recycle();
                }
                activity.runOnUiThread(() -> shell.toast("Afiche PNG descargado."));
            } catch (Exception | OutOfMemoryError error) {
                activity.runOnUiThread(() -> shell.toast("No pudimos guardar el archivo PNG."));
            }
        });
    }

    static void createFlyer(AppShell shell, JSONObject p, String message, boolean preferWhatsApp) {
        MainActivity activity = shell.activity();
        JSONArray selected = flyerProducts(shell);
        JSONObject settings = SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"));
        int style = shell.flyerStyle();
        int format = shell.flyerFormat();
        shell.toast("Preparando afiche…");
        shell.worker().execute(() -> {
            try {
                Bitmap bitmap = FlyerRenderer.render(selected, settings, message, style, format, shell.ui());
                File dir = new File(activity.getCacheDir(), "shared");
                dir.mkdirs();
                File file = new File(dir, "manchapp-publicacion.png");
                try (OutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                bitmap.recycle();
                activity.runOnUiThread(() -> {
                    Uri uri = Uri.parse("content://pe.manchapp.beta.files/" + file.getName());
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("image/png");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    StringBuilder productsText = new StringBuilder();
                    for (int i = 0; i < selected.length(); i++) {
                        JSONObject item = selected.optJSONObject(i);
                        if (i > 0) productsText.append("\n");
                        productsText.append(item.optString("name")).append(" · ").append(shell.cash(item.optDouble("price")));
                    }
                    String caption = (message.isEmpty() ? "" : message + "\n") + productsText
                            + "\nVenta: " + SaleSchedule.day(settings) + "\n" + SaleSchedule.window(settings)
                            + "\nReservas hasta " + SaleSchedule.deadline(settings)
                            + "\n" + settings.optString("pickup")
                            + "\nCatálogo: " + catalogDeepLink(settings);
                    share.putExtra(Intent.EXTRA_TEXT, caption);
                    share.setClipData(ClipData.newRawUri("Afiche", uri));
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    openWhatsAppOrChooser(activity, share, "Compartir publicación", preferWhatsApp);
                });
            } catch (Exception | OutOfMemoryError error) {
                activity.runOnUiThread(() -> shell.toast("No pudimos preparar el afiche. Prueba una foto más pequeña."));
            }
        });
    }

    private static void openWhatsAppOrChooser(MainActivity activity, Intent share, String title, boolean preferWhatsApp) {
        if (preferWhatsApp) {
            for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
                try {
                    Intent direct = new Intent(share);
                    direct.setPackage(pkg);
                    activity.startActivity(direct);
                    return;
                } catch (ActivityNotFoundException ignored) {
                }
            }
        }
        try {
            activity.startActivity(Intent.createChooser(share, title));
        } catch (Exception error) {
            android.widget.Toast.makeText(activity, "No hay una aplicación disponible para compartir en este equipo.", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
