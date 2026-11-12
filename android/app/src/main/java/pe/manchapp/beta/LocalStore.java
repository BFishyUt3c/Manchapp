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
    private void upgrade(Context context){try{JSONObject seed=new JSONObject(read(context.getAssets().open("seed.json"))),defaults=seed.getJSONObject("settings");boolean changed=SaleSchedule.upgrade(data,defaults);if(data.optString("storeId").isEmpty()){data.put("storeId",UUID.randomUUID().toString());changed=true;}JSONObject settings=data.getJSONObject("settings");if(settings.optString("name").equals("Mujeres Emprendedoras")){settings.put("name","Manka Kusi");changed=true;}if(settings.optString("pickup").contains("Mujeres Emprendedoras")||settings.optString("pickup").contains("Módulo Carrió")){settings.put("pickup",defaults.optString("pickup"));changed=true;}if(settings.optString("phone").isEmpty()){settings.put("phone",defaults.optString("phone"));changed=true;}for(String key:new String[]{"latitude","longitude"})if(!settings.has(key)){settings.put(key,defaults.get(key));changed=true;}if(settings.optString("saleDate").equals("2026-11-06")){for(String key:SaleSchedule.KEYS)settings.put(key,defaults.get(key));changed=true;}JSONArray products=data.optJSONArray("products"),seedProducts=seed.optJSONArray("products");for(int i=0;products!=null&&i<products.length();i++){JSONObject product=products.getJSONObject(i),fresh=null;for(int j=0;seedProducts!=null&&j<seedProducts.length();j++)if(seedProducts.getJSONObject(j).optString("id").equals(product.optString("id")))fresh=seedProducts.getJSONObject(j);if(fresh!=null&&product.optString("saleDate").equals("2026-11-06")){for(String key:SaleSchedule.KEYS)product.put(key,fresh.get(key));changed=true;}}JSONArray zones=settings.optJSONArray("zones");if(zones==null||zones.length()!=2||!zones.optJSONObject(0).has("maxKm")){settings.put("zones",defaults.getJSONArray("zones"));changed=true;}if(changed)save();}catch(Exception error){throw new IllegalStateException("No pudimos actualizar las fechas de venta.",error);}}
    public static String read(InputStream stream) throws IOException { try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); return new String(out.toByteArray(),StandardCharsets.UTF_8); } }
    public static JSONObject copy(JSONObject value) { try { return new JSONObject(value.toString()); } catch(JSONException e) { throw new IllegalStateException(e); } }
    public synchronized JSONObject snapshot() { return copy(data); }
    private void save() throws IOException {file.save(data);context.getContentResolver().notifyChange(android.net.Uri.parse("content://pe.manchapp.beta.bridge/catalog"),null);}
    synchronized JSONObject publicCatalog(){JSONObject result=copy(data);result.remove("orders");result.remove("pending");result.remove("nextLocalNumber");return result;}
    synchronized void saveSchedule(JSONArray productIds,JSONObject schedule)throws Exception{JSONObject before=copy(data);try{if(productIds==null||productIds.length()==0)throw new IOException("Elige al menos un producto para esta venta.");JSONArray products=data.getJSONArray("products");int changed=0;for(int i=0;i<products.length();i++){JSONObject product=products.getJSONObject(i);boolean selected=false;for(int j=0;j<productIds.length();j++)if(product.optString("id").equals(productIds.optString(j)))selected=true;if(!selected)continue;for(String key:SaleSchedule.KEYS)product.put(key,schedule.get(key));product.put("version",product.optInt("version")+1);changed++;}if(changed==0)throw new IOException("No encontramos los productos elegidos.");JSONObject settings=data.getJSONObject("settings");for(String key:SaleSchedule.KEYS)settings.put(key,schedule.get(key));save();}catch(Exception error){data=before;throw error;}}
    synchronized void deleteProduct(String id)throws Exception{JSONObject before=copy(data);try{JSONArray kept=new JSONArray(),products=data.optJSONArray("products");boolean found=false;for(int i=0;products!=null&&i<products.length();i++){JSONObject product=products.getJSONObject(i);if(product.optString("id").equals(id)){found=true;continue;}kept.put(product);}if(!found)throw new IOException("Este producto ya no existe.");JSONArray promosKept=new JSONArray(),promos=data.optJSONArray("promos");for(int i=0;promos!=null&&i<promos.length();i++)if(!promos.getJSONObject(i).optString("productId").equals(id))promosKept.put(promos.getJSONObject(i));JSONArray pendingKept=new JSONArray(),pending=data.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++)if(!pending.getJSONObject(i).optJSONObject("data").optString("id").equals(id))pendingKept.put(pending.getJSONObject(i));data.put("products",kept).put("promos",promosKept).put("pending",pendingKept);save();}catch(Exception error){data=before;throw error;}}
    synchronized JSONObject receiveDeviceOrder(JSONObject request)throws Exception{String requestId=request.optString("requestId",request.optString("id"));JSONArray orders=data.optJSONArray("orders");for(int i=0;orders!=null&&i<orders.length();i++)if(orders.getJSONObject(i).optString("requestId").equals(requestId))return copy(orders.getJSONObject(i));JSONObject quantities=new JSONObject();JSONArray incoming=request.optJSONArray("items");for(int i=0;incoming!=null&&i<incoming.length();i++)quantities.put(incoming.getJSONObject(i).optString("productId"),incoming.getJSONObject(i).optInt("quantity"));JSONObject order=OrderDraft.build(snapshot(),request,quantities,false,System.currentTimeMillis()),before=copy(data);try{order.put("requestId",requestId).put("number",data.optInt("nextLocalNumber",1)).put("status","registered").put("sendState","sent").put("version",1).put("localOnly",false);JSONArray products=data.getJSONArray("products"),items=order.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);for(int j=0;j<products.length();j++){JSONObject product=products.getJSONObject(j);if(product.optString("id").equals(item.optString("productId"))){if(product.optInt("stock")<item.optInt("quantity"))throw new IOException("Ya no quedan cupos suficientes para este producto.");product.put("stock",product.optInt("stock")-item.optInt("quantity")).put("version",product.optInt("version")+1);}}}if(orders==null)orders=new JSONArray();orders.put(order);data.put("orders",orders).put("nextLocalNumber",order.optInt("number")+1);save();return copy(order);}catch(Exception error){data=before;throw error;}}
    synchronized JSONObject deviceOrder(String id)throws Exception{JSONArray orders=data.optJSONArray("orders");for(int i=0;orders!=null&&i<orders.length();i++){JSONObject order=orders.getJSONObject(i);if(order.optString("id").equals(id)||order.optString("requestId").equals(id))return copy(order);}throw new IOException("No encontramos este pedido en Manka Kusi.");}
    public synchronized void enqueue(String type, JSONObject value) throws Exception {
        JSONObject before=copy(data);
        try { JSONArray pending=data.optJSONArray("pending"); if(pending==null) pending=new JSONArray();
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
        for(int i=0;i<all.length();i++) if(all.getJSONObject(i).optString("id").equals(value.optString("id"))) { if(type.equals("order")){JSONObject o=all.getJSONObject(i);String previous=o.optString("status"),target=value.optString("status",previous);if(target.equals("cancelled")&&!previous.equals("cancelled")){JSONArray products=data.optJSONArray("products"),items=o.optJSONArray("items");for(int p=0;products!=null&&p<products.length();p++)for(int j=0;items!=null&&j<items.length();j++)if(products.getJSONObject(p).optString("id").equals(items.getJSONObject(j).optString("productId")))products.getJSONObject(p).put("stock",products.getJSONObject(p).optInt("stock")+items.getJSONObject(j).optInt("quantity"));}java.util.Iterator<String> it=value.keys();while(it.hasNext()){String k=it.next();o.put(k,value.get(k));}o.put("version",value.optInt("version")+1);o.put("sendState","sent");}else if(!type.equals("manualOrder"))all.put(i,copy(value)); found=true; break; }
        if(!found){JSONObject local=copy(value);if(type.equals("manualOrder")){local.put("sendState","sent").put("localOnly",true);JSONArray products=data.optJSONArray("products"),items=local.optJSONArray("items");for(int i=0;products!=null&&i<products.length();i++)for(int j=0;items!=null&&j<items.length();j++)if(products.getJSONObject(i).optString("id").equals(items.getJSONObject(j).optString("productId")))products.getJSONObject(i).put("stock",Math.max(0,products.getJSONObject(i).optInt("stock")-items.getJSONObject(j).optInt("quantity")));}all.put(local);} data.put(array,all);
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
        JSONObject order=OrderDraft.build(snapshot(),details,quantities,true,System.currentTimeMillis());
        order.put("number",data.optInt("nextLocalNumber",1));JSONObject before=copy(data);
        try{data.put("nextLocalNumber",order.optInt("number")+1);enqueue("manualOrder",order);return copy(order);}catch(Exception error){data=before;throw error;}
    }
    public synchronized void markProblem(String operationId,String message)throws Exception{
        JSONObject before=copy(data);try{JSONArray pending=data.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++)if(pending.getJSONObject(i).optString("id").equals(operationId)){pending.getJSONObject(i).put("error",message);JSONObject value=pending.getJSONObject(i).optJSONObject("data");JSONArray orders=data.optJSONArray("orders");for(int j=0;orders!=null&&j<orders.length();j++)if(orders.getJSONObject(j).optString("id").equals(value.optString("id")))orders.getJSONObject(j).put("sendState","review");}save();}catch(Exception error){data=before;throw error;}
    }
    public synchronized JSONObject reviewConflict(JSONObject remote,String operationId)throws Exception{
        JSONArray pending=data.optJSONArray("pending"),keep=new JSONArray(),archived=new JSONArray();JSONObject chosen=null;
        for(int i=0;pending!=null&&i<pending.length();i++)if(pending.getJSONObject(i).optString("id").equals(operationId))chosen=pending.getJSONObject(i);
        if(chosen==null)throw new IOException("El cambio ya no está pendiente.");String entity=chosen.getJSONObject("data").optString("id");
        for(int i=0;i<pending.length();i++){JSONObject op=pending.getJSONObject(i);if(op.getJSONObject("data").optString("id").equals(entity))archived.put(op);else keep.put(op);}
        File dir=new File(context.getFilesDir(),"pending-history");dir.mkdirs();File archive=new File(dir,System.currentTimeMillis()+".json");try(OutputStream out=new FileOutputStream(archive)){out.write(archived.toString().getBytes("UTF-8"));}
        JSONObject before=copy(data);try{data.put("pending",keep);accept(remote,"");return copy(chosen);}catch(Exception error){data=before;throw error;}
    }
}
