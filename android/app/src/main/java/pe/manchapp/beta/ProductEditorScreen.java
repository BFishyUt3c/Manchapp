package pe.manchapp.beta;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

final class ProductEditorScreen {

    static void useStudioPhoto(AppShell shell) {
        MainActivity activity = shell.activity();
        new AlertDialog.Builder(activity)
                .setTitle("¿Dónde usarás la foto?")
                .setItems(new String[]{"En un producto nuevo", "En un producto que ya tengo"}, (dialog, index) -> {
                    if (index == 0) {
                        attachStudioPhoto(shell, null, false);
                        return;
                    }
                    JSONArray products = shell.data().optJSONArray("products");
                    List<String> names = new ArrayList<>(), ids = new ArrayList<>();
                    for (int i = 0; products != null && i < products.length(); i++) {
                        JSONObject product = products.optJSONObject(i);
                        if (product.optString("line").equals("commercial")) {
                            names.add(product.optString("name"));
                            ids.add(product.optString("id"));
                        }
                    }
                    if (names.isEmpty()) {
                        attachStudioPhoto(shell, null, false);
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle("Elige el producto para esta foto")
                            .setItems(names.toArray(new String[0]), (d, choice) -> {
                                JSONObject selected = shell.find("products", ids.get(choice));
                                if (selected != null && !shell.checkPending(selected.optString("id"))) {
                                    attachStudioPhoto(shell, selected, false);
                                }
                            })
                            .setNegativeButton("Volver", null)
                            .show();
                })
                .setNegativeButton("Seguir después", null)
                .show();
    }

    static void attachStudioPhoto(AppShell shell, JSONObject product, boolean replace) {
        MainActivity activity = shell.activity();
        if (!replace && ProductScreen.hasDraft(shell)
                && (product == null || !shell.draft().optString("id").equals(product.optString("id")))) {
            new AlertDialog.Builder(activity)
                    .setTitle("Ya tienes un producto en borrador")
                    .setMessage("Puedes usar la foto en ese borrador o reemplazarlo. La foto también seguirá disponible en Editar una foto.")
                    .setNegativeButton("Volver", null)
                    .setNeutralButton("Reemplazar borrador", (d, w) -> attachStudioPhoto(shell, product, true))
                    .setPositiveButton("Usar en mi borrador", (d, w) -> attachStudioPhoto(shell, shell.draft(), true))
                    .show();
            return;
        }
        try {
            JSONObject photo = PhotoStudioActivity.read(activity);
            String encoded = photo.getString("image");
            if (encoded.isEmpty()) throw new IOException();
            if (!shell.screen().equals("edit")) {
                ProductScreen.openEditor(shell, product);
            } else {
                JSONObject d = product == null ? new JSONObject() : LocalStore.copy(product);
                AppShell.put(d, "line", "commercial");
                shell.setDraft(d);
                shell.currentId(d.optString("id"));
            }
            JSONObject draft = shell.draft();
            shell.pickedImage(encoded);
            AppShell.put(draft, "image", encoded);
            AppShell.put(draft, "_sourcePhoto", photo.optString("source", encoded));
            if (!photo.optString("compositionId").isEmpty()) {
                AppShell.put(draft, "_compositionId", photo.optString("compositionId"));
                AppShell.put(draft, "_compositionHash", PhotoOriginals.digest(encoded));
            }
            AppShell.put(draft, "imageReference", false);
            shell.screen("edit");
            shell.saveDraft();
            shell.render();
            shell.toast("Foto añadida al borrador. Revisa y guarda el producto cuando esté listo.");
        } catch (Exception error) {
            shell.toast("No pudimos usar esa foto. Sigue disponible en Editar una foto.");
        }
    }

    static void showEditor(AppShell shell) {
        MainActivity activity = shell.activity();
        NativeUi ui = shell.ui();
        LinearLayout content = shell.content();
        JSONObject draft = shell.draft();
        if (draft != null && !draft.optString("line", "commercial").equals("commercial")) {
            shell.go("products", "");
            return;
        }
        if (draft == null) {
            draft = new JSONObject();
            shell.setDraft(draft);
        }
        shell.fields().clear();
        shell.setCategorySpinner(null);
        shell.setAvailability(null);

        shell.title(shell.currentId().isEmpty() ? "Agregar producto" : "Editar producto",
                "Foto, precio y cupos en un solo paso.");

        if (!shell.pickedImage().isEmpty() || ImageTools.hasProductPhoto(draft)) {
            JSONObject photoSource = draft;
            if (!shell.pickedImage().isEmpty()) {
                photoSource = new JSONObject();
                AppShell.put(photoSource, "image", shell.pickedImage());
            }
            ImageView photoPreview = ProductScreen.image(shell, photoSource, 320, true);
            photoPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            photoPreview.setPadding(shell.dp(6), shell.dp(6), shell.dp(6), shell.dp(6));
            photoPreview.setContentDescription("Vista previa completa de la foto del producto");
            shell.add(content, photoPreview, 14);
            photoPreview.getLayoutParams().height = shell.dp(200);
            shell.add(content, ui.button("Recortar y mejorar foto", false, "edit", () -> improvePhoto(shell)), 10);
        } else {
            shell.add(content, ui.message("Puedes empezar por la foto",
                    "La foto es opcional. Puedes elegirla o usar una ilustración.", NativeUi.GOLD), 14);
        }
        shell.pair(content, "Tomar foto", () -> shell.choosePhoto("product", true),
                "Elegir foto", () -> shell.choosePhoto("product", false));
        shell.add(content, shell.button("Elegir una ilustración", false, () -> imageBank(shell)), 8);
        if (PhotoOriginals.has(activity, draft.optString("id"))) {
            shell.add(content, shell.button("Recuperar foto original", false, () -> {
                try {
                    String original = PhotoOriginals.read(activity, shell.draft().optString("id"));
                    shell.pickedImage(original);
                    AppShell.put(shell.draft(), "image", original);
                    AppShell.put(shell.draft(), "imageReference", false);
                    shell.render();
                    shell.toast("Foto original recuperada.");
                } catch (Exception error) {
                    shell.toast("No pudimos recuperar esa foto.");
                }
            }), 8);
        }

        shell.field("name", "¿Qué vas a ofrecer?", draft.optString("name"), "Ejemplo: Papa rellena", false);
        shell.field("price", "Precio de venta (S/)", draft.optString("price"), "Ejemplo: 6.50", true);
        shell.add(content, shell.text("¿Qué recibe por ese precio?", 15, NativeUi.INK, true), 15);
        String[] unitCodes = {"unit", "portion", "combo", "cup"};
        Spinner unitSpinner = shell.spinner(
                new String[]{"Una unidad", "Una porción", "Un combo", "Una taza"},
                Math.max(0, Arrays.asList(unitCodes).indexOf(
                        draft.optString("saleUnit", SaleRules.defaultUnit(draft.optString("category"))))));
        unitSpinner.setContentDescription("Presentación del producto");
        shell.setUnitSpinner(unitSpinner);
        shell.add(content, unitSpinner, 6);
        shell.field("stock", "¿Cuántas unidades ofrecerás?", draft.optString("stock"),
                "Ejemplo: 20", true).setInputType(InputType.TYPE_CLASS_NUMBER);
        shell.field("description", "Descripción para el cliente", draft.optString("description"),
                "Ejemplo: Papa doradita con relleno y salsa criolla", false);

        shell.add(content, shell.button(shell.editorMore() ? "Ocultar opciones adicionales" : "Más opciones (categoría, porción, notas)", false, () -> {
            captureEditor(shell);
            shell.editorMore(!shell.editorMore());
            shell.render();
        }), 14);
        if (shell.editorMore()) {
            shell.field("portion", "Porción o contenido (opcional)", draft.optString("portion"),
                    "Ejemplo: una papa con salsa criolla", false);
            shell.field("extraText", "Notas adicionales (opcional)", draft.optString("extraText"),
                    "Ejemplo: Solo para recojo en la olla", false);
            String[] categories = {"Salados", "Antojitos", "Postres", "Bebidas", "Combos"};
            int index = Arrays.asList(categories).indexOf(draft.optString("category"));
            TextView categoryLabel = shell.text("Tipo de producto", 15, NativeUi.INK, true);
            shell.add(content, categoryLabel, 16);
            Spinner categorySpinner = shell.spinner(categories, Math.max(0, index));
            categorySpinner.setId(View.generateViewId());
            categoryLabel.setLabelFor(categorySpinner.getId());
            shell.setCategorySpinner(categorySpinner);
            shell.add(content, categorySpinner, 6);
            Switch availability = new Switch(activity);
            availability.setText("Disponible para ofrecer");
            availability.setTextColor(NativeUi.INK);
            availability.setTextSize(16);
            availability.setTypeface(shell.regular());
            availability.setChecked(draft.optBoolean("available", true));
            availability.setMinHeight(shell.dp(52));
            shell.setAvailability(availability);
            shell.add(content, availability, 10);
            shell.add(content, ui.message("Venta programada",
                    SaleSchedule.day(SaleSchedule.forProduct(draft, shell.data().optJSONObject("settings")))
                            + "\nCambia la fecha desde Inicio → Programar una venta.", NativeUi.GOLD), 12);
        }
        shell.primary("Guardar producto", () -> {
            if (shell.syncing()) {
                shell.toast("Espera un momento mientras actualizamos.");
                return;
            }
            JSONObject value = editorDraft(shell);
            if (value == null) return;
            String detail = value.optString("name") + "\n" + shell.cash(value.optDouble("price")) + " "
                    + ProductScreen.unit(value) + "\n" + value.optInt("stock") + " unidades";
            new AlertDialog.Builder(activity)
                    .setTitle("¿Guardar este producto?")
                    .setMessage(detail)
                    .setNegativeButton("Seguir editando", null)
                    .setPositiveButton("Guardar producto", (d, w) -> {
                        String sourcePhoto = value.optString("_sourcePhoto");
                        String compositionId = compositionFor(shell, value);
                        value.remove("_sourcePhoto");
                        value.remove("_compositionId");
                        value.remove("_compositionHash");
                        if (shell.enqueue("product", value, "products")) {
                            if (!compositionId.isEmpty()) {
                                try {
                                    PhotoOriginals.linkComposition(activity, value.optString("id"), value.optString("image"), compositionId);
                                } catch (Exception error) {
                                    shell.toast("El producto se guardó, pero no pudimos vincular su composición editable.");
                                }
                            }
                            if (!sourcePhoto.isEmpty()) {
                                try {
                                    PhotoOriginals.replace(activity, value.optString("id"), sourcePhoto);
                                } catch (Exception error) {
                                    shell.toast("El producto se guardó, pero no pudimos conservar su foto original.");
                                }
                            }
                            shell.setDraft(null);
                            new File(activity.getFilesDir(), "editor-draft.json").delete();
                        }
                    }).show();
        });
        shell.note("Se guarda en este celular aunque no tengas internet.");
    }

    static void captureEditor(AppShell shell) {
        JSONObject draft = shell.draft();
        if (draft == null) {
            draft = new JSONObject();
            shell.setDraft(draft);
        }
        for (String id : new String[]{"name", "price", "stock", "hours", "description", "portion", "extraText"}) {
            if (shell.fields().containsKey(id)) AppShell.put(draft, id, shell.value(id));
        }
        AppShell.put(draft, "image", shell.pickedImage());
        if (shell.unitSpinner() != null) {
            AppShell.put(draft, "saleUnit", new String[]{"unit", "portion", "combo", "cup"}[shell.unitSpinner().getSelectedItemPosition()]);
        }
        if (shell.categorySpinner() != null) {
            AppShell.put(draft, "category", shell.categorySpinner().getSelectedItem().toString());
        }
        if (shell.availability() != null) {
            AppShell.put(draft, "available", shell.availability().isChecked());
        }
    }

    static JSONObject editorDraft(AppShell shell) {
        captureEditor(shell);
        JSONObject value = LocalStore.copy(shell.draft());
        if (value.optString("name").length() < 2) {
            shell.editorError("name", "Escribe el nombre del producto. Ejemplo: Papa rellena.");
            return null;
        }
        double amount;
        try {
            amount = Double.parseDouble(value.optString("price").replace(',', '.'));
            if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0 || amount > 10000) {
                throw new NumberFormatException();
            }
        } catch (Exception e) {
            shell.editorError("price", "Escribe un precio mayor que cero. Ejemplo: 6.50.");
            return null;
        }
        int stock;
        try {
            String quantity = value.optString("stock");
            if (!quantity.matches("[0-9]+")) throw new NumberFormatException();
            stock = Integer.parseInt(quantity);
            if (stock > 10000) throw new NumberFormatException();
        } catch (Exception e) {
            shell.editorError("stock", "Escribe una cantidad sin decimales. Ejemplo: 20.");
            return null;
        }
        AppShell.put(value, "price", amount);
        AppShell.put(value, "stock", stock);
        AppShell.put(value, "line", "commercial");
        AppShell.put(value, "category", value.optString("category", "Salados"));
        AppShell.put(value, "available", value.optBoolean("available", true));
        if (value.optString("id").isEmpty()) AppShell.put(value, "id", UUID.randomUUID().toString());
        if (!value.has("version")) AppShell.put(value, "version", 0);
        JSONObject defaults = shell.data().optJSONObject("settings");
        for (String key : SaleSchedule.KEYS) {
            if (!value.has(key) && defaults.has(key)) AppShell.put(value, key, defaults.opt(key));
        }
        return value;
    }

