package pe.manchapp.beta;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Durable local snapshot plus an idempotent queue; no network needed to edit. */
public final class LocalStore {
    private static final java.util.Map<String,LocalStore> instances=new java.util.HashMap<>();
    static synchronized LocalStore open(Context context){String key=context.getFilesDir().getAbsolutePath();LocalStore value=instances.get(key);if(value==null){value=new LocalStore(context.getApplicationContext());instances.put(key,value);}return value;}
    private final SafeJson file;
    private final Context context;
    private JSONObject data;
    public LocalStore(Context context) { this(context,"manchapp.json"); }
    LocalStore(Context context,String filename) {
        this.context=context.getApplicationContext();file=new SafeJson(new File(context.getFilesDir(),filename),"settings");
        try{JSONObject seed=new JSONObject(read(context.getAssets().open("seed.json")));seed.put("pending",new JSONArray());data=file.load(seed);}catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}
        upgrade(context);
    }
    private void upgrade(Context context){try{JSONObject seed=new JSONObject(read(context.getAssets().open("seed.json"))),defaults=seed.getJSONObject("settings");boolean changed=SaleSchedule.upgrade(data,defaults);if(data.optString("storeId").isEmpty()){data.put("storeId",UUID.randomUUID().toString());changed=true;}if(data.optJSONArray("restockRequests")==null){data.put("restockRequests",new JSONArray());changed=true;}JSONObject settings=data.getJSONObject("settings");if(settings.optString("name").equals("Mujeres Emprendedoras")){settings.put("name","Manka Kusi");changed=true;}if(settings.optString("pickup").contains("Módulo Carrió")){settings.put("pickup",defaults.optString("pickup"));changed=true;}if(settings.optString("phone").isEmpty()&&!defaults.optString("phone").isEmpty()){settings.put("phone",defaults.optString("phone"));changed=true;}for(String key:new String[]{"latitude","longitude"})if(!settings.has(key)&&defaults.has(key)){settings.put(key,defaults.get(key));changed=true;}if(SaleSchedule.rollDemoIfNeeded(data,System.currentTimeMillis()))changed=true;if(settings.optString("saleDate").equals("2026-11-06")){for(String key:SaleSchedule.KEYS)settings.put(key,defaults.get(key));changed=true;}JSONArray products=data.optJSONArray("products"),seedProducts=seed.optJSONArray("products");for(int i=0;products!=null&&i<products.length();i++){JSONObject product=products.getJSONObject(i),fresh=null;for(int j=0;seedProducts!=null&&j<seedProducts.length();j++)if(seedProducts.getJSONObject(j).optString("id").equals(product.optString("id")))fresh=seedProducts.getJSONObject(j);if(fresh!=null&&product.optString("saleDate").equals("2026-11-06")){for(String key:SaleSchedule.KEYS)product.put(key,fresh.get(key));changed=true;}}JSONArray zones=settings.optJSONArray("zones");if(zones==null||zones.length()==0){settings.put("zones",defaults.getJSONArray("zones"));changed=true;}else{JSONArray examples=defaults.optJSONArray("zones");for(int i=0;i<zones.length();i++){JSONObject zone=zones.optJSONObject(i);if(zone==null||zone.has("maxKm"))continue;for(int j=0;examples!=null&&j<examples.length();j++){JSONObject example=examples.optJSONObject(j);if(example!=null&&example.optString("id").equals(zone.optString("id"))&&example.has("maxKm")){zone.put("maxKm",example.get("maxKm"));changed=true;break;}}}}if(changed)save();}catch(Exception error){throw new IllegalStateException("No pudimos actualizar las fechas de venta.",error);}}
    public static String read(InputStream stream) throws IOException { try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); return new String(out.toByteArray(),StandardCharsets.UTF_8); } }
    public static JSONObject copy(JSONObject value) { try { return new JSONObject(value.toString()); } catch(JSONException e) { throw new IllegalStateException(e); } }
    public synchronized JSONObject snapshot() { return copy(data); }
    private void save() throws IOException {file.save(data);context.getContentResolver().notifyChange(android.net.Uri.parse("content://pe.manchapp.beta.bridge/catalog"),null);}
    synchronized JSONObject publicCatalog(){JSONObject result=copy(data);result.remove("orders");result.remove("pending");result.remove("nextLocalNumber");result.remove("restockRequests");return result;}
    synchronized void saveSchedule(JSONArray productIds,JSONObject schedule)throws Exception{JSONObject before=copy(data);try{if(productIds==null||productIds.length()==0)throw new IOException("Elige al menos un producto para esta venta.");JSONArray products=data.getJSONArray("products");int changed=0;for(int i=0;i<products.length();i++){JSONObject product=products.getJSONObject(i);boolean selected=false;for(int j=0;j<productIds.length();j++)if(product.optString("id").equals(productIds.optString(j)))selected=true;if(!selected)continue;for(String key:SaleSchedule.KEYS)product.put(key,schedule.get(key));product.put("version",product.optInt("version")+1);changed++;}if(changed==0)throw new IOException("No encontramos los productos elegidos.");JSONObject settings=data.getJSONObject("settings");for(String key:SaleSchedule.KEYS)settings.put(key,schedule.get(key));settings.put("demo",false);save();}catch(Exception error){data=before;throw error;}}
    synchronized void deleteProduct(String id)throws Exception{JSONObject before=copy(data);try{JSONArray kept=new JSONArray(),products=data.optJSONArray("products");boolean found=false;for(int i=0;products!=null&&i<products.length();i++){JSONObject product=products.getJSONObject(i);if(product.optString("id").equals(id)){found=true;continue;}kept.put(product);}if(!found)throw new IOException("Este producto ya no existe.");JSONArray promosKept=new JSONArray(),promos=data.optJSONArray("promos");for(int i=0;promos!=null&&i<promos.length();i++)if(!promos.getJSONObject(i).optString("productId").equals(id))promosKept.put(promos.getJSONObject(i));JSONArray pendingKept=new JSONArray(),pending=data.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++)if(!pending.getJSONObject(i).optJSONObject("data").optString("id").equals(id))pendingKept.put(pending.getJSONObject(i));data.put("products",kept).put("promos",promosKept).put("pending",pendingKept);save();}catch(Exception error){data=before;throw error;}}
    synchronized JSONObject receiveDeviceOrder(JSONObject request) throws Exception {
        String requestId = request.optString("requestId", request.optString("id")).trim();
        if (requestId.isEmpty()) throw new IOException("La solicitud no tiene identificador de envío.");
        JSONArray orders = data.getJSONArray("orders");
        String incomingHash = request.optString("requestHash");
        for (int i = 0; i < orders.length(); i++) {
            JSONObject existing = orders.getJSONObject(i);
            if (!existing.optString("requestId").equals(requestId)) continue;
            String existingHash = existing.optString("requestHash");
            if (!existingHash.isEmpty() && !incomingHash.isEmpty() && !existingHash.equals(incomingHash)) {
                throw new IOException("Este identificador ya corresponde a otro pedido.");
            }
            return copy(existing);
        }

        JSONObject quantities = new JSONObject();
        JSONArray incoming = request.optJSONArray("items");
        for (int i = 0; incoming != null && i < incoming.length(); i++) {
            JSONObject item = incoming.getJSONObject(i);
            quantities.put(item.getString("productId"), item.get("quantity"));
        }
        JSONObject order = OrderDraft.build(snapshot(), request, quantities, false, System.currentTimeMillis());
        JSONObject expected = request.optJSONObject("expected");
        JSONObject current = order.getJSONObject("expected");
        if (expected != null && (!expected.optString("saleKey").equals(current.getString("saleKey"))
                || !expected.optString("pickup").equals(current.getString("pickup"))
                || Math.abs(expected.optDouble("subtotal", -1) - current.getDouble("subtotal")) > 0.001
                || Math.abs(expected.optDouble("fee", -1) - current.getDouble("fee")) > 0.001
                || Math.abs(expected.optDouble("total", -1) - current.getDouble("total")) > 0.001)) {
            throw new IOException("Cambió la fecha, el precio o la entrega. Revisa el pedido antes de enviarlo.");
        }

        JSONObject before = copy(data);
        try {
            order.put("requestId", requestId).put("number", data.optInt("nextLocalNumber", 1))
                    .put("status", "confirmed").put("sendState", "sent").put("version", 1).put("localOnly", false);
            if (!incomingHash.isEmpty()) order.put("requestHash", incomingHash);
            JSONArray products = data.getJSONArray("products");
            JSONArray items = order.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                for (int j = 0; j < products.length(); j++) {
                    JSONObject product = products.getJSONObject(j);
                    if (!product.optString("id").equals(item.optString("productId"))) continue;
                    int quantity = item.optInt("quantity");
                    if (product.optInt("stock") < quantity) {
                        throw new IOException("Ya no quedan cupos suficientes para este producto.");
                    }
                    product.put("stock", product.optInt("stock") - quantity)
                            .put("version", product.optInt("version") + 1);
                }
            }
            orders.put(order);
            data.put("nextLocalNumber", order.optInt("number") + 1);
            save();
            return copy(order);
        } catch (Exception error) {
            data = before;
            throw error;
        }
    }
    synchronized JSONObject deviceOrder(String id)throws Exception{JSONArray orders=data.optJSONArray("orders");for(int i=0;orders!=null&&i<orders.length();i++){JSONObject order=orders.getJSONObject(i);if(order.optString("id").equals(id)||order.optString("requestId").equals(id))return copy(order);}throw new IOException("No encontramos este pedido en Manka Kusi.");}
    synchronized JSONObject requestMore(JSONObject request) throws Exception {
        String id = request.optString("requestId").trim();
        JSONArray items = request.optJSONArray("items");
        String name = request.optString("customer").trim(), phone = request.optString("phone");
        if (id.isEmpty() || items == null || items.length() == 0 || items.length() > 30
                || name.length() < 2 || !SaleRules.validPhone(phone)) {
            throw new IOException("Completa los datos de la solicitud de cupos extra.");
        }
        JSONArray all = data.getJSONArray("restockRequests");
        for (int i = 0; i < all.length(); i++) {
            JSONObject existing = all.getJSONObject(i);
            if (id.equals(existing.optString("id"))) return copy(existing);
        }
        JSONArray products = data.getJSONArray("products");
        JSONArray requested = new JSONArray();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String productId = item.optString("productId");
            int quantity = item.optInt("quantity");
            JSONObject product = null;
            for (int j = 0; j < products.length(); j++) {
                JSONObject candidate = products.getJSONObject(j);
                if (productId.equals(candidate.optString("id"))) {
                    product = candidate;
                    break;
                }
            }
            if (product == null || !product.optString("line").equals("commercial")
                    || quantity < 1 || quantity > 99 || !seen.add(productId)) {
                throw new IOException("Revisa los productos y las cantidades de la solicitud.");
            }
            requested.put(new JSONObject().put("productId", productId)
                    .put("name", product.optString("name")).put("quantity", quantity));
        }
        JSONObject entry = new JSONObject().put("id", id).put("customer", name).put("phone", phone)
                .put("saleDate", request.optString("saleDate")).put("items", requested)
                .put("fulfillment", request.optString("fulfillment", "pickup"))
                .put("address", request.optString("address")).put("reference", request.optString("reference"))
                .put("baseRequestId", request.optString("baseRequestId"))
                .put("createdAt", System.currentTimeMillis()).put("status", "pending");
        JSONObject before = copy(data);
        try {
            all.put(entry);
            save();
            return copy(entry);
        } catch (Exception error) {
            data = before;
            throw error;
        }
    }

    synchronized JSONObject extraRequest(String id) throws Exception {
        return copy(findExtraRequest(id));
    }

    private JSONObject findExtraRequest(String id) throws Exception {
        JSONArray all = data.getJSONArray("restockRequests");
        for (int i = 0; i < all.length(); i++) {
            JSONObject request = all.getJSONObject(i);
            if (id.equals(request.optString("id"))) return request;
        }
        throw new IOException("No encontramos esta solicitud.");
    }

    synchronized JSONObject extraDeliveryOrder(JSONObject request) throws Exception {
        String baseId = request.optString("baseRequestId");
        if (baseId.isEmpty()) return null;
        JSONArray orders = data.getJSONArray("orders");
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.getJSONObject(i);
            if (!baseId.equals(order.optString("requestId"))) continue;
            String status = order.optString("status");
            if (!java.util.Arrays.asList("confirmed", "preparing", "ready", "sale_day").contains(status)
                    || !order.optString("saleDate").equals(request.optString("saleDate"))
                    || !order.optString("fulfillment").equals(request.optString("fulfillment"))
                    || !order.optString("address").trim().equals(request.optString("address").trim())) return null;
            return copy(order);
        }
        return null;
    }

    static JSONArray deliveryExtras(JSONObject state, String orderId) {
        JSONArray all = state.optJSONArray("restockRequests"), selected = new JSONArray();
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONObject extra = all.optJSONObject(i);
            if (extra != null && "approved".equals(extra.optString("status"))
                    && orderId.equals(extra.optString("sameDeliveryOrderId"))) selected.put(extra);
        }
        return selected;
    }

    synchronized void resolveExtraRequest(String id, boolean approve, double amount, boolean sameDelivery) throws Exception {
        JSONObject request = findExtraRequest(id);
        if (!request.optString("status").equals("pending")) {
            throw new IOException("Esta solicitud ya fue revisada.");
        }
        if (approve && (!Double.isFinite(amount) || amount < 0 || amount > 99999.99
                || Math.abs(amount - SaleRules.money(amount)) > .000001)) {
            throw new IOException("Ingresa un monto válido con hasta dos decimales.");
        }
        JSONObject base = approve && sameDelivery ? extraDeliveryOrder(request) : null;
        if (approve && sameDelivery && base == null) throw new IOException("El pedido original ya no admite esta entrega.");
        JSONObject before = copy(data);
        try {
            request.put("status", approve ? "approved" : "rejected").put("resolvedAt", System.currentTimeMillis());
            if (approve) {
                request.put("extraAmount", SaleRules.money(amount));
                if (base != null) request.put("sameDeliveryOrderId", base.getString("id"))
                        .put("sameDeliveryOrderNumber", base.getInt("number"));
            }
            save();
        } catch (Exception error) {
            data = before;
            throw error;
        }
    }

    public synchronized void enqueue(String type, JSONObject value) throws Exception {
        JSONObject before=copy(data);
        try { JSONArray pending=data.optJSONArray("pending"); if(pending==null) pending=new JSONArray();
            if (type.equals("order") && isDemoOrder(value.optString("id"))) {
                overlay(new JSONObject().put("type", type).put("data", copy(value)));
                save();
                return;
            }
            if(type.equals("product"))for(int i=0;i<pending.length();i++){
                JSONObject old=pending.getJSONObject(i);
                if(old.optString("type").equals(type)&&old.getJSONObject("data").optString("id").equals(value.optString("id"))&&old.has("attempted")&&!old.optBoolean("attempted")){
                    old.put("data",copy(value));overlay(old);save();return;
                }
            }
            JSONObject op=new JSONObject().put("id",UUID.randomUUID().toString()).put("type",type).put("data",copy(value)).put("attempted",false);
            pending.put(op); data.put("pending",pending); overlay(op); save();
        } catch(Exception e) { data=before; throw e; }
    }
    private void overlay(JSONObject op) throws JSONException {
        String type=op.optString("type"); JSONObject value=op.getJSONObject("data");
        if(type.equals("settings")) { JSONObject s=data.getJSONObject("settings"); java.util.Iterator<String> keys=value.keys(); while(keys.hasNext()){String k=keys.next();s.put(k,value.get(k));} return; }
        String array=type.equals("product")?"products":type.equals("promo")?"promos":"orders";
        JSONArray all=data.optJSONArray(array); if(all==null)all=new JSONArray(); boolean found=false;
        for(int i=0;i<all.length();i++) if(all.getJSONObject(i).optString("id").equals(value.optString("id"))) { if(type.equals("order")){JSONObject o=all.getJSONObject(i);String previous=o.optString("status"),target=value.optString("status",previous);if(target.equals("cancelled")&&!previous.equals("cancelled"))restoreOrderStock(o);java.util.Iterator<String> it=value.keys();while(it.hasNext()){String k=it.next();o.put(k,value.get(k));}o.put("version",value.optInt("version")+1);o.put("sendState","sent");}else if(!type.equals("manualOrder"))all.put(i,copy(value)); found=true; break; }
        if(!found){JSONObject local=copy(value);if(type.equals("manualOrder")){local.put("sendState","sent").put("localOnly",true);JSONArray products=data.optJSONArray("products"),items=local.optJSONArray("items");for(int i=0;products!=null&&i<products.length();i++)for(int j=0;items!=null&&j<items.length();j++)if(products.getJSONObject(i).optString("id").equals(items.getJSONObject(j).optString("productId")))products.getJSONObject(i).put("stock",Math.max(0,products.getJSONObject(i).optInt("stock")-items.getJSONObject(j).optInt("quantity")));}all.put(local);} data.put(array,all);
    }
    private void restoreOrderStock(JSONObject order) throws JSONException {
        if (order.optBoolean("demoOrder")) return;
        JSONObject expected = order.optJSONObject("expected");
        String reservedSale = expected == null ? SaleSchedule.key(order)
                : expected.optString("saleKey", SaleSchedule.key(order));
        if (reservedSale.equals("//")) return;
        JSONObject settings = data.optJSONObject("settings");
        JSONArray products = data.optJSONArray("products");
        JSONArray items = order.optJSONArray("items");
        for (int p = 0; products != null && p < products.length(); p++) {
            JSONObject product = products.getJSONObject(p);
            if (!reservedSale.equals(SaleSchedule.key(SaleSchedule.forProduct(product, settings)))) continue;
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (!product.optString("id").equals(item.optString("productId"))) continue;
                product.put("stock", product.optInt("stock") + item.optInt("quantity"))
                        .put("version", product.optInt("version") + 1);
            }
        }
    }

    synchronized void assignDispatch(String date, String driver, java.util.Map<String, Integer> selected) throws Exception {
        if (driver.length() < 2 || driver.length() > 80 || selected.isEmpty() || SaleSchedule.parse(date, true) < 0) {
            throw new IOException("Elige una fecha, un repartidor y al menos un pedido.");
        }
        JSONObject before = copy(data);
        try {
            JSONArray pending = data.getJSONArray("pending");
            for (java.util.Map.Entry<String, Integer> entry : selected.entrySet()) {
                JSONObject order = deviceOrder(entry.getKey());
                if (!DispatchScreen.eligible(order) || !date.equals(order.optString("saleDate"))
                        || order.optInt("version") != entry.getValue()) {
                    throw new IOException("Un pedido cambió. Actualiza la selección y vuelve a intentar.");
                }
                if (driver.equals(order.optString("driver"))) continue;
                if (hasPending(entry.getKey()) && !canEditUnsent(entry.getKey())) {
                    throw new IOException("Un pedido tiene una actualización pendiente de confirmación.");
                }
                JSONObject change = OrderScreen.orderChange(order).put("driver", driver);
                JSONObject operation = new JSONObject().put("id", UUID.randomUUID().toString())
                        .put("type", "order").put("data", change).put("attempted", false);
                if (!order.optBoolean("demoOrder")) pending.put(operation);
                overlay(operation);
            }
            save();
        } catch (Exception error) {
            data = before;
            throw error;
        }
    }

    private boolean isDemoOrder(String id) {
        JSONArray orders = data.optJSONArray("orders");
        for (int i = 0; orders != null && i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order != null && id.equals(order.optString("id"))) return order.optBoolean("demoOrder");
        }
        return false;
    }

    synchronized String addDemoDeliveries() throws Exception {
        JSONObject before = copy(data);
        try {
            long now = System.currentTimeMillis();
            JSONObject settings = data.getJSONObject("settings");
            String date = settings.optString("saleDate");
            if (SaleSchedule.parse(date, true) < 0) date = SaleSchedule.date(now);
            JSONArray orders = data.getJSONArray("orders");
            JSONArray zones = settings.optJSONArray("zones");
            java.util.List<JSONObject> enabledZones = new java.util.ArrayList<>();
            for (int i = 0; zones != null && i < zones.length(); i++) {
                JSONObject zone = zones.optJSONObject(i);
                if (zone != null && zone.optBoolean("enabled")) enabledZones.add(zone);
            }
            int number = data.optInt("nextLocalNumber", 1);
            String[] streets = {"Av. de Ejemplo", "Jr. de Prueba", "Pasaje Demo"};
            String[] references = {"Portón azul", "Frente al parque", "Al lado de la bodega"};
            for (int i = 0; i < 10; i++) {
                int sector = i % streets.length;
                JSONObject zone = enabledZones.isEmpty() ? null : enabledZones.get(i % enabledZones.size());
                double fee = zone == null ? 3 : zone.optDouble("fee", 3);
                int quantity = 1 + i % 3;
                boolean confirmed = i < 7;
                boolean paid = confirmed && i % 3 == 0;
                String id = "demo-" + UUID.randomUUID();
                JSONObject item = new JSONObject().put("productId", "demo-menu")
                        .put("name", "Menú de prueba").put("quantity", quantity).put("price", 8);
                JSONObject order = new JSONObject().put("id", id).put("requestId", id)
                        .put("number", number++).put("version", 1).put("createdAt", now)
                        .put("demoOrder", true).put("localOnly", true).put("sendState", "sent")
                        .put("customer", "Cliente de prueba " + (i + 1)).put("phone", "000000000")
                        .put("fulfillment", "delivery").put("source", "whatsapp").put("destinationType", "home")
                        .put("address", streets[sector] + " " + (100 + 11 * i))
                        .put("reference", references[sector]).put("deliveryWindow", confirmed ? "4:00 a 6:00 p. m." : "")
                        .put("zoneId", zone == null ? "demo-" + sector : zone.optString("id"))
                        .put("zone", zone == null ? "Sector de prueba " + (sector + 1) : zone.optString("name"))
                        .put("status", confirmed ? "confirmed" : "registered")
                        .put("payment", paid ? "yape" : "cash").put("paid", paid)
                        .put("items", new JSONArray().put(item)).put("subtotal", quantity * 8)
                        .put("fee", fee).put("total", SaleRules.money(quantity * 8 + fee));
                if (!confirmed) order.put("demoReviewReason", "El cliente pidió una entrega fuera del horario habitual.");
                orders.put(SaleSchedule.copyInto(order, settings).put("saleDate", date));
            }
            data.put("nextLocalNumber", number);
            save();
            return date;
        } catch (Exception error) {
            data = before;
            throw error;
        }
    }

    /** Product and advertised combo must survive together, even if saving fails. */
    public synchronized void enqueueCombo(JSONObject product, JSONObject promo) throws Exception {
        JSONObject before=copy(data);
        try {
            JSONArray pending=data.optJSONArray("pending");if(pending==null)pending=new JSONArray();
            for(int i=0;i<2;i++){
                JSONObject op=new JSONObject().put("id",UUID.randomUUID().toString()).put("type",i==0?"product":"promo").put("data",i==0?copy(product):promo==null?null:copy(promo)).put("attempted",false);
                pending.put(op);overlay(op);
            }
            data.put("pending",pending);save();
        }catch(Exception error){data=before;throw error;}
    }
    public synchronized void accept(JSONObject remote, String acknowledged) throws Exception {
        JSONObject before=copy(data);
        try {if(!data.optString("storeId").isEmpty()&&!data.optString("storeId").equals(remote.optString("storeId",data.optString("storeId"))))throw new IOException("Esta dirección corresponde a otro catálogo. Conservamos tus datos y cambios pendientes.");if(remote.optInt("revision")<data.optInt("revision"))throw new IOException("Llegó una actualización anterior. Tus cambios se conservaron; vuelve a actualizar.");JSONArray pending=data.optJSONArray("pending"), keep=new JSONArray();
            for(int i=0;pending!=null && i<pending.length();i++)if(!pending.getJSONObject(i).optString("id").equals(acknowledged))keep.put(pending.getJSONObject(i));
            int localNumber=data.optInt("nextLocalNumber",1);JSONObject previous=data.optJSONObject("settings");data=copy(remote);data.put("nextLocalNumber",localNumber);JSONObject fresh=data.optJSONObject("settings");if(previous!=null&&fresh!=null)for(String key:SaleSchedule.KEYS)if(!fresh.has(key)&&previous.has(key))fresh.put(key,previous.get(key));data.put("pending",keep);
            for(int i=0;i<keep.length();i++)overlay(keep.getJSONObject(i)); save();
        }catch(Exception e){data=before;throw e;}
    }
    public synchronized boolean hasPending(String entityId) {
        JSONArray p=data.optJSONArray("pending"); for(int i=0;p!=null&&i<p.length();i++)if(p.optJSONObject(i).optJSONObject("data").optString("id").equals(entityId))return true;return false;
    }
    public synchronized boolean canEditUnsent(String entityId){
        JSONArray pending=data.optJSONArray("pending");boolean found=false;
        for(int i=0;pending!=null&&i<pending.length();i++){JSONObject op=pending.optJSONObject(i);if(op.optJSONObject("data").optString("id").equals(entityId)){found=true;if(!op.has("attempted")||op.optBoolean("attempted"))return false;}}
        return found;
    }
    public synchronized void markAttempted(String operationId)throws Exception{
        JSONObject before=copy(data);try{JSONArray pending=data.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++)if(pending.optJSONObject(i).optString("id").equals(operationId))pending.optJSONObject(i).put("attempted",true);save();}catch(Exception error){data=before;throw error;}
    }
    public synchronized JSONObject manualOrder(JSONObject details,JSONObject quantities)throws Exception{
        String reqId=details==null?"":details.optString("requestId","").trim();
        if(!reqId.isEmpty()){JSONArray orders=data.optJSONArray("orders");for(int i=0;orders!=null&&i<orders.length();i++)if(orders.getJSONObject(i).optString("requestId").equals(reqId))return copy(orders.getJSONObject(i));}
        JSONObject order=OrderDraft.build(snapshot(),details,quantities,true,System.currentTimeMillis());
        order.put("number",data.optInt("nextLocalNumber",1));JSONObject before=copy(data);
        try{data.put("nextLocalNumber",order.optInt("number")+1);enqueue("manualOrder",order);return copy(order);}catch(Exception error){data=before;throw error;}
    }
    public synchronized void markProblem(String operationId,String message)throws Exception{
        JSONObject before=copy(data);try{JSONArray pending=data.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++)if(pending.getJSONObject(i).optString("id").equals(operationId)){pending.getJSONObject(i).put("error",message);JSONObject value=pending.getJSONObject(i).optJSONObject("data");JSONArray orders=data.optJSONArray("orders");for(int j=0;orders!=null&&j<orders.length();j++)if(orders.getJSONObject(j).optString("id").equals(value.optString("id")))orders.getJSONObject(j).put("sendState","review");}save();}catch(Exception error){data=before;throw error;}
    }
}
