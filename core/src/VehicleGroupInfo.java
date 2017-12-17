import model.VehicleType;

import java.util.ArrayList;
import java.util.List;

public class VehicleGroupInfo {
    public long facilityId = -100;

    final VehicleType vehicleType; //just testing
    private MyStrategy myStrategy;
    public int count;
    public Ownership ownership;
    public Point2D moveToPoint;
    public int moveToPointAt;
    public PointsInfo pointsInfo;
    public List<VehicleWrapper> vehicles = new ArrayList<>();
    public int lastShrinkI;
    public int lastShrinkForGatherI;
    public boolean isScaled;
    public boolean isRotated;
    public boolean shouldHeal;
    public int groupNumber;
    public PlainArray potentialMap;
    public int potentialMapCalcAt;
    public FacilityWrapper goToFacility;
    public int switchCount;
    public boolean nextShrinkIsScale;
    public boolean shrinkRotateToRight;
    public int noMoveCount;
    public int shrinkCount;
    public boolean isScaledNuclear;

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
        return moveToPoint != null && myStrategy.um.getMinTimeWithoutUpdates(this) < 2; //TODO check size
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

    public double getHpPercent() {
        int sum = 0;
        for (int i = 0; i < vehicles.size(); i++) {
            VehicleWrapper vehicle = vehicles.get(i);
            sum += vehicle.v.getDurability();
        }
        return sum / (vehicles.size() * myStrategy.getDurability(vehicleType) * 1.f);
    }

    public Point2D getCellAveragePoint() {
        Point2D averagePoint = getAveragePoint();
        return new Point2D((int) (averagePoint.getX() / myStrategy.cellSize), (int) (averagePoint.getY() / myStrategy.cellSize));
    }

    public boolean isAeral() {
        return vehicles.get(0).v.isAerial();
    }

    public Point2D getGoToFacilityPoint() {
        Point2D centerPos = goToFacility.getCenterPos();
        //if (count > 50) {
            centerPos = centerPos.add(16, 16);
        /*} else {        //TOO FAR
            centerPos = centerPos.add(32, 32);
        }*/
        return centerPos;
    }
}
