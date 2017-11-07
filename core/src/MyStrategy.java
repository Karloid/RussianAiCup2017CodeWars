import model.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static model.VehicleType.*;

@SuppressWarnings({"UnsecureRandomNumberGeneration", "FieldCanBeLocal", "unused", "OverlyLongMethod"})
public final class MyStrategy implements Strategy {
    private static int constantId;

    public static final int PLAIN_SMOOTH = constantId++;
    public static final int SMOTHY_SMOOTH = constantId++;

    private Random random;

    private TerrainType[][] terrainTypeByCellXY;
    private WeatherType[][] weatherTypeByCellXY;

    private Player me;
    private World world;
    private Game game;
    private Move move;

    private final Map<Long, Vehicle> vehicleById = new HashMap<>();
    private final Map<Long, Integer> updateTickByVehicleId = new HashMap<>();
    private final Queue<Consumer<Move>> delayedMoves = new ArrayDeque<>();
    private Point2D centerPoint;

    @Override
    public void move(Player me, World world, Game game, Move move) {
        initializeStrategy(world, game);
        initializeTick(me, world, game, move);

        if (me.getRemainingActionCooldownTicks() > 0) {
            return;
        }

        if (executeDelayedMove()) {
            delayedMovesSize();
            return;
        }

        move();

        executeDelayedMove();
        delayedMovesSize();
    }

    private void delayedMovesSize() {
        if (!delayedMoves.isEmpty()) {
            log("Delayed moves: " + delayedMoves.size());
        }
    }

    private void initializeStrategy(World world, Game game) {
        if (random == null) {
            random = new Random(game.getRandomSeed());

            terrainTypeByCellXY = world.getTerrainByCellXY();
            weatherTypeByCellXY = world.getWeatherByCellXY();

            centerPoint = new Point2D(world.getWidth() / 2, world.getHeight() / 2);
        }
    }

    private void initializeTick(Player me, World world, Game game, Move move) {
        this.me = me;
        this.world = world;
        this.game = game;
        this.move = move;

        for (Vehicle vehicle : world.getNewVehicles()) {
            vehicleById.put(vehicle.getId(), vehicle);
            updateTickByVehicleId.put(vehicle.getId(), world.getTickIndex());
        }

        for (VehicleUpdate vehicleUpdate : world.getVehicleUpdates()) {
            long vehicleId = vehicleUpdate.getId();

            if (vehicleUpdate.getDurability() == 0) {
                vehicleById.remove(vehicleId);
                updateTickByVehicleId.remove(vehicleId);
            } else {
                vehicleById.put(vehicleId, new Vehicle(vehicleById.get(vehicleId), vehicleUpdate));
                updateTickByVehicleId.put(vehicleId, world.getTickIndex());
            }
        }
    }

    /**
     * Достаём отложенное действие из очереди и выполняем его.
     *
     * @return Возвращает {@code true}, если и только если отложенное действие было найдено и выполнено.
     */
    private boolean executeDelayedMove() {
        Consumer<Move> delayedMove = delayedMoves.poll();
        if (delayedMove == null) {
            return false;
        }

        delayedMove.accept(move);
        return true;
    }

    /**
     * Основная логика нашей стратегии.
     */
    private void move() {
        // Каждые 300 тиков ...
        // ... для каждого типа техники ...
        log("Schedule new moves");

        List<VehicleGroupInfo> enemyGroups = getGroups(Ownership.ENEMY);
        List<VehicleGroupInfo> myGroups = getGroups(Ownership.ALLY);

        for (VehicleGroupInfo myGroup : myGroups) {
            if (myGroup.vehicleType == HELICOPTER) {
                boolean specialCase = false;
                for (VehicleGroupInfo group : myGroups) {
                    if (group.vehicleType == IFV) {
                        selectAll(HELICOPTER);
                        moveToPoint(myGroup, group.averagePoint);
                        specialCase = true;
                    }
                }
                if (specialCase) {
                    continue;
                }
            }

            List<VehicleType> targetType = getPreferredTargetType(myGroup.vehicleType);
            VehicleGroupInfo enemyGroup = priorityFilter(enemyGroups, targetType);
            if (enemyGroup == null) {
                selectAll(myGroup.vehicleType);

                Point2D point = this.centerPoint;
                moveToPoint(myGroup, point);
            } else {
                selectAll(myGroup.vehicleType);

                Point2D movePoint = enemyGroup.averagePoint;
                movePoint = normalize(movePoint);
                Point2D finalMovePoint = movePoint;
                delayedMoves.add(move -> {
                    move.setAction(ActionType.MOVE);
                    move.setX(finalMovePoint.getX() - myGroup.averagePoint.getX());
                    move.setY(finalMovePoint.getY() - myGroup.averagePoint.getY());
                    log("move to point " + finalMovePoint.toString() + " group " + myGroup + " dist: " +
                            Utils.format(finalMovePoint.getDistanceTo(myGroup.averagePoint)));
                });
            }
        }

        /*if (true) {
            return;
        }*/
        // Если ни один наш юнит не мог двигаться в течение 60 тиков ...
        long allUnits = streamVehicles(Ownership.ALLY).count();
        float notUpdatedUnits = streamVehicles(Ownership.ALLY).filter(vehicle -> world.getTickIndex() - updateTickByVehicleId.get(vehicle.getId()) > 60).count() * 1f;
        if (notUpdatedUnits / allUnits > 0.5) {
            /// ... находим центр нашей формации ...
            log("We stuck " + notUpdatedUnits);
            VehicleGroupInfo group = getGroup(Ownership.ALLY, null);
            delayedMoves.clear();
            selectAll(null);
            moveToPoint(group, centerPoint);
        }
    }

