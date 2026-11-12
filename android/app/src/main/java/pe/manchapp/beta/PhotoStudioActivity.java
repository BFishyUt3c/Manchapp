package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

public final class PhotoStudioActivity extends Activity {
    private PhotoUi ui;
    private JSONObject photo=new JSONObject();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Bitmap preview;
    private boolean busy=false;
    static File workspace(Context context){return new File(context.getFilesDir(),"photo-studio.json");}
    static JSONObject read(Context context)throws Exception{return new JSONObject(SafeJson.read(new CrossFile(workspace(context)).openRead()));}
    @Override public void onCreate(Bundle state){super.onCreate(state);ui=new PhotoUi(this);getWindow().getDecorView().setSystemUiVisibility(0);getWindow().setStatusBarColor(PhotoUi.BG);getWindow().setNavigationBarColor(PhotoUi.PANEL);
        try{AtomicFile file=new CrossFile(workspace(this));if(file.getBaseFile().exists()||new File(file.getBaseFile()+".bak").exists())photo=read(this);}catch(Exception error){Toast.makeText(this,"No pudimos abrir la última foto. El archivo se conserva.",Toast.LENGTH_LONG).show();}
        draw();
    }
    private void persist(JSONObject value)throws Exception{AtomicFile file=new CrossFile(workspace(this));FileOutputStream out=null;try{out=file.startWrite();out.write(value.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);throw error;}}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void draw(){
        if(isFinishing()||isDestroyed())return;Bitmap old=preview;preview=null;boolean ready=!photo.optString("image").isEmpty();
        LinearLayout root=ui.column();root.setTag("studio:root");root.setBackgroundColor(PhotoUi.BG);setContentView(root);TextView save=ui.button("Guardar",true,()->exportPhoto());save.setEnabled(ready&&!busy);save.setAlpha(ready&&!busy?1:.35f);root.addView(ui.header("Fotos",ui.icon("back","Volver",()->finish()),save));
        FrameLayout stage=new FrameLayout(this);stage.setTag("studio:stage");root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        if(ready){
            preview=ImageTools.decode(photo.optString("image"));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setPadding(ui.dp(18),ui.dp(12),ui.dp(18),ui.dp(16));if(preview!=null&&ImageTools.hasTransparency(preview))image.setBackground(new PhotoUi.Checker(ui.dp(10)));image.setImageBitmap(preview);image.setContentDescription("Tu foto editada, sin afiche");stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
            if(!photo.optString("source").equals(photo.optString("image"))){TextView original=ui.button("Restablecer original",false,()->restoreOriginal());original.setBackground(ui.background(0xee24252c,PhotoUi.LINE,20));FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-2,ui.dp(42),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);params.bottomMargin=ui.dp(12);stage.addView(original,params);}
        }else{
            LinearLayout empty=ui.column();empty.setGravity(Gravity.CENTER);empty.setPadding(ui.dp(32),ui.dp(20),ui.dp(32),ui.dp(20));TextView symbol=ui.text("",24,PhotoUi.LIGHT,false);symbol.setCompoundDrawablesWithIntrinsicBounds(null,new NativeUi.Icon("photo",PhotoUi.LIGHT,ui.dp(64)),null,null);symbol.setGravity(Gravity.CENTER);symbol.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24));symbol.setBackground(ui.background(PhotoUi.PANEL,PhotoUi.LINE,28));empty.addView(symbol,new LinearLayout.LayoutParams(ui.dp(116),ui.dp(116)));TextView heading=ui.text("Empieza con una foto",25,PhotoUi.TEXT,true);heading.setGravity(Gravity.CENTER);ui.add(empty,heading,26);TextView hint=ui.text("Recorta. Ajusta. Cambia el fondo.",14,PhotoUi.MUTED,false);hint.setGravity(Gravity.CENTER);ui.add(empty,hint,12);stage.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        }
        if(old!=null&&!old.isRecycled())old.recycle();
        LinearLayout panel=ui.column();panel.setPadding(ui.dp(18),ui.dp(16),ui.dp(18),ui.dp(18));panel.setBackground(ui.background(PhotoUi.PANEL,0,22));root.addView(panel);
        if(ready){LinearLayout actions=new LinearLayout(this);ui.weighted(actions,ui.button(photo.optString("compositionId").isEmpty()?"Editar foto":"Editar composición",true,()->edit()),false);ui.weighted(actions,ui.button("Usar en producto",false,()->{setResult(RESULT_OK);finish();}),true);ui.add(panel,actions,0);ui.add(panel,ui.button("Quitar esta foto",false,()->new AlertDialog.Builder(this).setTitle("¿Quitar la foto?").setMessage("Se limpiará este espacio de edición. Los productos que ya guardaste conservarán sus imágenes.").setNegativeButton("Conservar",null).setPositiveButton("Quitar",(d,w)->removePhoto()).show()),10);}
        LinearLayout choices=new LinearLayout(this);TextView gallery=ui.button("Elegir foto",!ready,()->pick(false)),camera=ui.button("Tomar foto",false,()->pick(true));gallery.setCompoundDrawablesWithIntrinsicBounds(new NativeUi.Icon("photo",PhotoUi.TEXT,ui.dp(19)),null,null,null);camera.setCompoundDrawablesWithIntrinsicBounds(new NativeUi.Icon("camera",PhotoUi.LIGHT,ui.dp(19)),null,null,null);gallery.setCompoundDrawablePadding(ui.dp(7));camera.setCompoundDrawablePadding(ui.dp(7));ui.weighted(choices,gallery,false);ui.weighted(choices,camera,true);ui.add(panel,choices,ready?10:0);
        if(busy){TextView status=ui.text("Preparando foto…",12,PhotoUi.MUTED,false);status.setGravity(Gravity.CENTER);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);ui.add(panel,status,12);enable(panel,false);}
    }
    private void restoreOriginal(){if(busy)return;try{JSONObject restored=new JSONObject(photo.toString()).put("image",photo.getString("source"));restored.remove("compositionId");persist(restored);photo=restored;draw();}catch(Exception error){toast("No pudimos recuperar la original. Tu foto actual sigue guardada.");}}
    private void removePhoto(){if(busy)return;try{persist(new JSONObject());photo=new JSONObject();draw();toast("Foto quitada del espacio de edición.");}catch(Exception error){toast("No pudimos quitarla. La foto sigue guardada.");}}
    private void enable(ViewGroup group,boolean value){for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);child.setEnabled(value);if(child instanceof ViewGroup)enable((ViewGroup)child,value);}}
    private void pick(boolean camera){if(busy)return;try{
        if(camera){new File(getCacheDir(),"shared").mkdirs();Uri uri=Uri.parse("content://pe.manchapp.beta.files/studio-camera.jpg");Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT,uri);intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("Foto",uri));startActivityForResult(intent,41);}
        else{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,42);}
    }catch(Exception error){toast("No pudimos abrir la cámara o la galería.");}}
    private void edit(){if(busy||photo.optString("image").isEmpty())return;if(!photo.optString("compositionId").isEmpty()){startActivityForResult(new Intent(this,PhotoComposeActivity.class).putExtra("compositionId",photo.optString("compositionId")),84);return;}busy=true;draw();final String encoded=photo.optString("image");worker.execute(()->{Bitmap bitmap=null;try{
        bitmap=ImageTools.decode(encoded);if(bitmap==null)throw new IOException();try(OutputStream out=new FileOutputStream(PhotoEditorActivity.input(this))){if(!bitmap.compress(ImageTools.hasTransparency(bitmap)?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,95,out))throw new IOException();}
        runOnUiThread(()->{busy=false;draw();if(!isFinishing()&&!isDestroyed())startActivityForResult(new Intent(this,PhotoEditorActivity.class).putExtra("includeObjects",true),84);});
    }catch(Exception|OutOfMemoryError error){runOnUiThread(()->{busy=false;draw();toast("No pudimos abrir esa foto. La imagen anterior sigue guardada.");});}finally{if(bitmap!=null)bitmap.recycle();}});}
    private void exportPhoto(){if(busy)return;try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png").putExtra(Intent.EXTRA_TITLE,"ManchApp-foto.png"),86);}catch(Exception error){toast("No pudimos abrir dónde guardar la foto.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;
        if(request==86){if(data==null||data.getData()==null)return;final Uri uri=data.getData();final String encoded=photo.optString("image");busy=true;draw();worker.execute(()->{Bitmap bitmap=null;try{bitmap=ImageTools.decode(encoded);if(bitmap==null)throw new IOException();try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();}runOnUiThread(()->toast("Foto guardada en el lugar que elegiste."));}catch(Exception|OutOfMemoryError error){runOnUiThread(()->toast("No pudimos exportarla. Tu foto sigue aquí para volver a intentar."));}finally{if(bitmap!=null)bitmap.recycle();runOnUiThread(()->{busy=false;draw();});}});return;}
        if(request!=41&&request!=42&&request!=84)return;final Uri uri=request==41?Uri.parse("content://pe.manchapp.beta.files/studio-camera.jpg"):data==null?null:data.getData();if(request!=84&&uri==null)return;final JSONObject previous=LocalStore.copy(photo);final String compositionId=data==null?"":data.getStringExtra("compositionId");busy=true;draw();worker.execute(()->{Bitmap bitmap=null;try{
            String encoded;if(request==84)encoded=SafeJson.read(new FileInputStream(PhotoEditorActivity.output(this)));else{bitmap=ImageTools.read(this,uri);encoded=ImageTools.encodeEdited(bitmap);}
            JSONObject next=request==84?previous.put("image",encoded):new JSONObject().put("source",encoded).put("image",encoded);if(request==84&&compositionId!=null&&!compositionId.isEmpty())next.put("compositionId",compositionId);else next.remove("compositionId");persist(next);
            runOnUiThread(()->{photo=next;busy=false;draw();if(request!=84&&!isFinishing()&&!isDestroyed())edit();});
        }catch(Exception|OutOfMemoryError error){runOnUiThread(()->{busy=false;draw();toast("No pudimos preparar esa foto. Prueba una imagen más pequeña.");});}finally{if(bitmap!=null)bitmap.recycle();}});
    }
    @Override public void onDestroy(){if(preview!=null&&!preview.isRecycled())preview.recycle();worker.shutdown();super.onDestroy();}
}
