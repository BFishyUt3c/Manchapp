package pe.manchapp.beta;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.widget.*;
import org.json.*;

final class ProductScreen {

    private static int catalogFilter = 0; // 0 = Todos, 1 = Productos, 2 = Combos

    private static boolean isCombo(JSONObject p) {
        return "Combos".equalsIgnoreCase(p.optString("category"))
                || "combo".equalsIgnoreCase(p.optString("saleUnit"));
    }

    static void showProducts(AppShell shell) {
        MainActivity activity = shell.activity();
        NativeUi ui = shell.ui();
        LinearLayout content = shell.content();

        shell.title("Lo que preparas", "Productos, combos y cupos para tu venta programada.");

        LinearLayout actionsRow = new LinearLayout(activity);
        TextView addProduct = ui.button("+ Producto", true, null, () -> startEditor(shell, null));
        TextView addCombo = ui.button("+ Combo", true, null, () -> ComboEditorScreen.selectProduct(shell, true));
        addProduct.setTextSize(15);
        addCombo.setTextSize(15);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1);
        leftLp.rightMargin = shell.dp(8);
        actionsRow.addView(addProduct, leftLp);
        actionsRow.addView(addCombo, new LinearLayout.LayoutParams(0, -2, 1));
        shell.add(content, actionsRow, 18);

        if (hasDraft(shell)) {
            shell.menu("Continuar borrador de producto",
                    shell.draft().optString("name").isEmpty() ? "Producto nuevo sin nombre" : shell.draft().optString("name"),
                    "edit", () -> {
                        shell.pickedImage(shell.draft().optString("image"));
                        shell.go("edit", "");
                    });
        }
        if (hasComboDraft(shell)) {
            String comboName = shell.comboDraft() != null ? shell.comboDraft().optString("name") : "";
            shell.menu("Continuar borrador de combo",
                    comboName.isEmpty() ? "Combo nuevo en progreso" : comboName,
                    "tag", () -> shell.go("promo", ""));
        }

        shell.segmented(new String[]{"Todos", "Productos", "Combos"}, catalogFilter, index -> {
            catalogFilter = index;
            shell.render();
        });

        JSONArray products = shell.data().optJSONArray("products");
        int found = 0;
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (!p.optString("line").equals("commercial")) continue;
            boolean combo = isCombo(p);
            if (catalogFilter == 1 && combo) continue;
            if (catalogFilter == 2 && !combo) continue;
            found++;

            LinearLayout c = shell.card();
            if (combo) {
                c.setBackground(shell.bg(NativeUi.CARD, NativeUi.GOLD, 16));
            }
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(image(shell, p, 88), new LinearLayout.LayoutParams(shell.dp(88), shell.dp(94)));

