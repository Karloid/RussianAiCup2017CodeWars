import model.Facility;
import model.Vehicle;
import model.VehicleType;
import model.VehicleUpdate;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnitManager {
    private MyStrategy mys;

    final Map<Long, VehicleWrapper> vehicleById = new HashMap<>();
    final Map<Long, FacilityWrapper> facilityById = new HashMap<>();
    private List<VehicleWrapper> deadVehicles = new ArrayList<>();

    TickStats myStats;
    TickStats enemyStats;
    private List<VehicleWrapper> cachedMy;
    private List<VehicleWrapper> cachedEnemy;
    public int maxMyHp;


    public UnitManager(MyStrategy mys) {

        this.mys = mys;
    }

    public void initializeTick() {
        cachedMy = null;
        cachedEnemy = null;

        myStats = new TickStats();
        enemyStats = new TickStats();

        initTickNewVehicles();

        initTickVehUpdates();


        initTickVehCalcStats();
        initTickFacUpdates();


        maxMyHp = streamVehicles(Ownership.ALLY).max(Comparator.comparingInt(value -> value.v.getDurability())).map(vehicleWrapper -> vehicleWrapper.v.getDurability()).orElse(50);

        if (myStats.isNonEmpty()) {
            mys.log("My stats: " + myStats);
        }
        if (enemyStats.isNonEmpty()) {
            mys.log("Enemy stats: " + enemyStats);
        }
    }

    private void initTickFacUpdates() {
        //facilities
        Facility[] facilities = mys.world.getFacilities();
        if (facilityById.isEmpty()) {
            for (Facility facility : facilities) {
                facilityById.put(facility.getId(), new FacilityWrapper(facility, mys));
            }
        } else {
            for (int i = 0; i < facilities.length; i++) {
                Facility facility = facilities[i];
                facilityById.get(facility.getId()).update(facility);
            }
        }
    }

    private void initTickVehCalcStats() {
        Collection<VehicleWrapper> allUnits = vehicleById.values();

        for (Iterator<VehicleWrapper> iterator = allUnits.iterator(); iterator.hasNext(); ) {
            VehicleWrapper u = iterator.next();
            TickStats stats = u.isEnemy ? enemyStats : myStats;

            stats.remainingUnits++;
            stats.remainingHp += u.v.getDurability();
        }
    }

    private void initTickVehUpdates() {
        for (VehicleUpdate vehicleUpdate : mys.world.getVehicleUpdates()) {
            long vehicleId = vehicleUpdate.getId();

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

            if (vehicleUpdate.getDurability() == 0) {
                deadVehicles.add(veh);
                vehicleById.remove(veh.v.getId());
            }
        }
    }

    private void initTickNewVehicles() {
        for (Vehicle vehicle : mys.world.getNewVehicles()) {
            VehicleWrapper mv = new VehicleWrapper(vehicle, mys);
            vehicleById.put(vehicle.getId(), mv);
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

    public int getUnitCount(Ownership ownership) {
        int count = 0;
        for (VehicleWrapper vw : vehicleById.values()) {
            switch (ownership) {
                case ANY:
                    count++;
                    break;
                case ALLY:
                    if (!vw.isEnemy) {
                        count++;
                    }
                    break;
                case ENEMY:
                    if (vw.isEnemy) {
                        count++;
                    }
                    break;
            }
        }
        return count;
    }
}
