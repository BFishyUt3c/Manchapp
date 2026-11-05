package pe.manchapp.beta;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import java.util.Random;

/** Photo-only checks use a new synthetic product, never any catalog ID or user data. */
public final class PhotoCropChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    @Override public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();
        try{
            Bitmap imported=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(imported);Paint paint=new Paint();paint.setColor(Color.rgb(30,100,210));canvas.drawRect(100,60,300,240,paint);
            String encoded=ImageTools.encodeEdited(imported);check(encoded.startsWith("data:image/png;base64,"),"Imported transparent product is PNG");
            Bitmap reloaded=ImageTools.decode(encoded);check(reloaded!=null&&Color.alpha(reloaded.getPixel(0,0))==0,"Imported transparency survives persistence");
            try(FoodPhotoEngine engine=new FoodPhotoEngine(context,reloaded)){
                Bitmap original=engine.render(false,"original");check(Color.alpha(original.getPixel(0,0))==0,"Reopened original retains transparency");check(Color.blue(original.getPixel(200,150))>190,"Straight foreground colours retained");original.recycle();
                engine.includeObjects(true);Bitmap cutout=engine.render(true,"transparent");check(engine.maskRuns==0,"Existing alpha reused without another neural inference");
                Bitmap cropped=PhotoCropView.apply(cutout,new RectF(.2f,.1f,.8f,.9f));check(cropped.getWidth()==240&&cropped.getHeight()==240,"Crop coordinates map to source pixels");check(Color.alpha(cropped.getPixel(0,0))==0&&Color.alpha(cropped.getPixel(120,120))==255,"Crop preserves transparency and product");
                check(ImageTools.encodeEdited(cropped).startsWith("data:image/png;base64,"),"Cropped output keeps PNG");cropped.recycle();cutout.recycle();
                Bitmap wood=engine.render(false,"wood");check(!ImageTools.hasTransparency(wood),"Replacement background is opaque");check(ImageTools.encodeEdited(wood).startsWith("data:image/jpeg;base64,"),"Opaque output stays lightweight JPEG");wood.recycle();
            }
            reloaded.recycle();imported.recycle();
            Bitmap noise=Bitmap.createBitmap(960,960,Bitmap.Config.ARGB_8888);Random random=new Random(27);int[] row=new int[960];for(int y=0;y<960;y++){for(int x=0;x<960;x++)row[x]=Color.argb(128,random.nextInt(256),random.nextInt(256),random.nextInt(256));noise.setPixels(row,0,960,0,y,960,1);}
            String small=ImageTools.encodeEdited(noise);byte[] bytes=android.util.Base64.decode(small.substring(small.indexOf(',')+1),android.util.Base64.DEFAULT);check(bytes.length<=600000,"Transparent photo respects storage cap");Bitmap bounded=ImageTools.decode(small);check(bounded.getWidth()<960&&ImageTools.hasTransparency(bounded),"Large PNG is downscaled without flattening alpha");check(noise.getWidth()==960&&!noise.isRecycled(),"Encoder never recycles or resizes its input");bounded.recycle();noise.recycle();
            result.putString("stream","PASS: arbitrary imported photo, reversible geometry, alpha round-trip, opaque backgrounds and bounded PNG storage.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
