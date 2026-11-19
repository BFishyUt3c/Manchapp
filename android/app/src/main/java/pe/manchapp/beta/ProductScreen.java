package pe.manchapp.beta;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.widget.*;
import org.json.*;

final class ProductScreen {

    static void showProducts(AppShell shell) {
        MainActivity activity = shell.activity();
        NativeUi ui = shell.ui();
        LinearLayout content = shell.content();

        shell.title("Lo que preparas", "Productos, combos y cupos para tu venta programada.");
        shell.add(content, ui.button("Agregar producto", true, "plus", () -> startEditor(shell, null)), 18);
        shell.pair(content,
                "Crear un combo", () -> ComboEditorScreen.selectProduct(shell, true),
                "Compartir catálogo", () -> FlyerStudioScreen.shareCatalogWhatsApp(shell));

        if (hasDraft(shell)) {
            shell.menu("Continuar con mi borrador",
                    shell.draft().optString("name").isEmpty() ? "Producto todavía sin nombre" : shell.draft().optString("name"),
                    "edit", () -> {
                        shell.pickedImage(shell.draft().optString("image"));
                        shell.go("edit", shell.draft().optString("id"));
                    });
        }

        JSONArray products = shell.data().optJSONArray("products");
        int found = 0;
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (!p.optString("line").equals("commercial")) continue;
            found++;
            LinearLayout c = shell.card(), row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(image(shell, p, 88), new LinearLayout.LayoutParams(shell.dp(88), shell.dp(94)));
            LinearLayout info = shell.column();
            info.setPadding(shell.dp(14), 0, 0, 0);
            info.addView(shell.text(p.optString("name"), 18, NativeUi.INK, true));
            shell.add(info, shell.text(shell.cash(p.optDouble("price")) + " " + unit(p), 18, NativeUi.WINE, true), 4);
            boolean available = p.optBoolean("available") && p.optInt("stock") > 0;
            shell.add(info, shell.text(available ? p.optInt("stock") + " cupos" : "No disponible", 13, available ? NativeUi.GREEN : NativeUi.MUTED, true), 4);
            shell.add(info, shell.text("Venta: " + SaleSchedule.display(p.optString("saleDate"), true), 12, NativeUi.MUTED, false), 4);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            TextView arrow = shell.text("›", 26, NativeUi.MUTED, false);
            row.addView(arrow);
            c.addView(row);

            LinearLayout stockRow = new LinearLayout(activity);
            stockRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView minus = ui.button("−", false, null, () -> adjustStock(shell, p, -1));
            minus.setTextSize(16);
            minus.setMinHeight(shell.dp(44));
            minus.setPadding(0, 0, 0, 0);
            minus.setEnabled(p.optInt("stock") > 0);
            minus.setAlpha(minus.isEnabled() ? 1f : .45f);
            minus.setContentDescription("Quitar 1 cupo a " + p.optString("name"));
            TextView countLabel = shell.text(p.optInt("stock") + " cupos rápidos", 14, available ? NativeUi.GREEN : NativeUi.MUTED, true);
            countLabel.setGravity(Gravity.CENTER);
            TextView plus = ui.button("+", false, null, () -> adjustStock(shell, p, 1));
            plus.setTextSize(16);
            plus.setMinHeight(shell.dp(44));
            plus.setPadding(0, 0, 0, 0);
            plus.setEnabled(p.optInt("stock") < 9999);
            plus.setContentDescription("Sumar 1 cupo a " + p.optString("name"));
            stockRow.addView(minus, new LinearLayout.LayoutParams(shell.dp(50), shell.dp(44)));
            stockRow.addView(countLabel, new LinearLayout.LayoutParams(0, -2, 1));
            stockRow.addView(plus, new LinearLayout.LayoutParams(shell.dp(50), shell.dp(44)));
            shell.add(c, stockRow, 10);

            c.setFocusable(true);
            c.setOnClickListener(v -> shell.go("detail", p.optString("id")));
            shell.add(content, c, 12);
        }
        if (found == 0) shell.note("Aquí aparecerán tus productos. Empieza con uno.");
    }

    private static void adjustStock(AppShell shell, JSONObject p, int delta) {
        if (shell.checkPending(p.optString("id"))) return;
        int next = Math.max(0, Math.min(9999, p.optInt("stock") + delta));
        if (next == p.optInt("stock")) return;
        try {
            JSONObject change = LocalStore.copy(p);
            AppShell.put(change, "stock", next);
            AppShell.put(change, "version", p.optInt("version") + 1);
            shell.store().enqueue("product", change);
            shell.render();
        } catch (Exception error) {
            shell.toast("No pudimos actualizar los cupos.");
        }
    }

    static void showProduct(AppShell shell) {
        MainActivity activity = shell.activity();
        NativeUi ui = shell.ui();
        LinearLayout content = shell.content();
        JSONObject p = shell.find("products", shell.currentId());
        if (p == null || !p.optString("line").equals("commercial")) {
            shell.go("products", "");
            return;
        }
        ImageView photo = image(shell, p, 320, true);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setPadding(shell.dp(6), shell.dp(6), shell.dp(6), shell.dp(6));
        shell.add(content, photo, 0);
        photo.getLayoutParams().height = shell.dp(240);
        if (!ImageTools.hasProductPhoto(p)) shell.add(content, shell.text("ILUSTRACIÓN REFERENCIAL", 11, NativeUi.MUTED, true), 7);
        shell.add(content, ui.heading(p.optString("name"), 28), 16);
        shell.add(content, shell.text(shell.cash(p.optDouble("price")) + " " + unit(p), 24, NativeUi.WINE, true), 6);

        LinearLayout details = shell.card();
        shell.add(details, shell.text(p.optInt("stock") + " cupos · " + (p.optBoolean("available") ? "Disponible" : "En pausa"), 16, NativeUi.GREEN, true), 0);
        shell.add(details, shell.text("Venta: " + SaleSchedule.day(SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"))), 15, NativeUi.INK, true), 10);
        if (!p.optString("portion").isEmpty()) shell.add(details, shell.text(p.optString("portion"), 16, NativeUi.INK, true), 8);
        if (!p.optString("description").isEmpty()) shell.add(details, shell.text(p.optString("description"), 15, NativeUi.MUTED, false), 7);
        shell.add(content, details, 18);

        shell.add(content, ui.button("Editar producto", false, "edit", () -> {
            if (!shell.checkPending(shell.currentId())) startEditor(shell, p);
        }), 16);

        if (p.optString("line").equals("commercial")) {
            shell.add(content, shell.button("Crear un combo con este producto", false, () -> shell.go("promo", shell.currentId())), 10);
            shell.primary("Crear publicación", () -> {
                shell.publicationMessage(p.optString("description"));
                shell.go("publish", shell.currentId());
            });
        }
        shell.add(content, shell.button(p.optBoolean("available") ? "Pausar venta" : "Volver a ofrecer", false, () -> {
            if (shell.checkPending(shell.currentId())) return;
            JSONObject change = LocalStore.copy(p);
            AppShell.put(change, "available", !p.optBoolean("available"));
            shell.enqueue("product", change, "products");
        }), 10);
        shell.add(content, shell.button("Eliminar producto", false, () ->
                new AlertDialog.Builder(activity)
                        .setTitle("¿Eliminar " + p.optString("name") + "?")
                        .setMessage("Se quitará del catálogo y de las ofertas. Los pedidos anteriores conservarán su información.")
                        .setNegativeButton("Conservar", null)
                        .setPositiveButton("Eliminar", (d, w) -> {
                            try {
                                shell.store().deleteProduct(p.optString("id"));
                                shell.screen("products");
                                shell.currentId("");
                                shell.render();
                                shell.toast("Producto eliminado.");
                            } catch (Exception error) {
                                shell.toast(error.getMessage());
                            }
                        }).show()), 10);
    }

    static ImageView image(AppShell shell, JSONObject p, int size) {
        return image(shell, p, size, false);
    }

    static ImageView image(AppShell shell, JSONObject p, int size, boolean revealTransparency) {
        MainActivity activity = shell.activity();
        ImageView result = new ImageView(activity);
        result.setScaleType(ImageView.ScaleType.CENTER_CROP);
        result.setBackground(shell.bg(0xffefe2d3, 0, 14));
        result.setClipToOutline(true);
        Bitmap bitmap = shell.images().productThumbnail(activity, p, shell.dp(size));
        if (bitmap != null) {
            result.setImageBitmap(bitmap);
            if (revealTransparency && ImageTools.hasTransparency(bitmap)) result.setBackground(new PhotoUi.Checker(shell.dp(10)));
        } else {
            result.setImageDrawable(new DishArt(p.optString("illustration", "papa")));
        }
        result.setContentDescription(!ImageTools.hasProductPhoto(p) ? "Ilustración referencial de " + p.optString("name") : "Foto de " + p.optString("name"));
        return result;
    }

    static String unit(JSONObject p) {
        return SaleRules.unit(p.optString("saleUnit", SaleRules.defaultUnit(p.optString("category"))));
    }

    static void startEditor(AppShell shell, JSONObject product) {
        MainActivity activity = shell.activity();
        if (hasDraft(shell) && (product == null || !shell.draft().optString("id").equals(product.optString("id")))) {
            new AlertDialog.Builder(activity)
                    .setTitle("Tienes un borrador pendiente")
                    .setMessage((shell.draft().optString("name").isEmpty() ? "Producto sin nombre" : shell.draft().optString("name")) + "\nPuedes seguir completándolo o empezar otro producto.")
                    .setNegativeButton("Volver", null)
                    .setNeutralButton("Empezar otro", (d, w) -> openEditor(shell, product))
                    .setPositiveButton("Continuar borrador", (d, w) -> {
                        shell.pickedImage(shell.draft().optString("image"));
                        shell.go("edit", shell.draft().optString("id"));
                    }).show();
            return;
        }
        openEditor(shell, product);
    }

    static void openEditor(AppShell shell, JSONObject product) {
        JSONObject draft = product == null ? new JSONObject() : LocalStore.copy(product);
        AppShell.put(draft, "line", "commercial");
        shell.setDraft(draft);
        shell.pickedImage(draft.optString("image"));
        shell.editorMore(false);
        shell.go("edit", draft.optString("id"));
    }

    static boolean hasDraft(AppShell shell) {
        JSONObject draft = shell.draft();
        return draft != null && draft.optString("line", "commercial").equals("commercial")
                && (!draft.optString("name").isEmpty() || !draft.optString("image").isEmpty() || !draft.optString("price").isEmpty());
    }
}