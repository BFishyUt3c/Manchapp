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
            if(SaleSchedule.rollDemoIfNeeded(state.getJSONObject("catalog"),System.currentTimeMillis()))save();
        }catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}
    }
    static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(JSONException error){throw new IllegalStateException(error);}}
    synchronized JSONObject snapshot(){return copy(state);}
    private void save()throws IOException{file.save(state);}
    synchronized String serverUrl(){return state.optString("serverUrl");}
    synchronized String storeId(){return state.optJSONObject("catalog").optString("storeId");}
    private JSONObject find(String id){JSONArray products=state.optJSONObject("catalog").optJSONArray("products");for(int i=0;i<products.length();i++)if(products.optJSONObject(i).optString("id").equals(id))return products.optJSONObject(i);return null;}
    private JSONObject extraCart() {
        JSONObject extra = state.optJSONObject("extraCart");
        return extra == null ? new JSONObject() : extra;
    }

    synchronized JSONObject shoppingCart() throws JSONException {
        JSONObject combined = copy(state.getJSONObject("cart"));
        JSONObject extra = extraCart();
        for (Iterator<String> ids = extra.keys(); ids.hasNext();) {
            String id = ids.next();
            combined.put(id, combined.optInt(id) + extra.optInt(id));
        }
        return combined;
    }

    private void pruneCart() throws JSONException {
        JSONObject wanted = shoppingCart();
        JSONObject kept = new JSONObject(), extra = new JSONObject();
        for (Iterator<String> ids = wanted.keys(); ids.hasNext();) {
            String id = ids.next();
            JSONObject product = find(id);
            if (product == null || !product.optString("line").equals("commercial")
                    || !product.optBoolean("available")) continue;
            int original = state.getJSONObject("cart").optInt(id);
            int regular = Math.min(original, Math.max(0, product.optInt("stock")));
            if (regular > 0) kept.put(id, regular);
            if (wanted.optInt(id) > regular) extra.put(id, wanted.optInt(id) - regular);
        }
        state.put("cart", kept).put("extraCart", extra);
        if (kept.length() == 0 && extra.length() == 0) {
            state.remove("cartSale");
            state.remove("cartRequestId");
        }
    }

    private JSONObject schedule(JSONObject product) {
        return SaleSchedule.forProduct(product, state.optJSONObject("catalog").optJSONObject("settings"));
    }

    private JSONObject cartSchedule() {
        JSONObject cart = state.optJSONObject("cart");
        Iterator<String> ids = cart.length() == 0 ? extraCart().keys() : cart.keys();
        while (ids.hasNext()) {
            JSONObject product = find(ids.next());
            if (product != null) return schedule(product);
        }
        return state.optJSONObject("catalog").optJSONObject("settings");
    }
    synchronized JSONObject currentSale(){return copy(cartSchedule());}
    synchronized JSONObject adjustQuantity(String id, int delta) throws Exception {
        int current = state.getJSONObject("cart").optInt(id);
        quantity(id, Math.max(0, current + delta));
        return copy(state.getJSONObject("cart"));
    }

    synchronized JSONObject changeExtra(String id, int delta) throws Exception {
        JSONObject product = find(id);
        if (product == null || !product.optString("line").equals("commercial")) {
            throw new Exception("No encontramos ese producto.");
        }
        JSONObject extras = extraCart();
        int quantity = Math.max(0, extras.optInt(id) + delta);
        if (delta > 0) {
            if (!product.optBoolean("available")) throw new Exception("Este producto está pausado.");
            if (quantity + state.getJSONObject("cart").optInt(id) > 99) {
                throw new Exception("Puedes pedir hasta 99 unidades por producto.");
            }
            requireShoppingSale(schedule(product));
        }
        JSONObject before = copy(state);
        try {
            if (shoppingCount() == 0) state.put("cartSale", SaleSchedule.key(schedule(product)));
            if (quantity == 0) extras.remove(id);
            else extras.put(id, quantity);
            state.put("extraCart", extras);
            if (shoppingCount() == 0) state.remove("cartSale");
            save();
            return copy(extras);
        } catch (Exception error) {
            state = before;
            throw error;
        }
    }

    private void requireShoppingSale(JSONObject settings) throws Exception {
        SaleSchedule.requireOpen(settings, System.currentTimeMillis());
        if (cartNeedsReview()) throw new Exception("La jornada cambió. Revisa el carrito antes de agregar productos.");
        if (shoppingCount() > 0 && !SaleSchedule.key(cartSchedule()).equals(SaleSchedule.key(settings))) {
            throw new Exception("Este producto se vende en otra fecha. Termina este pedido y luego crea uno nuevo.");
        }
    }

    synchronized void quantity(String id,int amount)throws Exception{
        JSONObject product=find(id);if(product==null||!product.optString("line").equals("commercial"))throw new Exception("Este producto no pertenece al catálogo de ventas.");
        int current = state.getJSONObject("cart").optInt(id);
        if (amount == current) return;
        if (amount < 0 || amount > 99) throw new Exception("Revisa la cantidad disponible; máximo 99 por producto.");
        JSONObject settings = schedule(product);
        if (amount > current) {
            if (!product.optBoolean("available") || amount > product.optInt("stock")) {
                throw new Exception("Revisa los cupos disponibles de este producto.");
            }
            if (amount + extraCart().optInt(id) > 99) throw new Exception("Puedes pedir hasta 99 unidades por producto.");
            requireShoppingSale(settings);
        }
        JSONObject before=copy(state);try{if(shoppingCount()==0)state.put("cartSale",SaleSchedule.key(settings));if(amount>0&&state.optString("cartRequestId").isEmpty())state.put("cartRequestId",UUID.randomUUID().toString());if(amount==0)state.getJSONObject("cart").remove(id);else state.getJSONObject("cart").put(id,amount);if(count()==0)state.remove("cartRequestId");save();}catch(Exception error){state=before;throw error;}
    }
    JSONObject bundledSale(Context context)throws Exception{return new JSONObject(SafeJson.read(context.getAssets().open("seed.json"))).getJSONObject("settings");}
    synchronized boolean cartNeedsReview(){return shoppingCount()>0&&!state.optString("cartSale",SaleSchedule.key(cartSchedule())).equals(SaleSchedule.key(cartSchedule()));}
    synchronized void loadBundledCatalog(Context context)throws Exception{
        if(!serverUrl().isEmpty())throw new Exception("Este catálogo está vinculado. Actualízalo desde la laptop.");
        JSONObject before=copy(state);try{state.put("catalog",new JSONObject(SafeJson.read(context.getAssets().open("seed.json"))));SaleSchedule.rollDemoIfNeeded(state.getJSONObject("catalog"),System.currentTimeMillis());pruneCart();save();}catch(Exception error){state=before;throw error;}
    }
    synchronized void acceptCatalog(JSONObject remote,String endpoint)throws Exception{
        if(remote.optJSONObject("settings")==null||remote.optJSONArray("products")==null||remote.optString("storeId").isEmpty())throw new IOException("La dirección no corresponde al servidor actualizado de ManchApp.");
        if(!storeId().isEmpty()&&!storeId().equals(remote.optString("storeId")))throw new IOException("Esta laptop contiene otro catálogo. Conservamos tus pedidos y carrito.");
        if(!storeId().isEmpty()&&remote.optInt("revision")<state.getJSONObject("catalog").optInt("revision"))return;
        JSONObject before=copy(state);
        try{if(count()>0&&!state.has("cartSale"))state.put("cartSale",SaleSchedule.key(state.getJSONObject("catalog").getJSONObject("settings")));state.put("catalog",copy(remote)).put("serverUrl",HttpApi.endpoint(endpoint));SaleSchedule.rollDemoIfNeeded(state.getJSONObject("catalog"),System.currentTimeMillis());pruneCart();save();}catch(Exception error){state=before;throw error;}
    }
    synchronized void acceptDeviceCatalog(JSONObject remote)throws Exception{
        if(remote.optJSONObject("settings")==null||remote.optJSONArray("products")==null)throw new IOException("ManchApp Emprendedora todavía no tiene un catálogo disponible.");JSONObject before=copy(state);try{if(count()>0&&!state.has("cartSale"))state.put("cartSale",SaleSchedule.key(cartSchedule()));state.put("catalog",copy(remote)).remove("serverUrl");SaleSchedule.rollDemoIfNeeded(state.getJSONObject("catalog"),System.currentTimeMillis());pruneCart();save();}catch(Exception error){state=before;throw error;}
    }
    synchronized void clearCart()throws Exception{JSONObject before=copy(state);try{state.put("cart",new JSONObject()).remove("cartSale");state.remove("extraCart");state.remove("cartRequestId");save();}catch(Exception error){state=before;throw error;}}
    synchronized void details(JSONObject value)throws Exception{if(state.optJSONObject("details").toString().equals(value.toString()))return;JSONObject before=copy(state);try{state.put("details",copy(value));save();}catch(Exception error){state=before;throw error;}}
    synchronized double subtotal(){JSONObject cart=state.optJSONObject("cart");double sum=0;Iterator<String> ids=cart.keys();while(ids.hasNext()){String id=ids.next();JSONObject product=find(id);if(product!=null)sum+=product.optDouble("price")*cart.optInt(id);}return SaleRules.money(sum);}
    synchronized int count(){JSONObject cart=state.optJSONObject("cart");int count=0;Iterator<String> ids=cart.keys();while(ids.hasNext())count+=cart.optInt(ids.next());return count;}
    synchronized int extraCount() {
        JSONObject extra = extraCart();
        int count = 0;
        for (Iterator<String> ids = extra.keys(); ids.hasNext();) count += extra.optInt(ids.next());
        return count;
    }
    synchronized int shoppingCount() { return count() + extraCount(); }

    synchronized JSONArray extraItems() throws JSONException {
        JSONObject extra = extraCart();
        JSONArray items = new JSONArray();
        for (Iterator<String> ids = extra.keys(); ids.hasNext();) {
            String id = ids.next();
            JSONObject product = find(id);
            if (product != null) items.put(new JSONObject().put("productId", id)
                    .put("name", product.optString("name")).put("quantity", extra.optInt(id)));
        }
        return items;
    }

    synchronized double fee(JSONObject details)throws Exception{
        if(!details.optString("fulfillment","pickup").equals("delivery"))return 0;
        JSONObject settings = cartSchedule();
        JSONObject zone = DeliveryDestination.verifiedZone(settings, details);
        return SaleRules.deliveryFee(subtotal(), zone.optDouble("fee"), settings.optDouble("deliveryFreeFrom", 0));
    }

    synchronized JSONObject placePracticeOrder(JSONObject details) throws Exception {
        return placePracticeOrder(details, System.currentTimeMillis());
    }

    synchronized JSONObject placeReviewedOrder(JSONObject details, int revision) throws Exception {
        return placeReviewedShopping(details, revision, false);
    }

    synchronized JSONObject placeReviewedShopping(JSONObject details, int revision, boolean includeExtras) throws Exception {
        if (revision != state.getJSONObject("catalog").optInt("revision")) {
            throw new Exception("El catálogo cambió. Revisa otra vez el total y la fecha antes de enviar.");
        }
        return queueShopping(details, System.currentTimeMillis(), includeExtras);
    }

    synchronized JSONObject placePracticeOrder(JSONObject details, long now) throws Exception {
        return queueShopping(details, now, false);
    }

    private JSONObject queueShopping(JSONObject details, long now, boolean includeExtras) throws Exception {
        String requestId = details.optString("requestId", state.optString("cartRequestId")).trim();
        if (requestId.isEmpty()) requestId = UUID.randomUUID().toString();
        JSONArray orders = state.getJSONArray("orders");
        for (int i = 0; i < orders.length(); i++) {
            JSONObject existing = orders.getJSONObject(i);
            if (requestId.equals(existing.optString("requestId"))) return copy(existing);
        }
        if (cartNeedsReview()) throw new Exception("La jornada cambió. Revisa tu carrito.");
        JSONObject payload = copy(details).put("requestId", requestId);
        JSONObject order = count() == 0 ? null : OrderDraft.build(
                state.getJSONObject("catalog"), payload, state.getJSONObject("cart"), false, now);
        JSONObject extra = includeExtras && extraCount() > 0
                ? buildExtra(details, order == null ? "" : requestId, now) : null;
        if (order == null && extra == null) throw new Exception("Elige al menos un producto.");

        JSONObject before = copy(state);
        try {
            if (extra != null) {
                JSONArray requests = state.optJSONArray("extraRequests");
                if (requests == null) requests = new JSONArray();
                state.put("extraRequests", requests.put(extra));
            }
            if (order != null) {
                int number = state.optInt("nextNumber", 1);
                order.put("number", number).put("sendState", "device_pending").remove("serverUrl");
                if (extra != null) order.put("extraRequestId", extra.getString("id"));
                JSONArray newest = new JSONArray().put(order);
                for (int i = 0; i < orders.length(); i++) newest.put(orders.get(i));
                state.put("orders", newest).put("nextNumber", number + 1);
            }
            JSONObject savedDetails = DeliveryDestination.normalized(details);
            savedDetails.remove("requestId");
            state.put("details", savedDetails).put("cart", new JSONObject());
            state.remove("extraCart");
            state.remove("cartSale");
            state.remove("cartRequestId");
            save();
            return copy(order == null ? extra : order);
        } catch (Exception error) {
            state = before;
            throw error;
        }
    }

    private JSONObject buildExtra(JSONObject details, String baseRequestId, long now) throws Exception {
        JSONObject sale = cartSchedule();
        SaleSchedule.requireOpen(sale, now);
        JSONArray items = extraItems();
        String date = sale.optString("saleDate");
        String name = details.optString("customer").trim(), phone = details.optString("phone");
        String fulfillment = details.optString("fulfillment", "pickup");
        if (items.length() == 0 || items.length() > 30 || name.length() < 2 || !SaleRules.validPhone(phone)) {
            throw new Exception("Revisa tus datos y los productos de la solicitud.");
        }
        if ("delivery".equals(fulfillment)) {
            if (details.optString("address").trim().length() < 4 || details.optString("reference").trim().length() < 3) {
                throw new Exception("Completa la dirección y referencia para la entrega.");
            }
            String invalid = DeliveryDestination.problem(details, date);
            if (!invalid.isEmpty()) throw new Exception(invalid);
            DeliveryDestination.verifiedZone(sale, details);
        }
        String id = UUID.randomUUID().toString();
        return new JSONObject().put("id", id).put("requestId", id).put("extra", true)
                .put("customer", name).put("phone", phone).put("saleDate", date)
                .put("items", items).put("status", "pending").put("sendState", "pending").put("createdAt", now)
                .put("fulfillment", fulfillment).put("address", details.optString("address"))
                .put("reference", details.optString("reference")).put("baseRequestId", baseRequestId);
    }

    synchronized JSONArray extraRequestsToSync() {
        JSONArray all = state.optJSONArray("extraRequests"), pending = new JSONArray();
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONObject extra = all.optJSONObject(i);
            if (extra != null && extra.optString("status").equals("pending")) pending.put(copy(extra));
        }
        return pending;
    }

    synchronized void acceptExtraReply(String id, JSONObject reply) throws Exception {
        JSONArray all = state.optJSONArray("extraRequests");
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONObject extra = all.getJSONObject(i);
            if (!id.equals(extra.optString("id"))) continue;
            String status = reply.optString("status", "pending");
            long receivedAt = reply.optLong("createdAt");
            boolean recoverTime = extra.optLong("createdAt") <= 0 && receivedAt > 0;
            double amount = reply.optDouble("extraAmount");
            int linkedOrder = reply.optInt("sameDeliveryOrderNumber");
            boolean quoteChanged = "approved".equals(status)
                    && ((Double.isFinite(amount) && Double.compare(amount, extra.optDouble("extraAmount")) != 0)
                        || (linkedOrder > 0 && linkedOrder != extra.optInt("sameDeliveryOrderNumber")));
            if (!recoverTime && !quoteChanged && extra.optString("status").equals(status)
                    && extra.optString("sendState").equals("sent")) return;
            JSONObject before = copy(state);
            try {
                extra.put("status", status).put("sendState", "sent");
                if (recoverTime) extra.put("createdAt", receivedAt);
                if ("approved".equals(status)) {
                    if (Double.isFinite(amount)) extra.put("extraAmount", amount);
                    if (linkedOrder > 0) extra.put("sameDeliveryOrderNumber", linkedOrder);
                }
                save();
            } catch (Exception error) {
                state = before;
                throw error;
            }
            return;
        }
    }

    synchronized JSONArray pending(){JSONArray result=new JSONArray(),orders=state.optJSONArray("orders");for(int i=0;i<orders.length();i++){JSONObject order=orders.optJSONObject(i);if(Arrays.asList("pending","device_pending","unbound").contains(order.optString("sendState")))result.put(copy(order));}return result;}
    synchronized void acceptOrder(String localId,JSONObject response)throws Exception{
        JSONObject before=copy(state);try{
            JSONArray orders=state.getJSONArray("orders");for(int i=0;i<orders.length();i++){
                JSONObject old=orders.getJSONObject(i);if(!old.optString("id").equals(localId))continue;
                JSONObject source=response.optJSONObject("order")!=null?response.getJSONObject("order"):response;JSONObject accepted=copy(source);accepted.put("sendState","sent").put("requestId",old.optString("requestId",old.optString("id"))).put("storeId",old.optString("storeId",storeId()));if(old.has("extraRequestId"))accepted.put("extraRequestId",old.optString("extraRequestId"));long submittedAt=old.optLong("submittedAt",old.optLong("createdAt"));if(submittedAt>0)accepted.put("submittedAt",submittedAt);orders.put(i,accepted);save();return;
            }
        }catch(Exception error){state=before;throw error;}
    }
    synchronized void markProblem(String id,String message)throws Exception{JSONObject before=copy(state);try{JSONArray orders=state.getJSONArray("orders");for(int i=0;i<orders.length();i++)if(orders.getJSONObject(i).optString("id").equals(id))orders.getJSONObject(i).put("sendState","review").put("syncError",message);save();}catch(Exception error){state=before;throw error;}}
    synchronized void rejectOrder(String id,String message)throws Exception{JSONObject before=copy(state);try{JSONArray orders=state.getJSONArray("orders");for(int i=0;i<orders.length();i++){JSONObject order=orders.getJSONObject(i);if(order.optString("id").equals(id))order.put("sendState","rejected").put("status","cancelled").put("syncError",message);}save();}catch(Exception error){state=before;throw error;}}
    synchronized void markRestockRequested(String id,String requestId)throws Exception{JSONObject before=copy(state);try{JSONArray orders=state.getJSONArray("orders");for(int i=0;i<orders.length();i++){JSONObject order=orders.getJSONObject(i);if(order.optString("id").equals(id))order.put("restockRequested",true).put("restockRequestId",requestId);}save();}catch(Exception error){state=before;throw error;}}
    synchronized void reorderToCart(String orderId)throws Exception{
        JSONArray orders=state.optJSONArray("orders");JSONObject source=null;
        for(int i=0;orders!=null&&i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o.optString("id").equals(orderId)||o.optString("requestId").equals(orderId)){source=o;break;}}
        if(source==null)throw new Exception("No encontramos ese pedido.");
        JSONArray items=source.optJSONArray("items");if(items==null||items.length()==0)throw new Exception("Este pedido no tiene productos.");
        JSONObject before=copy(state);
        try{
            clearCart();
            int added=0;
            for(int i=0;i<items.length();i++){
                JSONObject item=items.getJSONObject(i);
                JSONObject product=find(item.optString("productId"));
                if(product==null||!product.optString("line").equals("commercial")||!product.optBoolean("available")||product.optInt("stock")<=0)continue;
                int qty=Math.min(item.optInt("quantity",1),product.optInt("stock"));
                if(qty<=0)continue;
                try{quantity(product.optString("id"),qty);added++;}catch(Exception ignored){}
            }
            if(added==0)throw new Exception("Los productos de ese pedido no están disponibles para la jornada actual.");
            save();
        }catch(Exception error){state=before;throw error;}
    }
    synchronized void restoreToCart(String id)throws Exception{
        if(shoppingCount()>0)throw new Exception("Vacía o termina tu carrito actual antes de revisar esta solicitud.");JSONObject before=copy(state);
        try{JSONArray orders=state.getJSONArray("orders");for(int i=0;i<orders.length();i++){
            JSONObject order=orders.getJSONObject(i);if(!order.optString("id").equals(id))continue;
            if(!Arrays.asList("review","legacy","unbound").contains(order.optString("sendState")))throw new Exception("Esta solicitud puede estar recibida. Primero actualiza su estado.");
            JSONObject cart=new JSONObject();JSONArray items=order.getJSONArray("items");for(int j=0;j<items.length();j++){JSONObject item=items.getJSONObject(j);if(find(item.optString("productId"))!=null)cart.put(item.optString("productId"),item.optInt("quantity"));}
            JSONObject restoredDetails = copy(order);
            restoredDetails.remove("requestId");
            restoredDetails.remove("deliveryLocation");
            state.put("cart",cart).put("details",restoredDetails);
            state.remove("cartRequestId");
            pruneCart();
            if (shoppingCount() == 0) throw new Exception("Los productos ya no forman parte del catálogo.");
            state.put("cartSale", SaleSchedule.key(cartSchedule()));
            order.put("sendState","superseded");save();return;
        }}catch(Exception error){state=before;throw error;}
    }
}
