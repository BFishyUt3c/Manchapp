package pe.manchapp.beta;

import org.json.*;
import java.util.*;

final class OrderDraft {
    static JSONObject build(JSONObject catalog,JSONObject input,JSONObject quantities,boolean manual,long now)throws Exception{
        JSONObject settings=catalog.getJSONObject("settings"),details=DeliveryDestination.normalized(input),chosenSchedule=null;
        if(details.optString("customer").trim().length()<2)throw new Exception("Escribe el nombre del cliente.");
        if(!SaleRules.validPhone(details.optString("phone")))throw new Exception("Escribe un celular de 9 dígitos que empiece con 9.");
        String mode=details.optString("fulfillment","pickup"),payment=details.optString("payment","cash");
        if(!Arrays.asList("pickup","delivery").contains(mode)||!Arrays.asList("cash","yape","plin").contains(payment))throw new Exception("Revisa la entrega y la forma de pago.");
        JSONArray products=catalog.getJSONArray("products"),items=new JSONArray();double subtotal=0;
        Iterator<String> ids=quantities.keys();
        while(ids.hasNext()){
            String id=ids.next();Object raw=quantities.get(id);if(!(raw instanceof Number))throw new Exception("Revisa las cantidades.");int amount=((Number)raw).intValue();if(((Number)raw).doubleValue()!=amount||amount<0||amount>99)throw new Exception("Usa cantidades enteras entre 0 y 99.");if(amount==0)continue;
            JSONObject product=null;for(int i=0;i<products.length();i++)if(products.getJSONObject(i).optString("id").equals(id))product=products.getJSONObject(i);
            if(product==null||!product.optString("line").equals("commercial")||!product.optBoolean("available")||amount>product.optInt("stock"))throw new Exception("Revisa los cupos de los productos elegidos.");
            JSONObject schedule=SaleSchedule.forProduct(product,settings);if(chosenSchedule==null)chosenSchedule=schedule;else if(!SaleSchedule.key(chosenSchedule).equals(SaleSchedule.key(schedule)))throw new Exception("Elige productos de una misma fecha de venta. Puedes hacer otro pedido para la siguiente jornada.");
            items.put(new JSONObject().put("productId",id).put("name",product.optString("name")).put("price",product.optDouble("price")).put("quantity",amount));subtotal+=amount*product.optDouble("price");
        }
        if(items.length()==0||items.length()>30)throw new Exception("Elige al menos un producto, con un máximo de treinta diferentes.");
        if(chosenSchedule==null)throw new Exception("Elige al menos un producto.");
        if(manual){String invalid=SaleSchedule.invalid(chosenSchedule);if(!invalid.isEmpty())throw new Exception(invalid);if(!chosenSchedule.optBoolean("salesOpen"))throw new Exception("Las reservas de esta venta están pausadas.");if(now>SaleSchedule.parse(chosenSchedule.optString("pickupUntil"),false))throw new Exception("La jornada terminó. Revisa la fecha de venta.");}
        else SaleSchedule.requireOpen(chosenSchedule,now);
        subtotal=SaleRules.money(subtotal);double fee=0;String zoneName="";
        if(mode.equals("delivery")){
            if(details.optString("address").length()<4||details.optString("reference").length()<3)throw new Exception("Completa dirección y referencias para llegar.");
            String problem=DeliveryDestination.problem(details,chosenSchedule.optString("saleDate"));if(!problem.isEmpty())throw new Exception(problem);
            JSONObject zone=null;JSONArray zones=settings.optJSONArray("zones");for(int i=0;zones!=null&&i<zones.length();i++)if(zones.getJSONObject(i).optString("id").equals(details.optString("zoneId"))&&zones.getJSONObject(i).optBoolean("enabled"))zone=zones.getJSONObject(i);
            if(zone==null)throw new Exception("Elige un sector habilitado.");zoneName=zone.optString("name");fee=SaleRules.deliveryFee(subtotal,zone.optDouble("fee"),settings.optDouble("deliveryFreeFrom"));
        }
        String id=UUID.randomUUID().toString();JSONObject order=new JSONObject();for(String key:new String[]{"customer","phone","zoneId","address","reference","deliveryLocation","source"})if(details.has(key))order.put(key,details.get(key));
        order.put("id",id).put("requestId",id).put("storeId",catalog.optString("storeId")).put("items",items).put("subtotal",subtotal).put("fee",fee).put("total",SaleRules.money(subtotal+fee)).put("fulfillment",mode).put("payment",payment).put("zone",zoneName).put("paid",false).put("status",manual?"registered":"local").put("sendState","pending").put("version",manual?1:0).put("createdAt",now).put("source",manual?details.optString("source","presencial"):"app");
        SaleSchedule.copyInto(order,chosenSchedule);order.put("pickup",settings.optString("pickup"));
        order.put("expected",new JSONObject().put("saleKey",SaleSchedule.key(chosenSchedule)).put("pickup",settings.optString("pickup")).put("subtotal",subtotal).put("fee",fee).put("total",order.optDouble("total")));
        if(mode.equals("pickup")){for(String key:new String[]{"address","reference","zoneId","meeting","deliveryLocation"})order.remove(key);}
        return order;
    }
}
