package pe.manchapp.beta;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w360dp-h640dp-xhdpi",shadows=PhotoEditorUiTest.PreviewRenderer.class,instrumentedPackages={"pe.manchapp.beta"})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotoEditorUiTest {
    @Implements(value=FoodPhotoEngine.class,isInAndroidSdk=false)
    public static class PreviewRenderer {
        private Bitmap source;
        static boolean failMask=false;
        @Implementation protected void __constructor__(Context context,Bitmap bitmap){source=bitmap;}
        @Implementation protected void includeObjects(boolean value){}
        @Implementation protected void setManualMask(Bitmap value){}
        @Implementation protected Bitmap render(float strength,String background)throws IOException{if(failMask&&background.equals("transparent"))throw new IOException("No se pudo recortar automáticamente");return source.copy(Bitmap.Config.ARGB_8888,true);}
        @Implementation protected void close(){}
    }
    private static Object field(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static void call(Object target,String name)throws Exception{Method method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(target);}
    private void waitIdle(PhotoEditorActivity activity)throws Exception{long until=System.currentTimeMillis()+10000;while((Boolean)field(activity,"busy")&&System.currentTimeMillis()<until){Thread.sleep(20);ShadowLooper.idleMainLooper();}assertFalse((Boolean)field(activity,"busy"));}
    private void source()throws Exception{Context context=RuntimeEnvironment.getApplication();Bitmap bitmap=ImageTools.productPhoto(context,new JSONObject().put("id","causa"),640);try(OutputStream out=new FileOutputStream(PhotoEditorActivity.input(context))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}
    private void shot(View root,String name)throws Exception{root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1280);Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));java.nio.file.Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v072");Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();}
    @Test public void compactToolsKeepLargePreviewAndComparisonWithTestRenderer()throws Exception{
        source();try(ActivityController<PhotoEditorActivity> controller=Robolectric.buildActivity(PhotoEditorActivity.class).setup().visible()){
            PhotoEditorActivity activity=controller.get();waitIdle(activity);View root=(View)field(activity,"root");shot(root,"editor-adjust");View stage=root.findViewWithTag("editor:stage");assertTrue(stage.getHeight()>320*2);assertTrue(root.findViewWithTag("compare:original").performClick());assertEquals(true,field(activity,"showOriginal"));assertTrue(root.findViewWithTag("compare:result").performClick());assertEquals(false,field(activity,"showOriginal"));
            root.findViewWithTag("tool:background").performClick();assertEquals("background",field(activity,"tool"));assertTrue(root.findViewWithTag("custom").isShown());shot(root,"editor-backgrounds");root.findViewWithTag("tool:crop").performClick();call(activity,"openCrop");Dialog dialog=(Dialog)field(activity,"cropDialog");assertNotNull(dialog);PhotoCropView crop=dialog.findViewById(android.R.id.content).findViewWithTag("crop:frame");crop.zoom(true);shot(((android.view.ViewGroup)dialog.findViewById(android.R.id.content)).getChildAt(0),"editor-crop");dialog.findViewById(android.R.id.content).findViewWithTag("crop:apply").performClick();waitIdle(activity);assertFalse(PhotoCropView.isFull((RectF)field(activity,"crop")));assertEquals(((Bitmap)field(activity,"result")).getWidth(),((Bitmap)field(activity,"comparison")).getWidth());
            Bundle saved=new Bundle();activity.onSaveInstanceState(saved);assertEquals("crop",saved.getString("tool"));assertNotNull(saved.getFloatArray("crop"));Bitmap edited=(Bitmap)field(activity,"result");edited.setHasAlpha(true);edited.setPixel(0,0,Color.TRANSPARENT);root.findViewWithTag("compare:original").performClick();assertEquals(true,field(activity,"showOriginal"));assertTrue(root.findViewWithTag("editor:save").performClick());long until=System.currentTimeMillis()+10000;while(!activity.isFinishing()&&System.currentTimeMillis()<until){Thread.sleep(20);ShadowLooper.idleMainLooper();}assertTrue(activity.isFinishing());Bitmap output=ImageTools.decode(SafeJson.read(new FileInputStream(PhotoEditorActivity.output(activity))));assertNotNull(output);assertTrue(output.getWidth()<499);assertEquals(0,Color.alpha(output.getPixel(0,0)));output.recycle();
        }
    }
    @Test public void customBackgroundOpensCompositionAndCancelKeepsPreviousEdit()throws Exception{
        source();Context context=RuntimeEnvironment.getApplication();
        try(ActivityController<PhotoEditorActivity> controller=Robolectric.buildActivity(PhotoEditorActivity.class).setup().visible()){
            PhotoEditorActivity activity=controller.get();waitIdle(activity);View root=(View)field(activity,"root");root.findViewWithTag("tool:background").performClick();assertTrue(root.findViewWithTag("custom").performClick());assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT,Shadows.shadowOf(activity).getNextStartedActivityForResult().intent.getAction());Bitmap background=Bitmap.createBitmap(240,160,Bitmap.Config.ARGB_8888);background.eraseColor(Color.LTGRAY);ByteArrayOutputStream bytes=new ByteArrayOutputStream();background.compress(Bitmap.CompressFormat.PNG,100,bytes);background.recycle();android.net.Uri uri=android.net.Uri.parse("content://composition-test/background.png");Shadows.shadowOf(context.getContentResolver()).registerInputStreamSupplier(uri,()->new ByteArrayInputStream(bytes.toByteArray()));Method callback=PhotoEditorActivity.class.getDeclaredMethod("onActivityResult",int.class,int.class,android.content.Intent.class);callback.setAccessible(true);callback.invoke(activity,91,Activity.RESULT_OK,new android.content.Intent().setData(uri));waitIdle(activity);android.content.Intent opened=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;assertEquals(PhotoComposeActivity.class.getName(),opened.getComponent().getClassName());CompositionDocument doc=CompositionDocument.open(context,opened.getStringExtra("compositionId"));assertEquals(1,doc.layers().length());assertEquals(240,doc.width());callback.invoke(activity,92,Activity.RESULT_CANCELED,null);waitIdle(activity);assertNotNull(field(activity,"engine"));assertNotNull(field(activity,"result"));assertFalse(activity.isFinishing());
        }
    }

    @Test public void manualBrushCanOpenEvenWhenAutomaticCutoutFails()throws Exception{
        source();PreviewRenderer.failMask=true;
        try(ActivityController<PhotoEditorActivity> controller=Robolectric.buildActivity(PhotoEditorActivity.class).setup().visible()){
            PhotoEditorActivity activity=controller.get();waitIdle(activity);call(activity,"openManualMask");waitIdle(activity);android.content.Intent opened=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;assertEquals(MaskEditorActivity.class.getName(),opened.getComponent().getClassName());Bitmap mask=MaskEditSession.open(activity,opened.getStringExtra("maskSessionId")).read("current.png");assertNotNull(mask);assertEquals(255,Color.alpha(mask.getPixel(mask.getWidth()/2,mask.getHeight()/2)));mask.recycle();
        }finally{PreviewRenderer.failMask=false;}
    }

}
