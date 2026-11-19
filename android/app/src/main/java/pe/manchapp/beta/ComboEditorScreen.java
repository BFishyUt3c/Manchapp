package pe.manchapp.beta;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
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

    private static JSONArray getExtras(JSONObject draft) {
        JSONArray arr = draft.optJSONArray("extras");
        return arr != null ? arr : new JSONArray();
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
            AppShell.put(combo, "extras", new JSONArray());
            shell.setComboDraft(combo);
        }
        final JSONObject draft = combo;
        MainActivity activity = shell.activity();
        LinearLayout content = shell.content();
        NativeUi ui = shell.ui();

        shell.title("Crear un combo", "Marca los productos que incluye y define un precio especial.");

        // Obtener productos individuales del catálogo
        JSONArray allProducts = shell.data().optJSONArray("products");
        ArrayList<JSONObject> commercial = new ArrayList<>();
        for (int i = 0; allProducts != null && i < allProducts.length(); i++) {
            JSONObject p = allProducts.optJSONObject(i);
            if (p != null && p.optString("line").equals("commercial") && !p.optString("category").equals("Combos")) {
                commercial.add(p);
            }
        }

        // 1. Tarjeta de selección directa de productos + extras
        LinearLayout productsCard = shell.card();
        shell.add(productsCard, shell.text("ELIGE QUÉ LLEVA ESTE COMBO", 11, NativeUi.MUTED, true), 0);

        // Resumen vivo (se actualiza al tocar sin recargar toda la pantalla)
        TextView summaryText = shell.text("", 15, NativeUi.INK, true);
        TextView totalText = shell.text("", 13, NativeUi.WINE, true);

        Runnable updateSummary = () -> {
            StringBuilder sb = new StringBuilder();
            double sum = 0;
            for (String id : shell.comboIds) {
                JSONObject p = shell.find("products", id);
                if (p != null) {
                    if (sb.length() > 0) sb.append(" + ");
                    sb.append(p.optString("name"));
                    sum += p.optDouble("price", 0);
                }
            }
            JSONArray extras = getExtras(draft);
            for (int i = 0; i < extras.length(); i++) {
                JSONObject ex = extras.optJSONObject(i);
                if (ex == null) continue;
                if (sb.length() > 0) sb.append(" + ");
                sb.append(ex.optString("name"));
                sum += ex.optDouble("price", 0);
            }
            if (sb.length() > 0) {
                summaryText.setText("Incluye: " + sb.toString());
                summaryText.setTextColor(NativeUi.INK);
                totalText.setText(sum > 0 ? "Por separado suman " + shell.cash(sum) : "Sin precio referencial");
            } else {
                summaryText.setText("Marca al menos un producto o agrega un extra.");
                summaryText.setTextColor(NativeUi.MUTED);
                totalText.setText("");
            }
        };

        // Buscador rápido solo si hay más de 6 productos
        EditText search = null;
        if (commercial.size() > 6) {
            search = new EditText(activity);
            search.setHint("Buscar producto...");
            search.setSingleLine(true);
            search.setTextSize(15);
            search.setTypeface(shell.regular());
            search.setTextColor(NativeUi.INK);
            search.setHintTextColor(NativeUi.MUTED);
            search.setPadding(shell.dp(12), shell.dp(10), shell.dp(12), shell.dp(10));
            search.setBackground(shell.bg(NativeUi.BG, NativeUi.LINE, 10));
            search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            shell.add(productsCard, search, 8);
        }

        LinearLayout listContainer = shell.column();
        shell.add(productsCard, listContainer, 6);

        final EditText searchRef = search;
        Runnable renderChecks = () -> {
            listContainer.removeAllViews();
            String query = searchRef == null ? "" : searchRef.getText().toString().trim().toLowerCase(Locale.ROOT);
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
                box.setMinHeight(shell.dp(44));
                box.setChecked(shell.comboIds.contains(id));
                box.setOnCheckedChangeListener((btn, isChecked) -> {
                    if (isChecked) {
                        if (!shell.comboIds.contains(id)) shell.comboIds.add(id);
                    } else {
                        shell.comboIds.remove(id);
                    }
                    updateSummary.run();
                });
                shell.add(listContainer, box, 2);
            }
            if (commercial.isEmpty()) {
                shell.add(listContainer, shell.text("Aún no tienes productos individuales en tu catálogo.",
                        13, NativeUi.MUTED, false), 6);
            } else if (shown == 0) {
                shell.add(listContainer, shell.text("No se encontró \"" + query + "\".",
                        13, NativeUi.MUTED, false), 6);
            }
        };
        renderChecks.run();

        if (searchRef != null) {
            searchRef.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(Editable s) { renderChecks.run(); }
            });
        }

        // Lista de extras agregados solo para este combo
        JSONArray extras = getExtras(draft);
        if (extras.length() > 0) {
            shell.add(productsCard, shell.text("EXTRAS SOLO DE ESTE COMBO", 11, NativeUi.MUTED, true), 10);
            for (int i = 0; i < extras.length(); i++) {
                final int idx = i;
                JSONObject ex = extras.optJSONObject(i);
                if (ex == null) continue;
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                String label = "• " + ex.optString("name")
                        + (ex.optDouble("price", 0) > 0 ? " (" + shell.cash(ex.optDouble("price")) + ")" : "");
                row.addView(shell.text(label, 14, NativeUi.INK, false), new LinearLayout.LayoutParams(0, -2, 1));
                TextView remove = shell.text("Quitar", 13, NativeUi.WINE, true);
                remove.setPadding(shell.dp(10), shell.dp(6), shell.dp(4), shell.dp(6));
                remove.setOnClickListener(v -> {
                    JSONArray next = new JSONArray();
                    for (int j = 0; j < extras.length(); j++) {
                        if (j != idx) next.put(extras.optJSONObject(j));
                    }
                    AppShell.put(draft, "extras", next);
                    shell.render();
                });
                row.addView(remove);
                shell.add(productsCard, row, 4);
            }
        }

        // Botón para agregar extra exclusivo del combo
        shell.add(productsCard, shell.button("+ Extra solo para este combo", false,
                () -> addComboExtra(shell, draft)), 10);
        shell.add(productsCard, shell.text(
                "Solo se guarda en este combo, no en tu catálogo (recomendado para bebidas o acompañamientos).",
                12, NativeUi.MUTED, false), 6);

        // Resumen vivo al pie de la tarjeta
        shell.add(productsCard, summaryText, 12);
        shell.add(productsCard, totalText, 2);
        updateSummary.run();

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
        bind(draft, "description", shell.field("description", "Detalle del contenido (opcional)", draft.optString("description"), "Ej: 3 cachangas + 2 cafés", false));
        bind(draft, "price", shell.field("price", "Precio del combo (S/)", draft.optString("price"), "Ej: 15.00", true));
        EditText stockField = shell.field("stock", "¿Cuántos combos ofrecerás?", draft.optString("stock"), "Ej: 10", true);
        stockField.setInputType(InputType.TYPE_CLASS_NUMBER);
        bind(draft, "stock", stockField);

        shell.primary("Guardar combo", () -> {
            try {
                JSONArray currentExtras = getExtras(draft);
                if (shell.comboIds.isEmpty() && currentExtras.length() == 0) {
                    shell.toast("Elige al menos un producto o agrega un extra para el combo.");
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

                StringBuilder autoDesc = new StringBuilder();
                JSONObject baseSchedule = null;
                for (String id : shell.comboIds) {
                    JSONObject p = shell.find("products", id);
                    if (p != null) {
                        if (autoDesc.length() > 0) autoDesc.append(" + ");
                        autoDesc.append(p.optString("name"));
                        if (baseSchedule == null) baseSchedule = SaleSchedule.forProduct(p, shell.data().optJSONObject("settings"));
                    }
                }
                for (int i = 0; i < currentExtras.length(); i++) {
                    JSONObject ex = currentExtras.optJSONObject(i);
                    if (ex == null) continue;
                    if (autoDesc.length() > 0) autoDesc.append(" + ");
                    autoDesc.append(ex.optString("name"));
                }

                String id = UUID.randomUUID().toString();
                String desc = shell.value("description").isEmpty() ? autoDesc.toString() : shell.value("description");
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
                        .put("comboProductIds", new JSONArray(shell.comboIds))
                        .put("comboExtras", currentExtras);

                JSONObject schedule = baseSchedule != null ? baseSchedule : shell.data().optJSONObject("settings");
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
        e.setTextSize(16);
        e.setTypeface(shell.regular());
        e.setTextColor(NativeUi.INK);
        e.setHintTextColor(NativeUi.MUTED);
        e.setHint(hint);
        e.setPadding(shell.dp(13), shell.dp(12), shell.dp(13), shell.dp(12));
        e.setBackground(shell.bg(NativeUi.CARD, NativeUi.LINE, 12));
        e.setMinHeight(shell.dp(48));
        e.setInputType(inputType);
        shell.add(parent, e, 6);
        return e;
    }

    private static void addComboExtra(AppShell shell, JSONObject draft) {
        MainActivity activity = shell.activity();
        ScrollView wrapper = new ScrollView(activity);
        wrapper.setBackgroundColor(NativeUi.BG);
        LinearLayout form = shell.column();
        form.setPadding(shell.dp(20), shell.dp(14), shell.dp(20), shell.dp(16));
        wrapper.addView(form);

        shell.add(form, shell.ui().message(
                "Solo para este combo",
                "Este ítem no se agregará a tu catálogo principal. Recomendado para bebidas, salsas o acompañamientos extra.",
                NativeUi.GOLD), 0);

        EditText name = dialogField(shell, form, "Nombre del extra", "Ej. Vaso de chicha morada",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText price = dialogField(shell, form, "Precio referencial S/ (opcional)", "Ej. 2.00",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Agregar extra al combo")
                .setView(wrapper)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Añadir al combo", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String n = name.getText().toString().trim();
            if (n.length() < 2) {
                name.setError("Escribe el nombre del extra.");
                return;
            }
            double p = 0;
            String rawPrice = price.getText().toString().trim().replace(',', '.');
            if (!rawPrice.isEmpty()) {
                try {
                    p = Double.parseDouble(rawPrice);
                    if (!Double.isFinite(p) || p < 0 || p > 10000) throw new NumberFormatException();
                } catch (Exception bad) {
                    price.setError("Escribe un precio válido o déjalo vacío.");
                    return;
                }
            }
            try {
                JSONArray extras = getExtras(draft);
                extras.put(new JSONObject().put("name", n).put("price", p));
                AppShell.put(draft, "extras", extras);
                dialog.dismiss();
                shell.render();
                shell.toast("Extra añadido solo a este combo.");
            } catch (Exception e) {
                shell.toast("No pudimos añadir el extra.");
            }
        }));
        dialog.show();
    }
}