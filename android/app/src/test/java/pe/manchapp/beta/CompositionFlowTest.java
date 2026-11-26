package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.FrameLayout;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import java.io.*;
import java.lang.reflect.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w360dp-h640dp-xhdpi",shadows=PhotoEditorUiTest.PreviewRenderer.class,instrumentedPackages={"pe.manchapp.beta"})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CompositionFlowTest {
    private Context context;
    private Bitmap product,back;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();product=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(product);Paint paint=new Paint();paint.setColor(Color.RED);canvas.drawRect(25,25,75,75,paint);back=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);back.eraseColor(Color.BLUE);}
    @After public void release(){product.recycle();back.recycle();}
    private static Object field(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static Object call(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    private void waitIdle(Object activity)throws Exception{long until=System.currentTimeMillis()+10000;while((Boolean)field(activity,"busy")&&System.currentTimeMillis()<until){Thread.sleep(20);ShadowLooper.idleMainLooper();}assertFalse((Boolean)field(activity,"busy"));}
    private void shot(View root)throws Exception{root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1280);Bitmap bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));java.nio.file.Path dir=java.nio.file.Paths.get(System.getProperty("manchapp.root"),"tmp","ui-v073");java.nio.file.Files.createDirectories(dir);try(OutputStream out=java.nio.file.Files.newOutputStream(dir.resolve("compose.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @Test public void transparentProductsCombineOverRealBackgroundWithOrderAndBoundedCanvas()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,product,back);String first=doc.layers().getJSONObject(0).getString("id");Bitmap exported=doc.export();assertEquals(Color.BLUE,exported.getPixel(0,0));assertEquals(Color.RED,exported.getPixel(100,50));exported.recycle();
        Bitmap green=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);green.eraseColor(Color.GREEN);String second=doc.addProduct(green);green.recycle();doc.layer(second).put("x",.5);exported=doc.export();assertEquals(Color.GREEN,exported.getPixel(100,50));exported.recycle();
        doc.state.put("layers",new JSONArray().put(doc.layer(second)).put(doc.layer(first)));exported=doc.export();assertEquals(Color.RED,exported.getPixel(100,50));exported.recycle();doc.zoom(first,.5f);doc.pan(first,.2f,0);doc.state.put("format","portrait");doc.zoom("background",2);doc.pan("background",100,-100);doc.save();
        CompositionDocument restored=CompositionDocument.open(context,doc.id());assertEquals(doc.state.toString(),restored.state.toString());exported=restored.export();assertEquals(768,exported.getWidth());assertEquals(960,exported.getHeight());assertEquals(Color.BLUE,exported.getPixel(0,0));assertEquals(Color.BLUE,exported.getPixel(767,959));exported.recycle();
        doc.addProduct(product);doc.addProduct(product);try{doc.addProduct(product);fail("More than four products must be rejected");}catch(IOException expected){assertTrue(expected.getMessage().contains("4 productos"));}assertEquals(4,doc.layers().length());doc.close();restored.close();
    }
    @Test public void backgroundFitModesResizeBackground()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,product,back);
        assertEquals("cover",doc.state.getString("bgFit"));
        doc.state.put("format","square");
        RectF cover=doc.backgroundRect();
        doc.state.put("bgFit","contain");RectF contain=doc.backgroundRect();
        doc.state.put("bgFit","stretch");RectF stretch=doc.backgroundRect();
        assertEquals(960f,cover.height(),.1);
        assertEquals(480f,contain.height(),.1);
        assertEquals(240f,contain.top,.1);
        assertEquals(960f,stretch.height(),.1);
        assertEquals(0f,stretch.left,.1);
        doc.close();
    }

    @Test public void tabletopPresetsCoverPortraitAndLandscapeInBothPerspectives() throws Exception {
        for (String kind : new String[]{"wood", "marble", "cloth"}) {
            for (int[] size : new int[][]{{216, 336}, {336, 216}}) {
                int width = size[0], height = size[1];
                Bitmap overhead = PhotoBackgrounds.render(context, width, height, kind, false);
                Bitmap angled = PhotoBackgrounds.render(context, width, height, kind, true);
                try {
                    assertFalse(overhead.sameAs(angled));
                    for (int x : new int[]{0, width - 1}) {
                        for (int y : new int[]{0, height - 1}) {
                            assertEquals(255, Color.alpha(overhead.getPixel(x, y)));
                            assertEquals(255, Color.alpha(angled.getPixel(x, y)));
                        }
                    }
                } finally {
                    overhead.recycle();
                    angled.recycle();
                }
            }
        }
    }

    @Test public void contactShadowFollowsTheProductAndPersistsWithTheComposition() throws Exception {
        CompositionDocument doc = CompositionDocument.create(context, product, back);
        String id = doc.layers().getJSONObject(0).getString("id");
        RectF bounds = doc.selectionRect(doc.layer(id));
        int x = Math.round(bounds.centerX()), y = Math.round(bounds.bottom + 2);
        Bitmap shaded = doc.export();
        try {
            assertTrue(Color.blue(shaded.getPixel(x, y)) < Color.blue(Color.BLUE));
            assertEquals(Color.RED, shaded.getPixel(100, 50));
        } finally {
            shaded.recycle();
        }
        doc.state.put("contactShadow", false);
        doc.save();
        CompositionDocument reopened = CompositionDocument.open(context, doc.id());
        Bitmap unshaded = reopened.export();
        try {
            assertEquals(Color.BLUE, unshaded.getPixel(x, y));
            assertFalse(reopened.contactShadow());
        } finally {
            unshaded.recycle();
            doc.close();
            reopened.close();
        }
    }

    @Test public void actualDragMovesOnlySelectedProductAndPersistsOnRelease()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,product,back);String id=doc.layers().getJSONObject(0).getString("id");final int[] saved={0};PhotoCompositionView view=new PhotoCompositionView(context,doc,id,new PhotoCompositionView.Listener(){public void selected(){}public void failed(){fail("Composition cannot fail while drawing");}public void changed(String before){try{doc.save();saved[0]++;}catch(Exception error){throw new RuntimeException(error);}}});FrameLayout host=new FrameLayout(context);host.addView(view,new FrameLayout.LayoutParams(-1,-1));host.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));host.layout(0,0,720,800);
        long now=android.os.SystemClock.uptimeMillis();for(int i=0;i<3;i++){MotionEvent event=MotionEvent.obtain(now,now+i*30,i==0?MotionEvent.ACTION_DOWN:i==1?MotionEvent.ACTION_MOVE:MotionEvent.ACTION_UP,i==0?360:460,400,0);assertTrue(view.onTouchEvent(event));event.recycle();}assertEquals(1,saved[0]);assertTrue(doc.layer(id).getDouble("x")>.6);assertEquals(0,doc.state.getDouble("bgX"),0);assertEquals(doc.layer(id).getDouble("x"),CompositionDocument.open(context,doc.id()).layer(id).getDouble("x"),0);
    }
    @Test public void tappingCanvasSelectsTopProductFromBackgroundAndEmptyAreaReturnsToBackground()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,product,back);String id=doc.layers().getJSONObject(0).getString("id");final int[] selected={0};PhotoCompositionView view=new PhotoCompositionView(context,doc,"background",new PhotoCompositionView.Listener(){public void selected(){selected[0]++;}public void failed(){fail("Composition cannot fail while selecting");}public void changed(String before){}});FrameLayout host=new FrameLayout(context);host.addView(view,new FrameLayout.LayoutParams(-1,-1));host.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));host.layout(0,0,720,800);
        long now=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,360,400,0),up=MotionEvent.obtain(now,now+20,MotionEvent.ACTION_UP,360,400,0);assertTrue(view.onTouchEvent(down));assertTrue(view.onTouchEvent(up));down.recycle();up.recycle();assertEquals(id,view.selected);assertEquals(1,selected[0]);
        down=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_DOWN,view.frame.left+5,view.frame.top+5,0);up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,view.frame.left+5,view.frame.top+5,0);assertTrue(view.onTouchEvent(down));assertTrue(view.onTouchEvent(up));down.recycle();up.recycle();assertEquals("background",view.selected);assertEquals(2,selected[0]);
    }
    @Test public void actualGalleryImportAddsSeparateLayerAndSaveCanBeReopened()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,product,back);Intent intent=new Intent(context,PhotoComposeActivity.class).putExtra("compositionId",doc.id());
        try(ActivityController<PhotoComposeActivity> controller=Robolectric.buildActivity(PhotoComposeActivity.class,intent).setup().visible()){
            PhotoComposeActivity activity=controller.get();View root=(View)field(activity,"root");assertTrue(root.findViewWithTag("compose:add").performClick());assertEquals(Intent.ACTION_OPEN_DOCUMENT,Shadows.shadowOf(activity).getNextStartedActivityForResult().intent.getAction());ByteArrayOutputStream bytes=new ByteArrayOutputStream();product.compress(Bitmap.CompressFormat.PNG,100,bytes);Uri uri=Uri.parse("content://composition-test/second.png");Shadows.shadowOf(context.getContentResolver()).registerInputStreamSupplier(uri,()->new ByteArrayInputStream(bytes.toByteArray()));call(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},72,Activity.RESULT_OK,new Intent().setData(uri));waitIdle(activity);CompositionDocument current=(CompositionDocument)field(activity,"document");assertEquals(2,current.layers().length());shot((View)field(activity,"root"));assertEquals(2,CompositionDocument.open(context,doc.id()).layers().length());call(activity,"changeFormat",new Class<?>[]{String.class},"square");assertTrue(root.findViewWithTag("compose:save").performClick());long until=System.currentTimeMillis()+10000;while(!activity.isFinishing()&&System.currentTimeMillis()<until){Thread.sleep(20);ShadowLooper.idleMainLooper();}assertTrue(activity.isFinishing());Bitmap image=ImageTools.decode(SafeJson.read(new FileInputStream(PhotoEditorActivity.output(activity))));assertEquals(960,image.getWidth());assertEquals(960,image.getHeight());image.recycle();assertEquals(doc.id(),Shadows.shadowOf(activity).getResultIntent().getStringExtra("compositionId"));
        }
    }
    @Test public void compositionBindingDoesNotReopenStaleImage()throws Exception{String image=ImageTools.encodeEdited(product);CompositionDocument doc=CompositionDocument.create(context,product,back);PhotoOriginals.linkComposition(context,"product-test",image,doc.id());assertEquals(doc.id(),PhotoOriginals.composition(context,"product-test",image));assertEquals("",PhotoOriginals.composition(context,"product-test",image+"changed"));}
    @Test public void croppingOneLayerKeepsItsPositionAndProvidesAReferenceForTheBrush() throws Exception {
        CompositionDocument scene = CompositionDocument.create(context, product);
        String id = scene.layers().getJSONObject(0).getString("id");
        scene.pan(id, .2f, -.1f);
        scene.zoom(id, 1.4f);
        scene.save();
        Intent sceneIntent = new Intent(context, PhotoComposeActivity.class).putExtra("compositionId", scene.id());
        try (ActivityController<PhotoComposeActivity> parent =
                     Robolectric.buildActivity(PhotoComposeActivity.class, sceneIntent).setup().visible()) {
            PhotoComposeActivity composition = parent.get();
            View root = (View) field(composition, "root");
            assertTrue(root.findViewWithTag("compose:background").isEnabled());
            assertTrue(root.findViewWithTag("compose:add").isEnabled());
            assertTrue(root.findViewWithTag("layer:background").performClick());
            assertFalse(root.findViewWithTag("compose:edit").isShown());
            assertFalse(root.findViewWithTag("compose:mask").isShown());
            assertTrue(root.findViewWithTag("compose:format").performClick());
            AlertDialog format = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertEquals(0, format.getListView().getCheckedItemPosition());
            assertTrue(format.getListView().performItemClick(null, 3, 3));
            assertEquals("story", ((CompositionDocument) field(composition, "document")).state.getString("format"));
            assertTrue(root.findViewWithTag("layer:" + id).performClick());
            assertTrue(root.findViewWithTag("compose:edit").isShown());
            assertTrue(root.findViewWithTag("compose:edit").performClick());
            Intent editIntent = Shadows.shadowOf(composition).getNextStartedActivityForResult().intent;
            assertEquals(PhotoEditorActivity.class.getName(), editIntent.getComponent().getClassName());
            Intent result;
            try (ActivityController<PhotoEditorActivity> child =
                         Robolectric.buildActivity(PhotoEditorActivity.class, editIntent).setup().visible()) {
                PhotoEditorActivity editor = child.get();
                waitIdle(editor);
                ((RectF) field(editor, "crop")).set(.1f, .1f, .9f, .9f);
                call(editor, "requestRender", new Class<?>[0]);
                waitIdle(editor);
                assertTrue(((View) field(editor, "root")).findViewWithTag("editor:save").performClick());
                long deadline = System.currentTimeMillis() + 10000;
                while (!editor.isFinishing() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20);
                    ShadowLooper.idleMainLooper();
                }
                assertTrue(editor.isFinishing());
                result = Shadows.shadowOf(editor).getResultIntent();
                assertNotNull(result);
            }
            call(composition, "onActivityResult", new Class<?>[]{int.class, int.class, Intent.class},
                    74, Activity.RESULT_OK, result);
            waitIdle(composition);
            CompositionDocument updated = CompositionDocument.open(context, scene.id());
            JSONObject edited = updated.layer(id);
            JSONObject previous = scene.layer(id);
            for (String key : new String[]{"x", "y", "scale"}) {
                assertEquals(previous.getDouble(key), edited.getDouble(key), 0);
            }
            assertEquals(80, updated.bitmap(edited.getString("file")).getWidth());
            assertEquals(80, updated.bitmap(edited.getString("source")).getWidth());
            updated.close();
        } finally {
            scene.close();
        }
    }
    @Test public void studioReopensEditableCompositionInsteadOfFlattenedEditor()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,product,back);String source=ImageTools.encodeEdited(product);try(OutputStream out=new FileOutputStream(PhotoStudioActivity.workspace(context))){out.write(new JSONObject().put("source",source).put("image",source).toString().getBytes("UTF-8"));}Bitmap image=doc.export();String encoded=ImageTools.encodeEdited(image);image.recycle();try(OutputStream out=new FileOutputStream(PhotoEditorActivity.output(context))){out.write(encoded.getBytes("UTF-8"));}
        try(ActivityController<PhotoStudioActivity> controller=Robolectric.buildActivity(PhotoStudioActivity.class).setup().visible()){PhotoStudioActivity activity=controller.get();call(activity,"onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},84,Activity.RESULT_OK,new Intent().putExtra("compositionId",doc.id()));waitIdle(activity);assertEquals(doc.id(),PhotoStudioActivity.read(context).getString("compositionId"));}
        try(ActivityController<PhotoStudioActivity> controller=Robolectric.buildActivity(PhotoStudioActivity.class).setup().visible()){PhotoStudioActivity activity=controller.get();call(activity,"edit",new Class<?>[0]);Intent opened=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;assertEquals(PhotoComposeActivity.class.getName(),opened.getComponent().getClassName());assertEquals(doc.id(),opened.getStringExtra("compositionId"));call(activity,"restoreOriginal",new Class<?>[0]);assertFalse(PhotoStudioActivity.read(context).has("compositionId"));assertEquals(source,PhotoStudioActivity.read(context).getString("image"));}
    }
    @Test public void savingProductKeepsLocalCompositionLinkOutOfSharedCatalog()throws Exception{
        CompositionDocument doc=CompositionDocument.create(context,product,back);String encoded=ImageTools.encodeEdited(product);try(OutputStream out=new FileOutputStream(PhotoStudioActivity.workspace(context))){out.write(new JSONObject().put("source",encoded).put("image",encoded).put("compositionId",doc.id()).toString().getBytes("UTF-8"));}
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){MainActivity activity=controller.get();AppShell shell=(AppShell)field(activity,"shell");LocalStore store=shell.store();JSONObject existing=store.snapshot().getJSONArray("products").getJSONObject(0);ProductEditorScreen.attachStudioPhoto(shell,existing,true);shell.render();ViewGroup footer=(ViewGroup)field(shell,"footer");footer.getChildAt(0).performClick();org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();JSONArray pending=store.snapshot().getJSONArray("pending");JSONObject shared=pending.getJSONObject(pending.length()-1).getJSONObject("data");assertFalse(shared.has("_sourcePhoto"));assertFalse(shared.has("_compositionId"));assertFalse(shared.has("_compositionHash"));assertEquals(doc.id(),PhotoOriginals.composition(context,existing.getString("id"),encoded));}
    }
}
