import model.VehicleType;

import java.util.ArrayList;
import java.util.List;

public class VehicleGroupInfo {
    final VehicleType vehicleType; //just testing
    private MyStrategy myStrategy;
    public int count;
    public Ownership ownership;
    public Point2D moveToPoint;
    public int moveToPointAt;
    public PointsInfo pointsInfo;
    public List<VehicleWrapper> vehicles = new ArrayList<>();
    public int lastShrinkI;
    public boolean isScaled;

    public VehicleGroupInfo(Ownership ownership, VehicleType vehicleType, MyStrategy myStrategy) {
        this.ownership = ownership;
        this.vehicleType = vehicleType;
        this.myStrategy = myStrategy;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append(vehicleType);
        sb.append(", point=").append(getAveragePoint());
        sb.append(", count=").append(count);
        sb.append('}');
        return sb.toString();
    }

    Point2D getAveragePoint() {
        return pointsInfo != null ? pointsInfo.averagePoint : null;
    }

    public boolean isMovingToPoint() {  //TODO calc distance
        return moveToPoint != null && myStrategy.um.getMinTimeWithoutUpdates(this) < 2 ; //TODO check size
    }

    public boolean itsTooBig() {
        return pointsInfo != null && (pointsInfo.rect.getHeight() > 100 || pointsInfo.rect.getWidth() > 100);
    }

    public List<VehicleWrapper> countWillBeFurtherThenBefore(Point2D moveVector, Point2D target) {
        List<VehicleWrapper> o = new ArrayList<>();

        for (int i = 0; i < vehicles.size(); i++) {
            VehicleWrapper vehicle = vehicles.get(i);

            double before = target.getDistanceTo(vehicle) + MyStrategy.GROUP_SIZE;

            Point2D newPos = moveVector.add(vehicle.v.getX(), vehicle.v.getY());
            double after = target.getDistanceTo(newPos);
            if (after > before) {
                o.add(vehicle);
            }

        }
        return o;
    }
}
