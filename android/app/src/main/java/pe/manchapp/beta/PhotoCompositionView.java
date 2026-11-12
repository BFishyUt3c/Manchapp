package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.json.*;

final class PhotoCompositionView extends View {
    interface Listener {void selected();void changed(String before);void failed();}
    final CompositionDocument doc;
    private final Listener listener;
    String selected;
    final RectF frame=new RectF();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector pinch;
    private float lastX,lastY;
    private String before;
    PhotoCompositionView(Context context,CompositionDocument doc,String selected,Listener listener){super(context);this.doc=doc;this.selected=selected;this.listener=listener;setContentDescription("Composición. Toca un producto para seleccionarlo, arrástralo para moverlo y usa dos dedos para cambiar su tamaño.");setFocusable(true);pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector detector){try{doc.zoom(PhotoCompositionView.this.selected,detector.getScaleFactor());invalidate();return true;}catch(Exception error){listener.failed();return false;}}});}
    private void fit(){float scale=Math.min(Math.max(1,getWidth()-32*getResources().getDisplayMetrics().density)/doc.width(),Math.max(1,getHeight()-24*getResources().getDisplayMetrics().density)/doc.height());float w=doc.width()*scale,h=doc.height()*scale;frame.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(PhotoUi.BG);fit();int saved=canvas.save();canvas.clipRect(frame);canvas.translate(frame.left,frame.top);canvas.scale(frame.width()/doc.width(),frame.height()/doc.height());try{doc.draw(canvas);if(!selected.equals("background")&&doc.layer(selected)!=null){RectF bounds=doc.selectionRect(doc.layer(selected));paint.setStyle(Paint.Style.STROKE);paint.setColor(PhotoUi.LIGHT);paint.setStrokeWidth(1.5f*doc.width()/frame.width());canvas.drawRect(bounds,paint);}}catch(Exception error){listener.failed();}finally{canvas.restoreToCount(saved);}}
    @Override public boolean onTouchEvent(MotionEvent event){if(!isEnabled())return false;fit();pinch.onTouchEvent(event);try{
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(!frame.contains(event.getX(),event.getY()))return false;before=doc.state.toString();lastX=event.getX();lastY=event.getY();
                float x=(lastX-frame.left)*doc.width()/frame.width(),y=(lastY-frame.top)*doc.height()/frame.height();String hit="background";JSONArray layers=doc.layers();for(int i=layers.length()-1;i>=0;i--)if(doc.selectionRect(layers.getJSONObject(i)).contains(x,y)){hit=layers.getJSONObject(i).getString("id");break;}
                if(!selected.equals(hit)){selected=hit;listener.selected();}
                getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(before==null)return false;if(!pinch.isInProgress()&&event.getPointerCount()==1)doc.pan(selected,(event.getX()-lastX)/frame.width(),(event.getY()-lastY)/frame.height());lastX=event.getX();lastY=event.getY();invalidate();return true;
            case MotionEvent.ACTION_POINTER_UP:int next=event.getActionIndex()==0?1:0;if(next<event.getPointerCount()){lastX=event.getX(next);lastY=event.getY(next);}return true;
            case MotionEvent.ACTION_UP:if(before!=null){listener.changed(before);before=null;}performClick();getParent().requestDisallowInterceptTouchEvent(false);return true;
            case MotionEvent.ACTION_CANCEL:if(before!=null){doc.state=new JSONObject(before);before=null;invalidate();}getParent().requestDisallowInterceptTouchEvent(false);return true;
        }
    }catch(Exception error){listener.failed();}return before!=null;}
    @Override public boolean performClick(){super.performClick();return true;}
}
