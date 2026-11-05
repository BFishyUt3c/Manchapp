package pe.manchapp.beta;

import org.json.*;
import java.text.*;
import java.util.*;

/** One explicitly scheduled selling day. All business times belong to Lima. */
final class SaleSchedule {
    static final String[] KEYS={"saleDate","pickupFrom","pickupUntil","reserveUntil","salesOpen"};
    static final TimeZone LIMA=TimeZone.getTimeZone("America/Lima");
    private static SimpleDateFormat format(String pattern){SimpleDateFormat f=new SimpleDateFormat(pattern,new Locale("es","PE"));f.setTimeZone(LIMA);f.setLenient(false);return f;}
    static long parse(String value,boolean day){try{String pattern=day?"yyyy-MM-dd":"yyyy-MM-dd'T'HH:mm";if(value==null||!value.matches(day?"[0-9]{4}-[0-9]{2}-[0-9]{2}":"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}"))return -1;ParsePosition pos=new ParsePosition(0);Date date=format(pattern).parse(value,pos);return date!=null&&pos.getIndex()==value.length()?date.getTime():-1;}catch(Exception error){return -1;}}
    static String date(long millis){return format("yyyy-MM-dd").format(new Date(millis));}
    static String stamp(long millis){return format("yyyy-MM-dd'T'HH:mm").format(new Date(millis));}
    static String display(String value,boolean day){long time=parse(value,day);return time<0?"Fecha por coordinar":format(day?"EEEE d 'de' MMMM 'de' yyyy":"EEE d MMM, h:mm a").format(new Date(time));}
    static String day(JSONObject s){return display(s.optString("saleDate"),true);}
    static String window(JSONObject s){return "Desde "+display(s.optString("pickupFrom"),false)+"\nHasta "+display(s.optString("pickupUntil"),false);}
    static String deadline(JSONObject s){return display(s.optString("reserveUntil"),false);}
    static String invalid(JSONObject s){long day=parse(s.optString("saleDate"),true),from=parse(s.optString("pickupFrom"),false),until=parse(s.optString("pickupUntil"),false),cutoff=parse(s.optString("reserveUntil"),false);
        if(day<0||from<0||until<0||cutoff<0||!(s.opt("salesOpen") instanceof Boolean))return "Completa la fecha de venta, el plazo de reserva y el intervalo de recojo.";
        if(!date(from).equals(s.optString("saleDate")))return "El recojo debe empezar el día de venta.";
        if(until<=from)return "El fin del recojo debe ser posterior al inicio.";
        if(cutoff>from)return "Las reservas deben cerrar antes o al comenzar el recojo.";
        return "";
    }
    static String unavailable(JSONObject s,long now){String invalid=invalid(s);if(!invalid.isEmpty())return "La olla anunciará su próxima venta.";if(!s.optBoolean("salesOpen"))return "Las reservas están pausadas.";if(now>parse(s.optString("pickupUntil"),false))return "Esta jornada de venta terminó.";if(now>=parse(s.optString("reserveUntil"),false))return "Cerraron las reservas para esta venta.";return "";}
    static void requireOpen(JSONObject s,long now)throws Exception{String error=unavailable(s,now);if(!error.isEmpty())throw new Exception(error);}
    static String key(JSONObject s){return s.optString("saleDate")+"/"+s.optString("pickupFrom")+"/"+s.optString("pickupUntil");}
    static JSONObject editable(JSONObject source){JSONObject draft=new JSONObject();try{for(String key:KEYS)if(source.has(key))draft.put(key,source.get(key));if(source.has("pickup"))draft.put("pickup",source.get("pickup"));}catch(JSONException error){throw new IllegalStateException(error);}return draft;}
    static JSONObject copyInto(JSONObject order,JSONObject settings)throws JSONException{for(String key:KEYS)if(settings.has(key))order.put(key,settings.get(key));order.put("pickup",settings.optString("pickup"));return order;}
    static String next(String status,boolean delivery){switch(status){case "registered":case "confirmed":case "preparing":return "sale_day";case "sale_day":return delivery?"delivering":"pickup_available";case "ready":return delivery?"delivering":"pickup_available";case "pickup_available":case "delivering":return "delivered";default:return "";}}
    static void checkTransition(JSONObject order,String target,long now)throws Exception{
        if(target.equals("sale_day")||target.equals("delivering")||target.equals("pickup_available")){long date=parse(order.optString("saleDate"),true);if(date<0)throw new Exception("Agrega primero la fecha de venta de este pedido.");if(now<date)throw new Exception("Este pedido corresponde al "+day(order)+".");if(target.equals("pickup_available")){long from=parse(order.optString("pickupFrom"),false),until=parse(order.optString("pickupUntil"),false);if(from<0||until<0||now<from||now>until)throw new Exception("Revisa el intervalo de recojo de este pedido.");}}
    }
    /** Only replace known initial copy; never overwrite an owner's custom text or old orders. */
    static boolean upgrade(JSONObject catalog,JSONObject defaults)throws JSONException{
        JSONObject s=catalog.getJSONObject("settings");boolean changed=false;
        for(String key:KEYS)if(!s.has(key)&&defaults.has(key)){s.put(key,defaults.get(key));changed=true;}
        if(s.optString("pickup").equals("Módulo Carrió · confirmar referencia con la olla")){s.put("pickup","Olla Común Mujeres Emprendedoras · Manchay");changed=true;}
        if(s.optString("hours").equals("Almuerzos y antojitos de la tarde")){s.put("hours","Ventas en fechas programadas por la olla");changed=true;}
        for(String array:new String[]{"products","promos"}){JSONArray all=catalog.optJSONArray(array);for(int i=0;all!=null&&i<all.length();i++)changed=upgradeItem(all.getJSONObject(i))||changed;}
        JSONArray pending=catalog.optJSONArray("pending");for(int i=0;pending!=null&&i<pending.length();i++){JSONObject op=pending.getJSONObject(i);if(op.optString("type").equals("product")||op.optString("type").equals("promo"))changed=upgradeItem(op.getJSONObject("data"))||changed;}
        return changed;
    }
    private static boolean upgradeItem(JSONObject p)throws JSONException{boolean changed=false;if(p.optString("id").equals("merienda-combo")||p.optString("id").equals("merienda"))for(String key:new String[]{"description","portion"}){String old=p.optString(key);if(old.equals("2 cachangas + 1 café")||old.equals("2 cachangas + 1 café. Cantidad de combos preparada por la olla.")){p.put(key,key.equals("description")&&p.optString("id").equals("merienda-combo")?"3 cachangas + 2 cafés. Reserva para la fecha de venta de la olla.":"3 cachangas + 2 cafés");changed=true;}}return changed;}
}
