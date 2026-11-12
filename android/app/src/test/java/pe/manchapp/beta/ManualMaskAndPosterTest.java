package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w360dp-h640dp-xhdpi",shadows=PhotoEditorUiTest.PreviewRenderer.class,instrumentedPackages={"pe.manchapp.beta"})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ManualMaskAndPosterTest {
    private Context context;
    private Bitmap source,mask,background;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();source=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);source.eraseColor(Color.RED);mask=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);Paint paint=new Paint();paint.setColor(Color.RED);new Canvas(mask).drawRect(20,20,80,80,paint);background=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);background.eraseColor(Color.BLUE);}
    @After public void release(){source.recycle();mask.recycle();background.recycle();}
    private static Object field(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    private void waitIdle(Object activity)throws Exception{long until=System.currentTimeMillis()+10000;while((Boolean)field(activity,"busy")&&System.currentTimeMillis()<until){Thread.sleep(20);ShadowLooper.idleMainLooper();}assertFalse((Boolean)field(activity,"busy"));}
    private void tap(MaskBrushView brush,float x,float y){long now=android.os.SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(now,now+20,action,x,y,0);assertTrue(brush.onTouchEvent(event));event.recycle();}}
    private void layout(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    private void png(Bitmap bitmap,String name)throws Exception{java.nio.file.Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v074");Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}}
    @Test public void actualBrushErasesRestoresMissingPixelsAndUndoesWithoutChangingSource()throws Exception{
        MaskBrushView brush=new MaskBrushView(context,source,mask,()->{});FrameLayout host=new FrameLayout(context);host.addView(brush,new FrameLayout.LayoutParams(-1,-1));layout(host,400,400);tap(brush,200,200);assertEquals(0,Color.alpha(brush.working.getPixel(50,50)));brush.undo();assertEquals(255,Color.alpha(brush.working.getPixel(50,50)));brush.restore=true;tap(brush,59.2f,200);assertEquals(Color.RED,brush.working.getPixel(10,50));brush.undo();assertEquals(0,Color.alpha(brush.working.getPixel(10,50)));assertEquals(Color.RED,source.getPixel(10,50));assertEquals(0,Color.alpha(mask.getPixel(10,50)));brush.close();
    }
    @Test public void visibleRemoveButtonDeletesThirdOnlyAndAllowsRemovingLastAndUndo()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,mask,background,source);String first=doc.layers().getJSONObject(0).getString("id"),second=doc.addProduct(mask),third=doc.addProduct(mask);doc.save();Intent intent=new Intent(context,PhotoComposeActivity.class).putExtra("compositionId",doc.id());try(ActivityController<PhotoComposeActivity> controller=Robolectric.buildActivity(PhotoComposeActivity.class,intent).setup().visible()){PhotoComposeActivity activity=controller.get();View root=(View)field(activity,"root");root.findViewWithTag("layer:"+third).performClick();assertTrue(root.findViewWithTag("compose:remove").performClick());CompositionDocument current=(CompositionDocument)field(activity,"document");assertEquals(2,current.layers().length());assertNotNull(current.layer(first));assertNotNull(current.layer(second));assertNull(current.layer(third));assertEquals(2,CompositionDocument.open(context,doc.id()).layers().length());root.findViewWithTag("compose:remove").performClick();root.findViewWithTag("compose:remove").performClick();assertEquals(0,current.layers().length());assertEquals("Quitar fondo",((TextView)root.findViewWithTag("compose:remove")).getText());call(activity,"undo",new Class<?>[0]);assertEquals(1,current.layers().length());}
        doc.state=CompositionDocument.open(context,doc.id()).state;try(ActivityController<PhotoComposeActivity> reopened=Robolectric.buildActivity(PhotoComposeActivity.class,intent).setup()){assertFalse(reopened.get().isFinishing());}
    }
    @Test public void manualCorrectionUpdatesOnlySelectedLayerAndKeepsItsOriginalForRecovery()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,mask,background,source);String id=doc.layers().getJSONObject(0).getString("id"),oldFile=doc.layer(id).getString("file"),original=doc.layer(id).getString("source");Intent intent=new Intent(context,PhotoComposeActivity.class).putExtra("compositionId",doc.id());try(ActivityController<PhotoComposeActivity> parent=Robolectric.buildActivity(PhotoComposeActivity.class,intent).setup().visible()){PhotoComposeActivity activity=parent.get();View root=(View)field(activity,"root");assertTrue(root.findViewWithTag("compose:mask").performClick());waitIdle(activity);Intent opened=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;assertEquals(MaskEditorActivity.class.getName(),opened.getComponent().getClassName());Intent result;
            try(ActivityController<MaskEditorActivity> child=Robolectric.buildActivity(MaskEditorActivity.class,opened).setup().visible()){MaskEditorActivity editor=child.get();MaskBrushView brush=(MaskBrushView)field(editor,"brush");layout(brush,400,400);tap(brush,200,200);editor.findViewById(android.R.id.content).findViewWithTag("mask:apply").performClick();assertTrue(editor.isFinishing());result=Shadows.shadowOf(editor).getResultIntent();}
            call(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},73,Activity.RESULT_OK,result);waitIdle(activity);CompositionDocument current=(CompositionDocument)field(activity,"document");assertNotEquals(oldFile,current.layer(id).getString("file"));assertEquals(original,current.layer(id).getString("source"));assertEquals(0,Color.alpha(current.bitmap(current.layer(id).getString("file")).getPixel(50,50)));assertEquals(Color.RED,current.bitmap(original).getPixel(10,50));call(activity,"undo",new Class<?>[0]);assertEquals(oldFile,current.layer(id).getString("file"));
        }
    }
    @Test public void posterSelectionAcceptsFourProductsAndRendersAllStylesInBothSizes()throws Exception{
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){MainActivity activity=controller.get();LocalStore store=(LocalStore)field(activity,"store");String before=store.snapshot().toString();call(activity,"chooseFlyerProducts",new Class<?>[0]);ShadowLooper.idleMainLooper();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();ListView list=dialog.getListView();for(int i:new int[]{1,2,4,5})list.performItemClick(null,i,list.getItemIdAtPosition(i));assertFalse(list.isItemChecked(5));dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();JSONArray selected=(JSONArray)call(activity,"flyerProducts",new Class<?>[0]);assertEquals(4,selected.length());assertEquals(before,store.snapshot().toString());JSONObject settings=LocalStore.copy(store.snapshot().getJSONObject("settings")).put("phone","900000000").put("pickup","Punto de prueba").put("saleDate","2026-11-06").put("reserveUntil","2026-11-05T20:00").put("pickupFrom","2026-11-06T16:00").put("pickupUntil","2026-11-06T19:00");
            for(int style=0;style<3;style++)for(boolean square:new boolean[]{false,true}){Bitmap poster=FlyerRenderer.render(selected,settings,"Reserva con anticipación",style,square,new NativeUi(context));assertEquals(720,poster.getWidth());assertEquals(square?720:1280,poster.getHeight());png(poster,"poster-four-"+style+(square?"-square":"-vertical"));poster.recycle();}
            call(activity,"chooseFlyerProducts",new Class<?>[0]);ShadowLooper.idleMainLooper();AlertDialog cancel=ShadowAlertDialog.getLatestAlertDialog();cancel.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();ShadowLooper.idleMainLooper();assertEquals(4,((JSONArray)call(activity,"flyerProducts",new Class<?>[0])).length());
        }
    }
}
