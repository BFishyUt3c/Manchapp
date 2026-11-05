package pe.manchapp.beta;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Durable local snapshot plus an idempotent queue; no network needed to edit. */
public final class LocalStore {
    private final AtomicFile file;
    private JSONObject data;
    public LocalStore(Context context) { this(context,"manchapp.json"); }
    LocalStore(Context context,String filename) {
        file=new AtomicFile(new File(context.getFilesDir(),filename));
        try { data=new JSONObject(read(file.openRead())); }
        catch(Exception e) { try { data=new JSONObject(read(context.getAssets().open("seed.json"))); data.put("pending",new JSONArray()); save(); } catch(Exception fatal) { throw new IllegalStateException(fatal); } }
        upgrade(context);
    }
    private void upgrade(Context context){try{JSONObject defaults=new JSONObject(read(context.getAssets().open("seed.json"))).getJSONObject("settings");if(SaleSchedule.upgrade(data,defaults))save();}catch(Exception error){throw new IllegalStateException("No pudimos actualizar las fechas de venta.",error);}}
    public static String read(InputStream stream) throws IOException { try(InputStream in=stream; ByteArrayOutputStream out=new ByteArrayOutputStream()) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); return new String(out.toByteArray(),StandardCharsets.UTF_8); } }
    public static JSONObject copy(JSONObject value) { try { return new JSONObject(value.toString()); } catch(JSONException e) { throw new IllegalStateException(e); } }
    public synchronized JSONObject snapshot() { return copy(data); }
    private void save() throws IOException { FileOutputStream out=null; try { out=file.startWrite(); out.write(data.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); } catch(IOException e) { if(out!=null) file.failWrite(out); throw e; } }
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
        for(int i=0;i<all.length();i++) if(all.getJSONObject(i).optString("id").equals(value.optString("id"))) { if(type.equals("order")){JSONObject o=all.getJSONObject(i);java.util.Iterator<String> it=value.keys();while(it.hasNext()){String k=it.next();o.put(k,value.get(k));}}else all.put(i,copy(value)); found=true; break; }
        if(!found)all.put(copy(value)); data.put(array,all);
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
        try { JSONArray pending=data.optJSONArray("pending"), keep=new JSONArray();
            for(int i=0;pending!=null && i<pending.length();i++)if(!pending.getJSONObject(i).optString("id").equals(acknowledged))keep.put(pending.getJSONObject(i));
            JSONObject previous=data.optJSONObject("settings");data=copy(remote);JSONObject fresh=data.optJSONObject("settings");if(previous!=null&&fresh!=null)for(String key:SaleSchedule.KEYS)if(!fresh.has(key)&&previous.has(key))fresh.put(key,previous.get(key));data.put("pending",keep);
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
}
