package pe.manchapp.beta;

import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import java.io.*;
import java.security.*;
import java.util.Locale;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ModelAssetTest {
    @Test public void bundledU2NetPHasExpectedSizeAndHash()throws Exception{
        Context context=RuntimeEnvironment.getApplication();
        byte[] bytes;
        try(InputStream in=context.getAssets().open("models/u2netp.onnx");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int count;MessageDigest digest=MessageDigest.getInstance("SHA-256");
            while((count=in.read(buffer))!=-1){output.write(buffer,0,count);digest.update(buffer,0,count);}
            bytes=output.toByteArray();StringBuilder actual=new StringBuilder();for(byte value:digest.digest())actual.append(String.format(Locale.US,"%02x",value&255));
            assertEquals("309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8",actual.toString());
        }
        assertEquals(4574861,bytes.length);
    }
}
