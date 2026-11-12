package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.dnn.*;
import org.opencv.imgproc.Imgproc;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Seller-only OpenCV processing and local U2NetP inference. Single worker owns this session. */
final class FoodPhotoEngine implements AutoCloseable {
    private final Context context;
    private Mat original,enhanced,mask,plainMask,completeMask,smallMask,sourceAlpha,manualMask;
    private boolean includeObjects=false;
    private Net network;
    long maskMillis=0;
    int maskRuns=0;
    FoodPhotoEngine(Context context,Bitmap input)throws IOException{
        this.context=context.getApplicationContext();if(!OpenCVLoader.initLocal())throw new IOException("No pudimos iniciar el editor de fotos.");Core.setNumThreads(2);
        Mat rgba=new Mat(),alpha=new Mat();original=new Mat();
        try{
            Utils.bitmapToMat(input,rgba,true);Imgproc.cvtColor(rgba,original,Imgproc.COLOR_RGBA2RGB);
            Core.extractChannel(rgba,alpha,3);Core.MinMaxLocResult range=Core.minMaxLoc(alpha);
            if(range.maxVal==0)throw new IOException("La foto está vacía. Elige una imagen con el producto visible.");
            if(range.minVal<255){sourceAlpha=new Mat();alpha.convertTo(sourceAlpha,CvType.CV_32F,1/255.0);}
            enhanced=natural(original);
        }catch(IOException error){close();throw error;}catch(RuntimeException error){close();throw new IOException("No pudimos preparar esa foto.",error);}finally{rgba.release();alpha.release();}
    }
    private static Mat natural(Mat input){
        Mat balanced=input.clone(),lab=new Mat(),hsv=new Mat(),blur=new Mat(),sharp=new Mat();List<Mat> channels=new ArrayList<>();
        try{
            // Limit gray-world gains: strongly coloured food or plates must retain their identity.
            Scalar mean=Core.mean(input);double neutral=(mean.val[0]+mean.val[1]+mean.val[2])/3;
            Core.split(balanced,channels);for(int i=0;i<3;i++){double target=neutral/Math.max(1,mean.val[i]);double gain=1+(Math.max(.88,Math.min(1.12,target))-1)*.45;channels.get(i).convertTo(channels.get(i),-1,gain,0);}Core.merge(channels,balanced);release(channels);
            Imgproc.cvtColor(balanced,lab,Imgproc.COLOR_RGB2Lab);Core.split(lab,channels);Mat local=new Mat();org.opencv.imgproc.CLAHE clahe=Imgproc.createCLAHE(1.5,new Size(8,8));try{clahe.apply(channels.get(0),local);Core.addWeighted(channels.get(0),.78,local,.22,0,channels.get(0));}finally{local.release();clahe.collectGarbage();clahe.clear();}Core.merge(channels,lab);release(channels);Imgproc.cvtColor(lab,balanced,Imgproc.COLOR_Lab2RGB);
            Imgproc.cvtColor(balanced,hsv,Imgproc.COLOR_RGB2HSV);Core.split(hsv,channels);channels.get(1).convertTo(channels.get(1),-1,1.035,0);Core.merge(channels,hsv);release(channels);Imgproc.cvtColor(hsv,balanced,Imgproc.COLOR_HSV2RGB);
            Imgproc.GaussianBlur(balanced,blur,new Size(0,0),.9);Core.addWeighted(balanced,1.12,blur,-.12,0,sharp);return sharp;
        }catch(RuntimeException error){sharp.release();throw error;}finally{release(channels);balanced.release();lab.release();hsv.release();blur.release();}
    }
    private static void release(List<Mat> all){for(Mat mat:all)mat.release();all.clear();}
    Bitmap render(boolean improve,String background)throws IOException{return render(improve?1f:0f,background);}
    Bitmap render(float strength,String background)throws IOException{
        if(original==null)throw new IOException("Vuelve a abrir la foto.");float amount=Float.isNaN(strength)?0:Math.max(0,Math.min(1,strength));
        if(amount==0)return renderSource(original,background);if(amount==1)return renderSource(enhanced,background);
        Mat blended=new Mat();try{Core.addWeighted(original,1-amount,enhanced,amount,0,blended);return renderSource(blended,background);}finally{blended.release();}
    }
    private Bitmap renderSource(Mat source,String background)throws IOException{
        if(background.equals("original"))return sourceAlpha==null?bitmap(source):isolated(source,sourceAlpha,false);
        ensureMask();if(background.equals("transparent"))return isolated(source,mask,sourceAlpha==null&&manualMask==null);
        Mat back=new Mat();Bitmap texture=null;
        try{
            if(background.equals("blur"))Imgproc.GaussianBlur(source,back,new Size(0,0),Math.max(5,source.cols()/60.0));
            else{texture=texture(source.cols(),source.rows(),background);Utils.bitmapToMat(texture,back);Imgproc.cvtColor(back,back,Imgproc.COLOR_RGBA2RGB);}
            return composite(source,back,mask);
        }finally{if(texture!=null)texture.recycle();back.release();}
    }
    void setManualMask(Bitmap bitmap)throws IOException{if(bitmap.getWidth()!=original.cols()||bitmap.getHeight()!=original.rows())throw new IOException("Este recorte no coincide con la foto.");Mat rgba=new Mat(),alpha=new Mat(),next=new Mat();try{Utils.bitmapToMat(bitmap,rgba,true);Core.extractChannel(rgba,alpha,3);alpha.convertTo(next,CvType.CV_32F,1/255.0);if(manualMask!=null)manualMask.release();manualMask=next;next=null;mask=manualMask;}finally{rgba.release();alpha.release();if(next!=null)next.release();}}
    void includeObjects(boolean value){includeObjects=value;mask=manualMask!=null?manualMask:sourceAlpha!=null?sourceAlpha:value?completeMask:plainMask;}
    private void ensureMask()throws IOException{
        if(manualMask!=null){mask=manualMask;return;}
        if(sourceAlpha!=null){mask=sourceAlpha;return;}
        if(mask!=null)return;
        if(smallMask!=null){Mat rgb=new Mat(),soft=smallMask.clone();try{Imgproc.resize(original,rgb,new Size(320,320));if(includeObjects)completeObjects(rgb,soft);mask=finishMask(soft);if(includeObjects)completeMask=mask;else plainMask=mask;}finally{rgb.release();soft.release();}return;}long start=android.os.SystemClock.elapsedRealtime();Mat small=new Mat(),floats=new Mat(),blob=null,prediction=null,flat=null,soft=new Mat(),candidate=new Mat();List<Mat> channels=new ArrayList<>();
        try{
            if(network==null){network=Dnn.readNetFromONNX(modelFile().getAbsolutePath());network.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);network.setPreferableTarget(Dnn.DNN_TARGET_CPU);}
            Imgproc.resize(original,small,new Size(320,320),0,0,Imgproc.INTER_AREA);Mat scalar=small.reshape(1);double max;try{max=Math.max(1,Core.minMaxLoc(scalar).maxVal);}finally{scalar.release();}small.convertTo(floats,CvType.CV_32FC3,1/max);
            Core.split(floats,channels);double[] means={.485,.456,.406},std={.229,.224,.225};for(int i=0;i<3;i++){Core.subtract(channels.get(i),new Scalar(means[i]),channels.get(i));Core.multiply(channels.get(i),new Scalar(1/std[i]),channels.get(i));}Core.merge(channels,floats);release(channels);
            blob=Dnn.blobFromImage(floats);network.setInput(blob);prediction=network.forward(network.getUnconnectedOutLayersNames().get(0));
            flat=prediction.reshape(1,320);Core.MinMaxLocResult range=Core.minMaxLoc(flat);if(range.maxVal-range.minVal<1e-6)throw new IOException("No distinguimos bien el producto del fondo. Puedes conservar la foto original.");
            flat.convertTo(soft,CvType.CV_32F,1/(range.maxVal-range.minVal),-range.minVal/(range.maxVal-range.minVal));
            smallMask=soft.clone();if(includeObjects)completeObjects(small,soft);
            candidate=finishMask(soft);mask=candidate;if(includeObjects)completeMask=mask;else plainMask=mask;candidate=null;maskMillis=android.os.SystemClock.elapsedRealtime()-start;maskRuns++;
        }catch(IOException error){throw error;}catch(RuntimeException error){throw new IOException("No pudimos separar el fondo de esta foto. Puedes usar la mejora natural.",error);}finally{small.release();floats.release();release(channels);if(blob!=null)blob.release();if(flat!=null)flat.release();if(prediction!=null)prediction.release();soft.release();if(candidate!=null)candidate.release();}
    }
    private Mat finishMask(Mat source)throws IOException{Mat soft=source.clone(),candidate=new Mat(),kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(3,3)),binary=new Mat(),labels=new Mat(),stats=new Mat(),centroids=new Mat(),keep=new Mat(),component=new Mat();try{
        Core.compare(soft,new Scalar(.35),binary,Core.CMP_GT);int count=Imgproc.connectedComponentsWithStats(binary,labels,stats,centroids,8,CvType.CV_32S);
        if(count>1){int largest=0;for(int i=1;i<count;i++)largest=Math.max(largest,(int)stats.get(i,Imgproc.CC_STAT_AREA)[0]);int minimum=Math.max(24,Math.round(largest*.005f));for(int i=1;i<count;i++){if((int)stats.get(i,Imgproc.CC_STAT_AREA)[0]<minimum)continue;Core.compare(labels,new Scalar(i),component,Core.CMP_EQ);Core.bitwise_or(keep,component,keep);}keep.convertTo(keep,CvType.CV_32F,1/255.0);Core.multiply(soft,keep,soft);}
        Imgproc.dilate(soft,soft,kernel);Imgproc.resize(soft,candidate,original.size(),0,0,Imgproc.INTER_LINEAR);Imgproc.GaussianBlur(candidate,candidate,new Size(0,0),1.1);Core.min(candidate,new Scalar(1),candidate);Core.max(candidate,new Scalar(0),candidate);double coverage=Core.mean(candidate).val[0];if(coverage<.01||coverage>.985){candidate.release();throw new IOException("El recorte no quedó claro. Prueba desactivar incluir plato, o conserva el fondo original.");}return candidate;}finally{soft.release();kernel.release();binary.release();labels.release();stats.release();centroids.release();keep.release();component.release();}}
    /** U2NetP saliency may ignore a second cup. Colour refinement keeps the complete still life. */
    private static void completeObjects(Mat rgb,Mat soft){
        Mat labels=new Mat(rgb.size(),CvType.CV_8U,new Scalar(Imgproc.GC_PR_BGD)),background=new Mat(),foreground=new Mat(),sure=new Mat(),keep=new Mat(),other=new Mat(),refined=new Mat();
        try{
            Mat inside=labels.submat(new org.opencv.core.Rect(5,5,rgb.cols()-10,rgb.rows()-10));try{inside.setTo(new Scalar(Imgproc.GC_PR_FGD));}finally{inside.release();}
            Imgproc.threshold(soft,sure,.85,255,Imgproc.THRESH_BINARY);sure.convertTo(sure,CvType.CV_8U);labels.setTo(new Scalar(Imgproc.GC_FGD),sure);
            int edge=10;for(int[] corner:new int[][]{{0,0},{rgb.cols()-edge,0},{0,rgb.rows()-edge},{rgb.cols()-edge,rgb.rows()-edge}}){Mat area=labels.submat(new org.opencv.core.Rect(corner[0],corner[1],edge,edge));try{area.setTo(new Scalar(Imgproc.GC_BGD));}finally{area.release();}}
            Imgproc.grabCut(rgb,labels,new org.opencv.core.Rect(),background,foreground,2,Imgproc.GC_INIT_WITH_MASK);
            Core.compare(labels,new Scalar(Imgproc.GC_FGD),keep,Core.CMP_EQ);Core.compare(labels,new Scalar(Imgproc.GC_PR_FGD),other,Core.CMP_EQ);Core.bitwise_or(keep,other,keep);keep.convertTo(refined,CvType.CV_32F,1/255.0);Core.max(soft,refined,soft);
        }finally{labels.release();background.release();foreground.release();sure.release();keep.release();other.release();refined.release();}
    }
    private File modelFile()throws IOException{
        String expected="309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8";File model=new File(context.getFilesDir(),"u2netp-"+expected.substring(0,12)+".onnx");if(model.isFile())return model;
        android.util.AtomicFile atomic=new CrossFile(model);FileOutputStream output=null;
        try(InputStream in=context.getAssets().open("models/u2netp.onnx")){MessageDigest hash=MessageDigest.getInstance("SHA-256");output=atomic.startWrite();byte[] buffer=new byte[16384];int count;while((count=in.read(buffer))!=-1){output.write(buffer,0,count);hash.update(buffer,0,count);}StringBuilder actual=new StringBuilder();for(byte b:hash.digest())actual.append(String.format(Locale.US,"%02x",b&255));if(!actual.toString().equals(expected))throw new IOException("El archivo del recorte no está completo.");atomic.finishWrite(output);output=null;return model;}catch(Exception error){if(output!=null)atomic.failWrite(output);if(error instanceof IOException)throw (IOException)error;throw new IOException("No pudimos preparar el recorte.",error);}
    }
    private static Bitmap bitmap(Mat rgb){Bitmap result=Bitmap.createBitmap(rgb.cols(),rgb.rows(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(rgb,result);return result;}
    /** Keep straight RGB and alpha; Bitmap.setPixels performs Android's premultiplication. */
    private static Bitmap isolated(Mat foreground,Mat mask,boolean clean){
        int width=foreground.cols(),height=foreground.rows();Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        byte[] rgb=new byte[width*3];float[] alpha=new float[width];int[] row=new int[width];
        for(int y=0;y<height;y++){
            foreground.get(y,0,rgb);mask.get(y,0,alpha);
            for(int x=0;x<width;x++){
                float value=Math.max(0,Math.min(1,alpha[x]));
                // Remove low-confidence haze, while keeping a soft transition at the product edge.
                if(clean)value=value<.02f?0:value>.98f?1:value;
                int opacity=Math.round(value*255),at=x*3;row[x]=Color.argb(opacity,rgb[at]&255,rgb[at+1]&255,rgb[at+2]&255);
            }
            result.setPixels(row,0,width,0,y,width,1);
        }
        return result;
    }
    private static Bitmap composite(Mat foreground,Mat background,Mat mask){
        int width=foreground.cols(),height=foreground.rows();Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);byte[] front=new byte[width*3],back=new byte[width*3];float[] alpha=new float[width];int[] row=new int[width];
        for(int y=0;y<height;y++){foreground.get(y,0,front);background.get(y,0,back);mask.get(y,0,alpha);for(int x=0;x<width;x++){float a=Math.max(0,Math.min(1,alpha[x]));int at=x*3;int r=Math.round((front[at]&255)*a+(back[at]&255)*(1-a)),g=Math.round((front[at+1]&255)*a+(back[at+1]&255)*(1-a)),b=Math.round((front[at+2]&255)*a+(back[at+2]&255)*(1-a));row[x]=Color.rgb(r,g,b);}result.setPixels(row,0,width,0,y,width,1);}return result;
    }
    static Bitmap texture(int width,int height,String kind){
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);int[] row=new int[width];
        for(int y=0;y<height;y++){for(int x=0;x<width;x++){double light=1-.10*Math.pow((x-width*.42)/Math.max(1,width),2)-.08*y/Math.max(1.0,height);int r,g,b;
            if(kind.equals("wood")){double grain=7*Math.sin(y*.16+Math.sin(x*.009)*1.8)+3*Math.sin(y*.83+x*.012);double plank=y%Math.max(1,height/4)<2?-22:0;r=(int)((177+grain+plank)*light);g=(int)((126+grain+plank)*light);b=(int)((78+grain+plank)*light);}
            else if(kind.equals("marble")){double wave=Math.sin(x*.016+Math.sin(y*.011)*2.5+y*.012),vein=Math.pow(Math.max(0,1-Math.abs(wave)*12),2)*27;r=(int)((233-vein)*light);g=(int)((229-vein)*light);b=(int)((220-vein*.8)*light);}
            else{int size=Math.max(12,width/12);boolean check=(x/size+y/size)%2==0;double weave=2*Math.sin(x*2)+2*Math.sin(y*2);r=(int)(((check?235:173)+weave)*light);g=(int)(((check?213:91)+weave)*light);b=(int)(((check?174:69)+weave)*light);}
            row[x]=Color.rgb(Math.max(0,Math.min(255,r)),Math.max(0,Math.min(255,g)),Math.max(0,Math.min(255,b)));}result.setPixels(row,0,width,0,y,width,1);}return result;
    }
    Bitmap maskPreview()throws IOException{ensureMask();Mat view=new Mat();try{mask.convertTo(view,CvType.CV_8U,255);return bitmap(view);}finally{view.release();}}
    @Override public void close(){if(original!=null)original.release();if(enhanced!=null)enhanced.release();if(plainMask!=null)plainMask.release();if(completeMask!=null)completeMask.release();if(smallMask!=null)smallMask.release();if(sourceAlpha!=null)sourceAlpha.release();if(manualMask!=null)manualMask.release();manualMask=null;original=null;enhanced=null;mask=null;plainMask=null;completeMask=null;smallMask=null;sourceAlpha=null;network=null;}
}
