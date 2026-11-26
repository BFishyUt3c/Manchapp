package pe.manchapp.beta;

import android.graphics.Bitmap;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.util.Random;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImageToolsTest {
    @Test public void transparentEditedPhotoKeepsAtLeast960px()throws Exception{
        int edge=1200;Bitmap bitmap=Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);
        int[] pixels=new int[edge*edge];Random random=new Random(7);
        for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt();
        bitmap.setPixels(pixels,0,edge,0,0,edge,edge);
        for(int y=0;y<100;y++)for(int x=0;x<100;x++)bitmap.setPixel(x,y,Color.TRANSPARENT);
        for(int y=edge/2-50;y<edge/2+50;y++)for(int x=edge/2-50;x<edge/2+50;x++)bitmap.setPixel(x,y,Color.rgb(200,100,50));
        Bitmap decoded=ImageTools.decode(ImageTools.encodeEdited(bitmap));
        assertNotNull(decoded);
        assertTrue(Math.max(decoded.getWidth(),decoded.getHeight())>=960);
        assertEquals(0,Color.alpha(decoded.getPixel(0,0)));
        assertEquals(255,Color.alpha(decoded.getPixel(decoded.getWidth()/2,decoded.getHeight()/2)));
        decoded.recycle();bitmap.recycle();
    }
}
