package pe.manchapp.beta;

import android.content.Context;
import android.graphics.*;
import android.util.*;
import org.json.*;
import java.io.*;
import java.util.UUID;

final class CompositionDocument {
    final File directory;
    JSONObject state;
    private final LruCache<String,Bitmap> bitmaps=new LruCache<String,Bitmap>(20*1024*1024){protected int sizeOf(String key,Bitmap value){return value.getByteCount();}};
    private CompositionDocument(Context context,String id)throws Exception{if(!id.matches("[a-fA-F0-9-]{36}"))throw new IOException("Composición no válida.");directory=new File(new File(context.getFilesDir(),"compositions"),id);}
    static CompositionDocument create(Context context,Bitmap product,Bitmap background)throws Exception{
        return create(context,product,background,product);
    }
    static CompositionDocument create(Context context,Bitmap product,Bitmap background,Bitmap source)throws Exception{
        CompositionDocument doc=new CompositionDocument(context,UUID.randomUUID().toString());if(!doc.directory.mkdirs())throw new IOException("No pudimos preparar la composición.");
        String back=doc.asset(background,"background");doc.state=new JSONObject().put("format","background").put("width",background.getWidth()).put("height",background.getHeight()).put("background",back).put("bgScale",1).put("bgX",0).put("bgY",0).put("layers",new JSONArray());doc.layers().put(doc.prepareProduct(product,source));doc.save();return doc;
    }
    static CompositionDocument open(Context context,String id)throws Exception{CompositionDocument doc=new CompositionDocument(context,id);doc.state=new JSONObject(SafeJson.read(new CrossFile(new File(doc.directory,"scene.json")).openRead()));if(doc.state.optJSONArray("layers")==null||doc.state.optInt("width")<1||doc.state.optInt("height")<1)throw new IOException("No pudimos recuperar la composición.");return doc;}
    String id(){return directory.getName();}
    void save()throws Exception{AtomicFile file=new CrossFile(new File(directory,"scene.json"));FileOutputStream out=null;try{out=file.startWrite();out.write(state.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);throw error;}}
    String asset(Bitmap bitmap,String prefix)throws Exception{String name=prefix+"-"+UUID.randomUUID()+".png";AtomicFile file=new CrossFile(new File(directory,name));FileOutputStream out=null;try{out=file.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException();file.finishWrite(out);return name;}catch(Exception error){if(out!=null)file.failWrite(out);throw error;}}
    Bitmap bitmap(String name)throws IOException{if(!name.matches("[a-zA-Z0-9-]+\\.png"))throw new IOException("Imagen no válida.");Bitmap value=bitmaps.get(name);if(value==null){value=BitmapFactory.decodeFile(new File(directory,name).getPath());if(value==null)throw new IOException("No pudimos abrir una imagen de la composición.");bitmaps.put(name,value);}return value;}
    JSONArray layers(){return state.optJSONArray("layers");}
    JSONObject layer(String id){JSONArray values=layers();for(int i=0;i<values.length();i++)if(values.optJSONObject(i).optString("id").equals(id))return values.optJSONObject(i);return null;}
    String addProduct(Bitmap input)throws Exception{
        JSONObject layer=prepareProduct(input);layers().put(layer);return layer.getString("id");
    }
    JSONObject prepareProduct(Bitmap input)throws Exception{return prepareProduct(input,input);}
    JSONObject prepareProduct(Bitmap input,Bitmap source)throws Exception{
        if(layers().length()>=4)throw new IOException("Puedes combinar hasta 4 productos en esta imagen.");if(input.getWidth()!=source.getWidth()||input.getHeight()!=source.getHeight())throw new IOException("Las imágenes del producto no coinciden.");Rect visible=visibleBounds(input);String name=asset(input,"product"),original=asset(source,"source");String id=UUID.randomUUID().toString();int count=layers().length();return new JSONObject().put("id",id).put("file",name).put("source",original).put("bounds",bounds(visible)).put("baseW",visible.width()).put("baseH",visible.height()).put("anchorX",visible.exactCenterX()/input.getWidth()).put("anchorY",visible.exactCenterY()/input.getHeight()).put("x",count==0?.5:.35+count*.1).put("y",.5).put("scale",count==0?1:.7);
    }
    static JSONArray bounds(Rect rect){return new JSONArray().put(rect.left).put(rect.top).put(rect.right).put(rect.bottom);}
    static Rect visibleBounds(Bitmap bitmap)throws IOException{int left=bitmap.getWidth(),top=bitmap.getHeight(),right=-1,bottom=-1;int[] row=new int[bitmap.getWidth()];for(int y=0;y<bitmap.getHeight();y++){bitmap.getPixels(row,0,row.length,0,y,row.length,1);for(int x=0;x<row.length;x++)if(Color.alpha(row[x])>12){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}}if(right<left)throw new IOException("El recorte quedó vacío. Conserva la foto y vuelve a revisar los bordes.");int margin=2;return new Rect(Math.max(0,left-margin),Math.max(0,top-margin),Math.min(bitmap.getWidth(),right+margin+1),Math.min(bitmap.getHeight(),bottom+margin+1));}
    int width(){String format=state.optString("format");return format.equals("square")?960:format.equals("portrait")?768:format.equals("story")?540:state.optInt("width");}
    int height(){return state.optString("format").equals("background")?state.optInt("height"):960;}
    RectF backgroundRect()throws Exception{Bitmap background=bitmap(state.getString("background"));float factor=Math.max(width()/(float)background.getWidth(),height()/(float)background.getHeight())*(float)state.optDouble("bgScale",1);float w=background.getWidth()*factor,h=background.getHeight()*factor;float x=Math.max(width()-w,Math.min(0,(width()-w)/2+(float)state.optDouble("bgX")*width())),y=Math.max(height()-h,Math.min(0,(height()-h)/2+(float)state.optDouble("bgY")*height()));return new RectF(x,y,x+w,y+h);}
    RectF layerRect(JSONObject layer)throws Exception{Bitmap bitmap=bitmap(layer.getString("file"));float factor=Math.min(width()*.72f/(float)layer.optDouble("baseW",bitmap.getWidth()),height()*.72f/(float)layer.optDouble("baseH",bitmap.getHeight()))*(float)layer.optDouble("scale",1),w=bitmap.getWidth()*factor,h=bitmap.getHeight()*factor,x=(float)layer.optDouble("x",.5)*width()-w*(float)layer.optDouble("anchorX",.5),y=(float)layer.optDouble("y",.5)*height()-h*(float)layer.optDouble("anchorY",.5);return new RectF(x,y,x+w,y+h);}
    RectF selectionRect(JSONObject layer)throws Exception{RectF image=layerRect(layer);JSONArray bounds=layer.optJSONArray("bounds");if(bounds==null)return image;Bitmap bitmap=bitmap(layer.getString("file"));float sx=image.width()/bitmap.getWidth(),sy=image.height()/bitmap.getHeight();return new RectF(image.left+(float)bounds.optDouble(0)*sx,image.top+(float)bounds.optDouble(1)*sy,image.left+(float)bounds.optDouble(2)*sx,image.top+(float)bounds.optDouble(3)*sy);}
    void pan(String selected,float dx,float dy)throws Exception{if(selected.equals("background")){Bitmap bitmap=bitmap(state.getString("background"));float factor=Math.max(width()/(float)bitmap.getWidth(),height()/(float)bitmap.getHeight())*(float)state.optDouble("bgScale",1),maxX=(bitmap.getWidth()*factor-width())/(2*width()),maxY=(bitmap.getHeight()*factor-height())/(2*height());state.put("bgX",Math.max(-maxX,Math.min(maxX,state.optDouble("bgX")+dx))).put("bgY",Math.max(-maxY,Math.min(maxY,state.optDouble("bgY")+dy)));}else{JSONObject layer=layer(selected);if(layer!=null)layer.put("x",Math.max(.05,Math.min(.95,layer.optDouble("x")+dx))).put("y",Math.max(.05,Math.min(.95,layer.optDouble("y")+dy)));}}
    void zoom(String selected,float factor)throws Exception{if(selected.equals("background")){state.put("bgScale",Math.max(1,Math.min(4,state.optDouble("bgScale",1)*factor)));pan(selected,0,0);}else{JSONObject layer=layer(selected);if(layer!=null)layer.put("scale",Math.max(.15,Math.min(3,layer.optDouble("scale",1)*factor)));}}
    void replaceBackground(Bitmap background)throws Exception{String name=asset(background,"background");state.put("background",name).put("width",background.getWidth()).put("height",background.getHeight()).put("bgScale",1).put("bgX",0).put("bgY",0);}
    void draw(Canvas canvas)throws Exception{Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);canvas.drawColor(Color.WHITE);canvas.drawBitmap(bitmap(state.getString("background")),null,backgroundRect(),paint);JSONArray layers=layers();for(int i=0;i<layers.length();i++){JSONObject layer=layers.getJSONObject(i);canvas.drawBitmap(bitmap(layer.getString("file")),null,layerRect(layer),paint);}}
    Bitmap export()throws Exception{Bitmap output=Bitmap.createBitmap(width(),height(),Bitmap.Config.ARGB_8888);try{draw(new Canvas(output));return output;}catch(Exception|OutOfMemoryError error){output.recycle();throw error;}}
    void close(){bitmaps.evictAll();}
}
