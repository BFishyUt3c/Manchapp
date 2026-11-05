package pe.manchapp.beta;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Photos stay in private files; crop coordinates are the only image state in the bundle. */
public final class PhotoEditorActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private NativeUi ui;
    private FoodPhotoEngine engine;
    private Bitmap original,result,comparison;
    private ImageView preview;
    private TextView progress,save,undoCrop;
    private boolean busy=true,destroyed=false,improve=true,showOriginal=false,includeObjects=false;
    private String background="original";
    private final RectF crop=new RectF(0,0,1,1);
    private LinearLayout options;
    private Dialog cropDialog;
    static File input(android.content.Context context){return new File(context.getCacheDir(),"photo-editor-source.jpg");}
    static File output(android.content.Context context){return new File(context.getCacheDir(),"photo-editor-result.txt");}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);ui=new NativeUi(this);includeObjects=getIntent().getBooleanExtra("includeObjects",false);
        if(state!=null){
            includeObjects=state.getBoolean("includeObjects",includeObjects);improve=state.getBoolean("improve",true);
            background=state.getString("background","original");showOriginal=state.getBoolean("showOriginal",false);
            float[] bounds=state.getFloatArray("crop");if(bounds!=null&&bounds.length==4)crop.set(bounds[0],bounds[1],bounds[2],bounds[3]);
        }
        getWindow().setStatusBarColor(NativeUi.DEEP);getWindow().setNavigationBarColor(NativeUi.WINE);draw();
        worker.execute(()->{
            try{
                original=BitmapFactory.decodeFile(input(this).getAbsolutePath());
                if(original==null)throw new IOException("Vuelve a elegir la foto.");
                engine=new FoodPhotoEngine(this,original);renderPhoto();
            }catch(Exception|OutOfMemoryError error){runOnUiThread(()->{if(!destroyed){Toast.makeText(this,"No pudimos abrir el editor. La foto del producto se conserva.",Toast.LENGTH_LONG).show();finish();}});}
        });
    }
    private void add(LinearLayout parent,View child,int gap){ui.add(parent,child,gap);}
    private void draw(){
        LinearLayout root=ui.column();root.setBackgroundColor(NativeUi.BG);setContentView(root);
        root.addView(ui.toolbar("Mejora tu foto","",()->finish(),null));
        ScrollView scroll=new ScrollView(this);LinearLayout body=ui.column();body.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(20));
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackground(new TransparencyGrid(ui.dp(12)));
        preview.setContentDescription("Vista previa de la foto del producto");add(body,preview,0);preview.getLayoutParams().height=ui.dp(195);
        progress=ui.text("Preparando la mejora…",14,NativeUi.WINE,true);progress.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,progress,12);
        options=ui.column();add(body,options,0);
        LinearLayout compare=new LinearLayout(this);
        for(String name:new String[]{"Original","Resultado"}){
            boolean before=name.equals("Original");TextView button=ui.button(name,false,null,()->{showOriginal=before;display();});button.setTag(before?"compare:original":"compare:result");addWeighted(compare,button);
        }
        add(options,compare,12);
        add(options,ui.button("Recortar foto",false,null,()->openCrop()),10);
        undoCrop=ui.button("Deshacer recorte",false,null,()->{crop.set(0,0,1,1);requestRender();});add(options,undoCrop,6);undoCrop.setVisibility(View.GONE);
        add(options,ui.heading("Elige el fondo",22),18);
        String[] ids={"original","transparent","blur","wood","marble","cloth"};
        String[] labels={"Original","Sin fondo","Desenfocado","Madera","Mármol","Mantel"};
        for(int row=0;row<2;row++){
            LinearLayout line=new LinearLayout(this);
            for(int i=row*3;i<row*3+3;i++){
                String id=ids[i];TextView choice=ui.button(labels[i],false,null,()->{background=id;requestRender();});
                choice.setTextSize(13);choice.setPadding(ui.dp(4),ui.dp(12),ui.dp(4),ui.dp(12));choice.setTag(id);addWeighted(line,choice);
            }
            add(options,line,8);
        }
        Switch auto=new Switch(this);auto.setText("Mejorar color y detalle");auto.setTypeface(ui.bold);auto.setTextSize(17);auto.setTextColor(NativeUi.INK);auto.setMinHeight(ui.dp(52));
        auto.setChecked(improve);auto.setOnCheckedChangeListener((view,checked)->{improve=checked;requestRender();});add(options,auto,16);
        Switch complete=new Switch(this);complete.setText("Incluir plato y acompañamientos");complete.setTypeface(ui.regular);complete.setTextSize(15);complete.setTextColor(NativeUi.INK);complete.setMinHeight(ui.dp(50));
        complete.setChecked(includeObjects);complete.setOnCheckedChangeListener((view,checked)->{includeObjects=checked;requestRender();});add(options,complete,6);
        add(body,ui.text("Revisa que el producto y sus acompañamientos estén completos. Los cuadros grises indican un fondo transparente; no se guardan en la foto.",14,NativeUi.MUTED,false),16);
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(10));save=ui.button("Usar esta foto",true,null,()->save());footer.addView(save);root.addView(footer);enabled(false);
    }
    private void addWeighted(LinearLayout row,View child){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(0,0,ui.dp(6),0);row.addView(child,params);}

    private void openCrop(){
        if(busy||destroyed||original==null||cropDialog!=null)return;
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);cropDialog=dialog;
        LinearLayout root=ui.column();root.setBackgroundColor(NativeUi.BG);dialog.setContentView(root);
        root.addView(ui.toolbar("Recortar foto","",()->dialog.dismiss(),null));
        TextView hint=ui.text("Arrastra las esquinas para encuadrar el producto. Mueve el centro para ajustar la posición.",15,NativeUi.INK,false);hint.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));root.addView(hint);
        PhotoCropView frame=new PhotoCropView(this,original,crop);frame.setTag("crop:frame");root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));
        LinearLayout tools=new LinearLayout(this);
        TextView zoomIn=ui.button("Acercar",false,null,()->frame.zoom(true));zoomIn.setTextSize(14);addWeighted(tools,zoomIn);
        TextView zoomOut=ui.button("Alejar",false,null,()->frame.zoom(false));zoomOut.setTextSize(14);addWeighted(tools,zoomOut);
        TextView reset=ui.button("Foto completa",false,null,()->frame.reset());reset.setTextSize(14);reset.setPadding(ui.dp(4),ui.dp(12),ui.dp(4),ui.dp(12));addWeighted(tools,reset);add(footer,tools,0);
        TextView apply=ui.button("Aplicar recorte",true,null,()->{crop.set(frame.crop());dialog.dismiss();requestRender();});apply.setTag("crop:apply");add(footer,apply,10);
        TextView cancel=ui.button("Cancelar",false,null,()->dialog.dismiss());cancel.setTag("crop:cancel");add(footer,cancel,6);root.addView(footer);
        dialog.setOnDismissListener(value->{if(cropDialog==dialog)cropDialog=null;});dialog.show();
        Window window=dialog.getWindow();if(window!=null){window.setLayout(-1,-1);window.setStatusBarColor(NativeUi.DEEP);window.setNavigationBarColor(NativeUi.WINE);}
    }
    private void requestRender(){
        if(busy||destroyed)return;busy=true;showOriginal=false;enabled(false);
        progress.setText(background.equals("original")?"Ajustando la foto…":"Separando el producto y preparando el fondo…");worker.execute(()->renderPhoto());
    }
    private void renderPhoto(){
        final boolean selectedImprove=improve;final String selectedBackground=background;final RectF selectedCrop=new RectF(crop);
        Bitmap working=null,before=null;
        try{
            engine.includeObjects(includeObjects);working=engine.render(selectedImprove,selectedBackground);
            Bitmap next=PhotoCropView.apply(working,selectedCrop);if(next!=working)working.recycle();working=null;
            try{before=PhotoCropView.apply(original,selectedCrop);}catch(Exception|OutOfMemoryError error){next.recycle();throw error;}
            final Bitmap originalView=before;before=null;
            runOnUiThread(()->{
                if(destroyed){next.recycle();if(originalView!=original)originalView.recycle();return;}
                Bitmap old=result,oldComparison=comparison;result=next;comparison=originalView;busy=false;display();
                if(old!=null&&old!=original)old.recycle();if(oldComparison!=null&&oldComparison!=original)oldComparison.recycle();enabled(true);
                progress.setText(selectedBackground.equals("transparent")?"Producto sin fondo · revisa los bordes":selectedBackground.equals("original")?"Foto ajustada · compara con la original":"Fondo actualizado · revisa los bordes");
            });
        }catch(Exception|OutOfMemoryError error){
            if(working!=null)working.recycle();if(before!=null&&before!=original)before.recycle();
            runOnUiThread(()->{
                if(destroyed)return;busy=false;background="original";enabled(true);
                progress.setText(error instanceof IOException?error.getMessage():"No pudimos preparar ese fondo. Puedes conservar la foto original.");
                Bitmap old=result,oldComparison=comparison;result=null;comparison=original;crop.set(0,0,1,1);showOriginal=true;display();
                if(old!=null&&old!=original)old.recycle();if(oldComparison!=null&&oldComparison!=original)oldComparison.recycle();
                Toast.makeText(this,"Puedes elegir el fondo original y continuar.",Toast.LENGTH_LONG).show();
            });
        }
    }
    private void display(){
        preview.setImageBitmap(showOriginal||result==null?(comparison==null?original:comparison):result);
        save.setText(showOriginal?(PhotoCropView.isFull(crop)?"Usar foto original":"Usar original recortada"):"Usar esta foto");
        undoCrop.setVisibility(PhotoCropView.isFull(crop)?View.GONE:View.VISIBLE);mark(options);
    }
    private void mark(ViewGroup group){
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);if(child instanceof ViewGroup)mark((ViewGroup)child);
            if(child.getTag() instanceof String){String tag=(String)child.getTag();boolean selected=tag.startsWith("compare:")?tag.equals(showOriginal?"compare:original":"compare:result"):background.equals(tag);child.setSelected(selected);child.setBackground(ui.background(selected?NativeUi.GOLD:NativeUi.CARD,selected?NativeUi.WINE:NativeUi.LINE,14));}
        }
    }
    private void enabled(boolean value){save.setEnabled(value);save.setAlpha(value?1:.55f);enableChildren(options,value);}
    private void enableChildren(ViewGroup group,boolean value){for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);child.setEnabled(value);if(child instanceof ViewGroup)enableChildren((ViewGroup)child,value);}}
    private void save(){
        if(busy)return;final Bitmap chosen=showOriginal||result==null?(comparison==null?original:comparison):result;if(chosen==null)return;
        busy=true;enabled(false);progress.setText("Guardando foto…");
        worker.execute(()->{
            android.util.AtomicFile file=new android.util.AtomicFile(output(this));FileOutputStream out=null;
            try{
                String encoded=ImageTools.encodeEdited(chosen);out=file.startWrite();out.write(encoded.getBytes("UTF-8"));file.finishWrite(out);
                runOnUiThread(()->{if(!destroyed){setResult(RESULT_OK);finish();}});
            }catch(Exception|OutOfMemoryError error){if(out!=null)file.failWrite(out);runOnUiThread(()->{if(!destroyed){busy=false;enabled(true);progress.setText("No pudimos guardar. Prueba el fondo original o una foto más pequeña.");}});}
        });
    }
    @Override public void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putBoolean("includeObjects",includeObjects);state.putBoolean("improve",improve);state.putBoolean("showOriginal",showOriginal);state.putString("background",background);state.putFloatArray("crop",new float[]{crop.left,crop.top,crop.right,crop.bottom});
    }
    @Override public void onDestroy(){
        destroyed=true;if(cropDialog!=null)cropDialog.dismiss();preview.setImageDrawable(null);
        worker.execute(()->{if(engine!=null)engine.close();if(comparison!=null&&comparison!=original&&!comparison.isRecycled())comparison.recycle();if(original!=null&&!original.isRecycled())original.recycle();if(result!=null&&result!=original&&!result.isRecycled())result.recycle();});
        worker.shutdown();super.onDestroy();
    }
    private static final class TransparencyGrid extends Drawable {
        private final int cell;private final Paint paint=new Paint();
        TransparencyGrid(int cell){this.cell=Math.max(1,cell);}
        @Override public void draw(Canvas canvas){Rect bounds=getBounds();for(int y=bounds.top;y<bounds.bottom;y+=cell)for(int x=bounds.left;x<bounds.right;x+=cell){paint.setColor(((x-bounds.left)/cell+(y-bounds.top)/cell)%2==0?0xffe8e4de:0xffc9c5bf);canvas.drawRect(x,y,Math.min(x+cell,bounds.right),Math.min(y+cell,bounds.bottom),paint);}}
        @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}