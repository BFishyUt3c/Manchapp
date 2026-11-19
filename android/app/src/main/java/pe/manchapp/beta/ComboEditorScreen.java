package pe.manchapp.beta;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p.optString("line").equals("commercial") && !p.optString("category").equals("Combos")) {
                ids.add(p.optString("id"));
                names.add(p.optString("name"));
            }
        }
        if (ids.isEmpty()) {
            shell.toast("Primero agrega productos individuales o usa '+ Producto rápido'.");
            return;
        }
        boolean[] checked = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            if (shell.comboIds.contains(ids.get(i))) checked[i] = true;
        }
        new AlertDialog.Builder(shell.activity())
                .setTitle("Elige los productos del combo")
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
        shell.title("Crear un combo", "Combina varios productos con un precio especial.");
        LinearLayout content = shell.content();

        String[] presets = {"Combo merienda", "Combo familiar", "Antojo de la tarde"};
        int selectedPreset = draft.optInt("preset", 0);
        shell.segmented(new String[]{"Merienda", "Familiar", "Antojo"}, selectedPreset, index -> {
            AppShell.put(draft, "preset", index);
            AppShell.put(draft, "name", presets[index]);
            shell.render();
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
        } else {
            shell.add(content, shell.ui().message("Sin productos aún", "Toca 'Elegir productos' para armar este combo.", NativeUi.SOFT), 12);
        }

        bind(draft, "name", shell.field("name", "Nombre del combo", draft.optString("name"), "Ej: Combo familiar", false));
        bind(draft, "description", shell.field("description", "Detalle del contenido", draft.optString("description"), "Ej: 3 cachangas + 2 cafés", false));
        bind(draft, "price", shell.field("price", "Precio del combo (S/)", draft.optString("price"), "Ej: 15.00", true));
        bind(draft, "stock", shell.field("stock", "¿Cuántos combos ofrecerás?", draft.optString("stock"), "Ej: 10", true));

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