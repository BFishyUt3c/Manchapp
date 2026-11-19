package pe.manchapp.beta;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Estado compartido, navegación y helpers de UI de la app Emprendedora. */
final class AppShell {
    private final MainActivity activity;
    private final NativeUi ui;
    private final ImageTools images = new ImageTools();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("es", "PE"));
    private LocalStore store;
    private JSONObject renderState;
    private String screen = "home";
    private String lastRenderedScreen = "";
    private String currentId = "";
    private String orderDelivery = "all";
    private String orderDate = "";
    private String summaryDate = "";
    private String orderFilter = "active";
    private String pickedImage = "";
    private String photoFor = "product";
    private String pendingQr = "";
    private String publicationMessage = "";
    private final ArrayList<String> history = new ArrayList<>();
    private boolean syncing = false;
    private boolean resumed = false;
    private LinearLayout root, content, footer;
    private ScrollView scroll;
    private JSONObject draft = null;
    private JSONObject saleDraft = null;
    private JSONObject comboDraft = null;
    private int flyerStyle = 0;
    final ArrayList<String> flyerIds = new ArrayList<>();
    private int flyerFormat = 0;
    final ArrayList<String> comboIds = new ArrayList<>();
    private final Map<String, EditText> fields = new HashMap<>();
    private Spinner categorySpinner, unitSpinner;
    private Switch availability;
    private boolean editorMore = false;

    AppShell(MainActivity activity, NativeUi ui, LocalStore store) {
        this.activity = activity;
        this.ui = ui;
        this.store = store;
    }

    MainActivity activity() { return activity; }
    NativeUi ui() { return ui; }
    ImageTools images() { return images; }
    ExecutorService worker() { return worker; }
    LocalStore store() { return store; }
    void setStore(LocalStore s) { store = s; }
    Typeface regular() { return ui.regular; }

    String screen() { return screen; }
    void screen(String v) { screen = v; }
    String currentId() { return currentId; }
    void currentId(String v) { currentId = v; }
    String orderDelivery() { return orderDelivery; }
    void orderDelivery(String v) { orderDelivery = v; }
    String orderDate() { return orderDate; }
    void orderDate(String v) { orderDate = v; }
    String summaryDate() { return summaryDate; }
    void summaryDate(String v) { summaryDate = v; }
    String orderFilter() { return orderFilter; }
    void orderFilter(String v) { orderFilter = v; }
    String pickedImage() { return pickedImage; }
    void pickedImage(String v) { pickedImage = v; }
    String photoFor() { return photoFor; }
    void photoFor(String v) { photoFor = v; }
    String pendingQr() { return pendingQr; }
    void pendingQr(String v) { pendingQr = v; }
    String publicationMessage() { return publicationMessage; }
    void publicationMessage(String v) { publicationMessage = v; }
    ArrayList<String> history() { return history; }
    boolean syncing() { return syncing; }
    boolean resumed() { return resumed; }
    void resumed(boolean v) { resumed = v; }
    LinearLayout root() { return root; }
    LinearLayout content() { return content; }
    JSONObject draft() { return draft; }
    void setDraft(JSONObject d) { draft = d; }
    JSONObject saleDraft() { return saleDraft; }
    void setSaleDraft(JSONObject d) { saleDraft = d; }
    JSONObject comboDraft() { return comboDraft; }
    void setComboDraft(JSONObject d) { comboDraft = d; }
    int flyerStyle() { return flyerStyle; }
    void flyerStyle(int v) { flyerStyle = v; }
    int flyerFormat() { return flyerFormat; }
    void flyerFormat(int v) { flyerFormat = v; }
    Map<String, EditText> fields() { return fields; }
    Spinner categorySpinner() { return categorySpinner; }
    void setCategorySpinner(Spinner s) { categorySpinner = s; }
    Spinner unitSpinner() { return unitSpinner; }
    void setUnitSpinner(Spinner s) { unitSpinner = s; }
    Switch availability() { return availability; }
    void setAvailability(Switch s) { availability = s; }
    boolean editorMore() { return editorMore; }
    void editorMore(boolean v) { editorMore = v; }

    void shutdown() {
        images.clear();
        worker.shutdownNow();
    }

    int dp(float n) { return (int) (n * activity.getResources().getDisplayMetrics().density + .5f); }

    GradientDrawable bg(int color, int stroke, int radius) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color);
        b.setCornerRadius(dp(radius));
        if (stroke != 0) b.setStroke(dp(1), stroke);
        return b;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(activity);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    TextView text(String value, int size, int color, boolean heavy) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(heavy ? ui.bold : ui.regular);
        t.setLineSpacing(dp(3), 1);
        return t;
    }

    void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        parent.addView(view, p);
    }

    TextView button(String label, boolean primary, Runnable action) {
        return ui.button(label, primary, null, action);
    }

    LinearLayout card() { return ui.card(); }

    void primary(String label, Runnable action) {
        footer.removeAllViews();
        footer.setVisibility(View.VISIBLE);
        footer.addView(button(label, true, action), new LinearLayout.LayoutParams(-1, -2));
    }

    void pair(LinearLayout parent, String first, Runnable a, String second, Runnable b) {
        LinearLayout row = new LinearLayout(activity);
        TextView one = button(first, false, a), two = button(second, false, b);
        one.setTextSize(14);
        two.setTextSize(14);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.rightMargin = dp(8);
        row.addView(one, p);
        row.addView(two, new LinearLayout.LayoutParams(0, -2, 1));
        add(parent, row, 12);
    }

    void menu(String heading, String detail, String icon, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(16), dp(12), dp(16));
        row.setBackground(bg(NativeUi.CARD, NativeUi.LINE, 16));
        TextView symbol = text("", 16, NativeUi.WINE, true);
        symbol.setCompoundDrawablesWithIntrinsicBounds(new NativeUi.Icon(icon, NativeUi.WINE, dp(25)), null, null, null);
        row.addView(symbol, new LinearLayout.LayoutParams(dp(40), dp(34)));
        LinearLayout copy = column();
        copy.addView(text(heading, 17, NativeUi.INK, true));
        add(copy, text(detail, 13, NativeUi.MUTED, false), 3);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("", 16, NativeUi.MUTED, true);
        arrow.setCompoundDrawablesWithIntrinsicBounds(new NativeUi.Icon("next", NativeUi.MUTED, dp(20)), null, null, null);
        row.addView(arrow);
        row.setFocusable(true);
        row.setContentDescription(heading + ". " + detail);
        row.setOnClickListener(v -> action.run());
        add(content, row, 12);
    }

    void note(String value) {
        add(content, text(value, 15, NativeUi.MUTED, false), 12);
    }

    void title(String heading, String subtitle) {
        TextView headingView = ui.heading(heading, 27);
        if (Build.VERSION.SDK_INT >= 28) headingView.setAccessibilityHeading(true);
        add(content, headingView, 2);
        if (!subtitle.isEmpty()) add(content, text(subtitle, 16, NativeUi.MUTED, false), 7);
    }

    void segmented(String[] names, int selected, IntConsumer onSelect) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setBackground(bg(0xffe7bd91, 0, 12));
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView b = text(names[i], 13, i == selected ? NativeUi.WINE : NativeUi.MUTED, true);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(dp(44));
            if (i == selected) b.setBackground(bg(NativeUi.GOLD, 0, 9));
            b.setOnClickListener(v -> onSelect.accept(index));
            bar.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        add(content, bar, 18);
    }

    EditText field(String id, String label, String value, String placeholder, boolean numeric) {
        TextView labelView = text(label, 15, NativeUi.INK, true);
        add(content, labelView, 16);
        EditText e = new EditText(activity);
        e.setId(View.generateViewId());
        labelView.setLabelFor(e.getId());
        e.setSingleLine(true);
        e.setTextSize(18);
        e.setTypeface(ui.regular);
        e.setTextColor(NativeUi.INK);
        e.setHintTextColor(NativeUi.MUTED);
        e.setHint(placeholder);
        e.setPadding(dp(13), dp(13), dp(13), dp(13));
        e.setBackground(bg(NativeUi.CARD, NativeUi.LINE, 14));
        e.setMinHeight(dp(56));
        e.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (Arrays.asList("pickup", "hours", "description", "message", "portion").contains(id)) {
            e.setSingleLine(false);
            e.setMinLines(2);
            e.setMaxLines(5);
            e.setGravity(Gravity.TOP);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        e.setText(value);
        fields.put(id, e);
        add(content, e, 6);
        return e;
    }

    String value(String id) {
        EditText f = fields.get(id);
        return f == null ? "" : f.getText().toString().trim();
    }

    Spinner spinner(String[] values, int index) {
        Spinner s = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(adapter);
        s.setSelection(index);
        s.setMinimumHeight(dp(48));
        s.setBackground(bg(NativeUi.CARD, NativeUi.LINE, 12));
        return s;
    }

    void editorError(String id, String message) {
        EditText field = fields.get(id);
        if (field != null) {
            field.setError(message);
            field.requestFocus();
            field.announceForAccessibility(message);
        } else {
            toast(message);
        }
    }

    JSONObject data() { return renderState == null ? store.snapshot() : renderState; }

    JSONObject find(String array, String id) {
        JSONArray all = data().optJSONArray(array);
        for (int i = 0; all != null && i < all.length(); i++) {
            if (all.optJSONObject(i).optString("id").equals(id)) return all.optJSONObject(i);
        }
        return null;
    }

    String cash(double n) { return currency.format(n); }

    void toast(String message) { Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }

    boolean checkPending(String id) {
        boolean blocked = store.hasPending(id) && !store.canEditUnsent(id);
        if (blocked) toast("Este cambio ya se intentó enviar. Espera la confirmación para evitar duplicados.");
        return blocked;
    }

    String status(String s) {
        switch (s) {
            case "registered":
                return "Por confirmar";
            case "confirmed":
                return "Pedido confirmado";
            case "preparing":
            case "ready":
                return "En preparación";
            case "sale_day":
                return "Día de venta";
            case "pickup_available":
                return "Disponible para recoger";
            case "delivering":
                return "Delivery salió";
            case "delivered":
                return "Entregado";
            case "cancelled":
                return "Cancelado";
            default:
                return s;
        }
    }

    boolean isRoot() { return Arrays.asList("home", "publications", "products", "orders").contains(screen); }

    String destination() {
        return screen.equals("publications") ? "publications"
                : screen.equals("products") ? "products"
                : screen.equals("orders") ? "orders"
                : "home";
    }

    void changeTab(String target) {
        if (screen.equals("edit")) {
            ProductEditorScreen.captureEditor(this);
            saveDraft();
        }
        go(target, "");
    }

    void go(String next, String id) {
        if (next.equals("publish") && !screen.equals("publish")) {
            flyerIds.clear();
            if (!id.isEmpty()) flyerIds.add(id);
        }
        if (next.equals("promo") && !screen.equals("promo")) {
            comboIds.clear();
            comboDraft = null;
            if (!id.isEmpty()) comboIds.add(id);
        }
        if (!screen.equals(next) || !currentId.equals(id)) history.add(screen + "\t" + currentId);
        if (history.size() > 30) history.remove(0);
        if (screen.equals("edit")) ProductEditorScreen.captureEditor(this);
        if (root != null) {
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
        screen = next;
        currentId = id;
        render();
    }

    boolean enqueue(String type, JSONObject value, String destination) {
        try {
            store.enqueue(type, value);
            screen = destination;
            render();
            toast("Guardado en este celular.");
            return true;
        } catch (Exception e) {
            toast("No se pudo guardar. Conserva la pantalla y vuelve a intentar.");
            return false;
        }
    }

    List<String> sellingDays() {
        TreeSet<String> dates = new TreeSet<>(Collections.reverseOrder());
        JSONArray products = data().optJSONArray("products");
        for (int i = 0; products != null && i < products.length(); i++) {
            String date = products.optJSONObject(i).optString("saleDate");
            if (!date.isEmpty()) dates.add(date);
        }
        JSONArray orders = data().optJSONArray("orders");
        for (int i = 0; orders != null && i < orders.length(); i++) {
            String date = orders.optJSONObject(i).optString("saleDate");
            if (!date.isEmpty()) dates.add(date);
        }
        return new ArrayList<>(dates);
    }

    void chooseFilter(String label, List<String> names, List<String> ids, String selected, Consumer<String> action) {
        add(content, text(label, 14, NativeUi.INK, true), 12);
        Spinner picker = new Spinner(activity);
        picker.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, names));
        picker.setMinimumHeight(dp(52));
        picker.setSelection(Math.max(0, ids.indexOf(selected)));
        picker.setContentDescription(label);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!ids.get(position).equals(selected)) action.accept(ids.get(position));
            }
        });
        add(content, picker, 4);
    }

    void saveDraft() {
        if (screen.equals("edit")) ProductEditorScreen.captureEditor(this);
        if (draft == null) return;
        android.util.AtomicFile file = new CrossFile(new File(activity.getFilesDir(), "editor-draft.json"));
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(draft.toString().getBytes("UTF-8"));
            file.finishWrite(out);
        } catch (Exception error) {
            if (out != null) file.failWrite(out);
            toast("No pudimos conservar el borrador. Intenta guardar antes de salir.");
        }
    }

    File qrDraftFile() { return new File(activity.getFilesDir(), "pending-qr.txt"); }

    void rememberQr(String encoded) throws IOException {
        android.util.AtomicFile file = new CrossFile(qrDraftFile());
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(encoded.getBytes("UTF-8"));
            file.finishWrite(out);
        } catch (IOException error) {
            if (out != null) file.failWrite(out);
            throw error;
        }
    }

    void choosePhoto(String target, boolean camera) {
        if (screen.equals("edit")) ProductEditorScreen.captureEditor(this);
        photoFor = target;
        try {
            if (camera) {
                File dir = new File(activity.getCacheDir(), "shared");
                dir.mkdirs();
                Uri cameraUri = Uri.parse("content://pe.manchapp.beta.files/camera.jpg");
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setClipData(ClipData.newRawUri("Foto", cameraUri));
                activity.startActivityForResult(intent, 41);
            } else {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                activity.startActivityForResult(intent, 42);
            }
        } catch (Exception e) {
            toast("No se pudo abrir la cámara o la galería de este equipo.");
        }
    }

    void openStudio() {
        saveDraft();
        activity.startActivityForResult(new Intent(activity, PhotoStudioActivity.class), 87);
    }

    private boolean isOffline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info == null || !info.isConnected();
        } catch (Exception ignored) {
            return false;
        }
    }

    void render() {
        int prevScrollY = scroll != null && screen.equals(lastRenderedScreen) ? scroll.getScrollY() : 0;
        renderState = store.snapshot();
        try {
            fields.clear();
            categorySpinner = null;
            unitSpinner = null;
            availability = null;

            // --- Bloqueo por configuración incompleta ---
            JSONObject s = renderState.optJSONObject("settings");
            boolean profileComplete = s != null && !s.optString("name").isEmpty()
                    && !s.optString("pickup").isEmpty();
            boolean paymentComplete = s != null
                    && (!s.optString("paymentImage").isEmpty() || s.optBoolean("cashOnly", false)
                    || SaleRules.validPhone(s.optString("walletPhone")));

            if (isRoot() || screen.equals("summary")) {
                if (!profileComplete && !screen.equals("profile")) {
                    screen = "profile";
                } else if (profileComplete && !paymentComplete && !screen.equals("payments") && !screen.equals("qr")) {
                    screen = "payments";
                }
            }
            // ----------------------------------------------

            root = column();
            root.setBackgroundColor(NativeUi.BG);
            activity.setContentView(root);
            root.addView(ui.toolbar(isRoot() ? "ManchApp" : screenTitle(),
                    isRoot() ? "EMPRENDEDORA · " + data().optJSONObject("settings").optString("name") : "",
                    isRoot() ? null : () -> activity.onBackPressed(),
                    isRoot() ? () -> go("settings", "") : null));
            JSONArray pendingArr = renderState.optJSONArray("pending");
            int pendingCount = pendingArr == null ? 0 : pendingArr.length();
            boolean offline = isOffline();
            if (offline || pendingCount > 0) {
                TextView statusBar = text(
                        offline ? "Sin internet · operando en modo local" + (pendingCount > 0 ? " (" + pendingCount + " por sincronizar)" : "")
                                : pendingCount + (pendingCount == 1 ? " cambio guardado en este celular" : " cambios guardados en este celular"),
                        12, NativeUi.INK, true);
                statusBar.setBackgroundColor(offline ? NativeUi.GOLD : NativeUi.SOFT);
                statusBar.setPadding(dp(18), dp(7), dp(18), dp(7));
                root.addView(statusBar, new LinearLayout.LayoutParams(-1, -2));
            }
            scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            content = column();
            content.setPadding(dp(18), dp(18), dp(18), dp(22));
            scroll.addView(content);
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            if (prevScrollY > 0) {
                final ScrollView activeScroll = scroll;
                activeScroll.post(() -> activeScroll.scrollTo(0, prevScrollY));
            }
            lastRenderedScreen = screen;
            footer = column();
            footer.setPadding(dp(18), dp(9), dp(18), dp(10));
            footer.setBackgroundColor(NativeUi.BG);
            footer.setVisibility(View.GONE);
            root.addView(footer);

            switch (screen) {
                case "summary": OrderScreen.showSummary(this); break;
                case "schedule": ScheduleScreen.showSchedule(this, false); break;
                case "orderSchedule": ScheduleScreen.showSchedule(this, true); break;
                case "home": HomeScreen.showHome(this); break;
                case "products": ProductScreen.showProducts(this); break;
                case "detail": ProductScreen.showProduct(this); break;
                case "edit": ProductEditorScreen.showEditor(this); break;
                case "orders": OrderScreen.showOrders(this); break;
                case "order": OrderScreen.showOrder(this); break;
                case "settings": SettingsScreen.showSettings(this); break;
                case "profile": SettingsScreen.showProfile(this); break;
                case "delivery": SettingsScreen.showDelivery(this); break;
                case "payments": SettingsScreen.showPayments(this); break;
                case "qr": SettingsScreen.showQrPreview(this); break;
                case "help": HomeScreen.showHelp(this); break;
                case "publish": FlyerStudioScreen.showPublish(this); break;
                case "publications": HomeScreen.showPublications(this); break;
                case "promo": ComboEditorScreen.showPromo(this); break;
                default:
                    screen = "home";
                    HomeScreen.showHome(this);
                    break;
            }
            root.addView(ui.navigation(new String[]{"home", "publications", "products", "orders"},
                    new String[]{"Inicio", "Imágenes", "Productos", "Gestionar Ventas"},
                    new String[]{"home", "photo", "food", "orders"}, destination(), this::changeTab));
        } finally {
            renderState = null;
        }
    }

    String screenTitle() {
        switch (screen) {
            case "summary": return "Resumen de jornada";
            case "schedule":
            case "orderSchedule": return "Fecha de venta";
            case "home": return "Inicio";
            case "settings": return "Manka Kusi";
            case "profile": return "Datos de Manka Kusi";
            case "delivery": return "Reparto cercano";
            case "payments": return "Pagos y QR";
            case "qr": return "Revisar mi QR";
            case "help": return "Ayuda para tus fotos";
            case "edit": return "Tu producto";
            case "detail": return "Producto";
            case "publish": return "Crear afiche";
            case "publications": return "Imágenes";
            case "promo": return "Crear combo";
            case "photo": return "Editar foto";
            default: return "Detalle del pedido";
        }
    }

    static void put(JSONObject o, String key, Object value) {
        try {
            o.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
