package pe.manchapp.beta;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class MaskEditorActivity extends Activity {
    private MaskBrushView brush;
    private MaskEditSession session;
    private boolean dirty=false;
    private TextView erase,restore;
    @Override public void onCreate(Bundle state){super.onCreate(state);PhotoUi ui=new PhotoUi(this);getWindow().setStatusBarColor(PhotoUi.BG);getWindow().setNavigationBarColor(PhotoUi.PANEL);Bitmap source=null,mask=null;try{session=MaskEditSession.open(this,getIntent().getStringExtra("maskSessionId"));source=session.read("source.png");mask=session.read("current.png");brush=new MaskBrushView(this,source,mask,()->dirty=true);if(state!=null){brush.restore=state.getBoolean("restore");brush.brush=state.getFloat("brush",16);}LinearLayout root=ui.column();root.setBackgroundColor(PhotoUi.BG);setContentView(root);TextView done=ui.button("Aplicar",true,()->apply());done.setTag("mask:apply");root.addView(ui.header("Corregir recorte",ui.icon("back","Cancelar corrección",()->finish()),done));brush.setTag("mask:canvas");root.addView(brush,new LinearLayout.LayoutParams(-1,0,1));TextView hint=ui.text("Un dedo pinta · dos dedos acercan y mueven",12,PhotoUi.MUTED,false);hint.setGravity(Gravity.CENTER);hint.setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(10));root.addView(hint);LinearLayout tools=ui.column();tools.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(14));tools.setBackground(ui.background(PhotoUi.PANEL,0,20));root.addView(tools);LinearLayout actions=new LinearLayout(this);erase=ui.button("Borrar",false,()->{brush.restore=false;mark(ui);});erase.setTag("mask:erase");restore=ui.button("Recuperar",false,()->{brush.restore=true;mark(ui);});restore.setTag("mask:restore");ui.weighted(actions,erase,false);ui.weighted(actions,restore,false);TextView undo=ui.button("Deshacer",false,()->brush.undo());undo.setTag("mask:undo");ui.weighted(actions,undo,true);ui.add(tools,actions,0);LinearLayout sizes=new LinearLayout(this);for(int size:new int[]{6,16,30}){TextView choice=ui.button(size==6?"Fino":size==16?"Medio":"Grande",false,()->{brush.brush=size;for(int i=0;i<sizes.getChildCount();i++)sizes.getChildAt(i).setSelected(false);markSizes(ui,sizes);});choice.setTag(size);ui.weighted(sizes,choice,size==30);}ui.add(tools,sizes,8);mark(ui);markSizes(ui,sizes);if(getIntent().getBooleanExtra("restoreLimited",false))Toast.makeText(this,"Para recuperar zonas que faltan en esta foto, vuelve a agregar la imagen original.",Toast.LENGTH_LONG).show();}catch(Exception|OutOfMemoryError error){Toast.makeText(this,"No pudimos abrir el recorte manual. Tu imagen anterior se conserva.",Toast.LENGTH_LONG).show();finish();}finally{if(source!=null)source.recycle();if(mask!=null)mask.recycle();}}
    private void mark(PhotoUi ui){erase.setSelected(!brush.restore);restore.setSelected(brush.restore);erase.setBackground(ui.background(!brush.restore?PhotoUi.ACCENT:PhotoUi.SURFACE,0,12));restore.setBackground(ui.background(brush.restore?PhotoUi.ACCENT:PhotoUi.SURFACE,0,12));}
    private void markSizes(PhotoUi ui,LinearLayout sizes){for(int i=0;i<sizes.getChildCount();i++){View choice=sizes.getChildAt(i);boolean active=((Integer)choice.getTag()).intValue()==(int)brush.brush;choice.setSelected(active);choice.setBackground(ui.background(active?PhotoUi.SURFACE:Color.TRANSPARENT,active?PhotoUi.LIGHT:0,12));}}
    private void persist(){if(!dirty||brush==null)return;try{session.write("current.png",brush.working);dirty=false;}catch(Exception error){Toast.makeText(this,"No pudimos conservar el último trazo. Mantén el editor abierto e intenta aplicar otra vez.",Toast.LENGTH_LONG).show();}}
    private void apply(){dirty=true;persist();if(dirty)return;setResult(RESULT_OK,new Intent().putExtra("maskSessionId",session.id()).putExtra("layerId",getIntent().getStringExtra("layerId")));finish();}
    @Override protected void onPause(){persist();super.onPause();}
    @Override public void onSaveInstanceState(Bundle state){persist();super.onSaveInstanceState(state);if(brush!=null){state.putBoolean("restore",brush.restore);state.putFloat("brush",brush.brush);}}
    @Override public void onDestroy(){if(brush!=null)brush.close();super.onDestroy();}
}
