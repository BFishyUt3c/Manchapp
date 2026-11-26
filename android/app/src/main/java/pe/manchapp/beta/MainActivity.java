package pe.manchapp.beta;

import android.app.Activity;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.*;

public class MainActivity extends Activity {
    private static final Uri BRIDGE=Uri.parse("content://pe.manchapp.beta.bridge/catalog");
    private AppShell shell;
    private final Handler bridgeHandler=new Handler(Looper.getMainLooper());
    private ContentObserver bridgeObserver;
    private int bridgeOrders=-1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        NativeUi ui = new NativeUi(this);
        LocalStore store;
        try {
            store = LocalStore.open(this);
        } catch (Exception error) {
            RecoveryScreen.show(this, error);
            return;
        }
        shell = new AppShell(this, ui, store);
        if (state != null) {
            if (state.getStringArrayList("flyerIds") != null) shell.flyerIds.addAll(state.getStringArrayList("flyerIds"));
            if (state.getStringArrayList("history") != null) shell.history().addAll(state.getStringArrayList("history"));
            shell.flyerStyle(state.getInt("flyerStyle", 0));
            shell.flyerFormat(state.containsKey("flyerFormat") ? state.getInt("flyerFormat", 0) : (state.getBoolean("flyerSquare", false) ? 1 : 0));
            shell.publicationMessage(state.getString("publicationMessage", ""));
            shell.screen(state.getString("screen", "home"));
            shell.currentId(state.getString("id", ""));
            shell.photoFor(state.getString("photoFor", "product"));
            shell.editorMore(state.getBoolean("editorMore", false));
            try {
                File f = new File(getFilesDir(), "editor-draft.json");
                if (f.exists()) {
                    JSONObject draft = new JSONObject(LocalStore.read(new CrossFile(f).openRead()));
                    shell.setDraft(draft);
                    shell.pickedImage(draft.optString("image"));
                }
            } catch (Exception ignored) {}
            try {
                if (state.containsKey("saleDraft")) shell.setSaleDraft(new JSONObject(state.getString("saleDraft")));
            } catch (Exception ignored) {}
        }
        if (state == null && "orders".equals(getIntent().getStringExtra("screen"))) shell.screen("orders");
        if (shell.screen().equals("photo")) shell.screen("edit");
        if (shell.draft() == null) {
            try {
                File f = new File(getFilesDir(), "editor-draft.json");
                if (f.exists()) shell.setDraft(new JSONObject(LocalStore.read(new CrossFile(f).openRead())));
            } catch (Exception ignored) {}
        }
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setStatusBarColor(NativeUi.DEEP);
        getWindow().setNavigationBarColor(NativeUi.WINE);
        shell.render();
    }

    @Override public void onResume() {
        super.onResume();
        if (shell == null) return;
        shell.resumed(true);
        bridgeOrders=orderCount();
        registerBridgeObserver();
        if (shell.store() != null && shell.root() != null) shell.render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (shell != null && "orders".equals(intent.getStringExtra("screen"))) shell.go("orders", "");
    }

    @Override public void onPause() {
        super.onPause();
        unregisterBridgeObserver();
        if (shell == null) return;
        shell.resumed(false);
        shell.saveDraft();
    }

    @Override public void onDestroy() {
        unregisterBridgeObserver();
        super.onDestroy();
        if (shell != null) shell.shutdown();
    }

    private int orderCount() {
        if (shell == null || shell.store() == null) return 0;
        JSONObject snapshot = shell.store().snapshot();
        org.json.JSONArray extras = snapshot.optJSONArray("restockRequests");
        return snapshot.optJSONArray("orders").length() + (extras == null ? 0 : extras.length());
    }

    private void registerBridgeObserver(){
        if(bridgeObserver!=null)return;
        bridgeObserver=new ContentObserver(bridgeHandler){
            @Override public void onChange(boolean selfChange){
                if(shell==null||shell.root()==null)return;
                int count=orderCount();
                if(count==bridgeOrders)return;
                bridgeOrders=count;
                String screen=shell.screen();
                if(screen.equals("home")||screen.equals("orders")||screen.equals("summary"))shell.render();
            }
        };
        getContentResolver().registerContentObserver(BRIDGE,true,bridgeObserver);
    }

    private void unregisterBridgeObserver(){
        if(bridgeObserver==null)return;
        getContentResolver().unregisterContentObserver(bridgeObserver);
        bridgeObserver=null;
    }

    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (shell == null) return;
        shell.saveDraft();
        out.putStringArrayList("flyerIds", shell.flyerIds);
        out.putStringArrayList("history", shell.history());
        out.putInt("flyerStyle", shell.flyerStyle());
        out.putInt("flyerFormat", shell.flyerFormat());
        out.putString("publicationMessage", shell.screen().equals("publish") ? shell.value("message") : shell.publicationMessage());
        out.putString("screen", shell.screen());
        out.putString("id", shell.currentId());
        out.putString("photoFor", shell.photoFor());
        out.putBoolean("editorMore", shell.editorMore());
        if (shell.saleDraft() != null) out.putString("saleDraft", shell.saleDraft().toString());
    }

    @Override public void onBackPressed() {
        if (shell == null) { super.onBackPressed(); return; }
        if (shell.screen().equals("edit")) {
            ProductEditorScreen.captureEditor(shell);
            shell.saveDraft();
            shell.toast("Borrador conservado. Puedes continuarlo desde Productos.");
        }
        if (shell.history().isEmpty()) {
            super.onBackPressed();
            return;
        }
        String[] previous = shell.history().remove(shell.history().size() - 1).split("\\t", -1);
        shell.screen(previous[0]);
        shell.currentId(previous.length > 1 ? previous[1] : "");
        shell.render();
    }

    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (shell == null) return;
        if (request == 87) {
            if (result == RESULT_OK) ProductEditorScreen.useStudioPhoto(shell);
            return;
        }
        if (request == 85) {
            if (result == RESULT_OK) {
                shell.setStore(LocalStore.open(this));
                String id = intent == null ? null : intent.getStringExtra("orderId");
                JSONObject saved = id == null ? null : shell.find("orders", id);
                String saleDate = saved == null ? "" : saved.optString("saleDate");
                shell.orderDate(saleDate.isEmpty() ? "*" : saleDate);
                shell.orderDelivery("all");
                shell.orderFilter("active");
                shell.orderPayment("all");
                shell.go("orders", "");
                shell.toast("Pedido registrado.");
            }
            return;
        }
        if (request == 88) {
            if (result == RESULT_OK && intent != null && intent.getData() != null) {
                FlyerStudioScreen.saveFlyerToUri(shell, intent.getData());
            }
            return;
        }
        if (request == 86) {
            if (result == RESULT_OK && intent != null && intent.getData() != null) {
                final Uri uri = intent.getData();
                final String backup = shell.store().snapshot().toString();
                shell.worker().execute(() -> {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IOException();
                        out.write(backup.getBytes("UTF-8"));
                        runOnUiThread(() -> shell.toast("Copia de seguridad guardada."));
                    } catch (Exception error) {
                        runOnUiThread(() -> shell.toast("No pudimos guardar la copia. Tus datos siguen en la app."));
                    }
                });
            }
            return;
        }
        if (request == 84) {
            if (result == RESULT_OK) {
                try {
                    String encoded = LocalStore.read(new FileInputStream(PhotoEditorActivity.output(this)));
                    shell.pickedImage(encoded);
                    if (shell.draft() == null) shell.setDraft(new JSONObject());
                    JSONObject draft = shell.draft();
                    AppShell.put(draft, "image", encoded);
                    draft.remove("_compositionId");
                    draft.remove("_compositionHash");
                    if (intent != null && intent.getStringExtra("compositionId") != null && !intent.getStringExtra("compositionId").isEmpty()) {
                        AppShell.put(draft, "_compositionId", intent.getStringExtra("compositionId"));
                        AppShell.put(draft, "_compositionHash", PhotoOriginals.digest(encoded));
                    }
                    AppShell.put(draft, "imageReference", false);
                    shell.screen("edit");
                    shell.saveDraft();
                    shell.render();
                    shell.toast("Foto aplicada al borrador. No necesitas guardar el producto para seguir editando.");
                } catch (Exception error) {
                    shell.toast("No pudimos recuperar la foto editada. Tu producto se conserva.");
                }
            }
            return;
        }
        if (result != RESULT_OK || !(request == 41 || request == 42)) return;
        Uri uri = request == 41 ? Uri.parse("content://pe.manchapp.beta.files/camera.jpg") : intent == null ? null : intent.getData();
        if (uri == null) return;
        final String target = shell.photoFor();
        shell.toast("Preparando imagen…");
        shell.worker().execute(() -> {
            try {
                Bitmap bitmap = ImageTools.read(this, uri);
                String encoded = target.equals("qr") ? ImageTools.encodeQr(bitmap) : ImageTools.encodeEdited(bitmap);
                bitmap.recycle();
                if (target.equals("qr")) shell.rememberQr(encoded);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (target.equals("qr")) {
                        shell.pendingQr(encoded);
                        shell.go("qr", "");
                    } else {
                        shell.pickedImage(encoded);
                        if (shell.draft() == null) shell.setDraft(new JSONObject());
                        JSONObject draft = shell.draft();
                        AppShell.put(draft, "_sourcePhoto", encoded);
                        AppShell.put(draft, "image", encoded);
                        AppShell.put(draft, "imageReference", false);
                        shell.screen("edit");
                        shell.saveDraft();
                        shell.render();
                        ProductEditorScreen.improvePhoto(shell);
                    }
                });
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> shell.toast(target.equals("qr")
                        ? "No pudimos preparar el QR. Elige una captura clara y de menor tamaño."
                        : "No pudimos preparar esa imagen. Prueba una foto de menor tamaño."));
            }
        });
    }
}
