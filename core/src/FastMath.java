
public class FastMath {

    private static final double TWO_POW_450 = Double.longBitsToDouble(0x5C10000000000000L);
    private static final double TWO_POW_N450 = Double.longBitsToDouble(0x23D0000000000000L);
    private static final double TWO_POW_750 = Double.longBitsToDouble(0x6ED0000000000000L);
    private static final double TWO_POW_N750 = Double.longBitsToDouble(0x1110000000000000L);
    public static double hypot(double x, double y) {
        x = Math.abs(x);
        y = Math.abs(y);
        if (y < x) {
            double a = x;
            x = y;
            y = a;
        } else if (!(y >= x)) { // Testing if we have some NaN.
            if ((x == Double.POSITIVE_INFINITY) || (y == Double.POSITIVE_INFINITY)) {
                return Double.POSITIVE_INFINITY;
            } else {
                return Double.NaN;
            }
        }
        if (y-x == y) { // x too small to substract from y
            return y;
        } else {
            double factor;
            if (x > TWO_POW_450) { // 2^450 < x < y
                x *= TWO_POW_N750;
                y *= TWO_POW_N750;
                factor = TWO_POW_750;
            } else if (y < TWO_POW_N450) { // x < y < 2^-450
                x *= TWO_POW_750;
                y *= TWO_POW_750;
                factor = TWO_POW_N750;
            } else {
                factor = 1.0;
            }
            return factor * Math.sqrt(x*x+y*y);
        }
    }
}