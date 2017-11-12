import model.Vehicle;
import model.World;

public class MyVehicle {
    public Point2D moveVector;
    public Vehicle v;
    public int updatedAt;
    public int movedAt;

    public MyVehicle(Vehicle v, World world) {
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
}
