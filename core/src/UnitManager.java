import model.Vehicle;
import model.VehicleType;
import model.VehicleUpdate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class UnitManager {
    private MyStrategy mys;

    final Map<Long, MyVehicle> vehicleById = new HashMap<>();
    private List<MyVehicle> deadVehicles = new ArrayList<>();

    public UnitManager(MyStrategy mys) {

        this.mys = mys;
    }

    public void initializeTick() {
        for (Vehicle vehicle : mys.world.getNewVehicles()) {
            MyVehicle mv = new MyVehicle(vehicle, mys.world);
            vehicleById.put(vehicle.getId(), mv);
        }

        for (VehicleUpdate vehicleUpdate : mys.world.getVehicleUpdates()) {
            long vehicleId = vehicleUpdate.getId();

            if (vehicleUpdate.getDurability() == 0) {
                MyVehicle deadVehicle = vehicleById.get(vehicleId);
                deadVehicles.add(deadVehicle);
            } else {
                MyVehicle myVehicle = vehicleById.get(vehicleId);
                myVehicle.update(new Vehicle(myVehicle.v, vehicleUpdate));
            }
        }
    }

    public MyVehicle get(long id) {
        return vehicleById.get(id);
    }

    Stream<MyVehicle> streamVehicles(Ownership ownership, VehicleType vehicleType) {
        Stream<MyVehicle> stream = vehicleById.values().stream();

        switch (ownership) {
            case ALLY:
                stream = stream.filter(vehicle -> vehicle.v.getPlayerId() == mys.me.getId());
                break;
            case ENEMY:
                stream = stream.filter(vehicle -> vehicle.v.getPlayerId() != mys.me.getId());
                break;
            default:
        }

        if (vehicleType != null) {
            stream = stream.filter(vehicle -> vehicle.v.getType() == vehicleType);
        }

        return stream;
    }

    Stream<MyVehicle> streamVehicles(Ownership ownership) {
        return streamVehicles(ownership, null);
    }

    private Stream<MyVehicle> streamVehicles() {
        return streamVehicles(Ownership.ANY);
    }

    public int getMinTimeWithoutUpdates(VehicleGroupInfo vehicleGroupInfo) {
        return streamVehicles(vehicleGroupInfo.ownership, vehicleGroupInfo.vehicleType)
                .mapToInt(v -> mys.world.getTickIndex() - v.movedAt)
                .min().orElse(0);
    }
}
