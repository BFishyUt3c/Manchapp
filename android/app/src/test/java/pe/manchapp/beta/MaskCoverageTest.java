package pe.manchapp.beta;

import org.junit.Test;
import static org.junit.Assert.*;

public class MaskCoverageTest {
    @Test public void almostFullOrEmptyMaskNeedsGuidedMode(){
        assertTrue(MaskCoverage.needsGuidance(0.99));
        assertTrue(MaskCoverage.needsGuidance(0.003));
        assertTrue(MaskCoverage.needsGuidance(Double.NaN));
        assertFalse(MaskCoverage.needsGuidance(0.01));
        assertFalse(MaskCoverage.needsGuidance(0.62));
        assertFalse(MaskCoverage.needsGuidance(0.96));
    }
}
