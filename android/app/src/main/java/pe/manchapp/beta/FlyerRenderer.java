package pe.manchapp.beta;

import android.graphics.*;
import android.text.*;
import org.json.JSONObject;

final class FlyerRenderer {
    private static void block(Canvas canvas,String value,float x,float y,int width,int size,int color,Typeface font,int lines){TextPaint paint=new TextPaint(3);paint.setColor(color);paint.setTextSize(size);paint.setTypeface(font);StaticLayout layout=StaticLayout.Builder.obtain(value==null?"":value,0,value==null?0:value.length(),paint,width).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(3,1).setIncludePad(false).build();canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore();}
    static Bitmap render(JSONObject product,JSONObject settings,String message,int style,NativeUi ui){
        Bitmap result=Bitmap.createBitmap(720,1080,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);Paint p=new Paint(3);int accent=style==2?NativeUi.GREEN:style==1?NativeUi.CORAL:NativeUi.WINE;c.drawColor(NativeUi.BG);
        p.setColor(accent);c.drawRoundRect(24,24,696,102,22,22,p);block(c,"MANCHAPP  /  HECHO EN COMUNIDAD",49,48,625,22,Color.WHITE,ui.bold,1);
        block(c,settings.optString("name"),38,126,646,28,NativeUi.INK,ui.bold,2);
        p.setColor(style==1?0xfff0d9b5:0xffefe2d3);c.drawRoundRect(34,208,686,565,28,28,p);
        Bitmap photo=ImageTools.productPhoto(ui.context,product,960);
        if(photo!=null){float scale=Math.min(620f/photo.getWidth(),326f/photo.getHeight());int width=Math.round(photo.getWidth()*scale),height=Math.round(photo.getHeight()*scale);c.drawBitmap(photo,null,new Rect((720-width)/2,224+(326-height)/2,(720+width)/2,224+(326+height)/2),p);photo.recycle();}
        else{DishArt art=new DishArt(product.optString("illustration"));art.setBounds(45,217,675,553);art.draw(c);block(c,"ILUSTRACIÓN REFERENCIAL",414,531,245,14,NativeUi.INK,ui.bold,1);}
        block(c,"VENTA: "+SaleSchedule.day(settings),38,593,644,19,accent,ui.bold,1);
        String name=product.optString("name");
        block(c,name,37,630,642,name.length()>48?28:name.length()>30?35:42,NativeUi.INK,ui.heavy,name.length()>48?3:2);
        String price=java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("es","PE")).format(product.optDouble("price"));block(c,price,36,740,640,59,accent,ui.heavy,1);
        block(c,SaleRules.unit(product.optString("saleUnit",SaleRules.defaultUnit(product.optString("category")))),40,803,640,17,NativeUi.MUTED,ui.regular,1);
        String hours=SaleSchedule.window(settings);block(c,hours,38,825,640,21,NativeUi.INK,ui.bold,2);
        Bitmap qr=ImageTools.decode(settings.optString("paymentImage"));int textWidth=qr==null?640:450;
        block(c,message,38,891,textWidth,23,NativeUi.MUTED,ui.regular,2);
        String contact=settings.optString("phone");block(c,contact.isEmpty()?settings.optString("pickup"):"Pedidos: "+contact,38,972,textWidth,22,accent,ui.bold,2);
        if(qr!=null){p.setColor(Color.WHITE);c.drawRoundRect(528,900,681,1056,12,12,p);float scale=Math.min(133f/qr.getWidth(),133f/qr.getHeight());float width=qr.getWidth()*scale,height=qr.getHeight()*scale;c.drawBitmap(qr,null,new RectF(604.5f-width/2,976.5f-height/2,604.5f+width/2,976.5f+height/2),p);qr.recycle();}
        block(c,"Reservas hasta "+SaleSchedule.deadline(settings),38,1045,470,15,NativeUi.MUTED,ui.regular,1);return result;
    }
}
