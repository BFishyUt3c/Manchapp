package pe.manchapp.beta;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Locale;

final class FlyerStudioScreen {

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
        shell.history().add(shell.screen() + "\t" + shell.currentId());
        shell.flyerIds.clear();
        shell.flyerIds.addAll(chosen);
        shell.flyerStyle(2);   // Próxima venta
        shell.flyerFormat(2);  // Story Flash
        shell.publicationMessage("¡Reserva con anticipación para nuestra próxima venta!");
        shell.screen("publish");
        shell.currentId(chosen.get(0));
        shell.render();
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
        final boolean fromOutside = !shell.screen().equals("publish");
        if (!fromOutside) shell.publicationMessage(shell.value("message"));

        JSONArray products = shell.data().optJSONArray("products");
        ArrayList<JSONObject> commercial = new ArrayList<>();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product != null && product.optString("line").equals("commercial")) {
                commercial.add(product);
            }
        }
        if (commercial.isEmpty()) {
            shell.toast("Primero agrega un producto para vender.");
            return;
        }

        ArrayList<String> chosen = new ArrayList<>();
        if (!fromOutside) {
            for (JSONObject p : commercial) {
                if (shell.flyerIds.contains(p.optString("id"))) {
                    chosen.add(p.optString("id"));
                }
            }
        }
        if (chosen.isEmpty()) {
            chosen.add(commercial.get(0).optString("id"));
        }

        MainActivity activity = shell.activity();
        LinearLayout root = shell.column();
        root.setBackgroundColor(NativeUi.BG);
        root.setPadding(shell.dp(18), shell.dp(14), shell.dp(18), shell.dp(10));

        TextView counter = shell.text("Elegidos: " + chosen.size() + " de 4 (misma fecha de venta)",
                13, NativeUi.WINE, true);
        root.addView(counter);

        EditText search = new EditText(activity);
        search.setHint("Buscar producto por nombre...");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTypeface(shell.regular());
        search.setTextColor(NativeUi.INK);
        search.setHintTextColor(NativeUi.MUTED);
        search.setPadding(shell.dp(13), shell.dp(11), shell.dp(13), shell.dp(11));
        search.setBackground(shell.bg(NativeUi.CARD, NativeUi.LINE, 12));
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        shell.add(root, search, 10);

        ScrollView listScroll = new ScrollView(activity);
        LinearLayout listContainer = shell.column();
        listScroll.addView(listContainer);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, shell.dp(300));
        scrollLp.topMargin = shell.dp(10);
        root.addView(listScroll, scrollLp);

        Runnable renderList = () -> {
            listContainer.removeAllViews();
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int shown = 0;
            for (JSONObject p : commercial) {
                String name = p.optString("name");
                if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
                shown++;
                String id = p.optString("id");
                String label = name + " · " + shell.cash(p.optDouble("price"))
                        + "\n" + SaleSchedule.display(p.optString("saleDate"), true);

                CheckBox box = new CheckBox(activity);
                box.setText(label);
                box.setTextSize(15);
                box.setTextColor(NativeUi.INK);
                box.setTypeface(shell.regular());
                box.setPadding(shell.dp(6), shell.dp(10), shell.dp(6), shell.dp(10));
                box.setChecked(chosen.contains(id));
                box.setOnCheckedChangeListener((btn, isChecked) -> {
                    if (isChecked) {
                        if (chosen.size() >= 4) {
                            btn.setChecked(false);
                            shell.toast("Elige hasta 4 productos para que se vean claros.");
                            return;
                        }
                        if (!chosen.contains(id)) chosen.add(id);
                    } else {
                        chosen.remove(id);
                    }
                    counter.setText("Elegidos: " + chosen.size() + " de 4 (misma fecha de venta)");
                });
                shell.add(listContainer, box, 4);
            }
            if (shown == 0) {
                shell.add(listContainer, shell.text("No hay productos que coincidan con \"" + query + "\".",
                        14, NativeUi.MUTED, false), 12);
            }
        };

        renderList.run();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { renderList.run(); }
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Productos del afiche")
                .setView(root)
                .setNegativeButton("Cancelar", null)
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
            if (fromOutside) {
                shell.history().add(shell.screen() + "\t" + shell.currentId());
                shell.flyerStyle(0);   // Catálogo / Producto
                shell.flyerFormat(1);  // Cuadrada 1:1
                shell.publicationMessage("");
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
        boolean isStoryAnnouncement = shell.flyerFormat() == 2 || shell.flyerStyle() == 2;
        shell.title(
                isStoryAnnouncement ? "Anunciar próxima venta" : "Tu afiche",
                isStoryAnnouncement
                        ? "Listo para subir a tus estados de WhatsApp o redes."
                        : (selected.length() == 1 ? "Elige un estilo y revisa tu publicación." : "Cada producto con su foto y precio.")
        );

        ArrayList<String> selectedNames = new ArrayList<>();
        for (int i = 0; i < selected.length(); i++) {
            selectedNames.add(selected.optJSONObject(i).optString("name"));
        }
        shell.add(content, shell.ui().message(
                "Productos incluidos (" + selected.length() + "/4)",
                TextUtils.join(" · ", selectedNames),
                NativeUi.GOLD
        ), 12);
        shell.add(content, shell.button("Cambiar productos (" + selected.length() + "/4)", false,
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
        String slug = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
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

    static void openWhatsAppOrChooser(MainActivity activity, Intent share, String title, boolean preferWhatsApp) {
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
