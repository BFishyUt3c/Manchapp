package pe.manchapp.beta;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import java.io.*;
import org.json.JSONObject;

/** Actual CPU inference and visual evidence; does not edit the catalog or the user's photos. */
public final class PhotoChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    @Override public void onStart(){Bundle result=new Bundle();StringBuilder report=new StringBuilder();Context context=getTargetContext();File dir=new File(context.getFilesDir(),"qa-photos");dir.mkdirs();
        try{for(String id:new String[]{"papa","causa","cachanga","cafe","merienda-combo","mazamorra"}){
            Bitmap original=ImageTools.productPhoto(context,new JSONObject().put("id",id),640);check(original!=null,"Photo exists: "+id);
            Bitmap sheet=Bitmap.createBitmap(960,660,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(sheet);canvas.drawColor(Color.rgb(247,221,184));Paint text=new Paint(3);text.setColor(Color.rgb(86,21,44));text.setTextSize(21);text.setTypeface(Typeface.DEFAULT_BOLD);
            try(FoodPhotoEngine engine=new FoodPhotoEngine(context,original)){engine.includeObjects(id.equals("merienda-combo"));
                String[] modes={"source","original","blur","wood","marble","cloth"},labels={"Original","Natural","Desenfocado","Madera","Mármol","Mantel"};
                for(int i=0;i<modes.length;i++){Bitmap view=i==0?original:engine.render(true,modes[i]);check(view.getWidth()==original.getWidth()&&view.getHeight()==original.getHeight(),"Composition keeps dimensions");int x=(i%3)*320,y=(i/3)*330;canvas.drawText(labels[i],x+12,y+26,text);float scale=Math.min(296f/view.getWidth(),284f/view.getHeight());float w=view.getWidth()*scale,h=view.getHeight()*scale;canvas.drawBitmap(view,null,new RectF(x+160-w/2,y+38+(284-h)/2,x+160+w/2,y+38+(284+h)/2),null);if(i>0)view.recycle();}
                Bitmap cutout=engine.render(true,"transparent");check(ImageTools.hasTransparency(cutout),"Cutout has real transparent pixels: "+id);boolean solid=false;for(int y=0;y<cutout.getHeight();y+=8)for(int x=0;x<cutout.getWidth();x+=8)if(Color.alpha(cutout.getPixel(x,y))>200)solid=true;check(solid,"Visible product preserved: "+id);try(OutputStream out=new FileOutputStream(new File(dir,id+"-cutout.png"))){cutout.compress(Bitmap.CompressFormat.PNG,100,out);}cutout.recycle();
                check(engine.maskRuns==1,"Mask reused across backgrounds and transparent output");Bitmap mask=engine.maskPreview();try(OutputStream out=new FileOutputStream(new File(dir,id+"-mask.png"))){mask.compress(Bitmap.CompressFormat.PNG,100,out);}mask.recycle();
                report.append(id).append(": mask_ms=").append(engine.maskMillis).append(", sampled_pss_kib=").append(Debug.getPss()).append('\n');
            }finally{original.recycle();}
            try(OutputStream out=new FileOutputStream(new File(dir,id+"-comparison.jpg"))){sheet.compress(Bitmap.CompressFormat.JPEG,92,out);}sheet.recycle();
        }result.putString("stream","PASS: OpenCV natural correction, six U2NetP CPU inferences, transparent cutouts, five backgrounds and reusable masks.\n"+report);finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error.toString()+"\n"+report);finish(Activity.RESULT_CANCELED,result);}
    }
}
