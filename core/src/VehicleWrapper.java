import model.Vehicle;

public class VehicleWrapper {
    public static final Point2D NOT_MOVING = new Point2D(0, 0);
    public final boolean isEnemy;
    private Point2D moveVector;
    public Vehicle v;
    private final MyStrategy mys;
    public int updatedAt;
    public int movedAt;
    int hpDelta;

    public VehicleWrapper(Vehicle v, MyStrategy mys) {
        this.v = v;
        this.mys = mys;
        setUpdatedAt(mys.world.getTickIndex());
        moveVector = NOT_MOVING;
        isEnemy = mys.me.getId() != v.getPlayerId();
    }

    public void setUpdatedAt(int tickIndex) {
        updatedAt = tickIndex;
        movedAt = tickIndex;
    }

    public void update(Vehicle newVehicle) {
        if (v.getX() != newVehicle.getX() || v.getY() != newVehicle.getY()) {
            moveVector = Point2D.vector(v.getX(), v.getY(), newVehicle.getX(), newVehicle.getY());
        }

        hpDelta = newVehicle.getDurability() - v.getDurability();

        v = newVehicle;
    }

    public double getDistanceTo(VehicleWrapper sec) {
        return Point2D.getDistance(v, sec.v);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("VW{");
        sb.append(", type=").append(v.getType());
        sb.append(", hp=").append(v.getDurability());
        sb.append(", XY=").append((int) v.getX()).append(" - ").append((int) v.getY());
        sb.append(", moveVector=").append(moveVector);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append(", movedAt=").append(movedAt);
        sb.append('}');
        return sb.toString();
    }

    public Point2D getPos(int ticks) {
        return new Point2D(getX(ticks), getY(ticks));
    }

    public double getY(int ticks) {
        Point2D mv = getMoveVector();
            return v.getY() + mv.getY() * ticks;
    }

    public double getX(int ticks) {
        Point2D mv = getMoveVector();
        return v.getX() + mv.getX() * ticks;
    }

    public double getDistanceToPredictTarget(VehicleWrapper target, int tick) {
        return Point2D.getDistance(getX(), getY(), target.getX(tick), target.getY(tick));
    }

    public double getDistanceToPredictBoth(VehicleWrapper target, int tick) {
        return Point2D.getDistance(getX(tick), getY(tick), target.getX(tick), target.getY(tick));
    }

    double getX() {
        return v.getX();
    }

    double getY() {
        return v.getY();
    }

    public Point2D getMoveVector() {
        return movedAt == mys.world.getTickIndex() ? moveVector : NOT_MOVING;
    }
}
