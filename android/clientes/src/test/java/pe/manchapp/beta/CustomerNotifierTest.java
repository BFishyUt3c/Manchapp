package pe.manchapp.beta;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.Shadows;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class CustomerNotifierTest {
    @Test public void cancelledNotificationContainsReason()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        CustomerNotifier.cancelled(context,new JSONObject().put("id","p-1").put("number",7).put("cancelReason","Se acabó la causa"));
        assertEquals(1,Shadows.shadowOf(manager).getAllNotifications().size());
        Notification posted=Shadows.shadowOf(manager).getAllNotifications().get(0);
        assertEquals("Pedido cancelado",Shadows.shadowOf(posted).getContentTitle().toString());
    }
}
