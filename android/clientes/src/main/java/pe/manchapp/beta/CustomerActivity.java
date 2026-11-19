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
    private JSONObject settings(){JSONObject selected=product(selectedId);if(selected!=null)return SaleSchedule.forProduct(selected,baseSettings());if(store.count()>0)return store.currentSale();return baseSettings();}
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
    private String activeTab(){if(screen.equals("detail"))return "food";if(screen.equals("checkout"))return "cart";if(screen.equals("receipt"))return "orders";return screen;}
    private boolean rootScreen(){return Arrays.asList("food","offers","cart","orders").contains(screen);}
    private boolean isOffline(){try{android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null)return false;android.net.NetworkInfo info=cm.getActiveNetworkInfo();return info==null||!info.isConnected();}catch(Exception ignored){return false;}}
    private void render(){
        int prevScrollY=scroll!=null&&screen.equals(lastRenderedScreen)?scroll.getScrollY():0;
        handler.removeCallbacks(saveDetailsDraft);viewState=store.snapshot();fields.clear();root=ui.column();root.setBackgroundColor(NativeUi.BG);setContentView(root);
        String heading=rootScreen()?"ManchApp":screen.equals("detail")?"Tu antojo":screen.equals("checkout")?"Arma tu pedido":"Tu pedido";
        String seller=baseSettings()==null?"":baseSettings().optString("name").trim();
        root.addView(ui.toolbar(heading,rootScreen()?"PEDIDOS · "+(seller.isEmpty()?"MANCHAPP":seller.toUpperCase(Locale.ROOT)):"",rootScreen()?null:()->onBackPressed(),null));
        int pendingCount=store.pending().length();boolean offline=isOffline();
        if(offline||!syncMessage.isEmpty()||pendingCount>0){
            String msg=offline?"Sin internet · pedidos guardados localmente":!syncMessage.isEmpty()?syncMessage:pendingCount+" pedido(s) por enviar a Emprendedora";
            LinearLayout bar=new LinearLayout(this);bar.setBackgroundColor(offline||!syncMessage.isEmpty()?NativeUi.GOLD:NativeUi.SOFT);bar.setPadding(dp(16),dp(6),dp(16),dp(6));bar.setGravity(Gravity.CENTER_VERTICAL);
            TextView label=ui.text(msg,12,NativeUi.INK,true);bar.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            if(pendingCount>0||!syncMessage.isEmpty()){TextView retry=ui.text(syncing?"…":"Reintentar",12,NativeUi.WINE,true);retry.setPadding(dp(10),dp(4),0,dp(4));retry.setOnClickListener(v->syncDevice(true));bar.addView(retry);}
            root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        }
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=ui.column();content.setPadding(dp(16),dp(16),dp(16),dp(22));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(prevScrollY>0){final ScrollView activeScroll=scroll;activeScroll.post(()->activeScroll.scrollTo(0,prevScrollY));}
        lastRenderedScreen=screen;
        footer=ui.column();footer.setPadding(dp(16),dp(8),dp(16),dp(9));footer.setVisibility(View.GONE);root.addView(footer);
        switch(screen){case "food":showFood();break;case "offers":showOffers();break;case "cart":showCart();break;case "detail":showProduct();break;case "checkout":showCheckout();break;case "orders":showOrders();break;case "receipt":showReceipt();break;default:screen="food";showFood();}
        int count=store.count();root.addView(ui.navigation(new String[]{"food","offers","cart","orders"},new String[]{"Comida","Ofertas",count>0?"Carrito · "+count:"Carrito","Pedidos"},new String[]{"food","tag","cart","orders"},activeTab(),id->go(id,"")));
    }
    private ImageView image(JSONObject product,int edge){ImageView view=new ImageView(this);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setBackground(ui.background(0xffeabe83,0,15));view.setClipToOutline(true);Bitmap bitmap=images.productThumbnail(this,product,dp(edge));if(bitmap!=null)view.setImageBitmap(bitmap);else view.setImageDrawable(new DishArt(product.optString("illustration","papa")));view.setContentDescription((bitmap==null?"Ilustración referencial de ":"Foto de ")+product.optString("name"));return view;}
    private boolean accepting(){return SaleSchedule.unavailable(settings(),System.currentTimeMillis()).isEmpty();}
    private boolean accepting(JSONObject product){return SaleSchedule.unavailable(SaleSchedule.forProduct(product,baseSettings()),System.currentTimeMillis()).isEmpty();}
    private void saleCard(){JSONObject saleSettings=settings();String closed=SaleSchedule.unavailable(saleSettings,System.currentTimeMillis());LinearLayout sale=ui.message("VENTA PROGRAMADA",SaleSchedule.day(saleSettings),NativeUi.GOLD);add(sale,ui.text(SaleSchedule.shortWindow(saleSettings),14,NativeUi.INK,true),9);add(sale,ui.text(closed.isEmpty()?"Reservas hasta "+SaleSchedule.deadline(saleSettings):closed,14,NativeUi.WINE,true),9);add(content,sale,13);}
    private void showFood(){
        nextSaleBanner();
        add(content,ui.heading("Productos de Manka Kusi",27),14);add(content,ui.text("Reserva los productos anunciados para cada fecha de venta.",14,NativeUi.MUTED,false),5);
        announcedProducts();
    }
    private void nextSaleBanner(){
        JSONObject s=baseSettings();if(s==null)return;
        String closed=SaleSchedule.unavailable(s,System.currentTimeMillis());
        LinearLayout banner=ui.message("PRÓXIMA VENTA · "+SaleSchedule.day(s),
                SaleSchedule.shortWindow(s)+"\n"+(closed.isEmpty()?"Reservas abiertas hasta "+SaleSchedule.deadline(s):closed),
                NativeUi.GOLD);
        LinearLayout actions=new LinearLayout(this);
        TextView remind=ui.button("Recordar",false,"clock",()->addSaleReminder(s));remind.setTextSize(13);
        TextView offersBtn=ui.button("Ver ofertas",false,"tag",()->go("offers",""));offersBtn.setTextSize(13);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(8);
        actions.addView(remind,lp);actions.addView(offersBtn,new LinearLayout.LayoutParams(0,-2,1));
        add(banner,actions,10);add(content,banner,0);
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
    private void announcedProducts(){
        categoryFilter();
        JSONArray products=catalog().optJSONArray("products");int announced=0,shown=0;long now=System.currentTimeMillis();for(int i=0;i<products.length();i++){JSONObject p=products.optJSONObject(i);if(!p.optString("line").equals("commercial")||!SaleSchedule.announced(p,baseSettings(),now))continue;announced++;if(!category.equals("Todo")&&!p.optString("category").equals(category))continue;productCard(p);shown++;}
        if(announced==0)empty("Por ahora no se ha anunciado ninguna venta","Vuelve más tarde. Gracias.");else if(shown==0)empty("No hay productos de este tipo","Elige otro tipo para ver las ventas anunciadas.");
    }
    private void categoryFilter(){
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterRow.setBackground(ui.background(NativeUi.CARD, NativeUi.LINE, 14));
        filterRow.setPadding(dp(16), dp(13), dp(12), dp(13));
        filterRow.setOnClickListener(v -> {
            ArrayList<String> list=new ArrayList<>(Arrays.asList("Todo","Salados","Antojitos","Postres","Bebidas","Combos"));
            JSONArray saved=baseSettings()==null?null:baseSettings().optJSONArray("categories");
            if(saved!=null)for(int i=0;i<saved.length();i++){String value=saved.optString(i);if(!value.isEmpty()&&!list.contains(value))list.add(value);}
            String[] categories=list.toArray(new String[0]);
            new AlertDialog.Builder(this).setTitle("Elegir categoría").setItems(categories, (d, i) -> {
                category = categories[i];
                render();
            }).show();
        });
        ui.buttonRole(filterRow);
        TextView filterLabel = ui.text("Categoría: ", 14, NativeUi.MUTED, false);
        filterRow.addView(filterLabel);
        TextView filterValue = ui.text(category, 16, NativeUi.WINE, true);
        filterRow.addView(filterValue, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(android.R.drawable.arrow_down_float);
        chevron.setColorFilter(NativeUi.WINE);
        filterRow.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        add(content, filterRow, 16);
    }
    private void productCard(JSONObject p){
        LinearLayout card=ui.card();card.setPadding(dp(12),dp(12),dp(12),dp(12));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(image(p,112),new LinearLayout.LayoutParams(dp(112),dp(112)));
        JSONObject schedule=SaleSchedule.forProduct(p,baseSettings());LinearLayout copy=ui.column();copy.setPadding(dp(12),0,0,0);add(copy,ui.text(p.optString("category").toUpperCase(new Locale("es")),10,NativeUi.CORAL,true),0);add(copy,ui.heading(p.optString("name"),19),4);String portion=p.optString("portion");if(!portion.isEmpty())add(copy,ui.text(portion,13,NativeUi.INK,false),4);add(copy,ui.text("Venta: "+SaleSchedule.day(schedule),12,NativeUi.MUTED,false),6);if(!ImageTools.hasProductPhoto(p))add(copy,ui.text("Imagen referencial",10,NativeUi.MUTED,false),5);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);LinearLayout price=ui.column();price.addView(ui.text(cash(p.optDouble("price")),23,NativeUi.WINE,true));price.addView(ui.text(unit(p),12,NativeUi.MUTED,false));bottom.addView(price,new LinearLayout.LayoutParams(0,-2,1));boolean available=p.optBoolean("available")&&p.optInt("stock")>0;int quantity=state().optJSONObject("cart").optInt(p.optString("id"));TextView action=ui.button(!accepting(p)?"Cerrado":available?(quantity>0?"Agregar · "+quantity:"Reservar"):"Sin cupos",true,null,()->changeQuantity(p,1,false));action.setTextSize(14);action.setEnabled(accepting(p)&&available&&quantity<p.optInt("stock"));action.setAlpha(action.isEnabled()?1f:.55f);bottom.addView(action,new LinearLayout.LayoutParams(dp(112),-2));add(card,bottom,10);card.setOnClickListener(v->go("detail",p.optString("id")));ui.buttonRole(card);add(content,card,13);
    }
    private void changeQuantity(JSONObject p,int delta,boolean wholeScreen){try{int quantity=state().optJSONObject("cart").optInt(p.optString("id"));store.quantity(p.optString("id"),quantity+delta);int y=scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));if(!wholeScreen)toast("Agregado: "+p.optString("name"));}catch(Exception error){toast(error.getMessage());}}
    private void showProduct(){JSONObject p=product(selectedId);if(p==null||!p.optString("line").equals("commercial")){go("food","");return;}ImageView photo=image(p,320);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);add(content,photo,0);photo.getLayoutParams().height=dp(215);if(!ImageTools.hasProductPhoto(p))note("Ilustración referencial.");add(content,ui.heading(p.optString("name"),28),16);add(content,ui.text(cash(p.optDouble("price"))+" "+unit(p),23,NativeUi.WINE,true),6);if(!p.optString("portion").isEmpty())add(content,ui.text(p.optString("portion"),17,NativeUi.INK,true),13);note(p.optString("description"));saleCard();add(content,ui.message("Recojo en Manka Kusi",SaleSchedule.window(settings()),NativeUi.CARD),15);note("También puedes pedir delivery dentro de las zonas cercanas habilitadas.");if(accepting(p)&&p.optBoolean("available")&&p.optInt("stock")>0)primary("Reservar en el carrito",()->changeQuantity(p,1,true));else note("Este producto no está disponible por ahora.");}
    private void showOffers(){title("Ofertas de Manka Kusi","Combos anunciados para las próximas jornadas.");announcedProducts();double from=baseSettings().optDouble("deliveryFreeFrom",0);if(from>0)add(content,ui.message("Reparto gratis desde "+cash(from),"Solo dentro de la cobertura habilitada.",NativeUi.SOFT),16);}
    private void empty(String title,String detail){add(content,ui.message(title,detail,NativeUi.GOLD),18);}
    private void showCart(){title("Tu carrito","Reservas para una misma jornada de venta.");saleCard();if(store.cartNeedsReview())note("Tu carrito corresponde a otra jornada. Vacíalo y vuelve a elegir los productos para esta fecha; tus pedidos anteriores se conservan.");if(store.count()==0){empty("Tu primer antojo te espera","Elige productos para la próxima venta o agrega un combo.");add(content,ui.button("Ver la comida",true,"food",()->go("food","")),18);return;}JSONObject cart=state().optJSONObject("cart");java.util.Iterator<String> ids=cart.keys();while(ids.hasNext()){JSONObject p=product(ids.next());if(p==null)continue;int quantity=cart.optInt(p.optString("id"));LinearLayout c=ui.card();add(c,ui.text(p.optString("name"),19,NativeUi.INK,true),0);add(c,ui.text(cash(p.optDouble("price"))+" "+unit(p),14,NativeUi.MUTED,false),5);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView minus=ui.button("−",false,null,()->changeQuantity(p,-1,true));minus.setContentDescription("Quitar uno de "+p.optString("name"));row.addView(minus,new LinearLayout.LayoutParams(dp(54),dp(50)));TextView amount=ui.text(String.valueOf(quantity),21,NativeUi.INK,true);amount.setGravity(Gravity.CENTER);row.addView(amount,new LinearLayout.LayoutParams(dp(44),-2));TextView plus=ui.button("+",false,null,()->changeQuantity(p,1,true));plus.setContentDescription("Agregar uno de "+p.optString("name"));plus.setEnabled(accepting()&&!store.cartNeedsReview()&&quantity<p.optInt("stock"));row.addView(plus,new LinearLayout.LayoutParams(dp(54),dp(50)));TextView price=ui.text(cash(quantity*p.optDouble("price")),19,NativeUi.WINE,true);price.setGravity(Gravity.RIGHT);row.addView(price,new LinearLayout.LayoutParams(0,-2,1));add(c,row,12);add(content,c,13);}add(content,ui.message("Comida: "+cash(store.subtotal()),"El reparto se calcula al elegir el sector. El recojo no tiene costo.",NativeUi.GOLD),16);add(content,ui.button("Vaciar carrito",false,null,()->{try{store.clearCart();render();}catch(Exception error){toast("No pudimos vaciar el carrito. Intenta otra vez.");}}),12);if(accepting()&&!store.cartNeedsReview())primary("Elegir cómo lo recibo",()->{step=0;go("checkout","");});}

    private EditText field(String key,String label,String hint,boolean phone){
        TextView name=ui.text(label,15,NativeUi.INK,true);add(content,name,16);EditText input=new EditText(this);input.setId(View.generateViewId());name.setLabelFor(input.getId());input.setTextSize(17);input.setTypeface(ui.regular);input.setTextColor(NativeUi.INK);input.setHintTextColor(NativeUi.MUTED);input.setHint(hint);input.setPadding(dp(14),dp(13),dp(14),dp(13));input.setMinHeight(dp(54));input.setBackground(ui.background(NativeUi.CARD,NativeUi.LINE,14));input.setInputType(phone?InputType.TYPE_CLASS_PHONE:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);if(phone)input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(9)});if(Arrays.asList("address","reference","meeting").contains(key)){input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});input.setMinLines(key.equals("address")?1:2);input.setMaxLines(4);input.setGravity(Gravity.TOP);}input.setText(details.optString(key));input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(android.text.Editable s){handler.removeCallbacks(saveDetailsDraft);handler.postDelayed(saveDetailsDraft,500);}});fields.put(key,input);add(content,input,6);return input;
    }
    private void captureDetails(){if(details==null||store==null)return;for(Map.Entry<String,EditText> field:fields.entrySet())put(details,field.getKey(),field.getValue().getText().toString().trim());try{store.details(details);}catch(Exception error){toast("No pudimos conservar tus datos. Intenta otra vez antes de salir.");}}
    private void error(String field,String message){EditText input=fields.get(field);if(input!=null){input.setError(message);input.requestFocus();input.announceForAccessibility(message);}else toast(message);}
    private void choices(String[] keys,String[] labels,String selected,java.util.function.Consumer<String> action){LinearLayout row=new LinearLayout(this);for(int i=0;i<keys.length;i++){String key=keys[i];boolean active=key.equals(selected);TextView choice=ui.button(labels[i],false,null,()->action.accept(key));choice.setTextSize(14);choice.setSelected(active);choice.setTextColor(active?NativeUi.CARD:NativeUi.WINE);choice.setBackground(ui.background(active?NativeUi.WINE:NativeUi.CARD,active?0:NativeUi.LINE,14));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(i==0?0:4),0,dp(i==keys.length-1?0:4),0);row.addView(choice,params);}add(content,row,12);}
    private void showCheckout(){
        if(store.count()==0){empty("Tu carrito está vacío","Agrega algo de la comida antes de continuar.");primary("Elegir comida",()->go("food",""));return;}
        if(!accepting()){saleCard();primary("Volver al carrito",()->go("cart",""));return;}String[] names={"Cómo recibirlo","Tus datos","Revisar"};add(content,ui.heading(names[step],24),0);add(content,ui.text("Venta: "+SaleSchedule.day(settings()),13,NativeUi.WINE,true),6);LinearLayout steps=new LinearLayout(this);for(int i=0;i<3;i++){TextView label=ui.text((i+1)+". "+new String[]{"Entrega","Datos","Revisar"}[i],12,i==step?NativeUi.CARD:NativeUi.WINE,true);label.setPadding(dp(8),dp(9),dp(8),dp(9));label.setGravity(Gravity.CENTER);label.setBackground(ui.background(i==step?NativeUi.WINE:NativeUi.CARD,0,10));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.rightMargin=dp(5);steps.addView(label,params);}add(content,steps,12);
        if(step==0){
            choices(new String[]{"pickup","delivery"},new String[]{"En la olla","Con delivery"},details.optString("fulfillment","pickup"),key->{captureDetails();put(details,"fulfillment",key);render();});
            if(details.optString("fulfillment","pickup").equals("pickup")){add(content,ui.message("En la olla",settings().optString("pickup")+"\n\n"+SaleSchedule.window(settings()),NativeUi.CARD),16);note("Acércate dentro del intervalo acordado cuando la olla confirme tu reserva.");}
            else{
                add(content,ui.text("¿Dónde recibirás?",15,NativeUi.INK,true),18);choices(new String[]{"home","work","other"},new String[]{"Casa","Trabajo","Otro lugar"},details.optString("destinationType","home"),key->{captureDetails();put(details,"destinationType",key);render();});note("Elige el lugar de entrega. El sector y la dirección deben corresponder a ese lugar, no a donde estás ahora.");
                add(content,ui.text("Sector de entrega",15,NativeUi.INK,true),18);JSONArray zones=settings().optJSONArray("zones");List<String> labels=new ArrayList<>(),ids=new ArrayList<>(),namesList=new ArrayList<>();labels.add("Elige tu sector");ids.add("");namesList.add("");for(int i=0;zones!=null&&i<zones.length();i++){JSONObject zone=zones.optJSONObject(i);if(!zone.optBoolean("enabled"))continue;double price=SaleRules.deliveryFee(store.subtotal(),zone.optDouble("fee"),settings().optDouble("deliveryFreeFrom",0));labels.add(zone.optString("name")+" · "+(price==0?"Gratis":cash(price)));ids.add(zone.optString("id"));namesList.add(zone.optString("name"));}
                Spinner picker=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels){@Override public View getView(int position,View convert,android.view.ViewGroup parent){TextView view=(TextView)super.getView(position,convert,parent);view.setTypeface(ui.regular);view.setTextColor(NativeUi.INK);view.setTextSize(16);return view;}};picker.setAdapter(adapter);picker.setBackground(ui.background(NativeUi.CARD,NativeUi.LINE,12));picker.setMinimumHeight(dp(54));picker.setContentDescription("Sector de entrega");picker.setSelection(Math.max(0,ids.indexOf(details.optString("zoneId"))));picker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){put(details,"zoneId",ids.get(position));put(details,"zone",namesList.get(position));updateSummary();}public void onNothingSelected(android.widget.AdapterView<?> parent){}});add(content,picker,6);
                field("address","Dirección de entrega","Sector, manzana, lote, calle o punto acordado",false);field("reference","Referencias para llegar","Casa verde, al pie de las escaleras, frente a la bodega",false);TextView gps=ui.button(locating?"Buscando ubicación…":"Usar mi ubicación actual",false,"pin",()->requestDeliveryLocation());gps.setEnabled(!locating);add(content,gps,16);note("Úsalo solo si ya estás en el lugar de entrega. Si pides para casa o trabajo desde otro lugar, escribe la dirección y referencias.");destinationCard(content,details,true);note("Reparto el "+SaleSchedule.day(settings())+". La olla confirmará la hora, el acceso y el punto de encuentro.");
            }
        }else if(step==1){field("customer","Tu nombre","¿A nombre de quién será el pedido?",false);field("phone","Celular de contacto","9 dígitos, empieza con 9",true);add(content,ui.heading("¿Cómo prefieres pagar?",21),22);String mode=settings().optString("paymentMode","both");String[] payKeys="cash".equals(mode)?new String[]{"cash"}:"yape".equals(mode)?new String[]{"cash","yape"}:"plin".equals(mode)?new String[]{"cash","plin"}:new String[]{"cash","yape","plin"};String[] payLabels="cash".equals(mode)?new String[]{"Efectivo"}:"yape".equals(mode)?new String[]{"Efectivo","Yape"}:"plin".equals(mode)?new String[]{"Efectivo","Plin"}:new String[]{"Efectivo","Yape","Plin"};String selectedPay=details.optString("payment","cash");if(!Arrays.asList(payKeys).contains(selectedPay)){selectedPay="cash";put(details,"payment","cash");}choices(payKeys,payLabels,selectedPay,key->{captureDetails();put(details,"payment",key);render();});note("Elegir una opción no realiza un cobro. Espera la confirmación de la olla antes de pagar.");}
        else{
            add(content,ui.heading("Así queda tu pedido",22),22);JSONObject cart=state().optJSONObject("cart");java.util.Iterator<String> ids=cart.keys();while(ids.hasNext()){String id=ids.next();JSONObject p=product(id);if(p==null)continue;summaryLine(content,cart.optInt(id)+" × "+p.optString("name"),cash(cart.optInt(id)*p.optDouble("price")),false);}
            LinearLayout contact=ui.message(details.optString("customer"),details.optString("phone"),NativeUi.CARD);add(content,contact,16);boolean delivery=details.optString("fulfillment","pickup").equals("delivery");String place=delivery?DeliveryDestination.typeLabel(details.optString("destinationType","home"))+" · "+details.optString("zone")+" · "+details.optString("address")+"\nReferencia: "+details.optString("reference"):settings().optString("pickup");add(content,ui.message(delivery?"Delivery el día de venta":"Recojo en Manka Kusi",place,NativeUi.CARD),12);if(delivery)destinationCard(content,details,false);add(content,ui.message("Forma de pago elegida",payment()+" · pago sin realizar",NativeUi.SOFT),12);add(content,ui.message("Fecha de venta",SaleSchedule.day(settings())+(delivery?"\nManka Kusi coordinará la hora del reparto.":"\n"+SaleSchedule.window(settings())),NativeUi.GOLD),12);note("El pedido se enviará directamente a ManchApp Emprendedora instalada en este celular.");
        }
        summary=ui.card();add(content,summary,18);updateSummary();
        if(step<2)primary(step==0?"Continuar con mis datos":"Revisar mi pedido",()->{captureDetails();if(!validStep())return;step++;hideKeyboard();render();});
        else primary(submittingOrder?"Enviando…":"Enviar pedido",()->{
            if(submittingOrder)return;
            captureDetails();
            try{
                final JSONObject order=store.placeReviewedOrder(details,catalog().optInt("revision"));
                submittingOrder=true;step=0;go("receipt",order.optString("id"));
                worker.execute(()->{
                    String notice="";boolean accepted=false;
                    try{store.acceptOrder(order.optString("id"),DeviceBridgeClient.placeOrder(this,order));accepted=true;}
                    catch(Exception bridge){
                        String message=bridge.getMessage()==null?"No pudimos enviar el pedido.":bridge.getMessage();
                        if(stockConflict(message)){
                            try{store.rejectOrder(order.optString("id"),"El último cupo se vendió antes de confirmar tu pedido.");}catch(Exception ignored){}
                            notice="Ya se vendió el último cupo. Puedes solicitar más ventas a la olla.";
                        }else{notice=message+" El pedido quedó guardado para reintentar.";}
                    }
                    final boolean ok=accepted;final String toastMsg=notice;
                    runOnUiThread(()->{submittingOrder=false;online=ok;if(isFinishing()||isDestroyed())return;if(!toastMsg.isEmpty())toast(toastMsg);render();syncDevice(false);});
                });
            }catch(Exception error){render();toast(error.getMessage());}
        });
    }

    private boolean deliveryScreen(){return screen.equals("checkout")&&step==0&&details.optString("fulfillment","pickup").equals("delivery");}
    private void requestDeliveryLocation(){captureDetails();if(!locator.permission()){locator.ask();return;}locating=true;render();locator.start(new DeliveryLocator.Result(){
        public void failed(String message){locating=false;if(!isFinishing()&&!isDestroyed()){toast(message);if(deliveryScreen())render();}}
        public void found(android.location.Location location){locating=false;if(isFinishing()||isDestroyed()||!deliveryScreen())return;captureDetails();try{
            JSONObject candidate=DeliveryDestination.point(location.getLatitude(),location.getLongitude(),location.getAccuracy(),System.currentTimeMillis());double distance=DeliveryDestination.distanceKm(baseSettings(),candidate);String zone=DeliveryDestination.zoneForDistance(baseSettings(),distance);render();if(distance<0){toast("No pudimos calcular la distancia. Revisa tu ubicación o escribe tu dirección.");return;}if(zone.isEmpty()){toast("Esta ubicación está fuera de la cobertura habilitada de Manka Kusi.");return;}
            new AlertDialog.Builder(CustomerActivity.this).setTitle("¿Estás en el lugar de entrega?").setMessage("Destino: "+DeliveryDestination.typeLabel(details.optString("destinationType","home"))+"\nEntrega: "+SaleSchedule.day(settings())+"\nDistancia aproximada: "+String.format(Locale.US,"%.1f km",distance)+"\nDirección: "+details.optString("address","Por completar")+"\nConfirma solo si este punto corresponde al lugar de entrega.").setNegativeButton("Volver",null).setPositiveButton("Usar este punto",(d,w)->{captureDetails();put(details,"deliveryLocation",candidate);put(details,"zoneId",zone);confirmDestination();}).show();
        }catch(Exception error){toast("No pudimos conservar ese punto. Puedes escribir tu dirección.");}}
    });}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==DeliveryLocator.REQUEST){if(locator.permission()&&deliveryScreen())requestDeliveryLocation();else toast("Puedes continuar con tu dirección y referencias.");}}
    private void confirmDestination(){try{DeliveryDestination.confirm(details,settings().optString("saleDate"));store.details(details);render();}catch(Exception error){toast("No pudimos guardar el punto. Vuelve a intentarlo.");}}
    private void destinationCard(LinearLayout parent,JSONObject value,boolean editable){JSONObject point=value.optJSONObject("deliveryLocation");if(point==null){String address=value.optString("address").trim();if(address.length()<4)return;LinearLayout card=ui.message("DIRECCIÓN DE ENTREGA",DeliveryDestination.typeLabel(value.optString("destinationType","home"))+" · "+value.optString("zone")+"\n"+address+"\nReferencia: "+value.optString("reference"),NativeUi.CARD);add(card,ui.button("Buscar dirección en app de mapas",false,"pin",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(DeliveryDestination.addressGeo(value))));}catch(Exception error){toast("No hay una app de mapas disponible. La dirección escrita se conserva.");}}),10);add(parent,card,12);return;}boolean valid=DeliveryDestination.validPoint(point);String saleDate=editable||screen.equals("checkout")?settings().optString("saleDate"):value.optString("saleDate");boolean confirmed=valid&&DeliveryDestination.problem(value,saleDate).isEmpty();LinearLayout card=ui.message(confirmed?"Punto de entrega guardado":"Revisa el punto de entrega",valid?"Precisión aproximada: "+Math.round(point.optDouble("accuracy"))+" m. "+(confirmed?"Confirmado para esta dirección y fecha.":"Confirma el punto después de cambiar la dirección o fecha."):"Quita este punto y vuelve a obtener tu ubicación.",NativeUi.CARD);if(valid)add(card,ui.button("Ver punto",false,"pin",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(DeliveryDestination.geo(point))));}catch(Exception error){toast("No hay una app de mapas disponible. La dirección escrita se conserva.");}}),10);if(editable){if(valid&&!confirmed)add(card,ui.button("Confirmar punto para esta entrega",false,null,()->{captureDetails();new AlertDialog.Builder(this).setTitle("¿El punto corresponde a esta dirección?").setMessage(details.optString("address")+"\nVenta: "+SaleSchedule.day(settings())).setNegativeButton("Volver",null).setPositiveButton("Sí, corresponde",(d,w)->confirmDestination()).show();}),10);add(card,ui.button("Quitar ubicación",false,null,()->{captureDetails();details.remove("deliveryLocation");try{store.details(details);render();}catch(Exception error){toast("No pudimos guardar el cambio.");}}),8);}add(parent,card,12);}

    private boolean typing(){View focus=getCurrentFocus();return focus instanceof EditText;}
    private void hideKeyboard(){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}
    private boolean validStep(){if(step==0&&details.optString("fulfillment","pickup").equals("delivery")){try{store.fee(details);}catch(Exception error){toast(error.getMessage());return false;}if(details.optString("address").length()<4){error("address","Escribe una dirección o punto de encuentro.");return false;}if(details.optString("reference").length()<3){error("reference","Agrega una referencia fácil de reconocer.");return false;}}if(step==0&&details.optString("fulfillment","pickup").equals("delivery")){String problem=DeliveryDestination.problem(details,settings().optString("saleDate"));if(!problem.isEmpty()){toast(problem);render();return false;}}if(step==1){if(details.optString("customer").length()<2){error("customer","Escribe el nombre del pedido.");return false;}if(!SaleRules.validPhone(details.optString("phone"))){error("phone","Escribe 9 dígitos que empiecen con 9.");return false;}}return true;}
    private void summaryLine(LinearLayout parent,String label,String value,boolean strong){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView name=ui.text(label,strong?19:15,NativeUi.INK,strong);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView amount=ui.text(value,strong?22:15,NativeUi.WINE,true);amount.setGravity(Gravity.RIGHT);row.addView(amount,new LinearLayout.LayoutParams(-2,-2));add(parent,row,10);}
    private void updateSummary(){if(summary==null||!screen.equals("checkout"))return;summary.removeAllViews();add(summary,ui.text("TOTAL DE TU PEDIDO",12,NativeUi.WINE,true),0);summaryLine(summary,"Comida",cash(store.subtotal()),false);try{double fee=store.fee(details);summaryLine(summary,"Entrega",details.optString("fulfillment","pickup").equals("pickup")?"Recojo gratis":fee==0?"Gratis por promoción":cash(fee),false);summaryLine(summary,"Total",cash(store.subtotal()+fee),true);}catch(Exception missing){summaryLine(summary,"Entrega","Elige un sector",false);}}
    private String orderStatus(JSONObject order){
        String send=order.optString("sendState");if(send.equals("pending")||send.equals("device_pending"))return "Guardado · falta enviar a Emprendedora";if(send.equals("review"))return "Revisa tu solicitud";if(send.equals("legacy")||send.equals("unbound"))return "Revisar antes de enviar";if(send.equals("superseded"))return "Solicitud reemplazada";
        switch(order.optString("status")){case "registered":return "Pedido recibido · espera confirmación";case "confirmed":return "Pedido aceptado por la olla";case "preparing":case "ready":return "En preparación";case "cooking":return "Cocinando tu pedido";case "sale_day":return "Día de venta";case "delivering":return "Delivery en camino";case "pickup_available":return "Pedido listo para recoger";case "delivered":return "Pedido entregado";case "cancelled":return "Cancelado";default:return "Pendiente de envío";}
    }
    private void showOrders(){
        title("Tus pedidos","Fecha de venta, pago y entrega de tus compras.");add(content,ui.button(syncing?"Actualizando…":"Actualizar pedidos",false,"orders",()->syncDevice(true)),12);JSONArray orders=state().optJSONArray("orders");
        if(orders.length()==0)empty("Todavía no hay pedidos","Elige tus productos y revisa la fecha de venta.");
        for(int i=0;i<orders.length();i++){JSONObject order=orders.optJSONObject(i);LinearLayout card=ui.card();add(card,ui.text((order.optString("sendState").equals("sent")?"PEDIDO N.º ":"SOLICITUD ")+String.format(Locale.US,"%03d",order.optInt("number")),12,NativeUi.CORAL,true),0);add(card,ui.heading(orderStatus(order),20),7);add(card,ui.text("Venta: "+SaleSchedule.day(order),15,NativeUi.WINE,true),8);add(card,ui.text(order.optString("customer")+" · "+cash(order.optDouble("total")),16,NativeUi.INK,true),6);add(card,ui.text(order.optBoolean("paid")?"Pago confirmado":"Pago pendiente",13,NativeUi.MUTED,false),6);add(card,ui.button("Ver mi pedido",false,null,()->go("receipt",order.optString("id"))),12);add(content,card,14);}
    }
    private JSONObject currentOrder(){JSONArray orders=state().optJSONArray("orders");for(int i=0;i<orders.length();i++)if(orders.optJSONObject(i).optString("id").equals(selectedId)||orders.optJSONObject(i).optString("requestId").equals(selectedId))return orders.optJSONObject(i);return null;}
    private LinearLayout stageTimeline(JSONObject order){
        boolean delivery=order.optString("fulfillment").equals("delivery");
        String[] keys=delivery?new String[]{"received","accepted","preparing","cooking","delivering","delivered"}:new String[]{"received","accepted","preparing","cooking","ready","delivered"};
        String[] labels=delivery?new String[]{"Pedido recibido","Pedido aceptado","En preparación","Cocinando tu pedido","Delivery en camino","Pedido entregado"}:new String[]{"Pedido recibido","Pedido aceptado","En preparación","Cocinando tu pedido","Pedido listo para recoger","Pedido entregado"};
        String status=order.optString("status");int current=-1;
        switch(status){case "registered":current=0;break;case "confirmed":current=1;break;case "preparing":case "ready":current=2;break;case "cooking":case "sale_day":current=3;break;case "delivering":case "pickup_available":current=4;break;case "delivered":current=5;break;default:break;}
        LinearLayout timeline=ui.card();
        for(int i=0;i<keys.length;i++){boolean done=current>=i;TextView row=ui.text(labels[i],15,done?NativeUi.WINE:NativeUi.MUTED,done);row.setTag("stage:"+keys[i]);add(timeline,row,8);}
        return timeline;
    }
    private void showReceipt(){
        JSONObject order=currentOrder();if(order==null){go("orders","");return;}String send=order.optString("sendState");boolean accepted=send.equals("sent");String status=order.optString("status");
        String subtitle=!accepted?"Se conserva en este celular. El cupo todavía no está confirmado.":status.equals("registered")?"La olla recibió tu pedido. Espera la confirmación para darlo por aceptado.":status.equals("confirmed")?"La olla aceptó tu pedido. Ya está en la lista de preparación.":status.equals("preparing")?"La olla está preparando tu pedido.":status.equals("cooking")?"Tu pedido está en cocina.":status.equals("sale_day")?"Hoy es el día de venta acordado.":status.equals("delivering")?"Quien reparte ya salió hacia tu punto de entrega.":status.equals("pickup_available")?"Tu pedido está listo para recoger.":status.equals("delivered")?"Tu pedido fue entregado. ¡Buen provecho!":status.equals("cancelled")?"La olla canceló este pedido. Revisa el detalle antes de volver a pedir.":"La olla confirmó tu pedido.";
        add(content,ui.message(orderStatus(order),subtitle,NativeUi.GOLD),0);
        if(status.equals("cancelled")&&!order.optString("cancelReason").isEmpty()){TextView reason=ui.text("Motivo de la cancelación: "+order.optString("cancelReason"),13,NativeUi.MUTED,false);reason.setTag("cancel:reason");add(content,reason,10);}
        add(content,ui.text("ESTADO DE TU PEDIDO",12,NativeUi.WINE,true),14);
        add(content,stageTimeline(order),6);
        add(content,ui.heading((accepted?"Pedido ":"Solicitud ")+String.format(Locale.US,"%03d",order.optInt("number")),25),19);
        LinearLayout food=ui.card();JSONArray items=order.optJSONArray("items");for(int i=0;items!=null&&i<items.length();i++){JSONObject item=items.optJSONObject(i);summaryLine(food,item.optInt("quantity")+" × "+item.optString("name"),cash(item.optInt("quantity")*item.optDouble("price")),false);}summaryLine(food,"Entrega",cash(order.optDouble("fee")),false);summaryLine(food,"Total",cash(order.optDouble("total")),true);add(content,food,14);
        String place=order.optString("fulfillment","pickup").equals("delivery")?DeliveryDestination.typeLabel(order.optString("destinationType","home"))+" · "+order.optString("zone")+" · "+order.optString("address")+"\nReferencia: "+order.optString("reference"):order.optString("pickup","Manka Kusi · Manchay");
        add(content,ui.message("DÍA DE VENTA",SaleSchedule.day(order),NativeUi.GOLD),12);if(!order.optString("fulfillment","pickup").equals("delivery"))add(content,ui.message("PUEDES RECOGER",SaleSchedule.window(order),NativeUi.CARD),12);
        add(content,ui.message("ENTREGA",place,NativeUi.CARD),12);if(order.optString("fulfillment").equals("delivery")){destinationCard(content,order,false);if(!order.optString("driver").isEmpty())note("Reparte: "+order.optString("driver"));if(!order.optString("deliveryWindow").isEmpty())note("Horario acordado: "+order.optString("deliveryWindow"));}
        add(content,ui.message(order.optBoolean("paid")?"PAGO CONFIRMADO":"PAGO PENDIENTE",order.optBoolean("paid")?"La olla registró el dinero recibido.":"Elegir Yape, Plin o efectivo no realiza un cobro.",NativeUi.SOFT),12);
        add(content,ui.button("Volver a pedir en 1 toque",false,"cart",()->{try{store.reorderToCart(order.optString("id"));details=store.snapshot().optJSONObject("details");go("cart","");toast("Productos cargados al carrito.");}catch(Exception error){toast(error.getMessage());}}),12);
        if(accepted&&!order.optBoolean("paid")&&!order.optString("payment").equals("cash")&&!order.optString("status").equals("cancelled")){
            JSONObject s=settings();
            String encoded=s.optString("paymentImage");
            String walletPhone=s.optString("walletPhone",s.optString("phone"));
            String walletHolder=s.optString("walletHolder",s.optString("name","Manka Kusi"));
            String payMethod=order.optString("payment").equals("yape")?"Yape":"Plin";
            if(!encoded.isEmpty()){ImageView qr=new ImageView(this);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setImageBitmap(images.thumbnail(encoded,dp(260)));qr.setBackgroundColor(Color.WHITE);qr.setContentDescription("QR de pago de la olla");add(content,qr,14);qr.getLayoutParams().height=dp(240);}
            LinearLayout wallet=ui.message("DATOS PARA PAGAR CON "+payMethod.toUpperCase(Locale.ROOT),
                    "Titular: "+walletHolder+(SaleRules.validPhone(walletPhone)?"\nCelular: "+walletPhone:"")+"\nMonto exacto: "+cash(order.optDouble("total")),NativeUi.CARD);
            if(SaleRules.validPhone(walletPhone)){
                add(wallet,ui.button("Copiar número ("+walletPhone+")",false,"pay",()->{
                    android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText("Celular "+payMethod,walletPhone));
                    toast("Número "+walletPhone+" copiado. Monto: "+cash(order.optDouble("total")));
                }),10);
            }
            add(wallet,ui.button("Abrir "+payMethod,false,"pay",()->openWalletApp(order.optString("payment"))),8);
            add(content,wallet,12);
            note("Verifica el titular y el monto antes de transferir. La olla confirmará tu abono.");
        }
        if(send.equals("review")){note(order.optString("syncError","Cambió la disponibilidad. Revisa antes de crear otra solicitud."));}
        if(Arrays.asList("review","legacy","unbound").contains(send))add(content,ui.button("Revisar productos y datos",false,null,()->{try{store.restoreToCart(order.optString("id"));details=store.snapshot().optJSONObject("details");go("cart","");}catch(Exception error){toast(error.getMessage());}}),14);
        String contact=settings().optString("phone");if(SaleRules.validPhone(contact)){add(content,ui.button("Escribir por WhatsApp",false,"share",()->openWhatsApp(contact,order)),12);add(content,ui.button("Llamar a la olla",false,null,()->{try{startActivity(new Intent(Intent.ACTION_DIAL,android.net.Uri.parse("tel:"+contact)));}catch(Exception error){toast("No hay una app de llamadas disponible.");}}),10);}
        primary((syncing||submittingOrder)?"Enviando…":send.equals("sent")?"Actualizar pedido":"Reintentar envío",()->syncDevice(true));
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
    private void syncDevice(boolean notify){
        if(syncing||submittingOrder||store==null)return;captureDetails();syncing=true;String before=store.snapshot().toString();worker.execute(()->{String problem="";List<JSONObject> cancelledOrders=new ArrayList<>();try{store.acceptDeviceCatalog(DeviceBridgeClient.catalog(this));JSONArray pending=store.pending();for(int i=0;i<pending.length();i++){JSONObject order=pending.getJSONObject(i);store.acceptOrder(order.optString("id"),DeviceBridgeClient.placeOrder(this,order));}JSONArray orders=store.snapshot().optJSONArray("orders");for(int i=0;i<orders.length();i++){JSONObject order=orders.optJSONObject(i);if(!order.optString("sendState").equals("sent"))continue;if(order.optString("status").equals("delivered")||order.optString("status").equals("cancelled"))continue;String previous=order.optString("status");JSONObject response=DeviceBridgeClient.order(this,order.optString("id"));store.acceptOrder(order.optString("id"),response);JSONObject source=response.optJSONObject("order")!=null?response.getJSONObject("order"):response;if(!previous.equals("cancelled")&&source.optString("status").equals("cancelled"))cancelledOrders.add(CustomerStore.copy(source).put("id",order.optString("id")));}store.acceptDeviceCatalog(DeviceBridgeClient.catalog(this));online=true;}catch(Exception error){online=false;problem=error.getMessage()==null?"No encontramos ManchApp Emprendedora en este celular.":error.getMessage();}String message=problem;List<JSONObject> cancellations=cancelledOrders;runOnUiThread(()->{syncing=false;syncMessage=message;if(isFinishing()||isDestroyed())return;for(JSONObject order:cancellations)CustomerNotifier.cancelled(this,order);boolean changed=!store.snapshot().toString().equals(before);if(!typing()&&(notify||changed)){int y=scroll==null?0:scroll.getScrollY();render();if(scroll!=null)scroll.post(()->scroll.scrollTo(0,y));}if(notify)toast(message.isEmpty()?"Productos y pedidos actualizados en este celular.":message);});});
    }
}
