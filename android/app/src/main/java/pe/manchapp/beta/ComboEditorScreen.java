package pe.manchapp.beta;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.json.*;
import java.util.*;

final class ComboEditorScreen {

    static void selectProduct(AppShell shell, boolean combo) {
        if (!combo) {
            FlyerStudioScreen.chooseFlyerProducts(shell);
            return;
        }
        JSONArray products = shell.data().optJSONArray("products");
        List<String> names = new ArrayList<>(), ids = new ArrayList<>();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p.optString("line").equals("commercial")) {
                names.add(p.optString("name"));
                ids.add(p.optString("id"));
            }
        }
        if (names.isEmpty()) {
            shell.toast("Primero agrega un producto para vender.");
            return;
        }
        new AlertDialog.Builder(shell.activity())
                .setTitle("Elige un producto")
                .setItems(names.toArray(new String[0]), (d, index) -> {
                    JSONObject p = shell.find("products", ids.get(index));
                    shell.publicationMessage(p.optString("description"));
                    shell.go("promo", ids.get(index));
                })
                .setNegativeButton("Volver", null)
                .show();
    }

    static void chooseComboProducts(AppShell shell) {
        JSONArray products = shell.data().optJSONArray("products");
        ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p.optString("line").equals("commercial") && !p.optString("category").equals("Combos")) {
                ids.add(p.optString("id"));
                names.add(p.optString("name"));
            }
        }
        if (ids.isEmpty()) {
            shell.toast("Primero agrega productos individuales.");
            return;
        }
        boolean[] checked = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            if (shell.comboIds.contains(ids.get(i))) checked[i] = true;
        }
        new AlertDialog.Builder(shell.activity())
                .setTitle("Seleccionar productos para el combo")
                .setMultiChoiceItems(names.toArray(new String[0]), checked, (d, index, on) -> {
                    if (on) {
                        if (!shell.comboIds.contains(ids.get(index))) shell.comboIds.add(ids.get(index));
                    } else {
                        shell.comboIds.remove(ids.get(index));
                    }
                })
                .setPositiveButton("Aceptar", (d, w) -> shell.render())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    static void showPromo(AppShell shell) {
        if (shell.currentId().isEmpty() && shell.comboIds.isEmpty()) {
            shell.go("products", "");
            return;
        }
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
            shell.setComboDraft(combo);
        }
        shell.title("Crear un combo", "Combina varios productos con un precio especial.");
        LinearLayout content = shell.content();

        shell.segmented(new String[]{"Merienda", "Familiar", "Antojo"}, 0, index -> {
            String name = new String[]{"Combo merienda", "Combo familiar", "Antojo de la tarde"}[index];
            EditText nameField = shell.fields().get("name");
            if (nameField != null) nameField.setText(name);
            EditText descField = shell.fields().get("description");
            if (descField != null && descField.getText().toString().isEmpty()) descField.setText(name);
        });

        shell.pair(content, "Elegir productos (" + shell.comboIds.size() + ")", () -> chooseComboProducts(shell),
                "+ Producto rápido", () -> createInlineProduct(shell));

        StringBuilder summary = new StringBuilder();
        JSONObject baseSchedule = null;
        for (String id : shell.comboIds) {
            JSONObject p = shell.find("products", id);
            if (p != null) {
                if (summary.length() > 0) summary.append(" + ");
                summary.append(p.optString("name"));
                if (baseSchedule == null) baseSchedule = SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"));
            }
        }
        if (summary.length() > 0) {
            shell.add(content, shell.ui().message("Incluye:", summary.toString(), NativeUi.GOLD), 12);
        }

        bind(shell, combo, "name", shell.field("name", "Nombre del combo", combo.optString("name"), "Ej: Combo familiar", false));
        bind(shell, combo, "description", shell.field("description", "Detalle del contenido", combo.optString("description"), "Ej: 3 cachangas + 2 cafés", false));
        bind(shell, combo, "price", shell.field("price", "Precio del combo (S/)", combo.optString("price"), "Ej: 15.00", true));
        bind(shell, combo, "stock", shell.field("stock", "¿Cuántos combos ofrecerás?", combo.optString("stock"), "Ej: 10", true));

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
                JSONObject product = new JSONObject()
                        .put("id", id)
                        .put("name", shell.value("name"))
                        .put("description", shell.value("description"))
                        .put("price", price)
                        .put("stock", stock)
                        .put("category", "Combos")
                        .put("saleUnit", "combo")
                        .put("portion", shell.value("description"))
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
    }

    private static void bind(AppShell shell, JSONObject combo, String key, EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {}
            public void afterTextChanged(Editable value) { AppShell.put(combo, key, value.toString()); }
        });
    }

    private static void createInlineProduct(AppShell shell) {
        MainActivity activity = shell.activity();
        LinearLayout form = shell.column();
        form.setPadding(shell.dp(20), shell.dp(8), shell.dp(20), 0);
        EditText name = new EditText(activity), price = new EditText(activity), stock = new EditText(activity);
        name.setHint("Nombre del producto (ej. Refresco)");
        price.setHint("Precio unitario S/ (ej. 3.00)");
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        stock.setHint("Cupos disponibles (ej. 20)");
        stock.setInputType(InputType.TYPE_CLASS_NUMBER);
        for (EditText e : new EditText[]{name, price, stock}) {
            e.setTextSize(16);
            e.setTypeface(shell.regular());
            e.setMinHeight(shell.dp(50));
            form.addView(e);
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Crear producto rápido")
                .setView(form)
                .setNegativeButton("Volver", null)
                .setPositiveButton("Crear y añadir", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            try {
                String n = name.getText().toString().trim();
                double p = Double.parseDouble(price.getText().toString().replace(',', '.'));
                int s = Integer.parseInt(stock.getText().toString().trim());
                if (n.length() < 2) { name.setError("Escribe el nombre."); return; }
                if (!Double.isFinite(p) || p <= 0 || p > 10000) { price.setError("Precio válido > 0."); return; }
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
