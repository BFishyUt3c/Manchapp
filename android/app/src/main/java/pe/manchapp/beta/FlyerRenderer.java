package pe.manchapp.beta;

import android.graphics.*;
import android.text.*;
import org.json.*;

final class FlyerRenderer {
    static Bitmap render(JSONArray products,JSONObject settings,String message,int style,boolean square,NativeUi ui){
        if(products.length()==1)return render(products.optJSONObject(0),settings,message,style,square,ui);if(products.length()<1||products.length()>4)throw new IllegalArgumentException("Elige entre 1 y 4 productos.");
        int height=square?720:1280,accent=style==1?NativeUi.CORAL:style==2?NativeUi.GREEN:NativeUi.WINE;Bitmap result=Bitmap.createBitmap(720,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);Paint paint=new Paint(3);canvas.drawColor(NativeUi.BG);paint.setColor(accent);canvas.drawRect(0,0,720,square?88:116,paint);block(canvas,"MANCHAPP",30,22,650,30,Color.WHITE,ui.heavy,1);block(canvas,settings.optString("name"),30,square?56:66,650,square?17:22,0xffffe0b4,ui.bold,1);
        block(canvas,style==1?"ELIGE TUS FAVORITOS":style==2?"VENTA PROGRAMADA":"HECHO EN COMUNIDAD",30,square?106:142,650,square?18:24,accent,ui.heavy,1);block(canvas,SaleSchedule.day(settings),30,square?137:184,650,square?20:32,NativeUi.INK,ui.bold,square?1:2);
        int rows=(products.length()+1)/2;float top=square?178:280,bottom=square?561:994,gap=square?12:18,cellWidth=322,cellHeight=(bottom-top-gap*(rows-1))/rows;
        for(int i=0;i<products.length();i++){JSONObject product=products.optJSONObject(i);float x=30+(i%2)*338,y=top+(i/2)*(cellHeight+gap);paint.setColor(NativeUi.CARD);canvas.drawRoundRect(x,y,x+cellWidth,y+cellHeight,18,18,paint);boolean compact=square&&rows==2;float photoHeight=cellHeight-(compact?64:square?83:114);photo(canvas,paint,product,new RectF(x+8,y+6,x+cellWidth-8,y+photoHeight),ui);block(canvas,product.optString("name"),x+12,y+photoHeight+4,298,square?19:27,NativeUi.INK,ui.heavy,compact?1:2);String amount=price(product)+" · "+SaleRules.unit(product.optString("saleUnit",SaleRules.defaultUnit(product.optString("category"))));block(canvas,amount,x+12,y+cellHeight-(square?28:43),298,square?18:24,accent,ui.bold,1);}
        if(!square)block(canvas,message,32,1014,650,22,NativeUi.MUTED,ui.regular,2);footer(canvas,paint,settings,square?582:1080,square,accent,ui);return result;
    }
    private static float block(Canvas canvas,String value,float x,float y,int width,int size,int color,Typeface font,int lines){
        String text=value==null?"":value;TextPaint paint=new TextPaint(3);paint.setColor(color);paint.setTextSize(size);paint.setTypeface(font);StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(3,1).setIncludePad(false).build();canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore();return layout.getHeight();
    }
    static Bitmap render(JSONObject product,JSONObject settings,String message,int style,NativeUi ui){return render(product,settings,message,style,false,ui);}
    static Bitmap render(JSONObject product,JSONObject settings,String message,int style,boolean square,NativeUi ui){
        int height=square?720:1280;Bitmap result=Bitmap.createBitmap(720,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);Paint paint=new Paint(3);int accent=style==1?NativeUi.CORAL:style==2?NativeUi.GREEN:NativeUi.WINE;canvas.drawColor(NativeUi.BG);
        paint.setColor(accent);canvas.drawRect(0,0,720,square?88:116,paint);block(canvas,"MANCHAPP",30,22,360,30,Color.WHITE,ui.heavy,1);block(canvas,settings.optString("name"),30,square?56:66,650,square?17:22,0xffffe0b4,ui.bold,1);
        if(square)drawSquare(canvas,paint,product,settings,message,style,accent,ui);else drawVertical(canvas,paint,product,settings,message,style,accent,ui);
        return result;
    }
    private static String price(JSONObject product){return java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("es","PE")).format(product.optDouble("price"));}
    private static void photo(Canvas canvas,Paint paint,JSONObject product,RectF frame,NativeUi ui){
        paint.setColor(NativeUi.CARD);canvas.drawRoundRect(frame,25,25,paint);Bitmap image=ImageTools.productPhoto(ui.context,product,960);
        if(image!=null){float padding=frame.height()<140?12:24;float scale=Math.min((frame.width()-padding)/image.getWidth(),(frame.height()-padding)/image.getHeight()),width=image.getWidth()*scale,height=image.getHeight()*scale;canvas.drawBitmap(image,null,new RectF(frame.centerX()-width/2,frame.centerY()-height/2,frame.centerX()+width/2,frame.centerY()+height/2),paint);image.recycle();}
        else{DishArt art=new DishArt(product.optString("illustration","papa"));art.setBounds((int)frame.left+15,(int)frame.top+15,(int)frame.right-15,(int)frame.bottom-15);art.draw(canvas);block(canvas,"ILUSTRACIÓN REFERENCIAL",frame.left+16,frame.bottom-26,(int)frame.width()-32,13,NativeUi.INK,ui.bold,1);}
    }
    private static void drawVertical(Canvas canvas,Paint paint,JSONObject product,JSONObject settings,String message,int style,int accent,NativeUi ui){
        float photoTop,nameTop,priceTop;
        if(style==1){
            block(canvas,"COMBO PARA COMPARTIR",32,144,650,23,accent,ui.heavy,1);block(canvas,product.optString("name"),32,184,656,42,NativeUi.INK,ui.heavy,2);photoTop=296;nameTop=764;priceTop=846;
        }else if(style==2){
            block(canvas,"NUESTRA PRÓXIMA VENTA",32,142,650,23,accent,ui.heavy,1);block(canvas,SaleSchedule.day(settings),32,181,650,36,NativeUi.INK,ui.heavy,2);photoTop=291;nameTop=749;priceTop=846;
        }else{photoTop=145;nameTop=634;priceTop=822;}
        photo(canvas,paint,product,new RectF(28,photoTop,692,photoTop+(style==0?463:438)),ui);
        if(style==1){block(canvas,product.optString("portion",product.optString("description")),32,nameTop,650,26,NativeUi.INK,ui.bold,2);}
        else{block(canvas,product.optString("name"),32,nameTop,650,style==2?32:42,NativeUi.INK,ui.heavy,2);if(style==0)block(canvas,product.optString("portion"),34,739,650,24,NativeUi.MUTED,ui.bold,2);}
        paint.setColor(accent);canvas.drawRoundRect(28,priceTop-8,360,priceTop+88,19,19,paint);block(canvas,price(product),48,priceTop,292,price(product).length()>9?44:64,Color.WHITE,ui.heavy,1);block(canvas,SaleRules.unit(product.optString("saleUnit",SaleRules.defaultUnit(product.optString("category")))),386,priceTop+20,290,22,NativeUi.INK,ui.bold,2);
        if(style!=2)block(canvas,"VENTA: "+SaleSchedule.day(settings),32,940,650,22,accent,ui.bold,2);else block(canvas,"Reserva con anticipación",32,940,650,25,accent,ui.bold,1);
        block(canvas,message,32,1000,650,23,NativeUi.MUTED,ui.regular,2);
        footer(canvas,paint,settings,1080,false,accent,ui);
    }
    private static void drawSquare(Canvas canvas,Paint paint,JSONObject product,JSONObject settings,String message,int style,int accent,NativeUi ui){
        block(canvas,style==1?"COMBO PARA COMPARTIR":style==2?"VENTA PROGRAMADA":"HECHO EN COMUNIDAD",30,106,650,18,accent,ui.heavy,1);
        photo(canvas,paint,product,new RectF(26,142,444,443),ui);
        paint.setColor(accent);canvas.drawRoundRect(461,156,694,254,17,17,paint);block(canvas,price(product),475,175,205,price(product).length()>9?29:47,Color.WHITE,ui.heavy,1);block(canvas,SaleRules.unit(product.optString("saleUnit",SaleRules.defaultUnit(product.optString("category")))),470,270,216,17,NativeUi.MUTED,ui.bold,2);block(canvas,SaleSchedule.day(settings),470,324,216,22,accent,ui.bold,3);
        block(canvas,product.optString("name"),28,462,660,32,NativeUi.INK,ui.heavy,2);String detail=product.optString("portion");if(detail.isEmpty())detail=message;block(canvas,detail,30,550,650,19,NativeUi.MUTED,ui.bold,1);
        footer(canvas,paint,settings,582,true,accent,ui);
    }
    private static void footer(Canvas canvas,Paint paint,JSONObject settings,int y,boolean square,int accent,NativeUi ui){
        Bitmap qr=ImageTools.decode(settings.optString("paymentImage"));int qrSize=square?98:146,textWidth=qr==null?650:510;int font=square?15:18,gap=square?27:35;
        block(canvas,"RECOJO: "+settings.optString("pickup"),30,y,textWidth,font,NativeUi.INK,ui.bold,2);
        String contact=settings.optString("phone");block(canvas,contact.isEmpty()?"Coordina tu pedido con la olla":"PEDIDOS: "+contact,30,y+gap*2,textWidth,square?18:23,accent,ui.heavy,1);
        block(canvas,"Reservas hasta "+SaleSchedule.deadline(settings),30,y+gap*3,textWidth,font-1,NativeUi.MUTED,ui.regular,1);
        if(!square)block(canvas,SaleSchedule.window(settings).replace('\n',' '),30,y+gap*4,textWidth,16,NativeUi.MUTED,ui.regular,2);
        if(qr!=null){int left=694-qrSize,top=y+4;paint.setColor(Color.WHITE);canvas.drawRoundRect(left-6,top-6,700,top+qrSize+6,12,12,paint);float scale=Math.min((float)qrSize/qr.getWidth(),(float)qrSize/qr.getHeight()),width=qr.getWidth()*scale,height=qr.getHeight()*scale;canvas.drawBitmap(qr,null,new RectF(left+(qrSize-width)/2,top+(qrSize-height)/2,left+(qrSize+width)/2,top+(qrSize+height)/2),paint);qr.recycle();}
    }
}