            LinearLayout info = shell.column();
            info.setPadding(shell.dp(14), 0, 0, 0);
            TextView badge = combo
                    ? ui.chip("COMBO", NativeUi.WINE, NativeUi.GOLD)
                    : ui.chip("PRODUCTO", NativeUi.INK, NativeUi.SOFT);
            badge.setTextSize(11);
            badge.setPadding(shell.dp(8), shell.dp(2), shell.dp(8), shell.dp(2));
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2);
            badgeLp.bottomMargin = shell.dp(4);
            info.addView(badge, badgeLp);

            info.addView(shell.text(p.optString("name"), 18, NativeUi.INK, true));
            shell.add(info, shell.text(shell.cash(p.optDouble("price")) + " " + unit(p), 18, NativeUi.WINE, true), 4);
            boolean available = p.optBoolean("available") && p.optInt("stock") > 0;
            shell.add(info, shell.text(available ? p.optInt("stock") + " cupos" : "No disponible", 13, available ? NativeUi.GREEN : NativeUi.MUTED, true), 4);
            shell.add(info, shell.text("Venta: " + SaleSchedule.display(p.optString("saleDate"), true), 12, NativeUi.MUTED, false), 4);

            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            TextView arrow = shell.text("›", 26, NativeUi.MUTED, false);
            row.addView(arrow);
            c.addView(row);

            c.setFocusable(true);
            c.setOnClickListener(v -> shell.go("detail", p.optString("id")));
            shell.add(content, c, 12);
        }
        if (found == 0) {
            String emptyMsg = catalogFilter == 2
                    ? "Todavía no tienes combos. Crea uno con el botón '+ Combo'."
                    : catalogFilter == 1
                    ? "Todavía no tienes productos individuales."
                    : "Aquí aparecerán tus productos y combos. Empieza con uno.";
            shell.note(emptyMsg);
        }
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
        boolean combo = isCombo(p);
        boolean available = p.optBoolean("available") && p.optInt("stock") > 0;
        JSONObject schedule = SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"));

        ImageView photo = image(shell, p, 320, true);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setPadding(shell.dp(6), shell.dp(6), shell.dp(6), shell.dp(6));
        shell.add(content, photo, 0);
        photo.getLayoutParams().height = shell.dp(230);
        if (!ImageTools.hasProductPhoto(p)) {
            shell.add(content, shell.text("ILUSTRACIÓN REFERENCIAL", 11, NativeUi.MUTED, true), 6);
        }

        LinearLayout badges = new LinearLayout(activity);
        TextView typeBadge = combo
                ? ui.chip("COMBO", NativeUi.WINE, NativeUi.GOLD)
                : ui.chip("PRODUCTO", NativeUi.INK, NativeUi.SOFT);
        typeBadge.setTextSize(11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = shell.dp(8);
        badges.addView(typeBadge, lp);

        TextView stateBadge = ui.chip(
                available ? "Disponible" : (p.optBoolean("available") ? "Sin cupos" : "En pausa"),
                available ? NativeUi.GREEN : NativeUi.MUTED,
                available ? 0xffe6f4ea : NativeUi.SOFT
        );
        stateBadge.setTextSize(11);
        badges.addView(stateBadge);
        shell.add(content, badges, 14);

        shell.add(content, ui.heading(p.optString("name"), 26), 8);
        shell.add(content, shell.text(shell.cash(p.optDouble("price")) + " " + unit(p), 22, NativeUi.WINE, true), 4);

        LinearLayout details = shell.card();
        shell.add(details, shell.text("PRÓXIMA VENTA", 11, NativeUi.MUTED, true), 0);
        shell.add(details, shell.text(SaleSchedule.day(schedule), 16, NativeUi.INK, true), 3);
        shell.add(details, shell.text(SaleSchedule.shortWindow(schedule), 14, NativeUi.MUTED, false), 2);

        if (!p.optString("portion").isEmpty() || !p.optString("description").isEmpty()) {
            shell.add(details, shell.text("DETALLE", 11, NativeUi.MUTED, true), 14);
            if (!p.optString("portion").isEmpty()) {
                shell.add(details, shell.text(p.optString("portion"), 15, NativeUi.INK, true), 3);
            }
            if (!p.optString("description").isEmpty() && !p.optString("description").equals(p.optString("portion"))) {
                shell.add(details, shell.text(p.optString("description"), 14, NativeUi.MUTED, false), 4);
            }
        }

        shell.add(details, shell.text("CUPOS DISPONIBLES", 11, NativeUi.MUTED, true), 14);
        LinearLayout stockRow = new LinearLayout(activity);
        stockRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = ui.button("−", false, null, () -> adjustStock(shell, p, -1));
        minus.setTextSize(18);
        minus.setMinHeight(shell.dp(44));
        minus.setPadding(0, 0, 0, 0);
        minus.setEnabled(p.optInt("stock") > 0);
        minus.setAlpha(minus.isEnabled() ? 1f : .45f);
        minus.setContentDescription("Quitar 1 cupo a " + p.optString("name"));
        TextView countLabel = shell.text(p.optInt("stock") + " cupos", 16, available ? NativeUi.GREEN : NativeUi.MUTED, true);
        countLabel.setGravity(Gravity.CENTER);
        TextView plus = ui.button("+", false, null, () -> adjustStock(shell, p, 1));
        plus.setTextSize(18);
        plus.setMinHeight(shell.dp(44));
        plus.setPadding(0, 0, 0, 0);
        plus.setEnabled(p.optInt("stock") < 9999);
        plus.setContentDescription("Sumar 1 cupo a " + p.optString("name"));
        stockRow.addView(minus, new LinearLayout.LayoutParams(shell.dp(52), shell.dp(44)));
        stockRow.addView(countLabel, new LinearLayout.LayoutParams(0, -2, 1));
        stockRow.addView(plus, new LinearLayout.LayoutParams(shell.dp(52), shell.dp(44)));
        shell.add(details, stockRow, 8);

        shell.add(content, details, 16);

        shell.pair(content,
                combo ? "Editar combo" : "Editar producto",
                () -> {
                    if (!shell.checkPending(shell.currentId())) startEditor(shell, p);
                },
                p.optBoolean("available") ? "Pausar venta" : "Volver a ofrecer",
                () -> {
                    if (shell.checkPending(shell.currentId())) return;
                    JSONObject change = LocalStore.copy(p);
                    AppShell.put(change, "available", !p.optBoolean("available"));
                    shell.enqueue("product", change, "products");
                });

        shell.add(content, shell.button(combo ? "Eliminar combo" : "Eliminar producto", false, () ->
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
                                shell.toast("Eliminado del catálogo.");
                            } catch (Exception error) {
                                shell.toast(error.getMessage());
                            }
                        }).show()), 10);

        shell.primary("Crear publicación", () -> {
            shell.publicationMessage(p.optString("description"));
            shell.go("publish", shell.currentId());
        });
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
        if (product == null && hasDraft(shell)) {
            new AlertDialog.Builder(activity)
                    .setTitle("Tienes un borrador pendiente")
                    .setMessage((shell.draft().optString("name").isEmpty() ? "Producto sin nombre" : shell.draft().optString("name")) + "\nPuedes seguir completándolo o empezar otro producto.")
                    .setNegativeButton("Volver", null)
                    .setNeutralButton("Empezar otro", (d, w) -> openEditor(shell, null))
                    .setPositiveButton("Continuar borrador", (d, w) -> {
                        shell.pickedImage(shell.draft().optString("image"));
                        shell.go("edit", "");
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
        return draft != null
                && draft.optString("id").isEmpty()
                && draft.optString("line", "commercial").equals("commercial")
                && (!draft.optString("name").isEmpty() || !draft.optString("image").isEmpty() || !draft.optString("price").isEmpty());
    }

    static boolean hasComboDraft(AppShell shell) {
        JSONObject combo = shell.comboDraft();
        return !shell.comboIds.isEmpty() || (combo != null && (
                !combo.optString("description").isEmpty()
                        || !combo.optString("price").isEmpty()
                        || !combo.optString("stock").isEmpty()));
    }
}