package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** A bounded, non-destructive crop in normalized source-image coordinates. */
final class PhotoCropView extends View {
    private static final int LEFT=1, TOP=2, RIGHT=4, BOTTOM=8, MOVE=16;
    private static final float MIN_CROP=.16f;
    private final Bitmap photo;
    private final RectF image=new RectF(), selection=new RectF(0,0,1,1);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final float density;
    private int drag;
    private float lastX,lastY;

    PhotoCropView(Context context,Bitmap photo,RectF initial){
        super(context);this.photo=photo;density=getResources().getDisplayMetrics().density;
        selection.set(initial);setFocusable(true);setContentDescription("Encuadre de la foto. Arrastra las esquinas para recortar o el centro para mover el encuadre.");
    }
    RectF crop(){return new RectF(selection);}
    void reset(){selection.set(0,0,1,1);invalidate();}
    void zoom(boolean closer){
        float factor=closer?.85f:1.18f;
        float width=Math.max(MIN_CROP,Math.min(1,selection.width()*factor));
        float height=Math.max(MIN_CROP,Math.min(1,selection.height()*factor));
        float left=Math.max(0,Math.min(1-width,selection.centerX()-width/2));
        float top=Math.max(0,Math.min(1-height,selection.centerY()-height/2));
        selection.set(left,top,left+width,top+height);invalidate();
    }
    private void layoutImage(){
        float availableWidth=Math.max(1,getWidth()-32*density),availableHeight=Math.max(1,getHeight()-32*density);
        float scale=Math.min(availableWidth/photo.getWidth(),availableHeight/photo.getHeight());
        float width=photo.getWidth()*scale,height=photo.getHeight()*scale;
        image.set((getWidth()-width)/2,(getHeight()-height)/2,(getWidth()+width)/2,(getHeight()+height)/2);
    }
    private RectF frame(){return new RectF(image.left+selection.left*image.width(),image.top+selection.top*image.height(),image.left+selection.right*image.width(),image.top+selection.bottom*image.height());}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);layoutImage();canvas.drawColor(PhotoUi.BG);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawBitmap(photo,null,image,paint);
        RectF frame=frame();paint.setColor(0x99000000);
        canvas.drawRect(image.left,image.top,image.right,frame.top,paint);
        canvas.drawRect(image.left,frame.bottom,image.right,image.bottom,paint);
        canvas.drawRect(image.left,frame.top,frame.left,frame.bottom,paint);
        canvas.drawRect(frame.right,frame.top,image.right,frame.bottom,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setColor(0x88ffffff);
        for(int i=1;i<3;i++){float x=frame.left+frame.width()*i/3,y=frame.top+frame.height()*i/3;canvas.drawLine(x,frame.top,x,frame.bottom,paint);canvas.drawLine(frame.left,y,frame.right,y,paint);}
        paint.setStrokeWidth(2*density);paint.setColor(Color.WHITE);canvas.drawRect(frame,paint);
        paint.setStyle(Paint.Style.FILL);
        for(float x:new float[]{frame.left,frame.right})for(float y:new float[]{frame.top,frame.bottom}){paint.setColor(PhotoUi.ACCENT);canvas.drawCircle(x,y,8*density,paint);paint.setColor(Color.WHITE);canvas.drawCircle(x,y,4*density,paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled())return false;layoutImage();float x=event.getX(),y=event.getY();
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                RectF frame=frame();float reach=28*density;RectF target=new RectF(frame);target.inset(-reach,-reach);if(!target.contains(x,y))return false;
                drag=0;
                if(Math.abs(x-frame.left)<=reach)drag|=LEFT;else if(Math.abs(x-frame.right)<=reach)drag|=RIGHT;
                if(Math.abs(y-frame.top)<=reach)drag|=TOP;else if(Math.abs(y-frame.bottom)<=reach)drag|=BOTTOM;
                if(drag==0){if(!frame.contains(x,y))return false;drag=MOVE;}
                lastX=x;lastY=y;getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(drag==0)return false;
                float dx=(x-lastX)/image.width(),dy=(y-lastY)/image.height();lastX=x;lastY=y;
                if(drag==MOVE){dx=Math.max(-selection.left,Math.min(1-selection.right,dx));dy=Math.max(-selection.top,Math.min(1-selection.bottom,dy));selection.offset(dx,dy);}
                else{
                    float minWidth=Math.max(MIN_CROP,Math.min(.5f,96f/photo.getWidth())),minHeight=Math.max(MIN_CROP,Math.min(.5f,96f/photo.getHeight()));
                    if((drag&LEFT)!=0)selection.left=Math.max(0,Math.min(selection.right-minWidth,selection.left+dx));
                    if((drag&RIGHT)!=0)selection.right=Math.min(1,Math.max(selection.left+minWidth,selection.right+dx));
                    if((drag&TOP)!=0)selection.top=Math.max(0,Math.min(selection.bottom-minHeight,selection.top+dy));
                    if((drag&BOTTOM)!=0)selection.bottom=Math.min(1,Math.max(selection.top+minHeight,selection.bottom+dy));
                }
                invalidate();return true;
            case MotionEvent.ACTION_UP:drag=0;performClick();getParent().requestDisallowInterceptTouchEvent(false);return true;
            case MotionEvent.ACTION_CANCEL:drag=0;getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return drag!=0;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    static boolean isFull(RectF crop){return crop.left<=.0001f&&crop.top<=.0001f&&crop.right>=.9999f&&crop.bottom>=.9999f;}
    static Bitmap apply(Bitmap source,RectF crop){
        if(isFull(crop))return source;
        int left=Math.max(0,Math.min(source.getWidth()-1,Math.round(crop.left*source.getWidth())));
        int top=Math.max(0,Math.min(source.getHeight()-1,Math.round(crop.top*source.getHeight())));
        int right=Math.max(left+1,Math.min(source.getWidth(),Math.round(crop.right*source.getWidth())));
        int bottom=Math.max(top+1,Math.min(source.getHeight(),Math.round(crop.bottom*source.getHeight())));
        return Bitmap.createBitmap(source,left,top,right-left,bottom-top);
    }
}
