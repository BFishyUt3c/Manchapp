package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.JSONObject;

/** Exercises real crop gestures, cancel/undo, transparent output and saving without editing a product. */
public final class EditorUiChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private static Field field(Object owner,String name)throws Exception{Field value=owner.getClass().getDeclaredField(name);value.setAccessible(true);return value;}
    private static void call(Object owner,String name)throws Exception{Method value=owner.getClass().getDeclaredMethod(name);value.setAccessible(true);value.invoke(owner);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void waitEditor(Activity editor)throws Exception{long end=android.os.SystemClock.elapsedRealtime()+45000;while(android.os.SystemClock.elapsedRealtime()<end){waitForIdleSync();if(!field(editor,"busy").getBoolean(editor))return;Thread.sleep(100);}throw new AssertionError("Editor did not finish");}
    private void onMain(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void invoke(Activity editor,String method){try{call(editor,method);}catch(Exception error){throw new IllegalStateException(error);}}
    private Dialog cropDialog(Activity editor){try{return (Dialog)field(editor,"cropDialog").get(editor);}catch(Exception error){throw new IllegalStateException(error);}}
    private PhotoCropView cropFrame(Dialog dialog){return dialog.getWindow().getDecorView().findViewWithTag("crop:frame");}
    private byte[] backup(File file)throws Exception{if(!file.isFile())return null;try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);return out.toByteArray();}}
    private void restore(File file,byte[] bytes)throws Exception{if(bytes==null){file.delete();return;}try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}}
    private void screenshot(View view,File file){try{Bitmap shot=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(shot));try(OutputStream out=new FileOutputStream(file)){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}catch(Exception error){throw new IllegalStateException(error);}}
    private void dragCorner(PhotoCropView frame){
        try{
            RectF image=new RectF((RectF)field(frame,"image").get(frame));long time=android.os.SystemClock.uptimeMillis();
            float x=image.left,y=image.top;
            for(int i=0;i<3;i++){
                int action=i==0?MotionEvent.ACTION_DOWN:i==1?MotionEvent.ACTION_MOVE:MotionEvent.ACTION_UP;
                MotionEvent event=MotionEvent.obtain(time,time+i*30,action,x+(i==0?0:image.width()*.12f),y+(i==0?0:image.height()*.12f),0);
                frame.dispatchTouchEvent(event);event.recycle();
            }
        }catch(Exception error){throw new IllegalStateException(error);}
    }
    @Override public void onStart(){
        Context context=getTargetContext();Bundle result=new Bundle();Activity editor=null;byte[] priorInput=null,priorOutput=null;boolean backed=false;
        try{
            priorInput=backup(PhotoEditorActivity.input(context));priorOutput=backup(PhotoEditorActivity.output(context));backed=true;
            Bitmap source=ImageTools.productPhoto(context,new JSONObject().put("id","causa"),640);
            try(OutputStream out=new FileOutputStream(PhotoEditorActivity.input(context))){source.compress(Bitmap.CompressFormat.JPEG,95,out);}source.recycle();
            editor=startActivitySync(new Intent(context,PhotoEditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitEditor(editor);
            check(field(editor,"result").get(editor)!=null,"Initial natural preview missing");final Activity active=editor;
            Bitmap original=(Bitmap)field(editor,"original").get(editor);int width=original.getWidth(),height=original.getHeight();
            File dir=new File(context.getFilesDir(),"qa-ui");dir.mkdirs();
            onMain(()->invoke(active,"openCrop"));Dialog first=cropDialog(active);check(first!=null&&first.isShowing(),"Crop dialog opens");
            onMain(()->{cropFrame(first).zoom(true);first.getWindow().getDecorView().findViewWithTag("crop:cancel").performClick();});
            check(PhotoCropView.isFull((RectF)field(editor,"crop").get(editor)),"Cancel leaves frame unchanged");
            onMain(()->invoke(active,"openCrop"));Dialog second=cropDialog(active);
            onMain(()->{dragCorner(cropFrame(second));screenshot(second.getWindow().getDecorView(),new File(dir,"crop.png"));second.getWindow().getDecorView().findViewWithTag("crop:apply").performClick();});waitEditor(editor);
            Bitmap cropped=(Bitmap)field(editor,"result").get(editor);check(cropped.getWidth()<width&&cropped.getHeight()<height,"Real corner gesture reduces frame");
            Bitmap comparison=(Bitmap)field(editor,"comparison").get(editor);check(comparison.getWidth()==cropped.getWidth()&&comparison.getHeight()==cropped.getHeight(),"Before and after use identical framing");
            check(original.getWidth()==width&&original.getHeight()==height&&!original.isRecycled(),"Cropping preserves full original");
            onMain(()->{try{((View)field(active,"undoCrop").get(active)).performClick();}catch(Exception error){throw new IllegalStateException(error);}});waitEditor(editor);
            check(((Bitmap)field(editor,"result").get(editor)).getWidth()==width,"Undo restores full width");
            onMain(()->invoke(active,"openCrop"));Dialog third=cropDialog(active);
            onMain(()->{cropFrame(third).zoom(true);third.getWindow().getDecorView().findViewWithTag("crop:apply").performClick();});waitEditor(editor);
            onMain(()->active.getWindow().getDecorView().findViewWithTag("marble").performClick());waitEditor(editor);
            onMain(()->active.getWindow().getDecorView().findViewWithTag("transparent").performClick());waitEditor(editor);
            Bitmap cutout=(Bitmap)field(editor,"result").get(editor);check(ImageTools.hasTransparency(cutout),"Transparent UI produces alpha");
            FoodPhotoEngine engine=(FoodPhotoEngine)field(editor,"engine").get(editor);check(engine.maskRuns==1,"Background switches reuse inference after cropping");
            onMain(()->{Bundle state=new Bundle();((PhotoEditorActivity)active).onSaveInstanceState(state);check(state.getFloatArray("crop")!=null,"Crop saved for activity recreation");screenshot(active.getWindow().getDecorView(),new File(dir,"editor-cutout.png"));invoke(active,"save");});
            long end=android.os.SystemClock.elapsedRealtime()+10000;while(!editor.isFinishing()&&android.os.SystemClock.elapsedRealtime()<end){Thread.sleep(100);waitForIdleSync();}
            check(editor.isFinishing(),"Editor save finishes");String encoded=LocalStore.read(new FileInputStream(PhotoEditorActivity.output(context)));
            check(encoded.startsWith("data:image/png;base64,"),"Edited cutout saved as PNG");Bitmap saved=ImageTools.decode(encoded);
            check(saved!=null&&saved.getWidth()<width&&saved.getHeight()<height&&ImageTools.hasTransparency(saved),"Saved image keeps crop and alpha");saved.recycle();
            result.putString("stream","PASS: actual crop gesture, cancel, undo, aligned comparison, cached cutout and private-file transparent save.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
        finally{try{if(editor!=null&&!editor.isFinishing()){final Activity active=editor;runOnMainSync(()->active.finish());}waitForIdleSync();if(backed){restore(PhotoEditorActivity.input(context),priorInput);restore(PhotoEditorActivity.output(context),priorOutput);}}catch(Exception ignored){}}
    }
}