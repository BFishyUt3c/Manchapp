package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

final class MaskBrushView extends View {
    final Bitmap working;
    private final int[] colors;
    private final byte[] alpha,sourceAlpha;
    private final int width,height;
    private final Paint paint=new Paint(3);
    private final PhotoUi.Checker checker;
    private final ScaleGestureDetector pinch;
    private final Runnable changed;
    private final ArrayDeque<Undo> history=new ArrayDeque<>();
    private int historyBytes=0;
    private byte[] before;
    private Rect touched;
    private final RectF image=new RectF();
    private float zoom=1,panX=0,panY=0,lastX,lastY,lastPaintX,lastPaintY;
    private boolean drawing=false,navigation=false;
    boolean restore=false;
    float brush=16;
    private static final class Undo {final Rect area;final byte[] pixels;Undo(Rect area,byte[] pixels){this.area=area;this.pixels=pixels;}}
    MaskBrushView(Context context,Bitmap source,Bitmap mask,Runnable changed){super(context);if(source.getWidth()!=mask.getWidth()||source.getHeight()!=mask.getHeight())throw new IllegalArgumentException("Dimensiones del recorte diferentes.");this.changed=changed;width=source.getWidth();height=source.getHeight();colors=new int[width*height];int[] pixels=new int[width*height];source.getPixels(colors,0,width,0,0,width,height);mask.getPixels(pixels,0,width,0,0,width,height);alpha=new byte[pixels.length];sourceAlpha=new byte[pixels.length];for(int i=0;i<pixels.length;i++){alpha[i]=(byte)Color.alpha(pixels[i]);sourceAlpha[i]=(byte)Color.alpha(colors[i]);pixels[i]=(colors[i]&0x00ffffff)|((alpha[i]&255)<<24);}working=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);working.setPixels(pixels,0,width,0,0,width,height);checker=new PhotoUi.Checker(Math.round(10*getResources().getDisplayMetrics().density));setContentDescription("Corrección manual del recorte. Un dedo pinta; dos dedos acercan y mueven la foto.");setFocusable(true);pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector detector){zoom=Math.max(1,Math.min(6,zoom*detector.getScaleFactor()));invalidate();return true;}});}
    private void fit(){float base=Math.min(Math.max(1,getWidth()-24*getResources().getDisplayMetrics().density)/width,Math.max(1,getHeight()-24*getResources().getDisplayMetrics().density)/height),w=width*base*zoom,h=height*base*zoom;panX=Math.max(-Math.max(0,(w-getWidth())/2),Math.min(Math.max(0,(w-getWidth())/2),panX));panY=Math.max(-Math.max(0,(h-getHeight())/2),Math.min(Math.max(0,(h-getHeight())/2),panY));image.set((getWidth()-w)/2+panX,(getHeight()-h)/2+panY,(getWidth()+w)/2+panX,(getHeight()+h)/2+panY);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(PhotoUi.BG);fit();int saved=canvas.save();canvas.clipRect(image);checker.setBounds((int)image.left,(int)image.top,(int)image.right,(int)image.bottom);checker.draw(canvas);canvas.drawBitmap(working,null,image,paint);canvas.restoreToCount(saved);}
    private float px(float x){return (x-image.left)*width/image.width();}private float py(float y){return (y-image.top)*height/image.height();}
    private void dab(float x,float y){float radius=brush*getResources().getDisplayMetrics().density*width/image.width();int left=Math.max(0,(int)(x-radius-1)),right=Math.min(width,(int)(x+radius+2)),top=Math.max(0,(int)(y-radius-1)),bottom=Math.min(height,(int)(y+radius+2));if(left>=right||top>=bottom)return;Rect area=new Rect(left,top,right,bottom);if(touched==null)touched=new Rect(area);else touched.union(area);int[] row=new int[right-left];for(int yy=top;yy<bottom;yy++){for(int xx=left;xx<right;xx++){int at=yy*width+xx;float distance=(float)Math.hypot(xx+.5f-x,yy+.5f-y),weight=Math.max(0,Math.min(1,(radius-distance)/Math.max(1,radius*.18f)));int old=alpha[at]&255,target=restore?sourceAlpha[at]&255:0;alpha[at]=(byte)Math.round(old+(target-old)*weight);row[xx-left]=(colors[at]&0x00ffffff)|((alpha[at]&255)<<24);}working.setPixels(row,0,row.length,left,yy,row.length,1);}invalidate();}
    private void line(float x,float y){float distance=(float)Math.hypot(x-lastPaintX,y-lastPaintY),step=Math.max(1,brush*getResources().getDisplayMetrics().density*width/image.width()*.35f);int steps=Math.max(1,(int)Math.ceil(distance/step));for(int i=1;i<=steps;i++)dab(lastPaintX+(x-lastPaintX)*i/steps,lastPaintY+(y-lastPaintY)*i/steps);lastPaintX=x;lastPaintY=y;}
    private void finishStroke(boolean cancel){if(before==null)return;if(cancel){System.arraycopy(before,0,alpha,0,alpha.length);refresh(new Rect(0,0,width,height));}else if(touched!=null){byte[] saved=new byte[touched.width()*touched.height()];for(int y=0;y<touched.height();y++)System.arraycopy(before,(touched.top+y)*width+touched.left,saved,y*touched.width(),touched.width());history.addLast(new Undo(new Rect(touched),saved));historyBytes+=saved.length;while(history.size()>10||historyBytes>8*1024*1024){Undo removed=history.removeFirst();historyBytes-=removed.pixels.length;}changed.run();}before=null;touched=null;drawing=false;}
    private void refresh(Rect area){int[] row=new int[area.width()];for(int y=area.top;y<area.bottom;y++){for(int x=area.left;x<area.right;x++){int at=y*width+x;row[x-area.left]=(colors[at]&0x00ffffff)|((alpha[at]&255)<<24);}working.setPixels(row,0,row.length,area.left,y,row.length,1);}invalidate();}
    void undo(){finishStroke(false);if(history.isEmpty())return;Undo last=history.removeLast();historyBytes-=last.pixels.length;for(int y=0;y<last.area.height();y++)System.arraycopy(last.pixels,y*last.area.width(),alpha,(last.area.top+y)*width+last.area.left,last.area.width());refresh(last.area);changed.run();}
    @Override public boolean onTouchEvent(MotionEvent event){if(!isEnabled())return false;fit();pinch.onTouchEvent(event);switch(event.getActionMasked()){
        case MotionEvent.ACTION_DOWN:before=alpha.clone();touched=null;drawing=true;navigation=false;lastX=event.getX();lastY=event.getY();lastPaintX=px(lastX);lastPaintY=py(lastY);getParent().requestDisallowInterceptTouchEvent(true);return true;
        case MotionEvent.ACTION_POINTER_DOWN:finishStroke(false);navigation=true;lastX=(event.getX(0)+event.getX(1))/2;lastY=(event.getY(0)+event.getY(1))/2;return true;
        case MotionEvent.ACTION_MOVE:if(event.getPointerCount()>1){float x=(event.getX(0)+event.getX(1))/2,y=(event.getY(0)+event.getY(1))/2;panX+=x-lastX;panY+=y-lastY;lastX=x;lastY=y;invalidate();return true;}if(drawing&&!navigation)line(px(event.getX()),py(event.getY()));return true;
        case MotionEvent.ACTION_UP:if(drawing&&!navigation){line(px(event.getX()),py(event.getY()));finishStroke(false);}drawing=false;navigation=false;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;
        case MotionEvent.ACTION_CANCEL:finishStroke(true);navigation=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
        default:return true;}}
    @Override public boolean performClick(){super.performClick();return true;}
    void close(){working.recycle();history.clear();}
}
