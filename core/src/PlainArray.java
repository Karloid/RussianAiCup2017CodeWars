import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;

public class PlainArray {

    private final double[] array;
    public final int cellsWidth;
    public final int cellsHeight;
    private double max = Double.MIN_VALUE;
    private double min = 0;


/*     public static final int PLAIN_SMOOTH = constantId++;
    public static final int SMOTHY_SMOOTH = constantId++;*/

    PlainArray(int cellsWidth, int cellsHeight) {
        this.cellsWidth = cellsWidth;
        this.cellsHeight = cellsHeight;
        array = new double[this.cellsWidth * cellsHeight];
    }

    double get(int x, int y) {
        if (!inBounds(x, y)) {
            return -9999;
        }
        return array[y * cellsWidth + x];
    }

    void add(int x, int y, int val) {
        if (!inBounds(x, y)) {
            return;
        }
        double newValue = array[y * cellsWidth + x] + val;
        array[y * cellsWidth + x] = newValue;

        checkNewValue(newValue);
    }

    private void checkNewValue(double newValue) {
        if (newValue > max) {
            max = newValue;
        }
        if (newValue < min) {
            min = newValue;
        }
    }

    void set(int x, int y, double val) {
        if (!inBounds(x, y)) {
            return;
        }
        array[y * cellsWidth + x] = val;
        checkNewValue(val);
    }

    private boolean inBounds(int x, int y) {
        return !(x < 0 || x >= cellsWidth || y < 0 || y >= cellsHeight);
    }

    public void addRadius(int x, int y, int radius, int val, int smoothType) {
        int startXAt = max(x - radius, 0);
        int endXAt = min(x + radius, cellsWidth - 1);

        int startYAt = max(y - radius, 0);
        int endYAt = min(y + radius, cellsHeight - 1);
        for (int xx = startXAt; xx <= endXAt; xx++) {
            for (int yy = startYAt; yy <= endYAt; yy++) {
                double distance = MyStrategy.distanceTo(x - xx, y - yy);
                if (distance <= radius) {
                    if (smoothType == MyStrategy.PLAIN_SMOOTH) {
                        add(xx, yy, (int) (val * (1 - distance / radius)));
                    } else if (smoothType == MyStrategy.SMOTHY_SMOOTH) {
                        add(xx, yy, (int) (val * max((1 - pow(distance / radius, 2)), 0.25f)));
                    } else {
                        add(xx, yy, val);
                    }
                }
            }
        }
    }

    public double getMax() {
        return max;
    }

    public double getMin() {
        return min;
    }
}
