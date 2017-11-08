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
    private List<VehicleGroupInfo> enemyGroups;
    private List<VehicleGroupInfo> myGroups;

    @Override
    public void move(Player me, World world, Game game, Move move) {
        initializeTick(me, world, game, move);
        initializeStrategy(world, game);

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

            enemyGroups = getGroups(Ownership.ENEMY);
            myGroups = getGroups(Ownership.ALLY);
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

        enemyGroups = getGroups(Ownership.ENEMY);

        refreshGroups(myGroups);

        log("Schedule new moves");

        boolean isArrvMoving = world.getTickIndex() < 200;

        for (VehicleGroupInfo myGroup : myGroups) {

            if (myGroup.isMovingToPoint()) {
                log("skip schedule for group myGroup");
                continue;
            }


            if (isArrvMoving && (myGroup.vehicleType == IFV || myGroup.vehicleType == TANK)) {
                continue;
            }
            if (myGroup.vehicleType == HELICOPTER) {
                boolean specialCase = moveToAllyGroup(myGroup, IFV);
                if (specialCase) {
                    continue;
                }
            }

            if (myGroup.vehicleType == FIGHTER) {
                boolean specialCase = moveToAllyGroup(myGroup, TANK);
                if (specialCase) {
                    continue;
                }
            }


            if (myGroup.vehicleType == ARRV) {
                ArrayList<VehicleGroupInfo> groups = new ArrayList<>(myGroups);
                Collections.sort(groups, Comparator.comparingDouble(value -> value.getAveragePoint().getDistanceTo(new Point2D(0, 0))));
                boolean shouldGo = groups.get(0).vehicleType == ARRV || world.getTickIndex() > 400;
                if (isArrvMoving || !shouldGo) {
                    double x = myGroup.getAveragePoint().getX();
                    double y = myGroup.getAveragePoint().getY();
                    if (x > y) {
                        y = 0;
                    } else {
                        x = 0;
                    }
                    selectAll(myGroup.vehicleType);
                    moveToPoint(myGroup, new Point2D(x, y));
                    continue;
                } else {
                    //selectAll(myGroup.vehicleType);
                    groups.remove(0);
                    moveToAllyGroup(myGroup, groups.get((int) (groups.size() * random.nextFloat())).vehicleType);
                    continue;
                }
            }

            List<VehicleType> targetType = getPreferredTargetType(myGroup.vehicleType);
            VehicleGroupInfo enemyGroup = priorityFilter(enemyGroups, targetType);
            if (enemyGroup == null) {
                selectAll(myGroup.vehicleType);

                Point2D point = this.centerPoint;
                if (myGroup.vehicleType == FIGHTER) {
                    point = new Point2D(world.getWidth(), 0);
                }

                moveToPoint(myGroup, point);
            } else {
                selectAll(myGroup.vehicleType);

                Point2D movePoint = enemyGroup.getAveragePoint();
                movePoint = normalize(movePoint);
                Point2D finalMovePoint = movePoint;
                moveToPoint(myGroup, finalMovePoint);
            }
        }

        /*if (true) {
            return;
        }*/
        // Если ни один наш юнит не мог двигаться в течение 60 тиков ...
        //TODO check moveToPointAt for detecting stuck
        if (world.getTickIndex() > 1000 && false) {
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
    }

    private void refreshGroups(List<VehicleGroupInfo> groups) {
        for (VehicleGroupInfo group : groups) {
            VehicleGroupInfo currentState = getGroup(group.ownership, group.vehicleType);
            group.pointsInfo = currentState.pointsInfo;
            group.count = currentState.count;
        }

        groups.removeIf(vehicleGroupInfo -> vehicleGroupInfo.count == 0);

    }

    private boolean moveToAllyGroup(VehicleGroupInfo myGroup, VehicleType allyType) {
        boolean specialCase = false;
        for (VehicleGroupInfo group : myGroups) {
            if (group.vehicleType == allyType) {
                selectAll(myGroup.vehicleType);
                moveToPoint(myGroup, group.getAveragePoint());
                specialCase = true;
            }
        }
        return specialCase;
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
            Point2D p = point;
            double distanceTo = myGroup.getAveragePoint().getDistanceTo(p);
            double maxDistance = 250;
            if (distanceTo > maxDistance) {
                double koeff = maxDistance / distanceTo;
                p = new Point2D((p.getX() - myGroup.getAveragePoint().getX()) * koeff + myGroup.getAveragePoint().getX(),
                        (p.getY() - myGroup.getAveragePoint().getY()) * koeff + myGroup.getAveragePoint().getY());
            }

            move.setAction(ActionType.MOVE);
            move.setX(p.getX() - myGroup.getAveragePoint().getX());
            move.setY(p.getY() - myGroup.getAveragePoint().getY());
            //TODO look at tanks group
            if (myGroup.vehicleType == IFV) {
                move.setMaxSpeed(game.getTankSpeed() * 0.6);
            }
            log("move to point " + p + " group " + myGroup);
            myGroup.moveToPoint = p;
            myGroup.moveToPointAt = world.getTickIndex();
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
        VehicleGroupInfo info = new VehicleGroupInfo(ownership, vehicleType, this);
        PointsInfo pointsInfo = streamVehicles(ownership, vehicleType)
                .map(vehicle -> {
                    info.count++;
                    return new Point2D(vehicle.getX(), vehicle.getY());
                })
                .collect(Utils.POINT_COLLECTOR);
        info.pointsInfo = pointsInfo;
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

    public int getMinTimeWithoutUpdates(VehicleGroupInfo vehicleGroupInfo) {
        return streamVehicles(vehicleGroupInfo.ownership, vehicleGroupInfo.vehicleType)
                .mapToInt(v -> world.getTickIndex() - updateTickByVehicleId.get(v.getId()))
                .min().orElse(0);
    }


    enum Ownership {
        ANY,
        ALLY,
        ENEMY
    }
}