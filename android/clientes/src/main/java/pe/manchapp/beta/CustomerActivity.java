package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.database.ContentObserver;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

public class CustomerActivity extends Activity {
    private static final Uri BRIDGE=Uri.parse("content://pe.manchapp.beta.bridge/catalog");
    private NativeUi ui;
    private CustomerStore store;
    private final ImageTools images=new ImageTools();
    private final Map<String,EditText> fields=new HashMap<>();
    private final NumberFormat currency=NumberFormat.getCurrencyInstance(new Locale("es","PE"));
    private LinearLayout root,content,footer;
    private LinearLayout summary;
    private ScrollView scroll;
    private final Map<String, Integer> shoppingScroll = new HashMap<>();
    private final List<Runnable> quantityRefreshers = new ArrayList<>();
    private TextView cartTab, shoppingAction, cartSubtotal;
    private String screen="food",lastRenderedScreen="",category="Todo",selectedId="";
    private final ArrayList<String> history=new ArrayList<>();
    private int step=0;
    private JSONObject details;
    private JSONObject viewState,bundledSale;
    private DeliveryLocator locator;
    private boolean locating=false,refreshLocationUi=false;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean syncing=false,online=false,submittingOrder=false;
    private String syncMessage="";
    private final Runnable saveDetailsDraft=new Runnable(){public void run(){captureDetails();}};
    private final Runnable bridgeRefresh=new Runnable(){public void run(){if(syncing){handler.postDelayed(this,300);return;}syncDevice(false);}};
    private ContentObserver bridgeObserver;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);ui=new NativeUi(this);try{store=CustomerStore.open(this);}catch(Exception error){RecoveryScreen.show(this,error);return;}details=store.snapshot().optJSONObject("details");locator=new DeliveryLocator(this);try{bundledSale=store.bundledSale(this);}catch(Exception ignored){}try{details=DeliveryDestination.normalized(details);}catch(JSONException error){throw new IllegalStateException(error);}
        if(state!=null){screen=state.getString("screen","food");category=state.getString("category","Todo");selectedId=state.getString("id","");step=state.getInt("step",0);if(state.getStringArrayList("history")!=null)history.addAll(state.getStringArrayList("history"));}
        else handleDeepLink(getIntent(),false);
        getWindow().getDecorView().setSystemUiVisibility(0);getWindow().setStatusBarColor(NativeUi.DEEP);getWindow().setNavigationBarColor(NativeUi.WINE);render();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(store!=null&&handleDeepLink(intent,true))render();}
    private boolean handleDeepLink(Intent intent,boolean notify){
        if(intent==null||intent.getData()==null)return false;
        android.net.Uri data=intent.getData();
        if(!"manchapp".equals(data.getScheme()))return false;
        String host=data.getHost()==null?"":data.getHost();
        String pathId=data.getLastPathSegment()==null?"":data.getLastPathSegment();
        if("product".equals(host)&&!pathId.isEmpty()&&product(pathId)!=null){screen="detail";selectedId=pathId;step=0;return true;}
        if("catalog".equals(host)){screen="food";selectedId="";step=0;if(notify)syncDevice(true);return true;}
        return false;
    }
    @Override public void onPause(){unregisterBridgeObserver();captureDetails();if(locator!=null)locator.cancel();refreshLocationUi=locating;locating=false;super.onPause();}
    @Override public void onResume(){super.onResume();registerBridgeObserver();if(refreshLocationUi&&root!=null){refreshLocationUi=false;render();}if(store!=null)syncDevice(false);}
    @Override public void onSaveInstanceState(Bundle out){captureDetails();super.onSaveInstanceState(out);out.putString("screen",screen);out.putString("category",category);out.putString("id",selectedId);out.putInt("step",step);out.putStringArrayList("history",history);}
    @Override public void onDestroy(){unregisterBridgeObserver();handler.removeCallbacksAndMessages(null);images.clear();worker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(screen.equals("checkout")&&step>0){captureDetails();step--;render();return;}if(history.isEmpty()){super.onBackPressed();return;}String[] previous=history.remove(history.size()-1).split("\\t",-1);captureDetails();screen=previous[0];selectedId=previous.length>1?previous[1]:"";step=previous.length>2?Integer.parseInt(previous[2]):0;render();}
    private int dp(float n){return ui.dp(n);}
    private JSONObject state(){return viewState==null?store.snapshot():viewState;}
    private JSONObject catalog(){return state().optJSONObject("catalog");}
    private JSONObject baseSettings(){return catalog().optJSONObject("settings");}
    private JSONObject settings(){JSONObject selected=product(selectedId);if(selected!=null)return SaleSchedule.forProduct(selected,baseSettings());if(store.shoppingCount()>0)return store.currentSale();return baseSettings();}
    private String cash(double n){return currency.format(n);}
    private void put(JSONObject object,String name,Object value){try{object.put(name,value);}catch(JSONException error){throw new IllegalStateException(error);}}
    private JSONObject product(String id){JSONArray products=catalog().optJSONArray("products");for(int i=0;i<products.length();i++)if(products.optJSONObject(i).optString("id").equals(id))return products.optJSONObject(i);return null;}
    private String unit(JSONObject p){return SaleRules.unit(p.optString("saleUnit",SaleRules.defaultUnit(p.optString("category"))));}
    private String payment(){switch(details.optString("payment","cash")){case "yape":return "Yape";case "plin":return "Plin";default:return "Efectivo";}}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void go(String next,String id){captureDetails();if(!screen.equals(next)||!selectedId.equals(id))history.add(screen+"\t"+selectedId+"\t"+step);if(history.size()>30)history.remove(0);if(locator!=null)locator.cancel();locating=false;if(root!=null)((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);screen=next;selectedId=id;if(!next.equals("checkout"))step=0;render();}
    private void add(LinearLayout parent,View child,int gap){ui.add(parent,child,gap);}
    private void title(String title,String subtitle){add(content,ui.heading(title,26),0);if(!subtitle.isEmpty())add(content,ui.text(subtitle,14,NativeUi.MUTED,false),5);}
    private void note(String text){add(content,ui.text(text,14,NativeUi.MUTED,false),12);}
    private void primary(String text,Runnable action){footer.setVisibility(View.VISIBLE);footer.removeAllViews();footer.addView(ui.button(text,true,null,action),new LinearLayout.LayoutParams(-1,-2));}
    private String activeTab(){if(screen.equals("detail"))return "food";if(screen.equals("checkout"))return "cart";if(screen.equals("receipt")||screen.equals("extras"))return "orders";return screen;}
    private boolean rootScreen(){return Arrays.asList("food","offers","cart","orders").contains(screen);}
    private boolean catalogScreen(String destination) {
        return destination.equals("food") || destination.equals("offers");
    }
    private void render(){
        if (scroll != null && (catalogScreen(lastRenderedScreen) || lastRenderedScreen.equals("cart"))) {
            shoppingScroll.put(lastRenderedScreen, scroll.getScrollY());
        }
        int prevScrollY = scroll != null && screen.equals(lastRenderedScreen)
                ? scroll.getScrollY() : shoppingScroll.getOrDefault(screen, 0);
        quantityRefreshers.clear();
        cartTab = null;
        shoppingAction = null;
        cartSubtotal = null;
        handler.removeCallbacks(saveDetailsDraft);viewState=store.snapshot();fields.clear();root=ui.column();root.setBackgroundColor(NativeUi.BG);setContentView(root);
        String heading=rootScreen()?"ManchApp":screen.equals("detail")?"Producto":screen.equals("checkout")?"Arma tu pedido":"Tu pedido";
        root.addView(ui.toolbar(heading,rootScreen()?baseSettings().optString("name","Manka Kusi"):"",rootScreen()?null:()->onBackPressed(),null));
        int pendingCount=store.pending().length();
        JSONArray extraPending = store.extraRequestsToSync();
        for (int i = 0; i < extraPending.length(); i++) {
            if (!"sent".equals(extraPending.optJSONObject(i).optString("sendState"))) pendingCount++;
        }
        if(!syncMessage.isEmpty()||pendingCount>0){
            String msg = pendingCount == 0 ? "Mostrando el catálogo guardado" : pendingCount == 1
                    ? "1 envío pendiente" : pendingCount + " envíos pendientes";
            LinearLayout bar=new LinearLayout(this);bar.setBackgroundColor(pendingCount>0?NativeUi.GOLD:NativeUi.SOFT);bar.setPadding(dp(16),dp(4),dp(16),dp(4));bar.setGravity(Gravity.CENTER_VERTICAL);
            TextView label=ui.text(msg,12,NativeUi.INK,true);bar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            if(pendingCount>0||!syncMessage.isEmpty()){TextView retry=ui.text(syncing?"…":"Reintentar",12,NativeUi.WINE,true);retry.setPadding(dp(10),dp(4),0,dp(4));retry.setOnClickListener(v->syncDevice(true));bar.addView(retry);}
            root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        }
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=ui.column();content.setPadding(dp(16),dp(16),dp(16),dp(22));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if (prevScrollY > 0) {
            ScrollView activeScroll = scroll;
            activeScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                                     int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    activeScroll.removeOnLayoutChangeListener(this);
                    activeScroll.scrollTo(0, prevScrollY);
                }
            });
        }
        lastRenderedScreen=screen;
        footer=ui.column();footer.setPadding(dp(16),dp(8),dp(16),dp(9));footer.setVisibility(View.GONE);root.addView(footer);
        switch(screen){case "food":showFood();break;case "offers":showOffers();break;case "cart":showCart();break;case "detail":showProduct();break;case "checkout":showCheckout();break;case "orders":showOrders();break;case "receipt":showReceipt();break;case "extras":showExtrasReceipt();break;default:screen="food";showFood();}
        if (catalogScreen(screen) || screen.equals("detail")) {
            shoppingAction = ui.button("Ver carrito", true, "cart", () -> go("cart", ""));
            shoppingAction.setTag("shopping:cart");
            footer.addView(shoppingAction, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout navigation = ui.navigation(new String[]{"food","offers","cart","orders"},
                new String[]{"Catálogo","Ofertas","Carrito","Pedidos"}, new String[]{"food","tag","cart","orders"},
                activeTab(), id -> { if (!id.equals(screen)) go(id, ""); });
        for (int i = 0; i < navigation.getChildCount(); i++) {
            TextView tab = (TextView) navigation.getChildAt(i);
            tab.setSingleLine(false);
            tab.setMaxLines(1);
            tab.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        cartTab = (TextView) navigation.getChildAt(2);
        root.addView(navigation);
        refreshShoppingActions();
    }
    private ImageView image(JSONObject product,int edge){ImageView view=new ImageView(this);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setBackground(ui.background(0xffeabe83,0,15));view.setClipToOutline(true);Bitmap bitmap=images.productThumbnail(this,product,dp(edge));if(bitmap!=null)view.setImageBitmap(bitmap);else view.setImageDrawable(new DishArt(product.optString("illustration","papa")));view.setContentDescription((bitmap==null?"Ilustración referencial de ":"Foto de ")+product.optString("name"));return view;}
    private boolean accepting(){return SaleSchedule.unavailable(settings(),System.currentTimeMillis()).isEmpty();}
    private boolean accepting(JSONObject product){return SaleSchedule.unavailable(SaleSchedule.forProduct(product,baseSettings()),System.currentTimeMillis()).isEmpty();}
    private void saleCard(){JSONObject saleSettings=settings();String closed=SaleSchedule.unavailable(saleSettings,System.currentTimeMillis());LinearLayout sale=ui.message("VENTA PROGRAMADA",SaleSchedule.day(saleSettings),NativeUi.GOLD);add(sale,ui.text(SaleSchedule.shortWindow(saleSettings),14,NativeUi.INK,true),9);add(sale,ui.text(closed.isEmpty()?"Reservas hasta "+SaleSchedule.deadline(saleSettings):closed,14,NativeUi.WINE,true),9);add(content,sale,13);}
    private void showFood() {
        LinearLayout header = ui.column();
        header.setPadding(dp(16), dp(10), dp(16), dp(8));
        header.setBackgroundColor(NativeUi.BG);
        root.addView(header, root.indexOfChild(scroll), new LinearLayout.LayoutParams(-1, -2));
        add(header, ui.heading("Catálogo", 24), 0);
        nextSaleBanner(header);
        content.setPadding(dp(16), 0, dp(16), dp(18));
        List<JSONObject> products = catalogProducts(false);
        if (products.isEmpty()) {
            empty("Todavía no hay productos para la próxima venta", "Vuelve cuando la olla anuncie una nueva jornada.");
            return;
        }
        categoryFilter(header, products);
        showFilteredProducts(products);
    }

    private void showFilteredProducts(List<JSONObject> products) {
        content.removeAllViews();
        quantityRefreshers.clear();
        for (JSONObject product : products) {
            if (category.equals("Todo") || product.optString("category").trim().equalsIgnoreCase(category)) {
                productCard(product);
            }
        }
    }

    private void nextSaleBanner(LinearLayout parent) {
        JSONObject sale = baseSettings();
        String closed = SaleSchedule.unavailable(sale, System.currentTimeMillis());
        LinearLayout banner = new LinearLayout(this);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(12), dp(9), dp(8), dp(9));
        banner.setBackground(ui.background(NativeUi.SOFT, 0, 14));
        LinearLayout info = ui.column();
        add(info, ui.text("Próxima venta", 12, NativeUi.WINE, true), 0);
        add(info, ui.text(SaleSchedule.day(sale), 14, NativeUi.INK, true), 2);
        add(info, ui.text(closed.isEmpty() ? "Reservas hasta " + SaleSchedule.deadline(sale) : closed,
                12, NativeUi.MUTED, false), 3);
        banner.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView remind = ui.button("", false, "clock", () -> addSaleReminder(sale));
        remind.setPadding(dp(12), 0, 0, 0);
        remind.setContentDescription("Guardar fecha de venta en el calendario");
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(dp(48), dp(48));
        button.leftMargin = dp(8);
        banner.addView(remind, button);
        add(parent, banner, 8);
    }
    private void addSaleReminder(JSONObject s){
        long start=SaleSchedule.parse(s.optString("pickupFrom"),false),end=SaleSchedule.parse(s.optString("pickupUntil"),false);
        if(start<0)start=System.currentTimeMillis()+3600_000L;if(end<=start)end=start+7200_000L;
        try{
            Intent cal=new Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.Events.TITLE,"Venta de "+s.optString("name","Manka Kusi"))
                    .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION,s.optString("pickup","Manchay"))
                    .putExtra(android.provider.CalendarContract.Events.DESCRIPTION,"Recojo y reservas en ManchApp · "+SaleSchedule.shortWindow(s))
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME,start)
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME,end);
            startActivity(cal);
        }catch(Exception error){toast("No encontramos una app de calendario en este celular.");}
    }
    private List<JSONObject> catalogProducts(boolean onlyOffers) {
        JSONArray products = catalog().optJSONArray("products");
        Set<String> promoted = new HashSet<>();
        JSONArray promos = catalog().optJSONArray("promos");
        for (int i = 0; onlyOffers && promos != null && i < promos.length(); i++) {
            JSONObject promo = promos.optJSONObject(i);
            if (promo != null && promo.optBoolean("active")) promoted.add(promo.optString("productId"));
        }
        List<JSONObject> visible = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; products != null && i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product == null || !product.optString("line").equals("commercial")
                    || !product.optBoolean("available")) continue;
            JSONObject sale = SaleSchedule.forProduct(product, baseSettings());
            if (!SaleSchedule.invalid(sale).isEmpty() || !sale.optBoolean("salesOpen")
                    || SaleSchedule.parse(sale.optString("pickupUntil"), false) <= now) continue;
            boolean offer = product.optString("saleUnit").equalsIgnoreCase("combo")
                    || product.optString("category").equalsIgnoreCase("combos")
                    || promoted.contains(product.optString("id"));
            if (!onlyOffers || offer) visible.add(product);
        }
        return visible;
    }

    private void categoryFilter(LinearLayout parent, List<JSONObject> products) {
        List<String> labels = new ArrayList<>();
        labels.add("Todo");
        for (JSONObject product : products) {
            String label = product.optString("category").trim();
            if (!label.isEmpty() && labels.stream().noneMatch(label::equalsIgnoreCase)) labels.add(label);
        }
        if (labels.stream().noneMatch(category::equalsIgnoreCase)) category = "Todo";
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setTag("catalog:categories");
        LinearLayout row = new LinearLayout(this);
        strip.addView(row);
        Map<String, TextView> chips = new LinkedHashMap<>();
        for (String label : labels) {
            boolean selected = label.equalsIgnoreCase(category);
            TextView chip = ui.button(label.equals("Todo") ? "Todos" : label, false, null, () -> {
                if (!label.equalsIgnoreCase(category)) {
                    category = label;
                    chips.forEach((name, item) -> styleCategory(item, name.equalsIgnoreCase(category)));
                    showFilteredProducts(products);
                }
            });
            chip.setTag("category:" + label);
            chip.setTextSize(13);
            chip.setMaxLines(1);
            chip.setPadding(dp(13), 0, dp(13), 0);
            styleCategory(chip, selected);
            chips.put(label, chip);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
            params.rightMargin = dp(6);
            row.addView(chip, params);
            if (selected) strip.post(() -> strip.scrollTo(Math.max(0, chip.getLeft() - dp(8)), 0));
        }
        add(parent, strip, 8);
    }

    private void styleCategory(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(selected ? NativeUi.CARD : NativeUi.WINE);
        chip.setBackground(ui.background(selected ? NativeUi.WINE : NativeUi.CARD,
                selected ? 0 : NativeUi.LINE, 24));
    }

    private TextView stockLabel(JSONObject product) {
        int stock = product.optInt("stock");
        boolean available = product.optBoolean("available") && stock > 0;
        boolean open = accepting(product);
        String label = !available ? "Sin cupos" : !open ? "Reservas cerradas"
                : stock == 1 ? "Queda 1 cupo" : stock + " cupos";
        return ui.chip(label, available && open ? NativeUi.GREEN : NativeUi.MUTED,
                available && open ? 0xffe6f4ea : NativeUi.SOFT);
    }

    private LinearLayout productHeader(JSONObject product, int photoSize, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(image(product, photoSize), new LinearLayout.LayoutParams(dp(photoSize), dp(photoSize)));
        LinearLayout description = ui.column();
        description.setPadding(dp(12), 0, 0, 0);
        add(description, ui.heading(product.optString("name"), 19), 0);
        if (!subtitle.isEmpty()) add(description, ui.text(subtitle, 13, NativeUi.MUTED, false), 3);
        row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void productCard(JSONObject product) {
        String id = product.optString("id");
        LinearLayout card = ui.card();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout header = productHeader(product, 68, product.optString("portion").trim());
        header.setOnClickListener(view -> go("detail", id));
        ui.buttonRole(header);
        header.setContentDescription("Ver " + product.optString("name"));
        header.setTag("shopping:product:" + id);
        card.addView(header);
        JSONObject sale = SaleSchedule.forProduct(product, baseSettings());
        if (!screen.equals("food") || !SaleSchedule.key(sale).equals(SaleSchedule.key(baseSettings()))) {
            add(card, ui.text(SaleSchedule.day(sale), 13, NativeUi.MUTED, false), 6);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info = ui.column();
        add(info, ui.text(cash(product.optDouble("price")) + " " + unit(product), 17, NativeUi.WINE, true), 0);
        add(info, stockLabel(product), 3);
        actions.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(quantityPicker(product, false), new LinearLayout.LayoutParams(dp(132), dp(52)));
        add(card, actions, 6);
        add(content, card, screen.equals("food") ? 7 : 12);
    }

    private int cartQuantity(String id) {
        return state().optJSONObject("cart").optInt(id);
    }

    private int extraQuantity(String id) {
        JSONObject extras = state().optJSONObject("extraCart");
        return extras == null ? 0 : extras.optInt(id);
    }

    private String additionProblem(JSONObject product) {
        if (!product.optBoolean("available")) return "No disponible";
        if (!accepting(product)) return "Cerrado";
        if (store.cartNeedsReview()) return "Revisa tu carrito";
        JSONObject sale = SaleSchedule.forProduct(product, baseSettings());
        if (store.shoppingCount() > 0 && !SaleSchedule.key(store.currentSale()).equals(SaleSchedule.key(sale))) {
            return "Otra jornada";
        }
        return "";
    }

    private FrameLayout quantityPicker(JSONObject product, boolean cart) {
        String id = product.optString("id"), name = product.optString("name");
        FrameLayout picker = new FrameLayout(this);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = ui.button("−", false, null, () -> changeQuantity(id, -1));
        TextView plus = ui.button("+", false, null, () -> changeQuantity(id, 1));
        minus.setPadding(0, 0, 0, 0);
        plus.setPadding(0, 0, 0, 0);
        minus.setContentDescription("Quitar uno de " + name);
        plus.setContentDescription("Agregar uno de " + name);
        minus.setTag("shopping:minus:" + id);
        plus.setTag("shopping:plus:" + id);
        TextView amount = ui.text("", 18, NativeUi.INK, true);
        amount.setGravity(Gravity.CENTER);
        amount.setTag("shopping:quantity:" + id);
        controls.addView(minus, new LinearLayout.LayoutParams(dp(48), -1));
        controls.addView(amount, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(plus, new LinearLayout.LayoutParams(dp(48), -1));
        picker.addView(controls, new FrameLayout.LayoutParams(-1, -1));

        TextView addButton = cart ? null : ui.button("Agregar", true, null, () -> changeQuantity(id, 1));
        if (addButton != null) {
            addButton.setTag("shopping:add:" + id);
            addButton.setTextSize(14);
            addButton.setPadding(dp(6), 0, dp(6), 0);
            picker.addView(addButton, new FrameLayout.LayoutParams(-1, -1));
        }

        Runnable refresh = () -> {
            int reserved = cartQuantity(id), extra = extraQuantity(id), quantity = reserved + extra;
            String problem = additionProblem(product);
            amount.setText(String.valueOf(quantity));
            amount.setContentDescription(quantity + " de " + name + (extra > 0 ? ", " + extra + " por confirmar" : ""));
            setActionEnabled(minus, quantity > 0);
            setActionEnabled(plus, problem.isEmpty() && quantity < 99);
            plus.setContentDescription(reserved >= product.optInt("stock")
                    ? "Solicitar una unidad extra de " + name : "Agregar uno de " + name);
            if (addButton != null) {
                controls.setVisibility(quantity > 0 ? View.VISIBLE : View.GONE);
                addButton.setVisibility(quantity == 0 ? View.VISIBLE : View.GONE);
                String action = product.optInt("stock") <= 0 ? "Solicitar" : "Agregar";
                addButton.setText(problem.isEmpty() ? action : problem);
                addButton.setContentDescription(problem.isEmpty() ? "Agregar " + name : name + " · " + problem);
                setActionEnabled(addButton, problem.isEmpty());
            }
        };
        refresh.run();
        quantityRefreshers.add(refresh);
        return picker;
    }

    private void changeQuantity(String id, int delta) {
        JSONObject product = product(id);
        if (product == null) return;
        if (delta > 0 && cartQuantity(id) >= product.optInt("stock")) {
            if (extraQuantity(id) > 0) {
                updateQuantity(id, delta, true);
                return;
            }
            int regular = cartQuantity(id);
            String description = regular == 0 ? "Por ahora no hay cupos disponibles."
                    : "Ya tienes " + regular + " unidad(es) con cupo en el carrito.";
            new AlertDialog.Builder(this).setTitle("¿Solicitar una unidad extra?")
                    .setMessage(description + " La unidad extra queda por confirmar y no se cobrará con este pedido.")
                    .setNegativeButton("Volver", null)
                    .setPositiveButton("Agregar solicitud", (dialog, which) -> updateQuantity(id, delta, true))
                    .show();
            return;
        }
        updateQuantity(id, delta, delta < 0 && extraQuantity(id) > 0);
    }

    private void updateQuantity(String id, int delta, boolean extra) {
        try {
            if (extra) put(viewState, "extraCart", store.changeExtra(id, delta));
            else put(viewState, "cart", store.adjustQuantity(id, delta));
            if (screen.equals("cart") && store.shoppingCount() == 0) {
                render();
                return;
            }
            for (Runnable refresh : quantityRefreshers) refresh.run();
            refreshShoppingActions();
        } catch (Exception error) {
            toast(error.getMessage());
        }
    }

    private void setActionEnabled(View action, boolean enabled) {
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : .6f);
    }

    private void refreshShoppingActions() {
        int count = store.shoppingCount();
        if (cartTab != null) {
            cartTab.setText(count == 0 ? "Carrito" : "Carrito · " + count);
            cartTab.setContentDescription(count == 0 ? "Carrito vacío" : "Carrito con " + count + " unidades");
        }
        if (shoppingAction == null) return;
        String subtotal = cash(store.subtotal());
        if (screen.equals("cart")) {
            cartSubtotal.setText(store.count() == 0 ? "Sin cobro" : subtotal);
            shoppingAction.setText(store.count() == 0 ? "Continuar con la solicitud" : "Elegir entrega");
            setActionEnabled(shoppingAction, count > 0 && accepting() && !store.cartNeedsReview());
        } else {
            footer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            shoppingAction.setText(store.count() == 0 && count > 0 ? "Ver carrito · " + count + " por confirmar"
                    : "Ver carrito · " + count + " · " + subtotal);
            shoppingAction.setContentDescription("Ver carrito, " + count + " unidades, subtotal " + subtotal);
        }
    }

    private void showProduct() {
        JSONObject product = product(selectedId);
        if (product == null || !product.optString("line").equals("commercial")) {
            empty("Producto no disponible", "Puedes elegir otro producto del catálogo.");
            add(content, ui.button("Ver catálogo", false, "food", () -> go("food", "")), 12);
            return;
        }
        ImageView photo = image(product, 320);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        add(content, photo, 0);
        photo.getLayoutParams().height = dp(215);
        if (!ImageTools.hasProductPhoto(product)) note("Ilustración referencial.");
        add(content, ui.heading(product.optString("name"), 28), 14);
        add(content, ui.text(cash(product.optDouble("price")) + " " + unit(product), 22, NativeUi.WINE, true), 5);
        add(content, stockLabel(product), 5);
        add(content, quantityPicker(product, false), 12);
        String portion = product.optString("portion").trim();
        String description = product.optString("description").trim();
        if (!portion.isEmpty()) add(content, ui.text(portion, 16, NativeUi.INK, true), 12);
        if (!description.isEmpty() && !description.equals(portion)) note(description);
        saleCard();
    }

    private void showOffers() {
        title("Ofertas", "Combos y promociones de la olla.");
        List<JSONObject> offers = catalogProducts(true);
        if (offers.isEmpty()) {
            empty("Todavía no hay ofertas", "Mientras tanto puedes elegir productos del catálogo.");
            add(content, ui.button("Ver catálogo", false, "food", () -> go("food", "")), 12);
        }
        for (JSONObject offer : offers) productCard(offer);
        double from = baseSettings().optDouble("deliveryFreeFrom", 0);
        if (from > 0) add(content, ui.message("Reparto gratis desde " + cash(from),
                "Solo dentro de la cobertura habilitada.", NativeUi.SOFT), 16);
    }

    private void empty(String title, String detail) {
        add(content, ui.message(title, detail, NativeUi.GOLD), 18);
    }

    private void showCart() {
        title("Tu carrito", "");
        if (store.shoppingCount() == 0) {
            empty("Tu carrito está vacío", "Elige productos del catálogo para armar tu pedido.");
            primary("Ver catálogo", () -> go("food", ""));
            return;
        }

        add(content, ui.message("Día de venta", SaleSchedule.day(settings()), NativeUi.GOLD), 12);
        if (store.cartNeedsReview()) {
            note("Cambió la jornada de venta. Quita los productos y vuelve a elegirlos para la próxima fecha.");
        } else if (!accepting()) {
            note(SaleSchedule.unavailable(settings(), System.currentTimeMillis()));
        }
        Set<String> products = new LinkedHashSet<>();
        state().optJSONObject("cart").keys().forEachRemaining(products::add);
        JSONObject extras = state().optJSONObject("extraCart");
        if (extras != null) extras.keys().forEachRemaining(products::add);
        for (String id : products) {
            JSONObject product = product(id);
            if (product != null) cartCard(product);
        }
        note("El costo de delivery se suma al elegir el sector. El recojo es gratis.");
        add(content, ui.button("Seguir comprando", false, "food", () -> go("food", "")), 12);
        add(content, ui.button("Vaciar carrito", false, null, () -> new AlertDialog.Builder(this)
                .setTitle("¿Vaciar carrito?").setMessage("Se quitarán todos los productos seleccionados.")
                .setNegativeButton("Conservar", null).setPositiveButton("Vaciar", (dialog, which) -> {
                    try {
                        store.clearCart();
                        render();
                    } catch (Exception error) {
                        toast("No pudimos vaciar el carrito. Intenta otra vez.");
                    }
                }).show()), 8);

        LinearLayout total = new LinearLayout(this);
        total.setGravity(Gravity.CENTER_VERTICAL);
        total.addView(ui.text("Subtotal", 15, NativeUi.MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        cartSubtotal = ui.text("", 21, NativeUi.WINE, true);
        cartSubtotal.setTag("shopping:subtotal");
        total.addView(cartSubtotal);
        add(footer, total, 0);
        shoppingAction = ui.button("Elegir entrega", true, null, () -> {
            step = 0;
            go("checkout", "");
        });
        shoppingAction.setTag("shopping:checkout");
        add(footer, shoppingAction, 5);
        footer.setVisibility(View.VISIBLE);
    }

    private void cartCard(JSONObject product) {
        String id = product.optString("id");
        LinearLayout card = ui.card();
        card.setTag("shopping:line:" + id);
        card.addView(productHeader(product, 56, cash(product.optDouble("price")) + " " + unit(product)));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(quantityPicker(product, true), new LinearLayout.LayoutParams(dp(132), dp(52)));
        TextView lineTotal = ui.text("", 19, NativeUi.WINE, true);
        lineTotal.setGravity(Gravity.END);
        row.addView(lineTotal, new LinearLayout.LayoutParams(0, -2, 1));
        add(card, row, 10);
        TextView extraLabel = ui.text("", 13, NativeUi.WINE, true);
        add(card, extraLabel, 6);
        add(content, card, 12);
        Runnable refresh = () -> {
            int amount = cartQuantity(id);
            int extras = extraQuantity(id);
            card.setVisibility(amount + extras == 0 ? View.GONE : View.VISIBLE);
            lineTotal.setText(cash(product.optDouble("price") * amount));
            extraLabel.setVisibility(extras == 0 ? View.GONE : View.VISIBLE);
            extraLabel.setText(amount + " con cupo · " + extras + " por confirmar");
        };
        refresh.run();
        quantityRefreshers.add(refresh);
    }

    private EditText field(String key,String label,String hint,boolean phone){
        TextView name=ui.text(label,15,NativeUi.INK,true);add(content,name,16);EditText input=new EditText(this);input.setId(View.generateViewId());name.setLabelFor(input.getId());input.setTextSize(17);input.setTypeface(ui.regular);input.setTextColor(NativeUi.INK);input.setHintTextColor(NativeUi.MUTED);input.setHint(hint);input.setPadding(dp(14),dp(13),dp(14),dp(13));input.setMinHeight(dp(54));input.setBackground(ui.background(NativeUi.CARD,NativeUi.LINE,14));input.setInputType(phone?InputType.TYPE_CLASS_PHONE:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);if(phone)input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(9)});if(Arrays.asList("address","reference","meeting").contains(key)){input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});input.setMinLines(key.equals("address")?1:2);input.setMaxLines(4);input.setGravity(Gravity.TOP);}input.setText(details.optString(key));input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable s){handler.removeCallbacks(saveDetailsDraft);handler.postDelayed(saveDetailsDraft,500);}});fields.put(key,input);add(content,input,6);return input;
    }
    private void captureDetails(){if(details==null||store==null)return;for(Map.Entry<String,EditText> field:fields.entrySet())put(details,field.getKey(),field.getValue().getText().toString().trim());try{store.details(details);}catch(Exception error){toast("No pudimos conservar tus datos. Intenta otra vez antes de salir.");}}
    private void error(String field,String message){EditText input=fields.get(field);if(input!=null){input.setError(message);input.requestFocus();input.announceForAccessibility(message);}else toast(message);}
    private void choices(String[] keys,String[] labels,String selected,java.util.function.Consumer<String> action){LinearLayout row=new LinearLayout(this);for(int i=0;i<keys.length;i++){String key=keys[i];boolean active=key.equals(selected);TextView choice=ui.button(labels[i],false,null,()->action.accept(key));choice.setTextSize(14);choice.setSelected(active);choice.setTextColor(active?NativeUi.CARD:NativeUi.WINE);choice.setBackground(ui.background(active?NativeUi.WINE:NativeUi.CARD,active?0:NativeUi.LINE,14));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(i==0?0:4),0,dp(i==keys.length-1?0:4),0);row.addView(choice,params);}add(content,row,12);}
    private void showCheckout() {
        if (store.shoppingCount() == 0) {
            empty("Tu carrito está vacío", "Agrega productos antes de continuar.");
            primary("Ver catálogo", () -> go("food", ""));
            return;
        }
        if (!accepting() || store.cartNeedsReview()) {
            saleCard();
            primary("Volver al carrito", () -> go("cart", ""));
            return;
        }
        String[] names = {"Entrega", "Tus datos", "Revisar"};
        if (step > 0) {
            TextView back = ui.button("‹ " + names[step - 1], false, null, this::onBackPressed);
            back.setMinHeight(dp(44));
            back.setTextSize(13);
            back.setTag("checkout:back");
            content.addView(back, new LinearLayout.LayoutParams(-2, -2));
        }
        add(content, ui.heading(names[step], 24), step == 0 ? 0 : 8);
        add(content, ui.text("Paso " + (step + 1) + " de 3 · " + SaleSchedule.day(settings()),
                13, NativeUi.WINE, true), 6);
        if (step == 0) showDeliveryStep();
        else if (step == 1) showContactStep();
        else showReviewStep();

        summary = ui.card();
        add(content, summary, 16);
        updateSummary();
        if (step < 2) {
            primary(step == 0 ? "Continuar con mis datos" : "Revisar", this::advanceCheckout);
        } else {
            primary(submittingOrder ? "Enviando…" : store.count() > 0 ? "Enviar pedido" : "Enviar solicitud",
                    this::confirmShopping);
        }
    }

    private void showDeliveryStep() {
        choices(new String[]{"pickup", "delivery"}, new String[]{"Recojo", "Delivery"},
                details.optString("fulfillment", "pickup"), key -> {
                    captureDetails();
                    if (locator != null) locator.cancel();
                    locating = false;
                    put(details, "fulfillment", key);
                    render();
                });
        if (!details.optString("fulfillment", "pickup").equals("delivery")) {
            add(content, ui.message("Recojo en la olla", settings().optString("pickup") + "\n"
                    + SaleSchedule.window(settings()), NativeUi.CARD), 12);
            return;
        }
        TextView gps = ui.button(locating ? "Buscando ubicación…" : "Usar mi ubicación",
                true, "pin", this::requestDeliveryLocation);
        gps.setEnabled(!locating);
        gps.setTag("checkout:gps");
        add(content, gps, 12);
        note("Úsala si estás en el destino. Si estás en otro lugar, busca la dirección de entrega.");
        field("address", "Dirección de entrega", "Calle, número o punto de encuentro", false);
        TextView search = ui.button("Buscar dirección", false, "pin", this::findDeliveryAddress);
        search.setEnabled(!locating);
        add(content, search, 8);
        field("reference", "Referencia para llegar", "Ej. Portón verde junto a la bodega", false);
        destinationCard(content, details, true);
        try {
            JSONObject zone = DeliveryDestination.verifiedZone(settings(), details);
            add(content, ui.message(zone.getString("name"),
                    store.count() > 0 ? "Delivery: " + cash(store.fee(details)) : "Cobertura verificada",
                    NativeUi.SOFT), 12);
        } catch (Exception ignored) {
            note("Calcularemos el sector y la tarifa al ubicar el destino.");
        }
    }

    private void showContactStep() {
        field("customer", "Tu nombre", "¿A nombre de quién será?", false);
        field("phone", "Celular de contacto", "9 dígitos, empieza con 9", true);
        if (store.count() == 0) {
            note("Si la olla acepta la solicitud, coordinará el pago contigo.");
            return;
        }
        add(content, ui.heading("Forma de pago", 21), 22);
        JSONObject shop = baseSettings();
        String mode = shop.optString("paymentMode", "both");
        boolean hasQr = !shop.optString("paymentImage").isEmpty();
        boolean hasWallet = hasQr || SaleRules.validPhone(shop.optString("walletPhone"));
        List<String> keys = new ArrayList<>(), labels = new ArrayList<>();
        keys.add("cash");
        labels.add("Efectivo");
        if (hasWallet && !shop.optBoolean("cashOnly") && !mode.equals("cash")) {
            if (!mode.equals("plin")) {
                keys.add("yape");
                labels.add(hasQr ? "Yape / QR" : "Yape");
            }
            if (!mode.equals("yape")) {
                keys.add("plin");
                labels.add(hasQr ? "Plin / QR" : "Plin");
            }
        }
        String selected = details.optString("payment", "cash");
        if (!keys.contains(selected)) {
            selected = "cash";
            put(details, "payment", selected);
        }
        choices(keys.toArray(new String[0]), labels.toArray(new String[0]), selected, key -> {
            captureDetails();
            put(details, "payment", key);
            render();
        });
        note(keys.size() == 1 ? "Paga en efectivo al recibir. La olla aún no habilitó un pago digital."
                : hasQr ? "El QR se muestra cuando la olla reserva tus cupos y confirma el pedido."
                : "La olla acepta transferencias por número. Verás los datos al confirmar el pedido.");
    }

    private void showReviewStep() {
        JSONObject cart = state().optJSONObject("cart");
        for (Iterator<String> ids = cart.keys(); ids.hasNext();) {
            String id = ids.next();
            JSONObject product = product(id);
            if (product != null) summaryLine(content, cart.optInt(id) + " × " + product.optString("name"),
                    cash(cart.optInt(id) * product.optDouble("price")), false);
        }
        if (store.extraCount() > 0) {
            LinearLayout extra = ui.message("Cupos extra por confirmar", extraCartDescription(), NativeUi.SOFT);
            add(extra, ui.text("Estas unidades quedan fuera del total. La olla decidirá si puede atenderlas.",
                    14, NativeUi.INK, false), 8);
            add(content, extra, 12);
        }
        add(content, ui.message(details.optString("customer"), details.optString("phone"), NativeUi.CARD), 12);
        boolean delivery = details.optString("fulfillment", "pickup").equals("delivery");
        String place = delivery ? details.optString("address") + "\n" + details.optString("reference")
                : settings().optString("pickup");
        add(content, ui.message(delivery ? "Delivery" : "Recojo", place, NativeUi.CARD), 12);
        if (store.count() > 0) {
            add(content, ui.message("Forma de pago", payment(), NativeUi.SOFT), 12);
        }
    }

    private String extraCartDescription() {
        StringBuilder text = new StringBuilder();
        JSONObject extras = state().optJSONObject("extraCart");
        for (Iterator<String> ids = extras == null ? Collections.<String>emptyList().iterator() : extras.keys(); ids.hasNext();) {
            String id = ids.next();
            JSONObject product = product(id);
            if (product != null) {
                if (text.length() > 0) text.append('\n');
                text.append(extras.optInt(id)).append(" × ").append(product.optString("name"));
            }
        }
        return text.toString();
    }

    private void advanceCheckout() {
        captureDetails();
        if (!validStep()) return;
        step++;
        hideKeyboard();
        render();
    }

    private void confirmShopping() {
        if (submittingOrder) return;
        if (store.extraCount() == 0) {
            sendShopping(false);
            return;
        }
        boolean hasRegular = store.count() > 0;
        AlertDialog.Builder confirm = new AlertDialog.Builder(this)
                .setTitle(hasRegular ? "¿Solicitar también los cupos extra?" : "¿Enviar la solicitud de cupos extra?")
                .setMessage(extraCartDescription() + "\n\nLa olla revisará si puede atenderla. Estas unidades no se cobran en el pedido con cupo.")
                .setNegativeButton("Volver", null)
                .setPositiveButton(hasRegular ? "Enviar ambos" : "Enviar solicitud", (dialog, which) -> sendShopping(true));
        if (hasRegular) {
            confirm.setNeutralButton("Solo con cupo", (dialog, which) -> sendShopping(false));
        }
        confirm.show();
    }

    private void sendShopping(boolean includeExtras) {
        if (submittingOrder) return;
        captureDetails();
        try {
            store.placeReviewedShopping(details, catalog().optInt("revision"), includeExtras);
            submittingOrder = true;
            details = store.snapshot().getJSONObject("details");
            step = 0;
            go("orders", "");
            if (syncing) handler.postDelayed(bridgeRefresh, 250);
            else syncDevice(true);
        } catch (Exception error) {
            submittingOrder = false;
            render();
            toast(error.getMessage());
        }
    }

    private boolean deliveryScreen(){return screen.equals("checkout")&&step==0&&details.optString("fulfillment","pickup").equals("delivery");}
    private void requestDeliveryLocation() {
        captureDetails();
        if (!locator.permission()) {
            locator.ask();
            return;
        }
        locating = true;
        render();
        locator.start(new DeliveryLocator.Result() {
            @Override public void failed(String message) {
                locating = false;
                if (deliveryScreen() && !isFinishing() && !isDestroyed()) {
                    render();
                    toast(message);
                }
            }
            @Override public void found(android.location.Location location) {
                locating = false;
                if (!deliveryScreen() || isFinishing() || isDestroyed()) return;
                captureDetails();
                try {
                    JSONObject point = DeliveryDestination.point(location.getLatitude(), location.getLongitude(),
                            location.getAccuracy(), System.currentTimeMillis());
                    setDeliveryPoint(point);
                } catch (Exception error) {
                    render();
                    toast(error.getMessage());
                }
            }
        });
    }

    private void findDeliveryAddress() {
        captureDetails();
        String address = details.optString("address").trim();
        if (address.length() < 4) {
            error("address", "Escribe la dirección que quieres buscar.");
            return;
        }
        String locality = baseSettings().optString("location", "Manchay, Pachacámac");
        locating = true;
        render();
        worker.execute(() -> {
            List<android.location.Address> matches = Collections.emptyList();
            try {
                if (android.location.Geocoder.isPresent()) {
                    List<android.location.Address> results = new android.location.Geocoder(this, new Locale("es", "PE"))
                            .getFromLocationName(address + ", " + locality, 4);
                    if (results != null) matches = results;
                }
            } catch (IOException | RuntimeException ignored) { /* Location remains available as an alternative. */ }
            List<android.location.Address> found = matches;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !deliveryScreen()) return;
                locating = false;
                captureDetails();
                render();
                if (!address.equals(details.optString("address").trim())) return;
                List<android.location.Address> usable = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                for (android.location.Address match : found) {
                    if (!match.hasLatitude() || !match.hasLongitude() || match.getAddressLine(0) == null) continue;
                    usable.add(match);
                    labels.add(match.getAddressLine(0));
                }
                if (usable.isEmpty()) {
                    toast("No encontramos la dirección. Prueba con una dirección más precisa o usa tu ubicación desde el destino.");
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Confirma tu dirección de entrega")
                        .setItems(labels.toArray(new String[0]), (dialog, index) -> {
                            android.location.Address match = usable.get(index);
                            try {
                                setDeliveryPoint(DeliveryDestination.point(match.getLatitude(), match.getLongitude(),
                                        100, System.currentTimeMillis()));
                            } catch (Exception error) {
                                toast(error.getMessage());
                            }
                        }).setNegativeButton("Volver", null).show();
            });
        });
    }

    private void setDeliveryPoint(JSONObject point) throws Exception {
        JSONObject destination = new JSONObject().put("deliveryLocation", point);
        JSONObject zone = DeliveryDestination.verifiedZone(settings(), destination);
        put(details, "deliveryLocation", point);
        put(details, "zoneId", zone.getString("id"));
        put(details, "zone", zone.getString("name"));
        store.details(details);
        render();
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==DeliveryLocator.REQUEST){if(locator.permission()&&deliveryScreen())requestDeliveryLocation();else toast("Puedes buscar la dirección o elegir recojo.");}}
    private void confirmDestination() {
        try {
            JSONObject zone = DeliveryDestination.verifiedZone(settings(), details);
            DeliveryDestination.confirm(details, settings().optString("saleDate"));
            put(details, "zoneId", zone.getString("id"));
            put(details, "zone", zone.getString("name"));
            store.details(details);
            render();
        } catch (Exception error) {
            toast(error.getMessage());
        }
    }
    private void destinationCard(LinearLayout parent,JSONObject value,boolean editable){JSONObject point=value.optJSONObject("deliveryLocation");if(point==null){String address=value.optString("address").trim();if(address.length()<4)return;LinearLayout card=ui.message("DIRECCIÓN DE ENTREGA",DeliveryDestination.typeLabel(value.optString("destinationType","home"))+" · "+value.optString("zone")+"\n"+address+"\nReferencia: "+value.optString("reference"),NativeUi.CARD);add(card,ui.button("Buscar dirección en app de mapas",false,"pin",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(DeliveryDestination.addressGeo(value))));}catch(Exception error){toast("No hay una app de mapas disponible. La dirección escrita se conserva.");}}),10);add(parent,card,12);return;}boolean valid=DeliveryDestination.validPoint(point);String saleDate=editable||screen.equals("checkout")?settings().optString("saleDate"):value.optString("saleDate");boolean confirmed=valid&&DeliveryDestination.problem(value,saleDate).isEmpty();LinearLayout card=ui.message(confirmed?"Punto de entrega guardado":"Revisa el punto de entrega",valid?"Precisión aproximada: "+Math.round(point.optDouble("accuracy"))+" m. "+(confirmed?"Confirmado para esta dirección y fecha.":"Confirma el punto después de cambiar la dirección o fecha."):"Quita este punto y vuelve a obtener tu ubicación.",NativeUi.CARD);if(valid)add(card,ui.button("Ver punto",false,"pin",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(DeliveryDestination.geo(point))));}catch(Exception error){toast("No hay una app de mapas disponible. La dirección escrita se conserva.");}}),10);if(editable){if(valid&&!confirmed)add(card,ui.button("Confirmar punto para esta entrega",false,null,()->{captureDetails();new AlertDialog.Builder(this).setTitle("¿El punto corresponde a esta dirección?").setMessage(details.optString("address")+"\nVenta: "+SaleSchedule.day(settings())).setNegativeButton("Volver",null).setPositiveButton("Sí, corresponde",(d,w)->confirmDestination()).show();}),10);add(card,ui.button("Quitar ubicación",false,null,()->{captureDetails();details.remove("deliveryLocation");try{store.details(details);render();}catch(Exception error){toast("No pudimos guardar el cambio.");}}),8);}add(parent,card,12);}

    private boolean typing(){View focus=getCurrentFocus();return focus instanceof EditText;}
    private void hideKeyboard(){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}
    private boolean validStep() {
        if (step == 0 && deliveryScreen()) {
            try {
                DeliveryDestination.verifiedZone(settings(), details);
            } catch (Exception error) {
                toast(error.getMessage());
                return false;
            }
            if (details.optString("address").length() < 4) {
                error("address", "Completa la dirección de entrega.");
                return false;
            }
            if (details.optString("reference").length() < 3) {
                error("reference", "Agrega una referencia fácil de reconocer.");
                return false;
            }
            if (!DeliveryDestination.problem(details, settings().optString("saleDate")).isEmpty()) {
                new AlertDialog.Builder(this).setTitle("¿El punto corresponde al destino?")
                        .setMessage(details.optString("address") + "\nEl repartidor usará la ubicación confirmada.")
                        .setNegativeButton("Revisar", null)
                        .setPositiveButton("Confirmar punto", (dialog, which) -> {
                            confirmDestination();
                            if (DeliveryDestination.problem(details, settings().optString("saleDate")).isEmpty()) {
                                advanceCheckout();
                            }
                        }).show();
                return false;
            }
        }
        if (step == 1) {
            if (details.optString("customer").length() < 2) {
                error("customer", "Escribe el nombre del pedido.");
                return false;
            }
            if (!SaleRules.validPhone(details.optString("phone"))) {
                error("phone", "Escribe 9 dígitos que empiecen con 9.");
                return false;
            }
        }
        return true;
    }
    private void summaryLine(LinearLayout parent,String label,String value,boolean strong){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView name=ui.text(label,strong?19:15,NativeUi.INK,strong);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView amount=ui.text(value,strong?22:15,NativeUi.WINE,true);amount.setGravity(Gravity.RIGHT);row.addView(amount,new LinearLayout.LayoutParams(-2,-2));add(parent,row,10);}
    private void updateSummary() {
        if (summary == null || !screen.equals("checkout")) return;
        summary.removeAllViews();
        if (store.count() == 0) {
            add(summary, ui.text("Solicitud sin cobro", 16, NativeUi.WINE, true), 0);
            return;
        }
        add(summary, ui.text("TOTAL CON CUPO", 12, NativeUi.WINE, true), 0);
        summaryLine(summary, "Comida", cash(store.subtotal()), false);
        try {
            double fee = store.fee(details);
            summaryLine(summary, "Entrega", details.optString("fulfillment", "pickup").equals("pickup")
                    ? "Recojo gratis" : fee == 0 ? "Gratis por promoción" : cash(fee), false);
            summaryLine(summary, "Total", cash(store.subtotal() + fee), true);
        } catch (Exception missing) {
            summaryLine(summary, "Entrega", "Falta ubicar el destino", false);
        }
    }
    private String orderStatus(JSONObject order){
        String send=order.optString("sendState");if(send.equals("pending")||send.equals("device_pending"))return "Guardado · falta enviar a Emprendedora";if(send.equals("review"))return "Revisa tu solicitud";if(send.equals("legacy")||send.equals("unbound"))return "Revisar antes de enviar";if(send.equals("superseded"))return "Solicitud reemplazada";
        switch(order.optString("status")){case "registered":return "Pedido recibido · espera confirmación";case "confirmed":return "Pedido confirmado por la olla";case "preparing":case "ready":return "En preparación";case "sale_day":return "Día de venta";case "delivering":return "Delivery salió";case "pickup_available":return "Puedes recoger";case "delivered":return "Entregado";case "cancelled":return "Cancelado";default:return "Pendiente de envío";}
    }
    private void showOrders(){
        title("Tus pedidos","Fecha de venta, pago y entrega de tus compras.");add(content,ui.button(syncing?"Actualizando…":"Actualizar pedidos",false,"orders",()->syncDevice(true)),12);JSONArray orders=state().optJSONArray("orders");
        JSONArray extras = state().optJSONArray("extraRequests");
        List<JSONObject> history = new ArrayList<>();
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order != null) history.add(order);
        }
        for (int i = extras == null ? -1 : extras.length() - 1; i >= 0; i--) {
            JSONObject extra = extras.optJSONObject(i);
            if (extra != null) history.add(extra);
        }
        if (history.isEmpty()) {
            empty("Todavía no hay pedidos", "Elige tus productos y revisa la fecha de venta.");
        }
        history.sort(Comparator.comparingLong(this::submittedTime).reversed());
        for (JSONObject order : history) {
            if (order.optBoolean("extra")) {
                addExtraLink(content, order);
                continue;
            }
            LinearLayout card = ui.card();
            boolean sent = "sent".equals(order.optString("sendState"));
            String label = sent ? "Pedido #" : "Solicitud #";
            add(card, ui.text(label + String.format(Locale.US, "%03d", order.optInt("number")),
                    13, NativeUi.CORAL, true), 0);
            add(card, ui.heading(orderStatus(order), 20), 6);
            addSubmittedTime(card, order);
            add(card, ui.text("Venta: " + SaleSchedule.day(order), 14, NativeUi.WINE, true), 6);
            add(card, ui.text(order.optString("customer") + " · " + cash(order.optDouble("total")),
                    16, NativeUi.INK, true), 6);
            String paymentStatus = receiptPaymentStatus(order);
            if (!paymentStatus.isEmpty()) add(card, ui.text(paymentStatus, 13, NativeUi.MUTED, false), 6);
            boolean walletPayment = sent && "Pago pendiente".equals(paymentStatus) && !order.optString("payment").equals("cash");
            add(card, ui.button(walletPayment ? "Ver pedido y pago" : "Ver mi pedido", false, null,
                    () -> go("receipt", order.optString("requestId", order.optString("id")))), 10);
            add(content, card, 14);
        }
    }

    private void addExtraLink(LinearLayout parent, JSONObject extra) {
        LinearLayout card = ui.message("Solicitud de cupos extra", extraItemsLabel(extra), NativeUi.SOFT);
        add(card, ui.text(extraStatus(extra), 14, NativeUi.WINE, true), 6);
        if ("approved".equals(extra.optString("status"))) addExtraQuote(card, extra);
        addSubmittedTime(card, extra);
        add(card, ui.text("Venta: " + SaleSchedule.day(extra), 13, NativeUi.MUTED, false), 4);
        String baseId = extra.optString("baseRequestId");
        JSONArray orders = state().optJSONArray("orders");
        for (int i = 0; extra.optInt("sameDeliveryOrderNumber") <= 0 && !baseId.isEmpty()
                && orders != null && i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order != null && baseId.equals(order.optString("requestId"))
                    && order.optString("sendState").equals("sent")) {
                add(card, ui.text("Pedido asociado #" + order.optInt("number"), 13, NativeUi.MUTED, false), 4);
                break;
            }
        }
        add(card, ui.button("Ver solicitud", false, null, () -> go("extras", extra.optString("id"))), 8);
        add(parent, card, 12);
    }

    private String extraItemsLabel(JSONObject extra) {
        StringBuilder text = new StringBuilder();
        JSONArray items = extra.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (text.length() > 0) text.append("\n");
            text.append(item.optInt("quantity")).append(" × ").append(item.optString("name"));
        }
        return text.toString();
    }

    private void addSubmittedTime(LinearLayout parent, JSONObject entry) {
        long time = submittedTime(entry);
        if (time <= 0) return;
        add(parent, ui.text("Hecho el " + SaleSchedule.display(SaleSchedule.stamp(time), false),
                13, NativeUi.MUTED, false), 5);
    }

    private long submittedTime(JSONObject entry) {
        return entry.optLong("submittedAt", entry.optLong("createdAt"));
    }

    private JSONObject extraRequest(String id) {
        JSONArray extras = state().optJSONArray("extraRequests");
        for (int i = 0; extras != null && i < extras.length(); i++) {
            JSONObject extra = extras.optJSONObject(i);
            if (extra != null && id.equals(extra.optString("id"))) return extra;
        }
        return null;
    }

    private String extraStatus(JSONObject extra) {
        if ("approved".equals(extra.optString("status"))) return "Aceptada";
        if ("rejected".equals(extra.optString("status"))) return "La olla no puede atender esta solicitud";
        return "sent".equals(extra.optString("sendState")) ? "Por confirmar · sin cobro"
                : "Guardada · pendiente de enviar";
    }

    private void addExtraQuote(LinearLayout parent, JSONObject extra) {
        double amount = extra.optDouble("extraAmount");
        String payment = !Double.isFinite(amount) ? "Monto por coordinar"
                : amount == 0 ? "Extras sin costo" : "Monto adicional: " + cash(amount);
        add(parent, ui.text(payment, 16, NativeUi.WINE, true), 6);
        int linkedOrder = extra.optInt("sameDeliveryOrderNumber");
        add(parent, ui.text(linkedOrder > 0 ? "Se entrega junto al pedido #" + linkedOrder
                : "Entrega por coordinar con la olla", 14, NativeUi.INK, false), 4);
    }

    private void showExtrasReceipt() {
        JSONObject extra = extraRequest(selectedId);
        if (extra == null) {
            empty("Solicitud no disponible", "Vuelve a la lista de pedidos.");
            primary("Ver mis pedidos", () -> go("orders", ""));
            return;
        }
        title("Cupos extra", "");
        add(content, ui.message("Estado de la solicitud", extraStatus(extra), NativeUi.GOLD), 12);
        if ("approved".equals(extra.optString("status"))) {
            LinearLayout quote = ui.card();
            addExtraQuote(quote, extra);
            add(content, quote, 8);
        }
        addSubmittedTime(content, extra);
        add(content, ui.text(SaleSchedule.day(extra), 14, NativeUi.WINE, true), 8);
        JSONArray items = extra.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) add(content, ui.text(item.optInt("quantity") + " × " + item.optString("name"),
                    16, NativeUi.INK, true), 8);
        }
        if (!extra.optString("baseRequestId").isEmpty()) {
            note("El total del pedido con cupo se mantiene por separado.");
        }
        if ("approved".equals(extra.optString("status")) && extra.optDouble("extraAmount") > 0) {
            note("Coordina el pago de los extras con la olla.");
        }
        String contact = baseSettings().optString("phone");
        if ("approved".equals(extra.optString("status")) && SaleRules.validPhone(contact)) {
            add(content, ui.button("Llamar a la olla", false, null, () -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contact)));
                } catch (ActivityNotFoundException error) {
                    toast("No hay una app de llamadas disponible.");
                }
            }), 10);
        }
        if ("pending".equals(extra.optString("status"))) {
            boolean sent = "sent".equals(extra.optString("sendState"));
            note(sent ? "Consulta si la olla ya aceptó o rechazó estas unidades."
                    : "La solicitud se guardó en el celular. Puedes intentar enviarla otra vez.");
            primary(sent ? "Consultar respuesta" : "Reintentar envío", () -> consultExtraRequest(extra));
        } else {
            primary("Volver a mis pedidos", () -> go("orders", ""));
        }
    }

    private void consultExtraRequest(JSONObject request) {
        if (syncing) {
            toast("Espera a que termine la actualización.");
            return;
        }
        syncing = true;
        boolean sent = "sent".equals(request.optString("sendState"));
        worker.execute(() -> {
            String message;
            try {
                JSONObject reply = exchangeExtraRequest(request);
                switch (reply.optString("status")) {
                    case "approved": message = "La olla aceptó tu solicitud."; break;
                    case "rejected": message = "La olla no puede atender tu solicitud."; break;
                    default: message = sent ? "Tu solicitud sigue por confirmar." : "Solicitud enviada a Emprendedora.";
                }
            } catch (Exception error) {
                message = extraBridgeError(error, sent);
            }
            String result = message;
            runOnUiThread(() -> {
                syncing = false;
                if (isFinishing() || isDestroyed()) return;
                if (screen.equals("extras") && selectedId.equals(request.optString("id"))) render();
                toast(result);
            });
        });
    }

    private JSONObject exchangeExtraRequest(JSONObject request) throws Exception {
        String id = request.optString("id");
        JSONObject reply = "sent".equals(request.optString("sendState"))
                ? DeviceBridgeClient.extraRequest(this, id) : DeviceBridgeClient.requestMore(this, request);
        store.acceptExtraReply(id, reply);
        return reply;
    }

    private String extraBridgeError(Exception error, boolean sent) {
        String detail = error.getMessage();
        if (error instanceof SecurityException || (detail != null && detail.contains("Opción no disponible"))) {
            return "Instala las versiones actualizadas de Pedidos y Emprendedora en este celular.";
        }
        if (detail != null && !detail.isEmpty() && !detail.equals("No pudimos intercambiar los datos.")) {
            return detail;
        }
        return sent ? "No pudimos consultar la respuesta. Prueba otra vez."
                : "No pudimos enviar la solicitud. Sigue guardada en este celular.";
    }

    private JSONObject currentOrder(){JSONArray orders=state().optJSONArray("orders");for(int i=0;i<orders.length();i++)if(orders.optJSONObject(i).optString("id").equals(selectedId)||orders.optJSONObject(i).optString("requestId").equals(selectedId))return orders.optJSONObject(i);return null;}
    private void showReceipt(){
        JSONObject order=currentOrder();if(order==null){go("orders","");return;}String send=order.optString("sendState");boolean accepted=send.equals("sent");String status=order.optString("status");
        String subtitle=!accepted?"Se conserva en este celular. El cupo todavía no está confirmado.":status.equals("registered")?"La olla recibió tu pedido. Espera la confirmación para darlo por aceptado.":status.equals("cancelled")?"La olla canceló este pedido. Revisa el detalle antes de volver a pedir.":"La olla confirmó tu pedido.";
        add(content,ui.message(orderStatus(order),subtitle,NativeUi.GOLD),0);
        if (accepted && !status.equals("registered") && !status.equals("cancelled") && !order.optBoolean("paid")) {
            if (order.optString("payment").equals("cash")) note("Paga " + cash(order.optDouble("total")) + " al recibir.");
            else showWalletPayment(order);
        }
        JSONObject extra = extraRequest(order.optString("extraRequestId"));
        if (extra != null) addExtraLink(content, extra);
        add(content,ui.heading((accepted?"Pedido ":"Solicitud ")+String.format(Locale.US,"%03d",order.optInt("number")),25),19);
        addSubmittedTime(content, order);
        LinearLayout food=ui.card();JSONArray items=order.optJSONArray("items");for(int i=0;items!=null&&i<items.length();i++){JSONObject item=items.optJSONObject(i);summaryLine(food,item.optInt("quantity")+" × "+item.optString("name"),cash(item.optInt("quantity")*item.optDouble("price")),false);}summaryLine(food,"Entrega",cash(order.optDouble("fee")),false);summaryLine(food,"Total",cash(order.optDouble("total")),true);add(content,food,14);
        String place=order.optString("fulfillment","pickup").equals("delivery")?DeliveryDestination.typeLabel(order.optString("destinationType","home"))+" · "+order.optString("zone")+" · "+order.optString("address")+"\nReferencia: "+order.optString("reference"):order.optString("pickup","Manka Kusi · Manchay");
        add(content,ui.message("DÍA DE VENTA",SaleSchedule.day(order),NativeUi.GOLD),12);if(!order.optString("fulfillment","pickup").equals("delivery"))add(content,ui.message("PUEDES RECOGER",SaleSchedule.window(order),NativeUi.CARD),12);
        add(content,ui.message("ENTREGA",place,NativeUi.CARD),12);if(order.optString("fulfillment").equals("delivery")){destinationCard(content,order,false);if(!order.optString("driver").isEmpty())note("Reparte: "+order.optString("driver"));if(!order.optString("deliveryWindow").isEmpty())note("Horario acordado: "+order.optString("deliveryWindow"));}
        String paymentStatus = receiptPaymentStatus(order);
        if (!paymentStatus.isEmpty()) add(content, ui.message(paymentStatus,
                order.optBoolean("paid") ? "La olla registró el dinero recibido." : "", NativeUi.SOFT), 12);
        add(content,ui.button("Volver a pedir en 1 toque",false,"cart",()->{try{store.reorderToCart(order.optString("id"));details=store.snapshot().optJSONObject("details");go("cart","");toast("Productos cargados al carrito.");}catch(Exception error){toast(error.getMessage());}}),12);
        if(send.equals("review")){note(order.optString("syncError","Cambió la disponibilidad. Revisa antes de crear otra solicitud."));}
        if(Arrays.asList("review","legacy","unbound").contains(send))add(content,ui.button("Revisar productos y datos",false,null,()->{try{store.restoreToCart(order.optString("id"));details=store.snapshot().optJSONObject("details");go("cart","");}catch(Exception error){toast(error.getMessage());}}),14);
        String contact=settings().optString("phone");if(SaleRules.validPhone(contact)){add(content,ui.button("Escribir por WhatsApp",false,"share",()->openWhatsApp(contact,order)),12);add(content,ui.button("Llamar a la olla",false,null,()->{try{startActivity(new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:"+contact)));}catch(Exception error){toast("No hay una app de llamadas disponible.");}}),10);}
        primary((syncing||submittingOrder)?"Enviando…":send.equals("sent")?"Actualizar pedido":"Reintentar envío",()->syncDevice(true));
    }
    private String receiptPaymentStatus(JSONObject order) {
        if (order.optBoolean("paid")) return "Pago confirmado";
        String status = order.optString("status");
        if (status.equals("cancelled")) return "";
        if (!order.optString("sendState").equals("sent") || status.equals("registered")) return "Cobro tras confirmar";
        return "Pago pendiente";
    }

    private void showWalletPayment(JSONObject order) {
        JSONObject shop = baseSettings();
        String encoded = shop.optString("paymentImage");
        String method = order.optString("payment");
        String wallet = "yape".equals(method) ? "Yape" : "Plin";
        String phone = shop.optString("walletPhone");
        String holder = shop.optString("walletHolder", shop.optString("name"));
        String amount = cash(order.optDouble("total"));
        LinearLayout card = ui.message("Paga con " + wallet, "Monto exacto: " + amount, NativeUi.CARD);
        if (!encoded.isEmpty()) {
            TextView showQr = ui.button("Ver QR de pago", true, "qr", () -> {
                Bitmap bitmap = ImageTools.decode(encoded);
                if (bitmap == null) {
                    toast("No pudimos abrir el QR de la olla.");
                    return;
                }
                ImageView image = new ImageView(this);
                image.setImageBitmap(bitmap);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setBackgroundColor(Color.WHITE);
                image.setContentDescription("QR de pago de " + holder);
                LinearLayout view = ui.column();
                view.addView(image, new LinearLayout.LayoutParams(-1, dp(280)));
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle(wallet + " · " + amount)
                        .setView(view).setPositiveButton("Cerrar", null).create();
                dialog.setOnDismissListener(ignored -> {
                    image.setImageDrawable(null);
                    bitmap.recycle();
                });
                dialog.show();
            });
            showQr.setTag("receipt:qr");
            add(card, showQr, 8);
        }
        if (SaleRules.validPhone(phone)) {
            add(card, ui.button("Copiar número " + phone, false, "pay", () -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(wallet, phone));
                toast("Número copiado. Monto: " + amount);
            }), 8);
        }
        if (!encoded.isEmpty() || SaleRules.validPhone(phone)) {
            add(card, ui.text("Titular: " + holder, 14, NativeUi.MUTED, false), 8);
            add(card, ui.button("Abrir " + wallet, false, "pay", () -> openWalletApp(method)), 8);
        } else {
            add(card, ui.text("La olla todavía no compartió sus datos de pago. Coordina con ella antes de transferir.",
                    14, NativeUi.MUTED, false), 8);
        }
        add(content, card, 12);
    }

    private boolean stockConflict(String message){String m=message==null?"":message.toLowerCase(Locale.ROOT);return m.contains("cupo")||m.contains("stock")||m.contains("agot");}
    private void openWalletApp(String method){
        String[] packages="yape".equals(method)?new String[]{"com.bcp.innovacxion.yapeapp"}:new String[]{"com.interbank.plin","com.bbva.nxt_peru","com.scotiabank.android"};
        for(String pkg:packages){
            Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch!=null){startActivity(launch);return;}
        }
        toast("Abre tu app de "+("yape".equals(method)?"Yape":"Plin")+" manualmente e ingresa el número y monto mostrado.");
    }
    private void openWhatsApp(String phone,JSONObject order){
        String digits=phone.replaceAll("[^0-9]","");if(digits.length()==9)digits="51"+digits;
        String text="Hola, acabo de hacer el pedido N.º "+String.format(Locale.US,"%03d",order.optInt("number"))+" ("+cash(order.optDouble("total"))+") en ManchApp.";
        try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://wa.me/"+digits+"?text="+android.net.Uri.encode(text))));}
        catch(Exception error){toast("No pudimos abrir WhatsApp en este equipo.");}
    }
    private void registerBridgeObserver(){if(bridgeObserver!=null)return;bridgeObserver=new ContentObserver(handler){@Override public void onChange(boolean selfChange){handler.removeCallbacks(bridgeRefresh);handler.postDelayed(bridgeRefresh,250);}};getContentResolver().registerContentObserver(BRIDGE,true,bridgeObserver);}
    private void unregisterBridgeObserver(){if(bridgeObserver==null)return;getContentResolver().unregisterContentObserver(bridgeObserver);bridgeObserver=null;handler.removeCallbacks(bridgeRefresh);}
    private void syncDevice(boolean notify) {
        if (syncing || store == null) return;
        captureDetails();
        syncing = true;
        String before = store.snapshot().toString();
        worker.execute(() -> {
            String problem = "";
            try {
                store.acceptDeviceCatalog(DeviceBridgeClient.catalog(this));
                JSONArray pending = store.pending();
                for (int i = 0; i < pending.length(); i++) {
                    JSONObject order = pending.getJSONObject(i);
                    String id = order.optString("id");
                    try {
                        store.acceptOrder(id, DeviceBridgeClient.placeOrder(this, order));
                    } catch (Exception error) {
                        String message = error.getMessage() == null ? "No pudimos enviar el pedido." : error.getMessage();
                        if (stockConflict(message) || message.startsWith("Cambió la fecha")) {
                            store.markProblem(id, message + " Puedes revisar los productos y solicitar lo que falte.");
                        }
                        problem = message;
                    }
                }

                JSONArray orders = store.snapshot().getJSONArray("orders");
                for (int i = 0; i < orders.length(); i++) {
                    JSONObject order = orders.getJSONObject(i);
                    String status = order.optString("status");
                    if (!order.optString("sendState").equals("sent") || status.equals("delivered") || status.equals("cancelled")) continue;
                    store.acceptOrder(order.optString("id"), DeviceBridgeClient.order(this, order.optString("id")));
                }
                store.acceptDeviceCatalog(DeviceBridgeClient.catalog(this));
                online = true;
            } catch (Exception error) {
                online = false;
                problem = error.getMessage() == null ? "No encontramos Emprendedora en este celular." : error.getMessage();
            }
            JSONArray requests = store.extraRequestsToSync();
            for (int i = 0; i < requests.length(); i++) {
                JSONObject request = requests.optJSONObject(i);
                try {
                    exchangeExtraRequest(request);
                } catch (Exception error) {
                    if (problem.isEmpty()) problem = extraBridgeError(error, "sent".equals(request.optString("sendState")));
                }
            }
            String message = problem;
            runOnUiThread(() -> {
                syncing = false;
                boolean justSubmitted = submittingOrder;
                submittingOrder = false;
                syncMessage = message;
                if (isFinishing() || isDestroyed()) return;
                boolean changed = !store.snapshot().toString().equals(before);
                if (!typing() && (notify || changed || justSubmitted)) render();
                if (notify && !message.isEmpty()) toast(message);
            });
        });
    }
}
