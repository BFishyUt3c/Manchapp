package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;

final class CustomerNotifier {
    private static final String CHANNEL="cancelaciones";

    static void cancelled(Context context,JSONObject order){
        try{
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if(manager==null)return;
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel channel=new NotificationChannel(CHANNEL,"Pedidos cancelados",NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Avisos cuando la olla cancela un pedido.");
                manager.createNotificationChannel(channel);
            }
            String reason=order.optString("cancelReason");
            String text="Pedido #"+String.format(java.util.Locale.US,"%03d",order.optInt("number"))+(reason.isEmpty()?".":" · "+reason);
            Intent open=new Intent(context,CustomerActivity.class).putExtra("screen","orders")
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending=PendingIntent.getActivity(context,2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder=Build.VERSION.SDK_INT>=26
                    ?new Notification.Builder(context,CHANNEL)
                    :new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Pedido cancelado")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setShowWhen(true);
            manager.notify((int)(order.optString("id").hashCode()&0x7fffffff),builder.build());
        }catch(Exception ignored){}
    }
}
