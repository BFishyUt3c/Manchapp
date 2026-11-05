package pe.manchapp.beta;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class CustomerStore {
    private final AtomicFile file;
    private JSONObject state;

    CustomerStore(Context context){this(context,"customer-prototype.json");}
    CustomerStore(Context context,String filename){
        file=new AtomicFile(new File(context.getFilesDir(),filename));
        try{state=new JSONObject(read(file.openRead()));}
        catch(Exception missing){try{
            JSONObject seed=new JSONObject(read(context.getAssets().open("seed.json")));
            state=new JSONObject().put("catalog",seed).put("cart",new JSONObject()).put("details",new JSONObject()).put("orders",new JSONArray());save();
        }catch(Exception fatal){throw new IllegalStateException("No pudimos preparar el catálogo de ejemplo.",fatal);}}
        upgrade(context);
    }
    private void upgrade(Context context){try{JSONObject defaults=new JSONObject(read(context.getAssets().open("seed.json"))).getJSONObject("settings");if(SaleSchedule.upgrade(state.getJSONObject("catalog"),defaults))save();}catch(Exception error){throw new IllegalStateException("No pudimos actualizar las fechas de venta.",error);}}
    private static String read(InputStream stream)throws IOException{try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(JSONException error){throw new IllegalStateException(error);}}
    synchronized JSONObject snapshot(){return copy(state);}
    private void save()throws IOException{FileOutputStream out=null;try{out=file.startWrite();out.write(state.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(IOException error){if(out!=null)file.failWrite(out);throw error;}}
    private JSONObject find(String id){JSONArray products=state.optJSONObject("catalog").optJSONArray("products");for(int i=0;i<products.length();i++)if(products.optJSONObject(i).optString("id").equals(id))return products.optJSONObject(i);return null;}
    synchronized void quantity(String id,int amount)throws Exception{
        JSONObject product=find(id);if(product==null||!product.optString("line").equals("commercial"))throw new Exception("Este producto no pertenece a la venta comercial.");
        if(amount<0||amount>10000||amount>product.optInt("stock")||(amount>0&&!product.optBoolean("available")))throw new Exception("Revisa la cantidad disponible.");
        JSONObject settings=state.getJSONObject("catalog").getJSONObject("settings");if(amount>state.getJSONObject("cart").optInt(id))SaleSchedule.requireOpen(settings,System.currentTimeMillis());
        if(amount>0&&count()>0&&!state.optString("cartSale",SaleSchedule.key(settings)).equals(SaleSchedule.key(settings)))throw new Exception("La fecha de venta cambió. Vacía el carrito para reservar en la nueva fecha.");
        JSONObject before=copy(state);try{if(count()==0)state.put("cartSale",SaleSchedule.key(settings));if(amount==0)state.getJSONObject("cart").remove(id);else state.getJSONObject("cart").put(id,amount);save();}catch(Exception error){state=before;throw error;}
    }
    JSONObject bundledSale(Context context)throws Exception{return new JSONObject(read(context.getAssets().open("seed.json"))).getJSONObject("settings");}
    synchronized boolean cartNeedsReview(){return count()>0&&!state.optString("cartSale",SaleSchedule.key(state.optJSONObject("catalog").optJSONObject("settings"))).equals(SaleSchedule.key(state.optJSONObject("catalog").optJSONObject("settings")));}
    synchronized void loadBundledCatalog(Context context)throws Exception{
        JSONObject catalog=new JSONObject(read(context.getAssets().open("seed.json"))),before=copy(state);String day=catalog.getJSONObject("settings").getString("saleDate");
        try{if(count()>0&&!state.has("cartSale"))state.put("cartSale",SaleSchedule.key(state.getJSONObject("catalog").getJSONObject("settings")));
            JSONArray products=catalog.getJSONArray("products"),orders=state.getJSONArray("orders");for(int i=0;i<products.length();i++){JSONObject product=products.getJSONObject(i);int reserved=0;for(int j=0;j<orders.length();j++){JSONObject order=orders.getJSONObject(j);if(!order.optString("saleDate").equals(day)||order.optString("status").equals("cancelled"))continue;JSONArray items=order.optJSONArray("items");for(int k=0;items!=null&&k<items.length();k++)if(items.getJSONObject(k).optString("productId").equals(product.optString("id")))reserved+=items.getJSONObject(k).optInt("quantity");}product.put("stock",Math.max(0,product.optInt("stock")-reserved));}
            state.put("catalog",catalog);save();
        }catch(Exception error){state=before;throw error;}
    }
    synchronized void clearCart()throws Exception{JSONObject before=copy(state);try{state.put("cart",new JSONObject()).remove("cartSale");save();}catch(Exception error){state=before;throw error;}}
    synchronized void details(JSONObject details)throws Exception{if(state.optJSONObject("details").toString().equals(details.toString()))return;JSONObject before=copy(state);try{state.put("details",copy(details));save();}catch(Exception error){state=before;throw error;}}
    synchronized double subtotal(){JSONObject cart=state.optJSONObject("cart");double sum=0;java.util.Iterator<String> ids=cart.keys();while(ids.hasNext()){String id=ids.next();JSONObject p=find(id);if(p!=null)sum+=p.optDouble("price")*cart.optInt(id);}return SaleRules.money(sum);}
    synchronized int count(){JSONObject cart=state.optJSONObject("cart");int count=0;java.util.Iterator<String> ids=cart.keys();while(ids.hasNext())count+=cart.optInt(ids.next());return count;}
    synchronized double fee(JSONObject details)throws Exception{
        if(!details.optString("fulfillment","pickup").equals("delivery"))return 0;
        JSONObject settings=state.optJSONObject("catalog").optJSONObject("settings");JSONArray zones=settings.optJSONArray("zones");
        for(int i=0;zones!=null&&i<zones.length();i++){JSONObject zone=zones.optJSONObject(i);if(zone.optString("id").equals(details.optString("zoneId"))&&zone.optBoolean("enabled"))return SaleRules.deliveryFee(subtotal(),zone.optDouble("fee"),settings.optDouble("deliveryFreeFrom",0));}
        throw new Exception("Elige un sector habilitado para el reparto.");
    }
    synchronized JSONObject placePracticeOrder(JSONObject details)throws Exception{return placePracticeOrder(details,System.currentTimeMillis());}
    synchronized JSONObject placePracticeOrder(JSONObject details,long now)throws Exception{
        details=DeliveryDestination.normalized(details);
        if(count()==0)throw new Exception("Agrega un producto antes de guardar tu reserva.");
        JSONObject settings=state.getJSONObject("catalog").getJSONObject("settings");SaleSchedule.requireOpen(settings,now);
        if(!state.optString("cartSale",SaleSchedule.key(settings)).equals(SaleSchedule.key(settings)))throw new Exception("La fecha de venta cambió. Vacía el carrito y revisa la nueva fecha.");
        if(details.optString("customer").trim().length()<2)throw new Exception("Escribe tu nombre.");
        if(!SaleRules.validPhone(details.optString("phone")))throw new Exception("El celular debe tener 9 dígitos y empezar con 9.");
        String mode=details.optString("fulfillment","pickup"),payment=details.optString("payment","cash");
        if(!mode.equals("pickup")&&!mode.equals("delivery"))throw new Exception("Elige recojo o reparto.");
        if(!java.util.Arrays.asList("cash","yape","plin").contains(payment))throw new Exception("Elige una forma de pago.");
        if(mode.equals("delivery")&&(details.optString("address").trim().length()<4||details.optString("reference").trim().length()<3))throw new Exception("Completa el punto de encuentro y una referencia.");
        if(mode.equals("delivery")){String destinationProblem=DeliveryDestination.problem(details,settings.optString("saleDate"));if(!destinationProblem.isEmpty())throw new Exception(destinationProblem);}
        double subtotal=subtotal(),fee=fee(details);JSONObject before=copy(state);
        try{
            JSONArray items=new JSONArray();JSONObject cart=state.getJSONObject("cart");java.util.Iterator<String> ids=cart.keys();
            while(ids.hasNext()){String id=ids.next();JSONObject p=find(id);int amount=cart.optInt(id);if(p==null||!p.optString("line").equals("commercial")||!p.optBoolean("available")||amount<=0||amount>p.optInt("stock"))throw new Exception("Cambió la disponibilidad. Revisa tu carrito.");items.put(new JSONObject().put("productId",id).put("name",p.optString("name")).put("quantity",amount).put("price",p.optDouble("price")));p.put("stock",p.optInt("stock")-amount);}
            JSONArray old=state.getJSONArray("orders");JSONObject order=copy(details).put("id",UUID.randomUUID().toString()).put("number",state.optInt("nextNumber",1)).put("items",items).put("fulfillment",mode).put("payment",payment).put("subtotal",subtotal).put("fee",fee).put("total",SaleRules.money(subtotal+fee)).put("status","local").put("paid",false).put("createdAt",System.currentTimeMillis());
            SaleSchedule.copyInto(order,settings);
            if(mode.equals("pickup")){order.remove("address");order.remove("reference");order.remove("zoneId");order.remove("meeting");order.remove("deliveryLocation");}
            JSONArray orders=new JSONArray().put(order);for(int i=0;i<old.length()&&i<49;i++)orders.put(old.get(i));state.put("orders",orders).put("nextNumber",order.optInt("number")+1).put("cart",new JSONObject()).put("details",copy(details));save();return copy(order);
        }catch(Exception error){state=before;throw error;}
    }
}
