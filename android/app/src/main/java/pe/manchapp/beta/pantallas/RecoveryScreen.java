package pe.manchapp.beta;

import android.app.Activity;
import android.widget.*;

final class RecoveryScreen {
    static void show(Activity activity,Exception error){NativeUi ui=new NativeUi(activity);LinearLayout root=ui.column();root.setPadding(ui.dp(24),ui.dp(36),ui.dp(24),ui.dp(24));root.setBackgroundColor(NativeUi.BG);ui.add(root,ui.heading("Tus archivos se conservaron",26),0);ui.add(root,ui.text(error.getMessage()==null?"Necesitamos revisar el almacenamiento antes de continuar.":error.getMessage(),17,NativeUi.INK,false),20);ui.add(root,ui.button("Cerrar",true,null,()->activity.finish()),24);activity.setContentView(root);}
}
