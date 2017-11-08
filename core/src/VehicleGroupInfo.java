import model.VehicleType;

public class VehicleGroupInfo {
    final VehicleType vehicleType; //just testing
    private MyStrategy myStrategy;
    public int count;
    public MyStrategy.Ownership ownership;
    public Point2D moveToPoint;
    public int moveToPointAt;
    public PointsInfo pointsInfo;

    public VehicleGroupInfo(MyStrategy.Ownership ownership, VehicleType vehicleType, MyStrategy myStrategy) {
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

    public boolean isMovingToPoint() {
        return moveToPoint != null && (moveToPoint.getDistanceTo(getAveragePoint()) > 20
                || pointsInfo.rect.getWidth() > 53
                || pointsInfo.rect.getHeight() > 53) &&  myStrategy.getMinTimeWithoutUpdates(this) < 120; //TODO check size
    }
}
