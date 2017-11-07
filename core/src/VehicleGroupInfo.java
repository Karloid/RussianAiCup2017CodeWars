import model.VehicleType;

public class VehicleGroupInfo {
    final VehicleType vehicleType; //just testing
    public Point2D averagePoint;
    public int count;

    public VehicleGroupInfo(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append(vehicleType);
        sb.append(", point=").append(averagePoint);
        sb.append(", count=").append(count);
        sb.append('}');
        return sb.toString();
    }
}
