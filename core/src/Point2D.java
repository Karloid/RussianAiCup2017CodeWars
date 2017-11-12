import model.Unit;

public final class Point2D {
    private final double x;
    private final double y;
    private final double val;

    public Point2D(int x, int y, double val) {

        this.x = x;
        this.y = y;
        this.val = val;
    }

    @Override
    public String toString() {
        return
                "x=" + Utils.format(x) +
                        ", y=" + Utils.format(y);
    }

    Point2D(double x, double y) {
        this.x = x;
        this.y = y;
        val = 0;
    }

    public double getX() {
        return x;
    }

    public int getIntX() {
        return (int) x;
    }

    public int getIntY() {
        return (int) y;
    }


    public double getY() {
        return y;
    }

    public double getDistanceTo(double x, double y) {
        return getDistance(this.x, this.y, x, y);
    }

    public static double getDistance(Unit unit1, Unit unit2) {
        return getDistance(unit1.getX(), unit1.getY(), unit2.getX(), unit2.getY());
    }

    public static double getDistance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double getDistanceTo(Point2D point) {
        return getDistanceTo(point.x, point.y);
    }

    public double getDistanceTo(Unit unit) {
        return getDistanceTo(unit.getX(), unit.getY());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Point2D point2D = (Point2D) o;

        return Integer.compare(point2D.getIntX(), getIntX()) == 0 && Integer.compare(point2D.getIntY(), getIntY()) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(x);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public static Point2D vector(double fromX, double fromY, double toX, double toY) {
        return new Point2D(toX - fromX, toY - fromY);
    }
}
