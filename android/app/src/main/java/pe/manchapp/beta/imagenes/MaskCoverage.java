package pe.manchapp.beta;

final class MaskCoverage {
    private MaskCoverage(){}
    static boolean tooBroad(double coverage){return Double.isNaN(coverage)||coverage>.90||coverage<.02;}
}
