package pe.manchapp.beta;

import android.app.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

final class ScheduleEditor {
    static void draw(Activity activity,NativeUi ui,LinearLayout content,JSONObject draft,Runnable refresh){
        ui.add(content,ui.heading("Programa tu venta",27),0);
        ui.add(content,ui.text("Elige cuándo venderá la olla y hasta cuándo recibirás reservas.",16,NativeUi.MUTED,false),7);
        LinearLayout quick=new LinearLayout(activity);
        String[] presetLabels={"Hoy","Mañana","Sábado"};
        for(int i=0;i<presetLabels.length;i++){
            final int mode=i;
            android.widget.TextView chip=ui.button(presetLabels[i],false,null,()->applyPreset(draft,mode,refresh));
            chip.setTextSize(13);chip.setMinHeight(ui.dp(44));chip.setPadding(ui.dp(6),ui.dp(6),ui.dp(6),ui.dp(6));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i<2)lp.rightMargin=ui.dp(6);
            quick.addView(chip,lp);
        }
        ui.add(content,quick,12);
        entry(activity,ui,content,draft,"saleDate","Día de venta",true,refresh);
        entry(activity,ui,content,draft,"reserveUntil","Recibir reservas hasta",false,refresh);
        ui.add(content,ui.heading("Recojo en la olla",22),23);
        ui.add(content,ui.text("Indica desde qué fecha y hora hasta qué fecha y hora pueden acercarse.",15,NativeUi.MUTED,false),7);
        entry(activity,ui,content,draft,"pickupFrom","Pueden recoger desde",false,refresh);
        entry(activity,ui,content,draft,"pickupUntil","Pueden recoger hasta",false,refresh);
        Switch open=new Switch(activity);open.setText("Recibir reservas");open.setTextSize(17);open.setTypeface(ui.bold);open.setTextColor(NativeUi.INK);open.setMinHeight(ui.dp(56));open.setChecked(draft.optBoolean("salesOpen"));open.setOnCheckedChangeListener((button,checked)->put(draft,"salesOpen",checked));ui.add(content,open,20);
        ui.add(content,ui.message("Reparto el día de venta","La salida del delivery se marca cuando quien reparte sale. Coordina la hora y el punto de encuentro con el cliente.",NativeUi.GOLD),14);
    }
    private static void entry(Activity activity,NativeUi ui,LinearLayout content,JSONObject draft,String key,String label,boolean day,Runnable refresh){
        ui.add(content,ui.text(label,15,NativeUi.INK,true),17);
        ui.add(content,ui.button(SaleSchedule.display(draft.optString(key),day),false,"clock",()->pick(activity,draft.optString(key),day,value->{
            if(day){long previous=SaleSchedule.parse(draft.optString(key),true),selected=SaleSchedule.parse(value,true);if(previous>=0&&selected>=0)for(String other:new String[]{"pickupFrom","pickupUntil","reserveUntil"}){long old=SaleSchedule.parse(draft.optString(other),false);if(old>=0)put(draft,other,SaleSchedule.stamp(old+selected-previous));}}
            put(draft,key,value);refresh.run();
        })),6);
    }
    private static void pick(Activity activity,String value,boolean day,Consumer<String> done){
        Calendar time=Calendar.getInstance(SaleSchedule.LIMA);long old=SaleSchedule.parse(value,day);if(old>=0)time.setTimeInMillis(old);
        DatePickerDialog date=new DatePickerDialog(activity,(picker,year,month,d)->{
            time.set(Calendar.YEAR,year);time.set(Calendar.MONTH,month);time.set(Calendar.DAY_OF_MONTH,d);time.set(Calendar.SECOND,0);time.set(Calendar.MILLISECOND,0);
            if(day)done.accept(SaleSchedule.date(time.getTimeInMillis()));
            else new TimePickerDialog(activity,(clock,hour,minute)->{time.set(Calendar.HOUR_OF_DAY,hour);time.set(Calendar.MINUTE,minute);done.accept(SaleSchedule.stamp(time.getTimeInMillis()));},time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE),false).show();
        },time.get(Calendar.YEAR),time.get(Calendar.MONTH),time.get(Calendar.DAY_OF_MONTH));date.show();
    }
    private static void applyPreset(JSONObject draft,int mode,Runnable refresh){
        Calendar c=Calendar.getInstance(SaleSchedule.LIMA);
        if(mode==1)c.add(Calendar.DAY_OF_MONTH,1);
        else if(mode==2){int delta=(Calendar.SATURDAY-c.get(Calendar.DAY_OF_WEEK)+7)%7;if(delta==0)delta=7;c.add(Calendar.DAY_OF_MONTH,delta);}
        c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        int hour=c.get(Calendar.HOUR_OF_DAY);
        if(mode==0&&hour>=17)c.add(Calendar.DAY_OF_MONTH,1);
        put(draft,"saleDate",SaleSchedule.date(c.getTimeInMillis()));
        if(mode==0&&hour>=14&&hour<17){
            c.add(Calendar.HOUR_OF_DAY,1);c.set(Calendar.MINUTE,0);put(draft,"reserveUntil",SaleSchedule.stamp(c.getTimeInMillis()));
            c.add(Calendar.HOUR_OF_DAY,2);put(draft,"pickupFrom",SaleSchedule.stamp(c.getTimeInMillis()));
            c.add(Calendar.HOUR_OF_DAY,2);put(draft,"pickupUntil",SaleSchedule.stamp(c.getTimeInMillis()));
        }else{
            c.set(Calendar.HOUR_OF_DAY,14);c.set(Calendar.MINUTE,0);put(draft,"reserveUntil",SaleSchedule.stamp(c.getTimeInMillis()));
            c.set(Calendar.HOUR_OF_DAY,17);c.set(Calendar.MINUTE,0);put(draft,"pickupFrom",SaleSchedule.stamp(c.getTimeInMillis()));
            c.set(Calendar.HOUR_OF_DAY,19);c.set(Calendar.MINUTE,0);put(draft,"pickupUntil",SaleSchedule.stamp(c.getTimeInMillis()));
        }
        refresh.run();
    }
    private static void put(JSONObject object,String key,Object value){try{object.put(key,value);}catch(JSONException error){throw new IllegalStateException(error);}}
}
