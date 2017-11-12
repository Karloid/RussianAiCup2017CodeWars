import model.Vehicle;
import model.World;

public class VehicleWrapper {
    public Point2D moveVector;
    public Vehicle v;
    public int updatedAt;
    public int movedAt;

    public VehicleWrapper(Vehicle v, World world) {
        this.v = v;
        setUpdatedAt(world.getTickIndex());
        moveVector = new Point2D(0, 0);
    }

    public void setUpdatedAt(int tickIndex) {
        updatedAt = tickIndex;
        movedAt = tickIndex;
    }

    public void update(Vehicle newVehicle) {
        if (v.getX() != newVehicle.getX() || v.getY() != newVehicle.getY()) {
            moveVector = Point2D.vector(v.getX(), v.getY(), newVehicle.getX(), newVehicle.getY());
        }


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
        sb.append(", XY=").append((int)v.getX()).append(" - ").append((int)v.getY());
        sb.append(", moveVector=").append(moveVector);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append(", movedAt=").append(movedAt);
        sb.append('}');
        return sb.toString();
    }

    public Point2D getPos(int ticks) {
        return new Point2D(v.getX() + moveVector.getX() * ticks, v.getY() + moveVector.getY() * ticks);
    }
}
