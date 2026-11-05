package pe.manchapp.beta;

import org.json.*;

/** A destination belongs to a reservation, not to a live tracking session. */
final class DeliveryDestination {
    static JSONObject normalized(JSONObject details)throws JSONException{
        JSONObject value=new JSONObject(details.toString());
        String reference=value.optString("reference").trim(),legacy=value.optString("meeting").trim();
        if(!legacy.isEmpty()&&!reference.contains(legacy))reference+=(reference.isEmpty()?"":"\n")+legacy;
        value.put("address",value.optString("address").trim()).put("reference",reference);value.remove("meeting");return value;
    }
    static JSONObject point(double latitude,double longitude,float accuracy,long capturedAt)throws JSONException{
        JSONObject point=new JSONObject().put("latitude",latitude).put("longitude",longitude).put("accuracy",accuracy).put("capturedAt",capturedAt);
        if(!validPoint(point))throw new IllegalArgumentException("No pudimos obtener un punto válido.");return point;
    }
    static boolean validPoint(JSONObject point){if(point==null)return false;Object lat=point.opt("latitude"),lon=point.opt("longitude"),accuracy=point.opt("accuracy"),at=point.opt("capturedAt");if(!(lat instanceof Number)||!(lon instanceof Number)||!(accuracy instanceof Number)||!(at instanceof Number))return false;double a=((Number)lat).doubleValue(),b=((Number)lon).doubleValue(),c=((Number)accuracy).doubleValue(),d=((Number)at).doubleValue();return Double.isFinite(a)&&Double.isFinite(b)&&Double.isFinite(c)&&Double.isFinite(d)&&a>=-90&&a<=90&&b>=-180&&b<=180&&c>0&&c<=10000&&d>0;}
    static void confirm(JSONObject details,String saleDate)throws JSONException{JSONObject point=details.optJSONObject("deliveryLocation");if(!validPoint(point))throw new IllegalArgumentException("Primero agrega una ubicación válida.");point.put("saleDate",saleDate).put("address",details.optString("address").trim());}
    static String problem(JSONObject details,String saleDate){
        if(!details.has("deliveryLocation"))return "";JSONObject point=details.optJSONObject("deliveryLocation");
        if(!validPoint(point))return "La ubicación guardada no es válida. Quítala o vuelve a obtenerla.";
        if(!point.optString("saleDate").equals(saleDate)||!point.optString("address").equals(details.optString("address").trim()))return "Confirma que el punto guardado corresponde a esta dirección y fecha de entrega.";
        return "";
    }
    static String geo(JSONObject point){if(!validPoint(point))throw new IllegalArgumentException("Ubicación no válida.");return "geo:0,0?q="+point.optDouble("latitude")+","+point.optDouble("longitude");}
}