    private Point2D normalize(Point2D movePoint) {
        double x = movePoint.getX();
        double y = movePoint.getY();
        int padding = 100;
        x = Math.min(x, world.getWidth() - padding);
        x = Math.max(x, padding);

        y = Math.min(y, world.getHeight() - padding);
        y = Math.max(y, padding);
        Point2D p = new Point2D(x, y);
        return p;
    }

    private void moveToPoint(VehicleGroupInfo myGroup, Point2D point) {
        delayedMoves.add(move -> {
            move.setAction(ActionType.MOVE);
            move.setX(point.getX() - myGroup.averagePoint.getX());
            move.setY(point.getY() - myGroup.averagePoint.getY());
            log("move to point " + point + " group " + myGroup);
        });
    }

    private boolean selectAll(VehicleType type) {
        return delayedMoves.add(move -> {
            move.setAction(ActionType.CLEAR_AND_SELECT);
            move.setRight(world.getWidth());
            move.setBottom(world.getHeight());
            move.setVehicleType(type);
        });
    }

    private void log(String s) {
        System.out.println(world.getTickIndex() + ": " + s);
    }

    private VehicleGroupInfo priorityFilter(List<VehicleGroupInfo> enemyGroups, List<VehicleType> targetType) {
        for (VehicleType vehicleType : targetType) {
            for (VehicleGroupInfo enemyGroup : enemyGroups) {
                if (enemyGroup.vehicleType == vehicleType) {
                    return enemyGroup;
                }
            }
        }
        return null;
    }

    private List<VehicleGroupInfo> getGroups(Ownership ownership) {
        List<VehicleGroupInfo> groups = new ArrayList<>();

        for (VehicleType vehicleType : values()) {
            VehicleGroupInfo info = getGroup(ownership, vehicleType);
            if (info.count > 0) {
                groups.add(info);
            }
        }
        return groups;
    }

    private VehicleGroupInfo getGroup(Ownership ownership, VehicleType vehicleType) {
        VehicleGroupInfo info = new VehicleGroupInfo(vehicleType);
        Point2D averagePoint = streamVehicles(ownership, vehicleType)
                .map(vehicle -> {
                    info.count++;
                    return new Point2D(vehicle.getX(), vehicle.getY());
                })
                .collect(Utils.POINT_COLLECTOR);
        info.averagePoint = averagePoint;
        return info;
    }

    /**
     * Вспомогательный метод, позволяющий для указанного типа техники получить другой тип техники, такой, что первый
     * наиболее эффективен против второго.
     *
     * @param vehicleType Тип техники.
     * @return Тип техники в качестве приоритетной цели.
     */
    private static List<VehicleType> getPreferredTargetType(VehicleType vehicleType) {
        switch (vehicleType) {
            case FIGHTER:
                return Arrays.asList(HELICOPTER, FIGHTER);
            case HELICOPTER:
                return Arrays.asList(TANK, ARRV, HELICOPTER, IFV, FIGHTER);
            case IFV:
                return Arrays.asList(FIGHTER, HELICOPTER, IFV, ARRV, TANK);
            case TANK:
                return Arrays.asList(IFV, TANK, ARRV, HELICOPTER, FIGHTER);
            default:
                return Collections.emptyList();
        }
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership, VehicleType vehicleType) {
        Stream<Vehicle> stream = vehicleById.values().stream();

        switch (ownership) {
            case ALLY:
                stream = stream.filter(vehicle -> vehicle.getPlayerId() == me.getId());
                break;
            case ENEMY:
                stream = stream.filter(vehicle -> vehicle.getPlayerId() != me.getId());
                break;
            default:
        }

        if (vehicleType != null) {
            stream = stream.filter(vehicle -> vehicle.getType() == vehicleType);
        }

        return stream;
    }

    private Stream<Vehicle> streamVehicles(Ownership ownership) {
        return streamVehicles(ownership, null);
    }

    private Stream<Vehicle> streamVehicles() {
        return streamVehicles(Ownership.ANY);
    }

    static double distanceTo(int xxxx, int yyyy) {
        return StrictMath.hypot(xxxx, yyyy);
    }


    private enum Ownership {
        ANY,
        ALLY,
        ENEMY
    }
}