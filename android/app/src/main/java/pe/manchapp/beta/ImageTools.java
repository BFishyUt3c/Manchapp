package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.*;
import org.json.JSONObject;
import java.io.*;

/** Bounded photo decoding; the list never decodes every photo at full size. */
final class ImageTools {
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(6*1024*1024){@Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}};
    Bitmap thumbnail(String encoded,int edge){if(encoded==null||encoded.isEmpty())return null;String key=encoded.hashCode()+":"+encoded.length()+":"+edge;Bitmap old=cache.get(key);if(old!=null&&!old.isRecycled())return old;try{byte[] bytes=Base64.decode(encoded.substring(encoded.indexOf(',')+1),Base64.DEFAULT);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample(bounds.outWidth,bounds.outHeight,edge);Bitmap result=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(result!=null)cache.put(key,result);return result;}catch(Exception|OutOfMemoryError error){return null;}}
    static String bundledPhoto(JSONObject product){
        if(product.optBoolean("imageReference",false))return "";
        String id=product.optString("id");
        return id.equals("papa")||id.equals("causa")||id.equals("cachanga")||id.equals("cafe")||id.equals("merienda-combo")||id.equals("mazamorra")?"photos/"+id+".png":"";
    }
    static boolean hasProductPhoto(JSONObject product){return !product.optString("image").isEmpty()||!bundledPhoto(product).isEmpty();}
    private static Bitmap assetPhoto(Context context,String path,int edge){
        if(path.isEmpty())return null;
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            try(InputStream in=context.getAssets().open(path)){BitmapFactory.decodeStream(in,null,bounds);}
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample(bounds.outWidth,bounds.outHeight,Math.max(1,edge));
            try(InputStream in=context.getAssets().open(path)){return BitmapFactory.decodeStream(in,null,options);}
        }catch(Exception|OutOfMemoryError error){return null;}
    }
    Bitmap productThumbnail(Context context,JSONObject product,int edge){
        Bitmap custom=thumbnail(product.optString("image"),edge);if(custom!=null)return custom;
        String path=bundledPhoto(product);if(path.isEmpty())return null;
        String key="asset:"+path+":"+edge;Bitmap old=cache.get(key);if(old!=null&&!old.isRecycled())return old;
        Bitmap result=assetPhoto(context,path,edge);if(result!=null)cache.put(key,result);return result;
    }
    /** Independent bitmap for a flyer; callers can recycle it without touching the thumbnail cache. */
    static Bitmap productPhoto(Context context,JSONObject product,int edge){Bitmap custom=decode(product.optString("image"));return custom!=null?custom:assetPhoto(context,bundledPhoto(product),edge);}
    static String encodeQr(Bitmap bitmap)throws IOException{
        ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,output);
        if(output.size()>600000)throw new IOException("Elige una captura del QR sin bordes ni contenido adicional.");
        return "data:image/png;base64,"+Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP);
    }
    static int sample(int width,int height,int edge){int sample=1;while(Math.max(width,height)/(sample*2)>=edge)sample*=2;return sample;}
    static Bitmap read(Context context,Uri uri)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream input=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(input,null,bounds);}if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("No pudimos leer la foto.");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample(bounds.outWidth,bounds.outHeight,960);Bitmap bitmap;try(InputStream input=context.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(input,null,options);}if(bitmap==null)throw new IOException("No pudimos leer la foto.");
        int orientation=1;try(InputStream input=context.getContentResolver().openInputStream(uri)){orientation=new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(Exception ignored){}
        Matrix transform=new Matrix();switch(orientation){case 2:transform.setScale(-1,1);break;case 3:transform.setRotate(180);break;case 4:transform.setScale(1,-1);break;case 5:transform.setRotate(90);transform.postScale(-1,1);break;case 6:transform.setRotate(90);break;case 7:transform.setRotate(270);transform.postScale(-1,1);break;case 8:transform.setRotate(270);break;}
        if(!transform.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),transform,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
        float scale=Math.min(1f,960f/Math.max(bitmap.getWidth(),bitmap.getHeight()));if(scale<1){Bitmap resized=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true);if(resized!=bitmap)bitmap.recycle();bitmap=resized;}return bitmap;
    }
    static String encode(Bitmap bitmap)throws IOException{ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,82,output);if(output.size()>600000){output.reset();bitmap.compress(Bitmap.CompressFormat.JPEG,65,output);}if(output.size()>600000)throw new IOException("Prueba una foto de menor tamaño.");return "data:image/jpeg;base64,"+Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP);}
    static boolean hasTransparency(Bitmap bitmap){
        if(!bitmap.hasAlpha())return false;
        int[] row=new int[bitmap.getWidth()];
        for(int y=0;y<bitmap.getHeight();y++){bitmap.getPixels(row,0,row.length,0,y,row.length,1);for(int pixel:row)if(Color.alpha(pixel)<255)return true;}
        return false;
    }
    /** JPEG for opaque photos, bounded PNG for a real transparent cutout. */
    static String encodeEdited(Bitmap bitmap)throws IOException{
        if(!hasTransparency(bitmap))return encode(bitmap);
        Bitmap current=bitmap;ByteArrayOutputStream output=new ByteArrayOutputStream();
        try{
            while(true){
                output.reset();if(!current.compress(Bitmap.CompressFormat.PNG,100,output))throw new IOException("No pudimos guardar la foto.");
                if(output.size()<=600000)return "data:image/png;base64,"+Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP);
                if(Math.max(current.getWidth(),current.getHeight())<=128)throw new IOException("Prueba un encuadre más pequeño.");
                Bitmap smaller=Bitmap.createScaledBitmap(current,Math.max(1,Math.round(current.getWidth()*.8f)),Math.max(1,Math.round(current.getHeight()*.8f)),true);
                if(current!=bitmap)current.recycle();current=smaller;
            }
        }finally{if(current!=bitmap)current.recycle();}
    }
    static Bitmap decode(String encoded){if(encoded==null||encoded.isEmpty())return null;try{byte[] bytes=Base64.decode(encoded.substring(encoded.indexOf(',')+1),Base64.DEFAULT);return BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Exception error){return null;}}
    static String improve(String encoded)throws IOException{
        Bitmap input=decode(encoded);if(input==null)throw new IOException("Elige una foto primero.");double total=0;int count=0;for(int y=0;y<input.getHeight();y+=12)for(int x=0;x<input.getWidth();x+=12){int pixel=input.getPixel(x,y);total+=.2126*Color.red(pixel)+.7152*Color.green(pixel)+.0722*Color.blue(pixel);count++;}
        float lift=(float)Math.max(0,Math.min(32,(137-total/Math.max(1,count))*.55));float contrast=1.04f;float offset=lift+128*(1-contrast);
        Bitmap output=Bitmap.createBitmap(input.getWidth(),input.getHeight(),Bitmap.Config.ARGB_8888);Paint paint=new Paint(3);paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{contrast,0,0,0,offset,0,contrast,0,0,offset,0,0,contrast,0,offset,0,0,0,1,0})));new Canvas(output).drawBitmap(input,0,0,paint);String result=encode(output);input.recycle();output.recycle();return result;
    }
    void clear(){cache.evictAll();}
}
