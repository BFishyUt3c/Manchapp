package pe.manchapp.beta;

final class SaleRules {
    static boolean validPhone(String value) { return value != null && value.matches("9[0-9]{8}"); }
    static double money(double value) { return Math.round(value * 100d) / 100d; }
    static double deliveryFee(double subtotal, double normalFee, double freeFrom) {
        if (!Double.isFinite(subtotal) || subtotal < 0 || !Double.isFinite(normalFee) || normalFee < 0 || !Double.isFinite(freeFrom) || freeFrom < 0) throw new IllegalArgumentException("Revisa los importes del pedido.");
        return freeFrom > 0 && money(subtotal) >= money(freeFrom) ? 0 : money(normalFee);
    }
    static String unit(String code) {
        switch(code) { case "portion": return "por porción"; case "combo": return "por combo"; case "cup": return "por taza"; default: return "por unidad"; }
    }
    static String defaultUnit(String category) {
        switch(category) { case "Combos": return "combo"; case "Postres": case "Menú social": return "portion"; case "Bebidas": return "cup"; default: return "unit"; }
    }
    private SaleRules() {}
}
