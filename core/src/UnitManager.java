import model.Vehicle;
import model.VehicleType;
import model.VehicleUpdate;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnitManager {
    private MyStrategy mys;

    final Map<Long, VehicleWrapper> vehicleById = new HashMap<>();
    private List<VehicleWrapper> deadVehicles = new ArrayList<>();

    TickStats myStats;
    TickStats enemyStats;
    private List<VehicleWrapper> cachedMy;
    private List<VehicleWrapper> cachedEnemy;


    public UnitManager(MyStrategy mys) {

        this.mys = mys;
    }

    public void initializeTick() {
        cachedMy = null;
        cachedEnemy = null;

        myStats = new TickStats();
        enemyStats = new TickStats();

        for (Vehicle vehicle : mys.world.getNewVehicles()) {
            VehicleWrapper mv = new VehicleWrapper(vehicle, mys);
            vehicleById.put(vehicle.getId(), mv);
        }

        for (VehicleUpdate vehicleUpdate : mys.world.getVehicleUpdates()) {
            long vehicleId = vehicleUpdate.getId();

            if (vehicleUpdate.getDurability() == 0) {
                VehicleWrapper deadVehicle = vehicleById.get(vehicleId);
                deadVehicles.add(deadVehicle);
                vehicleById.remove(deadVehicle.v.getId());
            } else {
                VehicleWrapper veh = vehicleById.get(vehicleId);
                veh.update(new Vehicle(veh.v, vehicleUpdate));
                if (veh.hpDelta != 0) {
                    TickStats stats = veh.isEnemy ? enemyStats : myStats;
                    if (veh.hpDelta > 0) {
                        stats.healedPoints += veh.hpDelta;
                    } else {
                        stats.damagedPoints += veh.hpDelta;
                        stats.damagedUnits += 1;
                        if (veh.v.getDurability() <= 0) {
                            stats.destroyedUnits += 1;
                        }
                    }
                }
            }
        }


        Collection<VehicleWrapper> allUnits = vehicleById.values();

        for (Iterator<VehicleWrapper> iterator = allUnits.iterator(); iterator.hasNext(); ) {
            VehicleWrapper u = iterator.next();
            TickStats stats = u.isEnemy ? enemyStats : myStats;

            stats.remainingUnits++;
            stats.remainingHp += u.v.getDurability();
        }
        if (myStats.isNonEmpty()) {
            mys.log("My stats: " + myStats);
        }
        if (enemyStats.isNonEmpty()) {
            mys.log("Enemy stats: " + enemyStats);
        }
    }

    public VehicleWrapper get(long id) {
        return vehicleById.get(id);
    }

    Stream<VehicleWrapper> streamVehicles(Ownership ownership, VehicleType vehicleType) {
        Stream<VehicleWrapper> stream = vehicleById.values().stream();

        switch (ownership) {
            case ALLY:
                if (cachedMy == null) {
                    cachedMy = stream.filter(vehicle -> !vehicle.isEnemy).collect(Collectors.toList());
                }
                stream = cachedMy.stream();
                break;
            case ENEMY:
                if (cachedEnemy == null) {
                    cachedEnemy = stream.filter(vehicle -> vehicle.isEnemy).collect(Collectors.toList());
                }
                stream = cachedEnemy.stream();
                break;
            default:
        }

        if (vehicleType != null) {
            stream = stream.filter(vehicle -> vehicle.v.getType() == vehicleType);
        }

        return stream;
    }

    Stream<VehicleWrapper> streamVehicles(Ownership ownership) {
        return streamVehicles(ownership, null);
    }

    Stream<VehicleWrapper> streamVehicles() {
        return streamVehicles(Ownership.ANY);
    }

    public int getMinTimeWithoutUpdates(VehicleGroupInfo vehicleGroupInfo) {
        return vehicleGroupInfo.vehicles.stream()
                .mapToInt(v -> mys.world.getTickIndex() - v.movedAt)
                .min().orElse(0);
    }
}
