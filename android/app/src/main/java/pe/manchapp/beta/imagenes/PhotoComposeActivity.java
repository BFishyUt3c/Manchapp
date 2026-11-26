package pe.manchapp.beta;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class PhotoComposeActivity extends Activity {
    private static final String[] FORMATS = {"background", "square", "portrait", "story"};
    private static final String[] FORMAT_LABELS = {
            "Tamaño del fondo", "Cuadrado 1:1", "Vertical 4:5", "Historia 9:16"
    };

    private PhotoUi ui;
    private CompositionDocument document;
    private PhotoCompositionView canvas;
    private LinearLayout root,panel,layerRow,productActions;
    private TextView save,status,formatButton;
    private final ArrayList<String> history=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean busy=false,destroyed=false;
    @Override public void onCreate(Bundle state){super.onCreate(state);ui=new PhotoUi(this);getWindow().setStatusBarColor(PhotoUi.BG);getWindow().setNavigationBarColor(PhotoUi.PANEL);try{document=CompositionDocument.open(this,getIntent().getStringExtra("compositionId"));String selected=state==null?(document.layers().length()==0?"background":document.layers().getJSONObject(0).getString("id")):state.getString("selected","background");if(state!=null&&state.getStringArrayList("history")!=null)history.addAll(state.getStringArrayList("history"));draw(selected);}catch(Exception error){Toast.makeText(this,"No pudimos abrir la composición. Tus fotos anteriores se conservan.",Toast.LENGTH_LONG).show();finish();}}
    private void draw(String selected){
        root=ui.column();root.setBackgroundColor(PhotoUi.BG);setContentView(root);ui.insets(root);save=ui.button("Listo",true,()->save());save.setTag("compose:save");root.addView(ui.header("Combinar fotos",ui.icon("back","Volver al editor",()->finish()),save));
        canvas=new PhotoCompositionView(this,document,selected,new PhotoCompositionView.Listener(){public void selected(){layers();showSelectionHint();}public void changed(String before){commit(before);}public void failed(){status.setText("No pudimos mostrar una imagen. Las fotos se conservan.");}});canvas.setTag("compose:canvas");root.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));
        status=ui.text("",12,PhotoUi.MUTED,false);status.setGravity(Gravity.CENTER);status.setPadding(ui.dp(12),0,ui.dp(12),ui.dp(10));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        showSelectionHint();
        panel=ui.column();panel.setPadding(ui.dp(14),ui.dp(10),ui.dp(14),ui.dp(12));panel.setBackground(ui.background(PhotoUi.PANEL,0,20));root.addView(panel);
        LinearLayout add = new LinearLayout(this);
        TextView background = ui.button("Mi fondo", false, () -> pick(true));
        background.setTag("compose:background");
        TextView more = ui.button("+ Producto", true, () -> pick(false));
        more.setTag("compose:add");
        formatButton = ui.button("Formato", false, this::showFormatDialog);
        formatButton.setTag("compose:format");
        for (TextView action : new TextView[]{background, more, formatButton}) {
            action.setPadding(ui.dp(5), ui.dp(10), ui.dp(5), ui.dp(10));
            ui.weighted(add, action, action == formatButton);
        }
        ui.add(panel, add, 0);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);layerRow=new LinearLayout(this);scroll.addView(layerRow);ui.add(panel,scroll,8);
        LinearLayout controls = new LinearLayout(this);
        controls.setBaselineAligned(false);
        TextView smaller = ui.button("−", false, () -> zoom(.85f));
        TextView larger = ui.button("+", false, () -> zoom(1.18f));
        smaller.setTextSize(22);
        larger.setTextSize(22);
        smaller.setContentDescription("Reducir el elemento seleccionado");
        larger.setContentDescription("Ampliar el elemento seleccionado");
        ui.weighted(controls, smaller, false);
        ui.weighted(controls, larger, false);
        ui.weighted(controls, ui.button("Centrar", false, this::center), false);
        ui.weighted(controls, ui.button("Más", false, this::options), true);
        ui.add(panel, controls, 8);
        productActions = new LinearLayout(this);
        TextView edit = ui.button("Editar foto", false, this::editSelected);
        edit.setTag("compose:edit");
        TextView correct = ui.button("Pincel", false, this::correctSelected);
        correct.setTag("compose:mask");
        correct.setContentDescription("Corregir recorte con pincel");
        TextView remove = ui.button("Quitar", false, this::removeSelected);
        remove.setTag("compose:remove");
        remove.setContentDescription("Quitar producto");
        ui.weighted(productActions, edit, false);
        ui.weighted(productActions, correct, false);
        ui.weighted(productActions, remove, true);
        ui.add(panel, productActions, 8);
        layers();
        markFormat();
    }
    private void layers(){if(layerRow==null)return;layerRow.removeAllViews();addLayerChoice("Fondo","background");JSONArray layers=document.layers();for(int i=0;i<layers.length();i++)addLayerChoice("Producto "+(i+1),layers.optJSONObject(i).optString("id"));canvas.invalidate();updateActions();}
    private void updateActions() {
        productActions.setVisibility(document.layer(canvas.selected) == null ? View.GONE : View.VISIBLE);
    }
    private void addLayerChoice(String name,String id){TextView choice=ui.button(name,false,()->{if(busy)return;canvas.selected=id;showSelectionHint();layers();});choice.setMinHeight(ui.dp(40));choice.setTag("layer:"+id);choice.setSelected(canvas.selected.equals(id));choice.setBackground(ui.background(canvas.selected.equals(id)?PhotoUi.SURFACE:Color.TRANSPARENT,canvas.selected.equals(id)?PhotoUi.LIGHT:0,12));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.rightMargin=ui.dp(6);layerRow.addView(choice,params);}
    private void showSelectionHint() {
        status.setText(canvas.selected.equals("background") ? "Arrastra el fondo · usa dos dedos para acercarlo"
                : "Arrastra el producto · usa dos dedos para ajustar");
    }
    private int currentFormat() {
        return Math.max(0, Arrays.asList(FORMATS).indexOf(document.state.optString("format")));
    }
    private void markFormat() {
        formatButton.setContentDescription("Formato de imagen. " + FORMAT_LABELS[currentFormat()]);
    }
    private void commit(String before){try{document.save();if(!before.equals(document.state.toString())){history.add(before);if(history.size()>10)history.remove(0);}canvas.invalidate();markFormat();}catch(Exception error){try{document.state=new JSONObject(before);}catch(Exception ignored){}Toast.makeText(this,"No pudimos guardar el último cambio. La composición anterior se conserva.",Toast.LENGTH_LONG).show();canvas.invalidate();}}
    private void zoom(float factor){if(busy)return;String before=document.state.toString();try{document.zoom(canvas.selected,factor);commit(before);}catch(Exception error){status.setText("No pudimos cambiar el tamaño.");}}
    private void center(){if(busy)return;String before=document.state.toString();try{if(canvas.selected.equals("background"))document.state.put("bgX",0).put("bgY",0).put("bgScale",1);else{JSONObject layer=document.layer(canvas.selected);if(layer!=null)layer.put("x",.5).put("y",.5).put("scale",1);}commit(before);}catch(Exception error){status.setText("No pudimos centrar la imagen.");}}
    private void changeFormat(String format){if(busy)return;String before=document.state.toString();try{document.state.put("format",format);document.pan("background",0,0);commit(before);}catch(Exception error){status.setText("No pudimos cambiar el formato.");}}
    private void showFormatDialog() {
        if (busy) return;
        String[] choices = FORMAT_LABELS.clone();
        for (int i = 0; i < choices.length; i++) {
            choices[i] += " · " + document.width(FORMATS[i]) + " × " + document.height(FORMATS[i]);
        }
        new AlertDialog.Builder(this).setTitle("Formato de imagen")
                .setSingleChoiceItems(choices, currentFormat(), (dialog, index) -> {
                    changeFormat(FORMATS[index]);
                    dialog.dismiss();
                }).setNegativeButton("Volver", null).show();
    }
    private void showFitDialog(){new AlertDialog.Builder(this).setTitle("Escala del fondo").setItems(new String[]{"Cubrir","Ajustar","Estirar"},(d,i)->changeFit(new String[]{"cover","contain","stretch"}[i])).show();}
    private void changeFit(String fit){if(busy)return;String before=document.state.toString();try{document.state.put("bgFit",fit);document.pan("background",0,0);commit(before);status.setText("Escala del fondo actualizada.");}catch(Exception error){status.setText("No pudimos cambiar la escala del fondo.");}}
    private void options(){if(busy)return;boolean product=document.layer(canvas.selected)!=null;String[] choices=product?new String[]{"Traer al frente","Deshacer último cambio"}:new String[]{"Quitar fondo","Escala del fondo",
            document.contactShadow()?"Quitar sombra de apoyo":"Añadir sombra de apoyo","Deshacer último cambio"};new AlertDialog.Builder(this).setTitle(product?"Producto seleccionado":"Fondo").setItems(choices,(dialog,index)->{
        if(product){
            if(index==1){undo();return;}
            String before=document.state.toString();JSONArray kept=new JSONArray();JSONObject selected=document.layer(canvas.selected);for(int i=0;i<document.layers().length();i++)if(!document.layers().optJSONObject(i).optString("id").equals(canvas.selected))kept.put(document.layers().optJSONObject(i));kept.put(selected);try{document.state.put("layers",kept);commit(before);layers();}catch(Exception error){status.setText("No pudimos cambiar el orden.");}
            return;
        }
        if(index==3){undo();return;}
        if(index==2){
            String before=document.state.toString();
            try{
                document.state.put("contactShadow",!document.contactShadow());
                commit(before);
            }catch(JSONException error){status.setText("No pudimos cambiar la sombra.");}
            return;
        }
        if(index==1){showFitDialog();return;}
        removeSelected();
    }).setNegativeButton("Volver",null).show();}
    private void removeSelected(){if(busy)return;String before=document.state.toString();try{if(canvas.selected.equals("background")){Bitmap plain=Bitmap.createBitmap(960,960,Bitmap.Config.ARGB_8888);plain.eraseColor(0xfff3e5d3);document.replaceBackground(plain);plain.recycle();canvas.selected="background";commit(before);layers();status.setText("Fondo quitado · puedes elegir otro");return;}JSONArray kept=new JSONArray();for(int i=0;i<document.layers().length();i++)if(!document.layers().optJSONObject(i).optString("id").equals(canvas.selected))kept.put(document.layers().optJSONObject(i));document.state.put("layers",kept);canvas.selected=kept.length()==0?"background":kept.getJSONObject(kept.length()-1).getString("id");commit(before);layers();status.setText("Producto quitado · puedes deshacer desde Más");}catch(Exception error){status.setText("No pudimos quitar la selección. Tus imágenes se conservan.");}}
    private void correctSelected(){if(busy)return;JSONObject layer=document.layer(canvas.selected);if(layer==null)return;String id=canvas.selected;enabled(false);status.setText("Abriendo el recorte manual…");worker.execute(()->{try{MaskEditSession session=MaskEditSession.create(this,document.bitmap(layer.optString("source",layer.optString("file"))),document.bitmap(layer.optString("file")));runOnUiThread(()->{if(!destroyed){enabled(true);startActivityForResult(new Intent(this,MaskEditorActivity.class).putExtra("maskSessionId",session.id()).putExtra("layerId",id).putExtra("restoreLimited",!layer.has("source")),73);}});}catch(Exception|OutOfMemoryError error){runOnUiThread(()->{if(!destroyed){enabled(true);status.setText("No pudimos abrir el recorte. La imagen se conserva.");}});}});}
    private void editSelected() {
        if (busy) return;
        JSONObject layer = document.layer(canvas.selected);
        if (layer == null) return;
        try {
            String current = layer.getString("file");
            startActivityForResult(new Intent(this, PhotoEditorActivity.class)
                    .putExtra("layerId", canvas.selected)
                    .putExtra("layerFile", document.assetFile(current).getPath())
                    .putExtra("layerSource", document.assetFile(layer.optString("source", current)).getPath()), 74);
        } catch (Exception error) {
            status.setText("No pudimos abrir esta foto.");
        }
    }
    private void undo(){if(history.isEmpty()){Toast.makeText(this,"No hay cambios por deshacer.",Toast.LENGTH_SHORT).show();return;}String before=document.state.toString(),previous=history.get(history.size()-1);try{document.state=new JSONObject(previous);document.save();history.remove(history.size()-1);if(!canvas.selected.equals("background")&&document.layer(canvas.selected)==null)canvas.selected="background";layers();markFormat();}catch(Exception error){try{document.state=new JSONObject(before);}catch(Exception ignored){}status.setText("No pudimos deshacer el cambio.");}}
    private void enabled(boolean value){busy=!value;canvas.setEnabled(value);save.setEnabled(value);save.setAlpha(value?1:.4f);enable(panel,value);updateActions();}
    private void enable(ViewGroup group,boolean value){for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);child.setEnabled(value);if(child instanceof ViewGroup)enable((ViewGroup)child,value);}}
    private void pick(boolean background){if(busy)return;if(!background&&document.layers().length()>=4){Toast.makeText(this,"Puedes combinar hasta 4 productos por imagen.",Toast.LENGTH_LONG).show();return;}try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),background?71:72);}catch(Exception error){status.setText("No pudimos abrir la galería.");}}
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == 73 || request == 74) {
            applyLayerEdit(data, request == 74);
        } else if ((request == 71 || request == 72) && data.getData() != null) {
            addImage(data.getData(), request == 72);
        }
    }

    private void applyLayerEdit(Intent data, boolean replaceSource) {
        String id = data.getStringExtra("layerId");
        String sessionId = data.getStringExtra("maskSessionId");
        if (id == null || sessionId == null || document.layer(id) == null) return;
        String before = document.state.toString();
        enabled(false);
        status.setText("Aplicando los cambios…");
        worker.execute(() -> {
            Bitmap edited = null, original = null;
            try {
                MaskEditSession session = MaskEditSession.open(this, sessionId);
                edited = session.read("current.png");
                Rect bounds = CompositionDocument.visibleBounds(edited);
                String file = document.asset(edited, "product");
                String source = null;
                if (replaceSource) {
                    original = session.read("source.png");
                    source = document.asset(original, "source");
                }
                String originalFile = source;
                float anchorX = bounds.exactCenterX() / edited.getWidth();
                float anchorY = bounds.exactCenterY() / edited.getHeight();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    try {
                        JSONObject layer = document.layer(id);
                        layer.put("file", file).put("bounds", CompositionDocument.bounds(bounds));
                        if (replaceSource) {
                            layer.put("source", originalFile).put("anchorX", anchorX).put("anchorY", anchorY);
                        }
                        commit(before);
                        status.setText("Foto actualizada");
                    } catch (JSONException error) {
                        status.setText("No pudimos aplicar los cambios.");
                    }
                    enabled(true);
                });
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    enabled(true);
                    status.setText("No pudimos aplicar los cambios. La foto anterior se conserva.");
                });
            } finally {
                if (edited != null) edited.recycle();
                if (original != null) original.recycle();
            }
        });
    }

    private void addImage(Uri uri, boolean product) {
        String before = document.state.toString();
        enabled(false);
        status.setText(product ? "Recortando el nuevo producto…" : "Preparando tu fondo…");
        worker.execute(() -> {
            Bitmap source = null, cutout = null, reference = null;
            try {
                source = ImageTools.read(this, uri);
                if (product) {
                    try (FoodPhotoEngine engine = new FoodPhotoEngine(this, source)) {
                        engine.includeObjects(true);
                        cutout = engine.render(true, "transparent");
                        reference = engine.render(true, "original");
                    } catch (FoodPhotoEngine.MaskFallbackException error) {
                        // Keep the product available for guided or manual editing.
                    }
                }
                boolean needsCutout = product && cutout == null;
                JSONObject layer = product ? document.prepareProduct(cutout == null ? source : cutout,
                        reference == null ? source : reference) : null;
                String background = product ? null : document.asset(source, "background");
                int width = source.getWidth(), height = source.getHeight();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    try {
                        if (product) {
                            document.layers().put(layer);
                            canvas.selected = layer.getString("id");
                        } else {
                            document.state.put("background", background).put("width", width).put("height", height)
                                    .put("bgScale", 1).put("bgX", 0).put("bgY", 0);
                            canvas.selected = "background";
                        }
                        commit(before);
                        layers();
                        status.setText(needsCutout ? "Usa Editar foto para quitar el fondo del producto"
                                : product ? "Arrastra el nuevo producto para colocarlo" : "Fondo listo · arrastra para encuadrar");
                    } catch (JSONException error) {
                        status.setText("No pudimos agregar la imagen.");
                    }
                    enabled(true);
                });
            } catch (Exception | OutOfMemoryError error) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    enabled(true);
                    status.setText("No pudimos agregar esa imagen. Tus productos se conservan.");
                    Toast.makeText(this, error instanceof IOException ? error.getMessage()
                            : "Prueba una imagen más pequeña.", Toast.LENGTH_LONG).show();
                });
            } finally {
                if (reference != null) reference.recycle();
                if (cutout != null) cutout.recycle();
                if (source != null) source.recycle();
            }
        });
    }
    private void save(){if(busy)return;enabled(false);status.setText("Uniendo las imágenes…");worker.execute(()->{Bitmap image=null;android.util.AtomicFile file=new CrossFile(PhotoEditorActivity.output(this));FileOutputStream out=null;try{image=document.export();String encoded=ImageTools.encodeEdited(image);out=file.startWrite();out.write(encoded.getBytes("UTF-8"));file.finishWrite(out);runOnUiThread(()->{if(!destroyed){setResult(RESULT_OK,new Intent().putExtra("compositionId",document.id()));finish();}});}catch(Exception|OutOfMemoryError error){if(out!=null)file.failWrite(out);runOnUiThread(()->{if(!destroyed){enabled(true);status.setText("No pudimos guardar. Tu composición sigue aquí.");}});}finally{if(image!=null)image.recycle();}});}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(canvas!=null){state.putString("selected",canvas.selected);state.putStringArrayList("history",history);}}
    @Override public void onDestroy(){destroyed=true;worker.execute(()->{if(document!=null)document.close();});worker.shutdown();super.onDestroy();}
}
