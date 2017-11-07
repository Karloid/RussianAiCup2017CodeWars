import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;

public class PlainArray {

    private final int[] array;
    private final int cellsWidth;
    private final int cellsHeight;


/*     public static final int PLAIN_SMOOTH = constantId++;
    public static final int SMOTHY_SMOOTH = constantId++;*/

    PlainArray(int cellsWidth, int cellsHeight) {
        this.cellsWidth = cellsWidth;
        this.cellsHeight = cellsHeight;
        array = new int[this.cellsWidth * cellsHeight];
    }

    int get(int x, int y) {
        if (!inBounds(x, y)) {
            return 0;
        }
        return array[y * cellsWidth + x];
    }

    void add(int x, int y, int val) {
        if (!inBounds(x, y)) {
            return;
        }
        array[y * cellsWidth + x] += val;
    }

    void set(int x, int y, int val) {
        if (!inBounds(x, y)) {
            return;
        }
        array[y * cellsWidth + x] = val;
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
}
