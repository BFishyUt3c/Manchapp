package pe.manchapp.beta;

import org.json.*;
import java.util.*;

final class OrderSummary {
    static JSONObject build(JSONObject data,String day)throws JSONException{
        JSONObject result=new JSONObject();JSONArray products=new JSONArray();Map<String,JSONObject> quantities=new LinkedHashMap<>();int orders=0,deliveries=0,pickups=0,unpaid=0,completed=0,unconfirmed=0;
        JSONArray all=data.optJSONArray("orders");
        for(int i=0;all!=null&&i<all.length();i++){
            JSONObject order=all.getJSONObject(i);if(!order.optString("saleDate").equals(day)||order.optString("status").equals("cancelled"))continue;
            boolean pending=order.optBoolean("localOnly");if(pending)unconfirmed++;else{orders++;if(order.optString("fulfillment").equals("delivery"))deliveries++;else pickups++;if(!order.optBoolean("paid"))unpaid++;if(order.optString("status").equals("delivered"))completed++;}
            JSONArray items=order.optJSONArray("items");for(int j=0;items!=null&&j<items.length();j++){JSONObject item=items.getJSONObject(j);String id=item.optString("productId");JSONObject row=quantities.get(id);if(row==null){row=new JSONObject().put("id",id).put("name",item.optString("name")).put("confirmed",0).put("pending",0);quantities.put(id,row);}String key=pending?"pending":"confirmed";row.put(key,row.optInt(key)+item.optInt("quantity"));}
        }
        for(JSONObject row:quantities.values())products.put(row);
        return result.put("orders",orders).put("deliveries",deliveries).put("pickups",pickups).put("unpaid",unpaid).put("completed",completed).put("unconfirmed",unconfirmed).put("products",products);
    }
}
