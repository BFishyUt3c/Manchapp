package pe.manchapp.beta;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

final class CustomerStore {
    private static final Map<String,CustomerStore> instances=new HashMap<>();
    static synchronized CustomerStore open(Context context){String key=context.getFilesDir().getAbsolutePath();CustomerStore value=instances.get(key);if(value==null){value=new CustomerStore(context.getApplicationContext());instances.put(key,value);}return value;}
    private final SafeJson file;
    private JSONObject state;
    CustomerStore(Context context){this(context,"customer-prototype.json");}
    CustomerStore(Context context,String filename){
        file=new SafeJson(new File(context.getFilesDir(),filename),"catalog");
        try{
            JSONObject seed=new JSONObject(SafeJson.read(context.getAssets().open("seed.json")));
            state=file.load(new JSONObject().put("schema",2).put("catalog",seed).put("cart",new JSONObject()).put("details",new JSONObject()).put("orders",new JSONArray()));
            if(state.optInt("schema")<2){JSONArray orders=state.optJSONArray("orders");for(int i=0;orders!=null&&i<orders.length();i++)orders.getJSONObject(i).put("sendState","legacy");state.put("schema",2);save();}
            if(SaleSchedule.upgrade(state.getJSONObject("catalog"),seed.getJSONObject("settings")))save();
        }catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}
    }
    static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(JSONException error){throw new IllegalStateException(error);}}
    synchronized JSONObject snapshot(){return copy(state);}
    private void save()throws IOException{file.save(state);}
    synchronized String serverUrl(){return state.optString("serverUrl");}
    synchronized String storeId(){return state.optJSONObject("catalog").optString("storeId");}
    private JSONObject find(String id){JSONArray products=state.optJSONObject("catalog").optJSONArray("products");for(int i=0;i<products.length();i++)if(products.optJSONObject(i).optString("id").equals(id))return products.optJSONObject(i);return null;}
    private JSONObject schedule(JSONObject product){return SaleSchedule.forProduct(product,state.optJSONObject("catalog").optJSONObject("settings"));}
    private JSONObject cartSchedule(){JSONObject cart=state.optJSONObject("cart");Iterator<String> ids=cart.keys();while(ids.hasNext()){JSONObject product=find(ids.next());if(product!=null)return schedule(product);}return state.optJSONObject("catalog").optJSONObject("settings");}
    synchronized JSONObject currentSale(){return copy(cartSchedule());}
    synchronized void quantity(String id,int amount)throws Exception{
        JSONObject product=find(id);if(product==null||!product.optString("line").equals("commercial"))throw new Exception("Este producto no pertenece al catálogo de ventas.");
        if(amount<0||amount>99||amount>product.optInt("stock")||(amount>0&&!product.optBoolean("available")))throw new Exception("Revisa la cantidad disponible; máximo 99 por producto.");
        JSONObject settings=schedule(product);if(amount>state.getJSONObject("cart").optInt(id))SaleSchedule.requireOpen(settings,System.currentTimeMillis());
        if(amount>0&&count()>0&&!SaleSchedule.key(cartSchedule()).equals(SaleSchedule.key(settings)))throw new Exception("Este producto se vende en otra fecha. Termina este pedido y luego crea uno nuevo.");
        if(amount>0&&cartNeedsReview())throw new Exception("La jornada cambió. Revisa el carrito antes de agregar productos.");
        JSONObject before=copy(state);try{if(count()==0)state.put("cartSale",SaleSchedule.key(settings));if(amount==0)state.getJSONObject("cart").remove(id);else state.getJSONObject("cart").put(id,amount);save();}catch(Exception error){state=before;throw error;}
    }
    JSONObject bundledSale(Context context)throws Exception{return new JSONObject(SafeJson.read(context.getAssets().open("seed.json"))).getJSONObject("settings");}
    synchronized boolean cartNeedsReview(){return count()>0&&!state.optString("cartSale",SaleSchedule.key(cartSchedule())).equals(SaleSchedule.key(cartSchedule()));}
    synchronized void loadBundledCatalog(Context context)throws Exception{
        if(!serverUrl().isEmpty())throw new Exception("Este catálogo está vinculado. Actualízalo desde la laptop.");
        JSONObject before=copy(state);try{state.put("catalog",new JSONObject(SafeJson.read(context.getAssets().open("seed.json"))));save();}catch(Exception error){state=before;throw error;}
    }
    synchronized void acceptCatalog(JSONObject remote,String endpoint)throws Exception{
        if(remote.optJSONObject("settings")==null||remote.optJSONArray("products")==null||remote.optString("storeId").isEmpty())throw new IOException("La dirección no corresponde al servidor actualizado de ManchApp.");
        if(!storeId().isEmpty()&&!storeId().equals(remote.optString("storeId")))throw new IOException("Esta laptop contiene otro catálogo. Conservamos tus pedidos y carrito.");
        if(!storeId().isEmpty()&&remote.optInt("revision")<state.getJSONObject("catalog").optInt("revision"))return;
        JSONObject before=copy(state);
        try{if(count()>0&&!state.has("cartSale"))state.put("cartSale",SaleSchedule.key(state.getJSONObject("catalog").getJSONObject("settings")));state.put("catalog",copy(remote)).put("serverUrl",HttpApi.endpoint(endpoint));save();}catch(Exception error){state=before;throw error;}
    }
    synchronized void acceptDeviceCatalog(JSONObject remote)throws Exception{
        if(remote.optJSONObject("settings")==null||remote.optJSONArray("products")==null)throw new IOException("ManchApp Emprendedora todavía no tiene un catálogo disponible.");JSONObject before=copy(state);try{if(count()>0&&!state.has("cartSale"))state.put("cartSale",SaleSchedule.key(cartSchedule()));state.put("catalog",copy(remote)).remove("serverUrl");save();}catch(Exception error){state=before;throw error;}
    }
    synchronized void clearCart()throws Exception{JSONObject before=copy(state);try{state.put("cart",new JSONObject()).remove("cartSale");save();}catch(Exception error){state=before;throw error;}}
    synchronized void details(JSONObject value)throws Exception{if(state.optJSONObject("details").toString().equals(value.toString()))return;JSONObject before=copy(state);try{state.put("details",copy(value));save();}catch(Exception error){state=before;throw error;}}
    synchronized double subtotal(){JSONObject cart=state.optJSONObject("cart");double sum=0;Iterator<String> ids=cart.keys();while(ids.hasNext()){String id=ids.next();JSONObject product=find(id);if(product!=null)sum+=product.optDouble("price")*cart.optInt(id);}return SaleRules.money(sum);}
    synchronized int count(){JSONObject cart=state.optJSONObject("cart");int count=0;Iterator<String> ids=cart.keys();while(ids.hasNext())count+=cart.optInt(ids.next());return count;}
    synchronized double fee(JSONObject details)throws Exception{
        if(!details.optString("fulfillment","pickup").equals("delivery"))return 0;
        JSONObject settings=state.optJSONObject("catalog").optJSONObject("settings");JSONArray zones=settings.optJSONArray("zones");
        for(int i=0;zones!=null&&i<zones.length();i++){JSONObject zone=zones.optJSONObject(i);if(zone.optString("id").equals(details.optString("zoneId"))&&zone.optBoolean("enabled"))return SaleRules.deliveryFee(subtotal(),zone.optDouble("fee"),settings.optDouble("deliveryFreeFrom",0));}
        throw new Exception("Elige un sector habilitado para el reparto.");
    }
    synchronized JSONObject placePracticeOrder(JSONObject details)throws Exception{return placePracticeOrder(details,System.currentTimeMillis());}
    synchronized JSONObject placeReviewedOrder(JSONObject details,int revision)throws Exception{if(revision!=state.getJSONObject("catalog").optInt("revision"))throw new Exception("El catálogo cambió. Revisa otra vez el total y la fecha antes de enviar.");return placePracticeOrder(details);}
    synchronized JSONObject placePracticeOrder(JSONObject details,long now)throws Exception{
        if(cartNeedsReview())throw new Exception("La jornada cambió. Vacía el carrito y revisa la nueva fecha.");
        JSONObject order=OrderDraft.build(state.getJSONObject("catalog"),details,state.getJSONObject("cart"),false,now),before=copy(state);
        try{
            order.put("number",state.optInt("nextNumber",1)).remove("serverUrl");order.put("sendState","device_pending");
            JSONArray orders=new JSONArray().put(order),old=state.getJSONArray("orders");for(int i=0;i<old.length();i++)orders.put(old.get(i));
            state.put("orders",orders).put("nextNumber",order.optInt("number")+1).put("cart",new JSONObject()).put("details",DeliveryDestination.normalized(details)).remove("cartSale");save();return copy(order);
        }catch(Exception error){state=before;throw error;}
    }
    synchronized JSONArray pending(){JSONArray result=new JSONArray(),orders=state.optJSONArray("orders");for(int i=0;i<orders.length();i++){JSONObject order=orders.optJSONObject(i);if(Arrays.asList("pending","device_pending","unbound").contains(order.optString("sendState")))result.put(copy(order));}return result;}
    synchronized void acceptOrder(String localId,JSONObject response)throws Exception{
        JSONObject before=copy(state);try{
            JSONArray orders=state.getJSONArray("orders");for(int i=0;i<orders.length();i++){
                JSONObject old=orders.getJSONObject(i);if(!old.optString("id").equals(localId))continue;
                JSONObject source=response.optJSONObject("order")!=null?response.getJSONObject("order"):response;JSONObject accepted=copy(source);accepted.put("sendState","sent").put("requestId",old.optString("requestId",old.optString("id"))).put("storeId",old.optString("storeId",storeId()));orders.put(i,accepted);save();return;
            }
        }catch(Exception error){state=before;throw error;}
    }
    synchronized void markProblem(String id,String message)throws Exception{JSONObject before=copy(state);try{JSONArray orders=state.getJSONArray("orders");for(int i=0;i<orders.length();i++)if(orders.getJSONObject(i).optString("id").equals(id))orders.getJSONObject(i).put("sendState","review").put("syncError",message);save();}catch(Exception error){state=before;throw error;}}
    synchronized void restoreToCart(String id)throws Exception{
        if(count()>0)throw new Exception("Vacía o termina tu carrito actual antes de revisar esta solicitud.");JSONObject before=copy(state);
        try{JSONArray orders=state.getJSONArray("orders");for(int i=0;i<orders.length();i++){
            JSONObject order=orders.getJSONObject(i);if(!order.optString("id").equals(id))continue;
            if(!Arrays.asList("review","legacy","unbound").contains(order.optString("sendState")))throw new Exception("Esta solicitud puede estar recibida. Primero actualiza su estado.");
            JSONObject cart=new JSONObject();JSONArray items=order.getJSONArray("items");for(int j=0;j<items.length();j++){JSONObject item=items.getJSONObject(j);if(find(item.optString("productId"))!=null)cart.put(item.optString("productId"),item.optInt("quantity"));}
            state.put("cart",cart).put("cartSale",SaleSchedule.key(order)).put("details",copy(order));order.put("sendState","superseded");save();return;
        }}catch(Exception error){state=before;throw error;}
    }
}
