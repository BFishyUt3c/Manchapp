package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.TextView;
import org.json.*;
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
@Config(sdk=30,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotoFlowTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();new android.util.AtomicFile(PhotoStudioActivity.workspace(context)).delete();for(String name:new String[]{"manchapp.json","editor-draft.json","pending-qr.json"})new File(context.getFilesDir(),name).delete();}
    private static Object field(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    private View text(View root,String label){if(root instanceof TextView&&((TextView)root).getText().toString().equals(label))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=text(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private View described(View root,String label){if(label.equals(root.getContentDescription()))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=described(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private String image(int color)throws Exception{Bitmap bitmap=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);bitmap.setPixel(0,0,Color.TRANSPARENT);String encoded=ImageTools.encodeEdited(bitmap);bitmap.recycle();return encoded;}
    private void workspace(String source,String edited)throws Exception{Files.write(PhotoStudioActivity.workspace(context).toPath(),new JSONObject().put("source",source).put("image",edited).toString().getBytes("UTF-8"));}
    private void waitIdle(PhotoStudioActivity activity)throws Exception{long until=System.currentTimeMillis()+10000;while((Boolean)field(activity,"busy")&&System.currentTimeMillis()<until){Thread.sleep(20);ShadowLooper.idleMainLooper();}assertFalse((Boolean)field(activity,"busy"));}
    private void profile(AppShell shell)throws Exception{JSONObject settings=shell.store().snapshot().getJSONObject("settings");AppShell.put(settings,"name","Prueba");AppShell.put(settings,"pickup","Punto de prueba");shell.enqueue("settings",settings,"settings");shell.render();}
    private void shot(View view,String name)throws Exception{if(view.getId()==android.R.id.content&&view instanceof ViewGroup&&((ViewGroup)view).getChildCount()==1)view=((ViewGroup)view).getChildAt(0);view.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));view.layout(0,0,720,1280);Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));java.nio.file.Path dir=Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v072");Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}

    @Test public void standalonePhotoNeedsNoProductAndOpensGalleryRatherThanPoster()throws Exception{
        try(ActivityController<MainActivity> owner=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=owner.get();AppShell shell=(AppShell)field(activity,"shell");profile(shell);shell.go("publications","");View action=text(activity.findViewById(android.R.id.content),"Editar una foto");assertNotNull(action);while(!action.isClickable()&&action.getParent() instanceof View)action=(View)action.getParent();assertTrue(action.performClick());Intent opened=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;assertEquals(PhotoStudioActivity.class.getName(),opened.getComponent().getClassName());assertEquals("publications",shell.screen());shot(activity.findViewById(android.R.id.content),"create-tools");
        }
        try(ActivityController<PhotoStudioActivity> controller=Robolectric.buildActivity(PhotoStudioActivity.class).setup().visible()){
            PhotoStudioActivity activity=controller.get();assertTrue(text(activity.findViewById(android.R.id.content),"Elegir foto").performClick());Intent picker=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.getAction());assertEquals("image/*",picker.getType());shot(activity.findViewById(android.R.id.content),"photo-empty");
        }
    }

    @Test public void editResultSurvivesReopenAndExportsTransparentPhotoWithoutProduct()throws Exception{
        String source=image(Color.RED),edited=image(Color.GREEN);workspace(source,source);Files.write(PhotoEditorActivity.output(context).toPath(),edited.getBytes("UTF-8"));
        try(ActivityController<PhotoStudioActivity> controller=Robolectric.buildActivity(PhotoStudioActivity.class).setup().visible()){
            PhotoStudioActivity activity=controller.get();call(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},84,Activity.RESULT_OK,null);waitIdle(activity);assertEquals(source,PhotoStudioActivity.read(context).getString("source"));assertEquals(edited,PhotoStudioActivity.read(context).getString("image"));
        }
        try(ActivityController<PhotoStudioActivity> controller=Robolectric.buildActivity(PhotoStudioActivity.class).setup().visible()){
            PhotoStudioActivity activity=controller.get();assertEquals(edited,((JSONObject)field(activity,"photo")).getString("image"));View save=text(activity.findViewById(android.R.id.content),"Guardar");assertTrue(save.performClick());Intent export=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,export.getAction());assertEquals("image/png",export.getType());
            ByteArrayOutputStream output=new ByteArrayOutputStream();Uri uri=Uri.parse("content://photo-test/export.png");Shadows.shadowOf(context.getContentResolver()).registerOutputStream(uri,output);call(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},86,Activity.RESULT_OK,new Intent().setData(uri));waitIdle(activity);Bitmap saved=BitmapFactory.decodeByteArray(output.toByteArray(),0,output.size());assertNotNull(saved);assertEquals(120,saved.getWidth());assertEquals(0,Color.alpha(saved.getPixel(0,0)));saved.recycle();shot(activity.findViewById(android.R.id.content),"photo-ready");
            assertTrue(text(activity.findViewById(android.R.id.content),"Restablecer original").performClick());assertEquals(source,PhotoStudioActivity.read(context).getString("image"));
        }
    }

    @Test public void photoCanStartUnsavedProductBeforeNamePriceOrQuantity()throws Exception{
        Bitmap actual=ImageTools.productPhoto(context,new JSONObject().put("id","causa"),640);String source=ImageTools.encodeEdited(actual);actual.recycle();String edited=source;workspace(source,edited);
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();AppShell shell=(AppShell)field(activity,"shell");profile(shell);LocalStore store=shell.store();int pending=store.snapshot().getJSONArray("pending").length(),products=store.snapshot().getJSONArray("products").length();
            ProductEditorScreen.attachStudioPhoto(shell,null,true);assertEquals("edit",shell.screen());JSONObject draft=shell.draft();assertEquals("",draft.optString("name"));assertEquals(edited,draft.getString("image"));assertEquals(source,draft.getString("_sourcePhoto"));assertEquals(products,store.snapshot().getJSONArray("products").length());assertEquals(pending,store.snapshot().getJSONArray("pending").length());assertNotNull(text(activity.findViewById(android.R.id.content),"Recortar y mejorar foto"));android.widget.ImageView preview=(android.widget.ImageView)described(activity.findViewById(android.R.id.content),"Vista previa completa de la foto del producto");assertNotNull(preview);assertEquals(android.widget.ImageView.ScaleType.FIT_CENTER,preview.getScaleType());assertEquals(shell.dp(200),preview.getLayoutParams().height);shot(activity.findViewById(android.R.id.content),"product-photo-first");
            call(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},84,Activity.RESULT_CANCELED,null);assertEquals(edited,shell.draft().getString("image"));shell.saveDraft();
        }
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();AppShell shell=(AppShell)field(activity,"shell");shell.go("products","");assertNotNull(text(activity.findViewById(android.R.id.content),"Continuar borrador de producto"));assertEquals(edited,shell.draft().getString("image"));
        }
    }

    @Test public void attachingPhotoToExistingProductKeepsItsDataAndWaitsForSave()throws Exception{
        String source=image(Color.RED),edited=image(Color.GREEN);workspace(source,edited);
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();AppShell shell=(AppShell)field(activity,"shell");LocalStore store=shell.store();JSONObject before=store.snapshot(),product=before.getJSONArray("products").getJSONObject(0);ProductEditorScreen.attachStudioPhoto(shell,product,true);JSONObject draft=shell.draft();assertEquals(product.getString("id"),draft.getString("id"));assertEquals(product.getString("name"),draft.getString("name"));assertEquals(product.getDouble("price"),draft.getDouble("price"),0);assertEquals(product.getInt("version"),draft.getInt("version"));assertEquals(edited,draft.getString("image"));assertEquals(before.toString(),store.snapshot().toString());
            ProductEditorScreen.attachStudioPhoto(shell,null,false);assertEquals("",shell.draft().optString("id",""));assertEquals(edited,shell.draft().optString("image",""));
        }
    }
}
