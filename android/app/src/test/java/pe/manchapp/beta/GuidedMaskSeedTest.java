package pe.manchapp.beta;

import org.junit.Test;
import static org.junit.Assert.*;

public class GuidedMaskSeedTest {
    @Test public void seedGeometryMarksTapAndOuterBand(){
        assertFalse(GuidedMaskSeed.inside(400,400,20,20,0.5f,0.5f));
        assertTrue(GuidedMaskSeed.inside(400,400,200,200,0.5f,0.5f));
        assertTrue(GuidedMaskSeed.outer(400,400,2,200));
        assertFalse(GuidedMaskSeed.outer(400,400,200,200));
    }
}
