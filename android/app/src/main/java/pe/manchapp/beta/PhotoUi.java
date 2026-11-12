package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class PhotoUi {
    static final int BG=0xff111216,PANEL=0xff1d1e24,SURFACE=0xff292a32,LINE=0xff3c3d48,
        TEXT=0xfff6f3f5,MUTED=0xffb5b0bc,ACCENT=0xffa32e55,LIGHT=0xfff3adc5;
    final Context context;
    private final Typeface normal=Typeface.create("sans-serif",Typeface.NORMAL),medium=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    PhotoUi(Context context){this.context=context;}
    int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout result=new LinearLayout(context);result.setOrientation(LinearLayout.VERTICAL);return result;}
    TextView text(String value,int size,int color,boolean strong){TextView view=new TextView(context);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setTypeface(strong?medium:normal);view.setIncludeFontPadding(false);view.setLineSpacing(dp(3),1);return view;}
    GradientDrawable background(int color,int stroke,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));if(stroke!=0)drawable.setStroke(dp(1),stroke);return drawable;}
    TextView button(String label,boolean primary,Runnable action){TextView view=text(label,14,primary?TEXT:LIGHT,true);view.setGravity(Gravity.CENTER);view.setMinHeight(dp(48));view.setPadding(dp(14),dp(10),dp(14),dp(10));StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},background(primary?0xff7f2442:SURFACE,0,12));states.addState(new int[]{},background(primary?ACCENT:Color.TRANSPARENT,primary?0:LINE,12));view.setBackground(states);view.setOnClickListener(v->action.run());role(view);return view;}
    TextView icon(String name,String label,Runnable action){TextView view=text("",14,TEXT,false);view.setGravity(Gravity.CENTER);view.setCompoundDrawablesWithIntrinsicBounds(new NativeUi.Icon(name,TEXT,dp(23)),null,null,null);view.setPadding(dp(12),dp(12),dp(12),dp(12));view.setMinHeight(dp(48));view.setMinWidth(dp(48));view.setContentDescription(label);view.setOnClickListener(v->action.run());role(view);return view;}
    LinearLayout header(String title,View back,View action){LinearLayout bar=new LinearLayout(context);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(4),dp(4),dp(12),dp(4));bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView heading=text(title,20,TEXT,true);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));if(action!=null)bar.addView(action,new LinearLayout.LayoutParams(-2,dp(48)));return bar;}
    void add(LinearLayout parent,View child,int gap){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(gap);parent.addView(child,params);}
    void weighted(LinearLayout row,View view,boolean last){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.rightMargin=last?0:dp(8);row.addView(view,params);}
    void role(View view){view.setFocusable(true);view.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(v,info);info.setClassName(android.widget.Button.class.getName());}});}
    static final class Checker extends Drawable {
        private final Paint paint=new Paint();private final int cell;
        Checker(int cell){this.cell=Math.max(1,cell);}
        public void draw(Canvas canvas){Rect b=getBounds();for(int y=b.top;y<b.bottom;y+=cell)for(int x=b.left;x<b.right;x+=cell){paint.setColor(((x-b.left)/cell+(y-b.top)/cell)%2==0?0xff34353d:0xff40414a);canvas.drawRect(x,y,Math.min(b.right,x+cell),Math.min(b.bottom,y+cell),paint);}}
        public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
