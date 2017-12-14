import model.Unit;

public final class Point2D {
    private final double x;
    private final double y;
    private double val;

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

    /*   public static double getDistance(double x1, double y1, double x2, double y2) {
           double dx = x1 - x2;
           double dy = y1 - y2;
           return FastMath.hypot(dx, dy);
       }
   */
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

    public double getDistanceTo(VehicleWrapper myVehicle) {
        return getDistanceTo(myVehicle.v);
    }

    public Point2D add(double x, double y) {
        return new Point2D(this.x + x, this.y + y);
    }

    public Point2D() {
        x = 0;
        y = 0;
    }

    public Point2D(Point2D v) {
        this.x = v.x;
        this.y = v.y;
        this.val = v.val;
    }

    public Point2D(double angle) {
        this.x = Math.cos(angle);
        this.y = Math.sin(angle);
    }

    public Point2D copy() {
        return new Point2D(this);
    }

    public Point2D sub(Point2D v) {
        return new Point2D(x - v.x, y - v.y);
    }

    public Point2D sub(double dx, double dy) {
        return new Point2D(x - dx, y - dy);
    }

    public Point2D mul(double f) {
        return new Point2D(x * f, y * f);
    }

    public double length() {
//        return hypot(x, y);
        return FastMath.hypot(x, y);
    }

    public double distance(Point2D v) {

//        return hypot(x - v.x, y - v.y);
        return FastMath.hypot(x - v.x, y - v.y);
    }

    public double squareDistance(Point2D v) {
        double tx = x - v.x;
        double ty = y - v.y;
        return tx * tx + ty * ty;
    }

    public double squareDistance(double x, double y) {
        double tx = this.x - x;
        double ty = this.y - y;
        return tx * tx + ty * ty;
    }

    public double squareLength() {
        return x * x + y * y;
    }

    public Point2D reverse() {
        return new Point2D(-x, -y);
    }

    public Point2D normalize() {
        double length = this.length();
        if (length == 0.0D) {
            return new Point2D(0, 0);
        } else {
            return new Point2D(x / length, y / length);
        }
    }

    public Point2D length(double length) {
        double currentLength = this.length();
        if (currentLength == 0.0D) {
            throw new IllegalStateException("Can\'t resize zero-width vector.");
        } else {
            return this.mul(length / currentLength);
        }
    }

    public Point2D perpendicular() {
        double a = y;
        return new Point2D(a, -x);
    }

    public double dotProduct(Point2D vector) {
        return x * vector.x + y * vector.y;
    }

    public double angle() {
        return Math.atan2(y, x);
    }

    public boolean nearlyEqual(Point2D potentialIntersectionPoint, double epsilon) {
        return Math.abs(x - potentialIntersectionPoint.x) < epsilon && Math.abs(y - potentialIntersectionPoint.y) < epsilon;
    }

    public Point2D rotate(Point2D angle) {
        double newX = angle.x * x - angle.y * y;
        double newY = angle.y * x + angle.x * y;
        return new Point2D(newX, newY);
    }

    public Point2D rotateBack(Point2D angle) {
        double newX = angle.x * x + angle.y * y;
        double newY = angle.x * y - angle.y * x;
        return new Point2D(newX, newY);
    }

    public Point2D div(double f) {
        return new Point2D(x / f, y / f);
    }

    public double getVal() {
        return val;
    }

    public void setVal(double val) {
        this.val = val;
    }

    public double getDistanceTo(FacilityWrapper fw) {
        return getDistanceTo(fw.getCenterPos());
    }
}
