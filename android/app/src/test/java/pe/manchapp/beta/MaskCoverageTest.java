package pe.manchapp.beta;

import org.junit.Test;
import static org.junit.Assert.*;

public class MaskCoverageTest {
    @Test public void almostFullOrEmptyMaskNeedsGuidedMode(){
        assertTrue(MaskCoverage.tooBroad(0.99));
        assertTrue(MaskCoverage.tooBroad(0.96));
        assertTrue(MaskCoverage.tooBroad(0.91));
        assertTrue(MaskCoverage.tooBroad(0.01));
        assertFalse(MaskCoverage.tooBroad(0.62));
        assertFalse(MaskCoverage.tooBroad(0.88));
    }
}
