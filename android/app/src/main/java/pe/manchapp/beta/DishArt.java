package pe.manchapp.beta;

import android.graphics.*;
import android.graphics.drawable.Drawable;

final class DishArt extends Drawable {
    private final String kind;private final Paint paint=new Paint(3);
    DishArt(String kind){this.kind=kind;}
    private void oval(Canvas c,int color,float a,float b,float d,float e){paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawOval(a,b,d,e,paint);}
    @Override public void draw(Canvas c){c.save();Rect b=getBounds();c.clipRect(b);c.translate(b.left,b.top);c.scale(b.width()/320f,b.height()/240f);c.drawColor(0xfff0e5d9);paint.setColor(0xffe8d5bf);c.drawCircle(285,5,125,paint);oval(c,0xffdfcebc,28,74,299,211);oval(c,0xfffffcf6,26,62,295,198);oval(c,0xfff1e8da,48,78,273,182);
        if(kind.equals("cafe")){paint.setColor(0xfffffefa);c.drawRoundRect(94,58,218,164,20,20,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(12);c.drawOval(198,80,251,139,paint);paint.setStyle(Paint.Style.FILL);oval(c,0xff4b2b20,98,57,214,94);paint.setColor(0xffb39276);paint.setStrokeWidth(3);c.drawLine(141,29,145,47,paint);c.drawLine(165,23,163,43,paint);}
        else if(kind.equals("mazamorra")){oval(c,0xfffdf9f3,72,70,251,175);oval(c,0xff5f253e,82,79,241,157);paint.setColor(0xffba9361);c.drawRoundRect(126,108,196,117,4,4,paint);}
        else if(kind.equals("causa")){paint.setColor(0xffedc65a);c.drawRoundRect(78,83,242,159,18,18,paint);paint.setColor(0xfff4e1b3);c.drawRect(79,115,242,130,paint);oval(c,0xff5f7b45,110,66,145,97);oval(c,0xff5f7b45,160,67,201,95);oval(c,0xff43352e,150,84,168,101);}
        else if(kind.equals("cachanga")){oval(c,0xffaf713c,61,91,228,163);oval(c,0xffdaa15c,77,75,253,143);paint.setColor(0xffbd7d3e);c.drawCircle(121,107,7,paint);c.drawCircle(191,106,5,paint);c.drawCircle(158,95,5,paint);}
        else if(kind.equals("social")){oval(c,0xff6b863f,67,89,156,163);oval(c,0xfff9f4e9,141,77,246,156);oval(c,0xffa15d3e,142,130,239,171);}
        else{oval(c,0xffa36737,77,96,245,165);oval(c,0xffd99b51,71,79,241,150);paint.setColor(0xffbd7c3b);c.drawCircle(120,104,7,paint);c.drawCircle(174,118,5,paint);c.drawCircle(196,103,4,paint);}
        oval(c,0xff658a48,42,135,87,161);oval(c,0xff86a45b,50,122,78,158);c.restore();}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
