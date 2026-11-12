package pe.manchapp.beta;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public final class ManualOrderActivity extends Activity {
    private NativeUi ui;
    private LocalStore store;
    private JSONObject quantities=new JSONObject(),details=new JSONObject();
    private final Map<String,EditText> fields=new HashMap<>();
    private LinearLayout body,footer;
    private int step=0;
    private int displayedStep=-1;
    private ScrollView scroll;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);ui=new NativeUi(this);try{store=LocalStore.open(this);if(state!=null){quantities=new JSONObject(state.getString("quantities","{}"));details=new JSONObject(state.getString("details","{}"));step=state.getInt("step",0);}else if(getIntent().hasExtra("restoreDraft")){JSONObject old=new JSONObject(getIntent().getStringExtra("restoreDraft"));details=old;JSONArray items=old.optJSONArray("items");for(int i=0;items!=null&&i<items.length();i++)quantities.put(items.getJSONObject(i).optString("productId"),items.getJSONObject(i).optInt("quantity"));}}catch(Exception error){RecoveryScreen.show(this,error);return;}
        getWindow().setStatusBarColor(NativeUi.DEEP);getWindow().setNavigationBarColor(NativeUi.WINE);draw();
    }
    private void put(JSONObject target,String key,Object value){try{target.put(key,value);}catch(Exception error){throw new IllegalStateException(error);}}
    private void add(LinearLayout target,View view,int gap){ui.add(target,view,gap);}
    private String cash(double value){return java.text.NumberFormat.getCurrencyInstance(new Locale("es","PE")).format(value);}
    private JSONObject settings(){return store.snapshot().optJSONObject("settings");}
    private JSONObject chosenSchedule(){JSONObject snapshot=store.snapshot(),settings=snapshot.optJSONObject("settings");JSONArray products=snapshot.optJSONArray("products");Iterator<String> ids=quantities.keys();while(ids.hasNext()){String id=ids.next();if(quantities.optInt(id)<=0)continue;for(int i=0;products!=null&&i<products.length();i++)if(products.optJSONObject(i).optString("id").equals(id))return SaleSchedule.forProduct(products.optJSONObject(i),settings);}return settings;}
    private void draw(){
        int oldY=scroll!=null&&displayedStep==step?scroll.getScrollY():0;displayedStep=step;fields.clear();LinearLayout root=ui.column();root.setBackgroundColor(NativeUi.BG);setContentView(root);root.addView(ui.toolbar("Registrar pedido",step==0?"1. Productos":"2. Cliente y entrega",()->onBackPressed(),null));
        scroll=new ScrollView(this);body=ui.column();body.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.post(()->scroll.scrollTo(0,oldY));
        add(body,ui.message("DÍA DE VENTA",SaleSchedule.day(chosenSchedule()),NativeUi.GOLD),0);
        if(step==0)products();else contact();
        footer=ui.column();footer.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(12));footer.addView(ui.button(step==0?"Continuar con el cliente":"Revisar pedido",true,null,()->{
            capture();if(step==0){int count=0;Iterator<String> ids=quantities.keys();while(ids.hasNext())count+=quantities.optInt(ids.next());if(count==0){toast("Elige al menos un producto.");return;}step=1;draw();}
            else review();
        }));root.addView(footer);
    }
    private void products(){
        add(body,ui.heading("¿Qué pidió el cliente?",24),16);add(body,ui.text("Registra pedidos recibidos por WhatsApp, llamada o en persona.",15,NativeUi.MUTED,false),8);
        JSONArray products=store.snapshot().optJSONArray("products");double subtotal=0;
        for(int i=0;products!=null&&i<products.length();i++){
            JSONObject product=products.optJSONObject(i);if(!product.optString("line").equals("commercial")||!product.optBoolean("available"))continue;String id=product.optString("id");int count=quantities.optInt(id);subtotal+=count*product.optDouble("price");
            LinearLayout card=ui.card();add(card,ui.text(product.optString("name"),19,NativeUi.INK,true),0);add(card,ui.text(cash(product.optDouble("price"))+" · "+product.optInt("stock")+" cupos",14,NativeUi.MUTED,false),5);add(card,ui.text("Venta: "+SaleSchedule.day(SaleSchedule.forProduct(product,settings())),13,NativeUi.WINE,true),5);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(ui.button("−",false,null,()->{put(quantities,id,Math.max(0,count-1));draw();}),new LinearLayout.LayoutParams(ui.dp(58),ui.dp(52)));
            TextView number=ui.text(String.valueOf(count),22,NativeUi.WINE,true);number.setGravity(Gravity.CENTER);row.addView(number,new LinearLayout.LayoutParams(0,-2,1));TextView plus=ui.button("+",false,null,()->{put(quantities,id,count+1);draw();});plus.setEnabled(count<Math.min(99,product.optInt("stock")));row.addView(plus,new LinearLayout.LayoutParams(ui.dp(58),ui.dp(52)));add(card,row,10);add(body,card,12);
        }
        add(body,ui.message("Productos: "+cash(subtotal),"Elige productos de una misma fecha. Para otra jornada, registra un pedido separado.",NativeUi.SOFT),14);
    }
    private EditText field(String key,String label,String hint,boolean phone){
        add(body,ui.text(label,15,NativeUi.INK,true),16);EditText input=new EditText(this);input.setText(details.optString(key));input.setHint(hint);input.setTextSize(17);input.setTypeface(ui.regular);input.setTextColor(NativeUi.INK);input.setPadding(ui.dp(12),ui.dp(14),ui.dp(12),ui.dp(14));input.setBackground(ui.background(NativeUi.CARD,NativeUi.LINE,12));input.setInputType(phone?InputType.TYPE_CLASS_PHONE:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(phone?9:key.equals("customer")?80:240)});if(key.equals("address")||key.equals("reference")){input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setMinLines(2);input.setMaxLines(4);}
        fields.put(key,input);add(body,input,5);return input;
    }
    private void select(String label,String[] names,String[] values,String key,String fallback,boolean redraw){
        add(body,ui.text(label,15,NativeUi.INK,true),16);Spinner picker=new Spinner(this);picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));picker.setMinimumHeight(ui.dp(54));int selected=Arrays.asList(values).indexOf(details.optString(key,fallback));picker.setSelection(Math.max(0,selected));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String old=details.optString(key,fallback);put(details,key,values[position]);if(redraw&&!old.equals(values[position])){capture();draw();}}});add(body,picker,5);
    }
    private void contact(){
        field("customer","Nombre del cliente","¿A nombre de quién es?",false);field("phone","Celular de contacto","9 dígitos, empieza con 9",true);
        select("¿Cómo llegó el pedido?",new String[]{"En persona","WhatsApp","Llamada"},new String[]{"presencial","whatsapp","telefono"},"source","presencial",false);
        select("Entrega",new String[]{"Recojo en la olla","Delivery"},new String[]{"pickup","delivery"},"fulfillment","pickup",true);
        if(details.optString("fulfillment","pickup").equals("delivery")){
            JSONArray zones=settings().optJSONArray("zones");List<String> names=new ArrayList<>(),ids=new ArrayList<>();names.add("Elige un sector");ids.add("");for(int i=0;zones!=null&&i<zones.length();i++){JSONObject zone=zones.optJSONObject(i);if(zone.optBoolean("enabled")){names.add(zone.optString("name")+" · "+cash(zone.optDouble("fee")));ids.add(zone.optString("id"));}}
            select("Sector",names.toArray(new String[0]),ids.toArray(new String[0]),"zoneId","",false);field("address","Dirección de entrega","Sector, manzana, lote o punto acordado",false);field("reference","Referencias para llegar","Escaleras, color de casa, bodega o punto de encuentro",false);
        }else add(body,ui.message("RECOJO",settings().optString("pickup")+"\n"+SaleSchedule.window(chosenSchedule()),NativeUi.CARD),14);
        select("Forma de pago",new String[]{"Efectivo","Yape","Plin"},new String[]{"cash","yape","plin"},"payment","cash",false);add(body,ui.text("El dinero recibido se confirma por separado dentro del pedido.",14,NativeUi.MUTED,false),12);
    }
    private void capture(){for(Map.Entry<String,EditText> entry:fields.entrySet()){String value=entry.getValue().getText().toString().trim();if(entry.getKey().equals("address")&&!value.equals(details.optString("address")))details.remove("deliveryLocation");put(details,entry.getKey(),value);}}
    private void review(){
        try{JSONObject order=OrderDraft.build(store.snapshot(),details,quantities,true,System.currentTimeMillis());StringBuilder text=new StringBuilder(details.optString("customer")).append("\n").append(SaleSchedule.day(order)).append("\n");JSONArray items=order.getJSONArray("items");for(int i=0;i<items.length();i++)text.append("\n").append(items.getJSONObject(i).optInt("quantity")).append(" × ").append(items.getJSONObject(i).optString("name"));text.append("\n\nTotal: ").append(cash(order.optDouble("total"))).append("\n").append(order.optString("fulfillment").equals("delivery")?"Delivery: "+order.optString("address"):"Recojo en la olla");
            new AlertDialog.Builder(this).setTitle("¿Están bien los datos?").setMessage(text.toString()).setNegativeButton("Volver",null).setPositiveButton("Registrar pedido",(dialog,which)->{try{JSONObject saved=store.manualOrder(details,quantities);setResult(RESULT_OK,new Intent().putExtra("orderId",saved.optString("id")));finish();}catch(Exception error){toast(error.getMessage());}}).show();
        }catch(Exception error){toast(error.getMessage());}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(step>0){capture();step=0;draw();}else super.onBackPressed();}
    @Override public void onSaveInstanceState(Bundle state){capture();state.putInt("step",step);state.putString("quantities",quantities.toString());state.putString("details",details.toString());super.onSaveInstanceState(state);}
}