    static void imageBank(AppShell shell) {
        captureEditor(shell);
        String[] names = {"Papa rellena", "Causa", "Cachanga", "Mazamorra", "Café"};
        String[] ids = {"papa", "causa", "cachanga", "mazamorra", "cafe"};
        new AlertDialog.Builder(shell.activity())
                .setTitle("Ilustraciones para tus productos")
                .setItems(names, (d, index) -> {
                    shell.pickedImage("");
                    AppShell.put(shell.draft(), "image", "");
                    AppShell.put(shell.draft(), "illustration", ids[index]);
                    AppShell.put(shell.draft(), "imageReference", true);
                    shell.render();
                })
                .setNegativeButton("Volver", null)
                .show();
    }

    static String compositionFor(AppShell shell, JSONObject value) {
        try {
            if (!value.optString("_compositionId").isEmpty()
                    && value.optString("_compositionHash").equals(PhotoOriginals.digest(value.optString("image")))) {
                return value.optString("_compositionId");
            }
            return PhotoOriginals.composition(shell.activity(), value.optString("id"), value.optString("image"));
        } catch (Exception error) {
            return "";
        }
    }

    static void improvePhoto(AppShell shell) {
        MainActivity activity = shell.activity();
        captureEditor(shell);
        shell.saveDraft();
        JSONObject draft = shell.draft();
        String compositionId = compositionFor(shell, draft);
        if (!compositionId.isEmpty()) {
            activity.startActivityForResult(new Intent(activity, PhotoComposeActivity.class).putExtra("compositionId", compositionId), 84);
            return;
        }
        final JSONObject source = LocalStore.copy(draft);
        if (!shell.pickedImage().isEmpty()) AppShell.put(source, "image", shell.pickedImage());
        shell.toast("Abriendo editor de fotos…");
        shell.worker().execute(() -> {
            Bitmap original = null;
            try {
                original = ImageTools.productPhoto(activity, source, 960);
                if (original == null) throw new IOException();
                float scale = Math.min(1f, 960f / Math.max(original.getWidth(), original.getHeight()));
                if (scale < 1) {
                    Bitmap small = Bitmap.createScaledBitmap(original,
                            Math.max(1, Math.round(original.getWidth() * scale)),
                            Math.max(1, Math.round(original.getHeight() * scale)), true);
                    if (small != original) original.recycle();
                    original = small;
                }
                PhotoOriginals.remember(activity, source.optString("id"), ImageTools.encodeEdited(original));
                try (OutputStream out = new FileOutputStream(PhotoEditorActivity.input(activity))) {
                    if (!original.compress(ImageTools.hasTransparency(original) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 95, out)) {
                        throw new IOException();
                    }
                }
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        activity.startActivityForResult(new Intent(activity, PhotoEditorActivity.class), 84);
                    }
                });
            } catch (Exception | OutOfMemoryError error) {
                activity.runOnUiThread(() -> shell.toast("No pudimos abrir esa foto. Elige una imagen de menor tamaño."));
            } finally {
                if (original != null && !original.isRecycled()) original.recycle();
            }
        });
    }
}
