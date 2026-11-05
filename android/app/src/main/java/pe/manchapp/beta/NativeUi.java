package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class NativeUi {
    static final int WINE=0xff861b3f, DEEP=0xff59152c, CORAL=0xffba3a25,
        GOLD=0xfff2ba4f, BG=0xfff7ddb8, CARD=0xfffff0d5, INK=0xff35221c,
        MUTED=0xff795644, LINE=0xffdfb68a, GREEN=0xff316347, SOFT=0xfff2c3a9;
    final Context context;
    final Typeface regular, bold, heavy;
    NativeUi(Context context) {
        this.context=context;
        regular=font("Nunito-Regular.ttf",Typeface.create("sans-serif",Typeface.NORMAL));
        bold=font("Nunito-Bold.ttf",Typeface.create("sans-serif-medium",Typeface.NORMAL));
        heavy=font("Nunito-ExtraBold.ttf",Typeface.DEFAULT_BOLD);
    }
    private Typeface font(String file,Typeface fallback){try{return Typeface.createFromAsset(context.getAssets(),file);}catch(Exception e){return fallback;}}
    int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    GradientDrawable background(int color,int stroke,int radius){GradientDrawable result=new GradientDrawable();result.setColor(color);result.setCornerRadius(dp(radius));if(stroke!=0)result.setStroke(dp(1),stroke);return result;}
    LinearLayout column(){LinearLayout result=new LinearLayout(context);result.setOrientation(LinearLayout.VERTICAL);return result;}
    TextView text(String value,int size,int color,boolean strong){TextView result=new TextView(context);result.setText(value);result.setTextSize(size);result.setTextColor(color);result.setTypeface(strong?bold:regular);result.setLineSpacing(dp(2),1.05f);return result;}
    TextView heading(String value,int size){TextView result=text(value,size,INK,true);result.setTypeface(heavy);if(android.os.Build.VERSION.SDK_INT>=28)result.setAccessibilityHeading(true);return result;}
    void add(LinearLayout parent,View child,int gap){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(gap);parent.addView(child,params);}
    LinearLayout card(){LinearLayout result=column();result.setPadding(dp(16),dp(16),dp(16),dp(16));result.setBackground(background(CARD,LINE,18));return result;}
    Button button(String label,boolean primary,String icon,Runnable action){
        Button result=new Button(context);result.setAllCaps(false);result.setText(label);result.setTextSize(16);result.setTypeface(bold);result.setTextColor(primary?Color.WHITE:WINE);
        result.setGravity(Gravity.CENTER);result.setMinHeight(dp(52));result.setPadding(dp(16),dp(12),dp(16),dp(12));result.setStateListAnimator(null);result.setElevation(0);
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},background(primary?DEEP:SOFT,primary?0:LINE,14));states.addState(new int[]{},background(primary?CORAL:CARD,primary?0:LINE,14));result.setBackground(states);
        if(icon!=null){Icon drawable=new Icon(icon,primary?Color.WHITE:WINE,dp(21));result.setCompoundDrawablesWithIntrinsicBounds(drawable,null,null,null);result.setCompoundDrawablePadding(dp(10));}
        result.setOnClickListener(view->action.run());return result;
    }
    TextView iconButton(String name,String label,Runnable action){TextView result=text("",16,CARD,true);result.setGravity(Gravity.CENTER);result.setMinWidth(dp(48));result.setMinHeight(dp(48));result.setCompoundDrawablesWithIntrinsicBounds(null,new Icon(name,CARD,dp(24)),null,null);result.setPadding(dp(10),dp(12),dp(10),0);result.setContentDescription(label);buttonRole(result);result.setOnClickListener(view->action.run());return result;}
    void buttonRole(View view){view.setFocusable(true);view.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(v,info);info.setClassName(Button.class.getName());}});}
    LinearLayout toolbar(String title,String subtitle,Runnable back,Runnable settings){
        LinearLayout bar=new LinearLayout(context);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(back==null?18:4),dp(8),dp(8),dp(10));bar.setBackgroundColor(WINE);
        if(back!=null)bar.addView(iconButton("back","Volver",back),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout copy=column();TextView heading=text(title,22,CARD,true);heading.setTypeface(heavy);copy.addView(heading);
        if(!subtitle.isEmpty()){TextView small=text(subtitle,12,0xffffd6ac,false);small.setMaxLines(1);small.setEllipsize(android.text.TextUtils.TruncateAt.END);add(copy,small,1);}
        bar.addView(copy,new LinearLayout.LayoutParams(0,-2,1));if(settings!=null)bar.addView(iconButton("settings","Datos y ajustes de la olla",settings),new LinearLayout.LayoutParams(dp(48),dp(48)));return bar;
    }
    LinearLayout navigation(String[] ids,String[] labels,String[] icons,String selected,java.util.function.Consumer<String> action){
        LinearLayout nav=new LinearLayout(context);nav.setPadding(dp(5),dp(7),dp(5),dp(7));nav.setBackgroundColor(WINE);
        for(int i=0;i<ids.length;i++){final String id=ids[i];boolean active=id.equals(selected);int color=active?DEEP:CARD;TextView item=text(labels[i],12,color,true);item.setGravity(Gravity.CENTER);item.setSingleLine(true);item.setMinHeight(dp(58));item.setPadding(dp(3),dp(7),dp(3),dp(7));item.setCompoundDrawablePadding(dp(4));item.setCompoundDrawablesWithIntrinsicBounds(null,new Icon(icons[i],color,dp(22)),null,null);item.setBackground(background(active?GOLD:WINE,0,13));item.setSelected(active);buttonRole(item);item.setOnClickListener(v->action.accept(id));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(3),0,dp(3),0);nav.addView(item,params);}return nav;
    }
    LinearLayout message(String title,String body,int fill){LinearLayout box=card();box.setBackground(background(fill,0,18));add(box,text(title,18,INK,true),0);if(!body.isEmpty())add(box,text(body,14,INK,false),5);return box;}
    TextView chip(String label,int color,int fill){TextView result=text(label,12,color,true);result.setPadding(dp(10),dp(5),dp(10),dp(5));result.setBackground(background(fill,0,9));return result;}
    static final class Icon extends Drawable {
        private final String name;private final Paint paint=new Paint(3);private final int size;
        Icon(String name,int color,int size){this.name=name;this.size=size;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
        private void line(Canvas c,float a,float b,float d,float e){c.drawLine(a,b,d,e,paint);}
        private void path(Canvas c,float... points){Path p=new Path();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);c.drawPath(p,paint);}
        @Override public void draw(Canvas target){Canvas c=target;c.save();Rect bounds=getBounds();c.translate(bounds.left,bounds.top);c.scale(bounds.width()/24f,bounds.height()/24f);
            switch(name){
                case "home":path(c,3,11,12,3,21,11);path(c,5,10,5,21,10,21,10,15,14,15,14,21,19,21,19,10);break;
                case "food":c.drawOval(3,7,21,19,paint);c.drawArc(7,5,17,16,0,180,false,paint);line(c,7,3,7,6);line(c,12,2,12,5);line(c,17,3,17,6);break;
                case "cart":path(c,2,3,5,3,8,16,20,16,23,7,6,7);c.drawCircle(10,21,1,paint);c.drawCircle(19,21,1,paint);break;
                case "minus":line(c,5,12,19,12);break;
                case "back":path(c,15,5,8,12,15,19);break;
                case "next":path(c,9,5,16,12,9,19);break;
                case "plus":line(c,12,5,12,19);line(c,5,12,19,12);break;
                case "check":path(c,5,12,10,17,20,6);break;
                case "products":c.drawRoundRect(4,7,20,21,3,3,paint);c.drawArc(8,2,16,12,180,180,false,paint);break;
                case "orders":c.drawRoundRect(5,3,19,21,2,2,paint);line(c,9,8,16,8);line(c,9,12,16,12);line(c,9,16,14,16);break;
                case "publish":path(c,4,10,18,4,18,18,4,14,4,10);line(c,8,15,10,21);line(c,21,8,23,7);line(c,21,14,23,15);break;
                case "photo":c.drawRoundRect(3,4,21,20,3,3,paint);c.drawCircle(9,9,1.6f,paint);path(c,4,17,10,12,14,16,17,12,21,16);break;
                case "camera":c.drawRoundRect(3,6,21,20,3,3,paint);path(c,7,6,9,3,15,3,17,6);c.drawCircle(12,13,4,paint);break;
                case "light":c.drawCircle(12,12,4,paint);for(int i=0;i<8;i++){double angle=i*Math.PI/4;line(c,12+(float)Math.cos(angle)*8,12+(float)Math.sin(angle)*8,12+(float)Math.cos(angle)*10,12+(float)Math.sin(angle)*10);}break;
                case "clock":c.drawCircle(12,12,9,paint);path(c,12,6,12,12,16,14);break;
                case "pin":c.drawCircle(12,9,2.5f,paint);Path pin=new Path();pin.moveTo(12,22);pin.cubicTo(-5,6,7,-2,12,2);pin.cubicTo(26,-1,26,10,12,22);c.drawPath(pin,paint);break;
                case "truck":c.drawRect(2,6,14,17,paint);path(c,14,10,19,10,23,14,23,17,14,17);c.drawCircle(6,19,2,paint);c.drawCircle(19,19,2,paint);break;
                case "pay":c.drawRoundRect(2,5,22,20,3,3,paint);line(c,2,10,22,10);line(c,6,15,10,15);break;
                case "settings":c.drawCircle(12,12,7,paint);c.drawCircle(12,12,2.8f,paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10);}break;
                case "link":c.drawRoundRect(2,8,14,16,4,4,paint);c.drawRoundRect(10,8,22,16,4,4,paint);break;
                case "help":c.drawCircle(12,12,9,paint);c.drawArc(9,6,15,12,180,270,false,paint);line(c,12,12,12,14);c.drawCircle(12,17,.3f,paint);break;
                case "edit":path(c,4,16,16,4,20,8,8,20,3,21,4,16);line(c,13,7,17,11);break;
                case "tag":path(c,3,3,12,3,22,13,13,22,3,12,3,3);c.drawCircle(8,8,1,paint);break;
                case "share":c.drawCircle(18,4,2.5f,paint);c.drawCircle(5,12,2.5f,paint);c.drawCircle(18,20,2.5f,paint);line(c,7,11,16,5);line(c,7,13,16,19);break;
                case "qr":c.drawRect(3,3,9,9,paint);c.drawRect(15,3,21,9,paint);c.drawRect(3,15,9,21,paint);path(c,15,14,15,19,20,19,20,22);line(c,20,13,22,13);break;
                default:c.drawCircle(12,12,8,paint);line(c,8,12,16,12);
            }c.restore();}
        @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
