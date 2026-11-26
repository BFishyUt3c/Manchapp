package pe.manchapp.beta;

final class MaskCoverage {
    private MaskCoverage(){}
    static boolean needsGuidance(double coverage) {
        return !Double.isFinite(coverage) || coverage < .005 || coverage > .985;
    }
}
