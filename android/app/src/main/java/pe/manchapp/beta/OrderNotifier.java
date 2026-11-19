package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.Locale;

final class OrderNotifier {
    private static final String CHANNEL="pedidos";

    static void newOrder(Context context, JSONObject order) {
        try {
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if(manager==null)return;
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel channel=new NotificationChannel(CHANNEL,"Pedidos nuevos",NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Avisos cuando un cliente reserva desde ManchApp Pedidos.");
                manager.createNotificationChannel(channel);
            }
            String customer=order.optString("customer","Cliente");
            int count=order.optJSONArray("items")==null?0:order.optJSONArray("items").length();
            String total=NumberFormat.getCurrencyInstance(new Locale("es","PE")).format(order.optDouble("total"));
            String text=count+" producto(s) · "+total;
            Intent open=new Intent(context,MainActivity.class).putExtra("screen","orders")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending=PendingIntent.getActivity(context,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder=Build.VERSION.SDK_INT>=26
                    ?new Notification.Builder(context,CHANNEL)
                    :new Notification.Builder(context);
            builder.setSmallIcon(R.drawable.icon)
                    .setContentTitle("Nuevo pedido de "+customer)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setShowWhen(true);
            manager.notify((int)(order.optString("id").hashCode()&0x7fffffff),builder.build());
        } catch(Exception ignored) {}
    }
}
