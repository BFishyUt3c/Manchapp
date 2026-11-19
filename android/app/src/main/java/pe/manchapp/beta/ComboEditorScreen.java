package pe.manchapp.beta;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.*;
import java.util.*;

final class ComboEditorScreen {

    static void selectProduct(AppShell shell, boolean combo) {
        if (!combo) {
            FlyerStudioScreen.chooseFlyerProducts(shell);
            return;
        }
        shell.go("promo", "");
    }

    static void chooseComboProducts(AppShell shell) {
        JSONArray products = shell.data().optJSONArray("products");
        ArrayList<JSONObject> commercial = new ArrayList<>();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p != null && p.optString("line").equals("commercial") && !p.optString("category").equals("Combos")) {
                commercial.add(p);
            }
        }
        if (commercial.isEmpty()) {
            shell.toast("Primero agrega productos individuales o usa '+ Producto rápido'.");
            return;
        }

        ArrayList<String> chosen = new ArrayList<>(shell.comboIds);
        MainActivity activity = shell.activity();
        LinearLayout root = shell.column();
        root.setBackgroundColor(NativeUi.BG);
        root.setPadding(shell.dp(18), shell.dp(14), shell.dp(18), shell.dp(10));

        TextView counter = shell.text("Elegidos: " + chosen.size(), 13, NativeUi.WINE, true);
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
                CheckBox box = new CheckBox(activity);
                box.setText(name + " · " + shell.cash(p.optDouble("price")));
                box.setTextSize(15);
                box.setTextColor(NativeUi.INK);
                box.setTypeface(shell.regular());
                box.setPadding(shell.dp(6), shell.dp(10), shell.dp(6), shell.dp(10));
                box.setChecked(chosen.contains(id));
                box.setOnCheckedChangeListener((btn, isChecked) -> {
                    if (isChecked) {
                        if (!chosen.contains(id)) chosen.add(id);
                    } else {
                        chosen.remove(id);
                    }
                    counter.setText("Elegidos: " + chosen.size());
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

        new AlertDialog.Builder(activity)
                .setTitle("Elige los productos del combo")
                .setView(root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aceptar", (d, w) -> {
                    shell.comboIds.clear();
                    shell.comboIds.addAll(chosen);
                    shell.render();
                })
                .show();
    }

    static void showPromo(AppShell shell) {
        if (shell.comboIds.isEmpty() && !shell.currentId().isEmpty()) {
            shell.comboIds.add(shell.currentId());
        }
        JSONObject combo = shell.comboDraft();
        if (combo == null) {
            combo = new JSONObject();
            AppShell.put(combo, "name", "Combo merienda");
            AppShell.put(combo, "description", "");
            AppShell.put(combo, "price", "");
            AppShell.put(combo, "stock", "");
            AppShell.put(combo, "preset", 0);
            shell.setComboDraft(combo);
        }
        final JSONObject draft = combo;
        LinearLayout content = shell.content();
        NativeUi ui = shell.ui();

        shell.title("Crear un combo", "Combina varios productos con un precio especial.");

        // 1. Bloque de productos incluidos en tarjeta limpia
        LinearLayout productsCard = shell.card();
        shell.add(productsCard, shell.text("PRODUCTOS DEL COMBO", 11, NativeUi.MUTED, true), 0);

        StringBuilder summary = new StringBuilder();
        double individualTotal = 0;
        JSONObject baseSchedule = null;
        for (String id : shell.comboIds) {
            JSONObject p = shell.find("products", id);
            if (p != null) {
                if (summary.length() > 0) summary.append(" + ");
                summary.append(p.optString("name"));
                individualTotal += p.optDouble("price", 0);
                if (baseSchedule == null) baseSchedule = SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"));
            }
        }

        if (summary.length() > 0) {
            shell.add(productsCard, shell.text(summary.toString(), 16, NativeUi.INK, true), 6);
            shell.add(productsCard, shell.text("Por separado suman " + shell.cash(individualTotal),
                    13, NativeUi.WINE, true), 4);
        } else {
            shell.add(productsCard, shell.text("Aún no eliges productos. Toca 'Elegir productos' para armar este combo.",
                    13, NativeUi.MUTED, false), 6);
        }

        shell.pair(productsCard,
                "Elegir productos (" + shell.comboIds.size() + ")", () -> chooseComboProducts(shell),
                "+ Producto rápido", () -> createInlineProduct(shell));
        shell.add(content, productsCard, 14);

        // 2. Datos del combo
        shell.add(content, ui.heading("Datos del combo", 19), 20);

        String[] presets = {"Combo merienda", "Combo familiar", "Antojo de la tarde"};
        int selectedPreset = draft.optInt("preset", 0);
        shell.segmented(new String[]{"Merienda", "Familiar", "Antojo"}, selectedPreset, index -> {
            AppShell.put(draft, "preset", index);
            AppShell.put(draft, "name", presets[index]);
            shell.render();
        });

        bind(draft, "name", shell.field("name", "Nombre del combo", draft.optString("name"), "Ej: Combo familiar", false));
        bind(draft, "description", shell.field("description", "Detalle del contenido", draft.optString("description"), "Ej: 3 cachangas + 2 cafés", false));
        bind(draft, "price", shell.field("price", "Precio del combo (S/)", draft.optString("price"), "Ej: 15.00", true));
        EditText stockField = shell.field("stock", "¿Cuántos combos ofrecerás?", draft.optString("stock"), "Ej: 10", true);
        stockField.setInputType(InputType.TYPE_CLASS_NUMBER);
        bind(draft, "stock", stockField);

        final JSONObject finalSchedule = baseSchedule;
        shell.primary("Guardar combo", () -> {
            try {
                if (shell.comboIds.isEmpty()) {
                    shell.toast("Elige al menos un producto para el combo.");
                    return;
                }
                if (shell.value("name").length() < 3) {
                    shell.editorError("name", "Escribe un nombre de al menos 3 letras.");
                    return;
                }
                double price;
                int stock;
                try { price = Double.parseDouble(shell.value("price").replace(',', '.')); }
                catch (Exception badPrice) { shell.editorError("price", "Escribe un precio mayor que 0."); return; }
                try { stock = Integer.parseInt(shell.value("stock")); }
                catch (Exception badStock) { shell.editorError("stock", "Escribe una cantidad entera."); return; }
                if (!Double.isFinite(price) || price <= 0 || price > 10000) {
                    shell.editorError("price", "Escribe un precio entre 0.01 y 10000.");
                    return;
                }
                if (stock < 0 || stock > 10000) {
                    shell.editorError("stock", "Escribe una cantidad entre 0 y 10000.");
                    return;
                }
                String id = UUID.randomUUID().toString();
                String desc = shell.value("description").isEmpty() ? summary.toString() : shell.value("description");
                JSONObject product = new JSONObject()
                        .put("id", id)
                        .put("name", shell.value("name"))
                        .put("description", desc)
                        .put("price", price)
                        .put("stock", stock)
                        .put("category", "Combos")
                        .put("saleUnit", "combo")
                        .put("portion", desc)
                        .put("line", "commercial")
                        .put("available", true)
                        .put("image", "")
                        .put("imageReference", true)
                        .put("version", 0)
                        .put("comboProductIds", new JSONArray(shell.comboIds));

                JSONObject schedule = finalSchedule != null ? finalSchedule : shell.data().optJSONObject("settings");
                if (schedule != null) {
                    for (String key : SaleSchedule.KEYS) {
                        if (schedule.has(key)) product.put(key, schedule.get(key));
                    }
                }
                if (shell.enqueue("product", product, "products")) {
                    shell.comboIds.clear();
                    shell.setComboDraft(null);
                }
            } catch (Exception e) {
                shell.toast("Revisa los datos y asegúrate de elegir productos.");
            }
        });
        shell.note("Se guarda en este celular aunque no tengas internet.");
    }

    private static void bind(JSONObject combo, String key, EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {}
            public void afterTextChanged(Editable value) { AppShell.put(combo, key, value.toString()); }
        });
    }

    private static EditText dialogField(AppShell shell, LinearLayout parent, String label, String hint, int inputType) {
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
        e.setInputType(inputType);
        shell.add(parent, e, 6);
        return e;
    }

    private static void createInlineProduct(AppShell shell) {
        MainActivity activity = shell.activity();
        ScrollView wrapper = new ScrollView(activity);
        wrapper.setBackgroundColor(NativeUi.BG);
        LinearLayout form = shell.column();
        form.setPadding(shell.dp(20), shell.dp(14), shell.dp(20), shell.dp(16));
        wrapper.addView(form);

        EditText name = dialogField(shell, form, "Nombre del producto", "Ej. Refresco de maracuyá",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText price = dialogField(shell, form, "Precio unitario (S/)", "Ej. 3.00",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText stock = dialogField(shell, form, "Cupos disponibles", "Ej. 20",
                InputType.TYPE_CLASS_NUMBER);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Crear producto rápido")
                .setView(wrapper)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Crear y añadir", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            try {
                String n = name.getText().toString().trim();
                double p = Double.parseDouble(price.getText().toString().trim().replace(',', '.'));
                int s = Integer.parseInt(stock.getText().toString().trim());
                if (n.length() < 2) { name.setError("Escribe el nombre."); return; }
                if (!Double.isFinite(p) || p <= 0 || p > 10000) { price.setError("Precio válido mayor a 0."); return; }
                if (s < 0 || s > 10000) { stock.setError("Cupos enteros."); return; }
                String id = UUID.randomUUID().toString();
                JSONObject product = new JSONObject()
                        .put("id", id).put("name", n).put("description", n).put("price", p).put("stock", s)
                        .put("category", "Salados").put("saleUnit", "unit").put("line", "commercial")
                        .put("available", true).put("image", "").put("imageReference", true).put("version", 0);
                JSONObject defaults = shell.data().optJSONObject("settings");
                for (String key : SaleSchedule.KEYS) {
                    if (defaults != null && defaults.has(key)) product.put(key, defaults.opt(key));
                }
                shell.store().enqueue("product", product);
                shell.comboIds.add(id);
                dialog.dismiss();
                shell.render();
                shell.toast("Producto creado y añadido al combo.");
            } catch (Exception e) {
                shell.toast("Revisa nombre, precio y cupos.");
            }
        }));
        dialog.show();
    }
}