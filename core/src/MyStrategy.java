import model.*;

import java.util.*;
import java.util.function.Consumer;

import static model.VehicleType.*;

@SuppressWarnings({"UnsecureRandomNumberGeneration", "FieldCanBeLocal", "unused", "OverlyLongMethod"})
public final class MyStrategy implements Strategy {
    public static final double GROUP_SIZE = 48;
    public static final double GROUP_HALF_SIZE = GROUP_SIZE / 2;
    public static final String NUCLEAR_STRIKE = "NUCLEAR_STIKE";
    private static int constantId;

    public static final int PLAIN_SMOOTH = constantId++;
    public static final int SMOTHY_SMOOTH = constantId++;

    private Random random;

    private TerrainType[][] terrainTypeByCellXY;
    private WeatherType[][] weatherTypeByCellXY;

    Player me;
    World world;
    private Game game;
    private Move move;


    private final Queue<Consumer<Move>> delayedMoves = new ArrayDeque<>();
    private Point2D centerPoint;
    private List<VehicleGroupInfo> enemyGroups;
    private List<VehicleGroupInfo> myGroups;

    UnitManager um = new UnitManager(this);
    private NuclearStrike scheduledStrike;

    @Override
    public void move(Player me, World world, Game game, Move move) {
        initializeTick(me, world, game, move);
        initializeStrategy(world, game);


        doConstantPart();

        if (me.getRemainingActionCooldownTicks() > 0) {
            return;
        }

        if (executeDelayedMove()) {
            delayedMovesSize();
            return;
        }

        oldMove();

        executeDelayedMove();
        delayedMovesSize();
    }

    private void doConstantPart() {
        if (scheduledStrike != null) {
            log(NUCLEAR_STRIKE + " moving of targeting vehicle " + scheduledStrike.myVehicle.getMoveVector());
        }
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

        um.initializeTick();
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
    private void oldMove() {
        // Каждые 300 тиков ...
        // ... для каждого типа техники ...

        if (scheduledStrike != null) {
            if (scheduledStrike.startedAt != -1 && world.getTickIndex() > scheduledStrike.startedAt + 30) {
                log(NUCLEAR_STRIKE + " was done " + scheduledStrike);
                scheduledStrike = null;
            }
        }

        enemyGroups = getGroups(Ownership.ENEMY);
        refreshGroups(myGroups);

        if (me.getRemainingNuclearStrikeCooldownTicks() == 0 && scheduledStrike == null) {
            NuclearStrike max = NuclearStrike.getMaxDmg(this);

            if (max != null && max.dmg > 50) {
                delayedMoves.clear();
                scheduledStrike = max;
                scheduledStrike.scheduledAt = world.getTickIndex();

                delayedMoves.add(move1 -> {
                    clearAndSelectOneUnit(max, move1, max.myVehicle);
                    scheduledStrike.scheduledAt = world.getTickIndex();
                    log(NUCLEAR_STRIKE + " select unit " + max);
                });
                delayedMoves.add(move1 -> {
                    move.setAction(ActionType.MOVE);
                    move.setX(0);
                    move.setY(0);
                    log(NUCLEAR_STRIKE + " stop unit " + max);
                });
                delayedMoves.add(move1 -> {
                    move1.setAction(ActionType.TACTICAL_NUCLEAR_STRIKE);
                    move1.setVehicleId(max.myVehicle.v.getId());

                    max.actualTarget = max.target.getPos(30);
                    double distance = max.actualTarget.getDistanceTo(max.myVehicle);
                    if (distance > max.myVehicle.v.getVisionRange()) {
                        log(NUCLEAR_STRIKE + " correct point from " + max.actualTarget);
                        Point2D vector = Point2D.vector(max.myVehicle.getX(), max.myVehicle.getY(), max.actualTarget.getX(), max.actualTarget.getY());

                        double k = distance / max.myVehicle.v.getVisionRange();
                        max.actualTarget = new Point2D(max.myVehicle.getX(0) + vector.getX() * k, max.myVehicle.getY(0) + vector.getY() * k);
                        log(NUCLEAR_STRIKE + " correct point to " + max.actualTarget);
                    }

                    move1.setX(max.actualTarget.getX());
                    move1.setY(max.actualTarget.getY());


                    max.startedAt = world.getTickIndex();
                    log(NUCLEAR_STRIKE + " start " + max);
                });
                return;
            }

        }


        log("Schedule new moves");

        boolean isArrvMoving = world.getTickIndex() < 200;
        for (VehicleGroupInfo myGroup : myGroups) {

            if (scheduledStrike != null && myGroup.vehicles.contains(scheduledStrike.myVehicle)) {
                log("Skip scheduling for acting group " + myGroup);
                continue;
            }

            if (myGroup.isMovingToPoint()) {
                // log("skip schedule for group myGroup");
                continue;
            }


            if (isArrvMoving && (myGroup.vehicleType == IFV || myGroup.vehicleType == TANK)) {

            /*    VehicleGroupInfo arrvs = findGroup(myGroups, ARRV);
                if (arrvs != null && arrvs.scheduleMoveToPoint != null) {
                    double x = myGroup.getAveragePoint().getX();
                    double y = myGroup.getAveragePoint().getY();
                    if (arrvs.scheduleMoveToPoint.getX() == GROUP_HALF_SIZE) {
                        y = GROUP_HALF_SIZE;
                    } else {
                        x = GROUP_HALF_SIZE;
                    }
                    Point2D point = new Point2D(x, y);
                    selectAll(myGroup.vehicleType);
                    scheduleMoveToPoint(myGroup, point);
                }*/
                continue;
            }
            if (myGroup.vehicleType == HELICOPTER) {
                boolean specialCase = moveToAllyGroup(myGroup, IFV);
                if (specialCase) {
                    continue;
                }
            }

            if (myGroup.vehicleType == FIGHTER) {
                boolean specialCase = false;
                VehicleGroupInfo enHelicopters = findGroup(enemyGroups, HELICOPTER);
                VehicleGroupInfo enIFV = findGroup(enemyGroups, IFV);
                VehicleGroupInfo enFighters = findGroup(enemyGroups, FIGHTER);
                if (enHelicopters != null && enIFV != null
                        && enHelicopters.getAveragePoint().getDistanceTo(enIFV.getAveragePoint()) > GROUP_SIZE * 1.3f) {
                    selectAll(FIGHTER);
                    scheduleMoveToPoint(myGroup, enHelicopters);
                    specialCase = true;
                } else if (world.getTickIndex() > 140 && enFighters != null && enIFV != null
                        && enFighters.getAveragePoint().getDistanceTo(enIFV.getAveragePoint()) > GROUP_SIZE * 1.3f) {
                    selectAll(FIGHTER);
                    scheduleMoveToPoint(myGroup, enFighters);
                    specialCase = true;
                }

                if (!specialCase) {
                    specialCase = moveToAllyGroup(myGroup, TANK);
                }
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
                        y = GROUP_HALF_SIZE;
                    } else {
                        x = GROUP_HALF_SIZE;
                    }
                    selectAll(myGroup.vehicleType);
                    scheduleMoveToPoint(myGroup, new Point2D(x, y));
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

                scheduleMoveToPoint(myGroup, point);
            } else {
                selectAll(myGroup.vehicleType);

                Point2D movePoint = enemyGroup.getAveragePoint();
                movePoint = normalize(movePoint);
                Point2D finalMovePoint = movePoint;
                scheduleMoveToPoint(myGroup, finalMovePoint);
            }
        }

        /*if (true) {
            return;
        }*/
        // Если ни один наш юнит не мог двигаться в течение 60 тиков ...
        //TODO check moveToPointAt for detecting stuck
        if (world.getTickIndex() > 1000 && false) {
            long allUnits = um.streamVehicles(Ownership.ALLY).count();
            float notUpdatedUnits = um.streamVehicles(Ownership.ALLY).filter(vehicle -> world.getTickIndex() - um.get(vehicle.v.getId()).movedAt > 60).count() * 1f;
            if (notUpdatedUnits / allUnits > 0.5) {
                /// ... находим центр нашей формации ...
                log("We stuck " + notUpdatedUnits);
                VehicleGroupInfo group = getGroup(Ownership.ALLY, null);
                delayedMoves.clear();
                selectAll(null);
                scheduleMoveToPoint(group, centerPoint);
            }
        }
    }

    private void clearAndSelectOneUnit(NuclearStrike max, Move move1, VehicleWrapper unit) {
        move1.setAction(ActionType.CLEAR_AND_SELECT);
        move1.setVehicleId(max.myVehicle.v.getId());
        move1.setRight(unit.v.getX() + 2);
        move1.setLeft(unit.v.getX() - 2);

        move1.setTop(unit.v.getY() - 2);
        move1.setBottom(unit.v.getY() + 2);
    }

    private VehicleGroupInfo findGroup(List<VehicleGroupInfo> groups, VehicleType type) {
        for (VehicleGroupInfo g : groups) {
            if (g.vehicleType == type) {
                return g;
            }
        }
        return null;
    }

    private void refreshGroups(List<VehicleGroupInfo> groups) {
        for (VehicleGroupInfo group : groups) {
            VehicleGroupInfo currentState = getGroup(group.ownership, group.vehicleType); //TODO optimize
            group.pointsInfo = currentState.pointsInfo;
            group.count = currentState.count;
            group.vehicles = currentState.vehicles;
        }

        groups.removeIf(vehicleGroupInfo -> vehicleGroupInfo.count == 0);

    }

    private boolean moveToAllyGroup(VehicleGroupInfo myGroup, VehicleType allyType) {
        boolean specialCase = false;
        for (VehicleGroupInfo group : myGroups) {
            if (group.vehicleType == allyType) {
                selectAll(myGroup.vehicleType);
                scheduleMoveToPoint(myGroup, group);
                specialCase = true;
            }
        }
        return specialCase;
    }

    private Point2D normalize(Point2D movePoint) {
        double x = movePoint.getX();
        double y = movePoint.getY();
        double padding = GROUP_HALF_SIZE;
        x = Math.min(x, world.getWidth() - padding);
        x = Math.max(x, padding);

        y = Math.min(y, world.getHeight() - padding);
        y = Math.max(y, padding);
        Point2D p = new Point2D(x, y);
        return p;
    }

    private void scheduleMoveToPoint(VehicleGroupInfo myGroup, VehicleGroupInfo toGroup) {
        delayedMoves.add(move -> {
            actualMoveToPoint(myGroup, toGroup.getAveragePoint(), move);
        });
    }

    private void scheduleMoveToPoint(VehicleGroupInfo myGroup, Point2D point) {
        delayedMoves.add(move -> {
            actualMoveToPoint(myGroup, point, move);
        });
    }

    private void actualMoveToPoint(VehicleGroupInfo myGroup, Point2D point, Move move) {
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
        log("oldMove to point " + p + " group " + myGroup);
        myGroup.moveToPoint = p;
        myGroup.moveToPointAt = world.getTickIndex();
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
        PointsInfo pointsInfo = um.streamVehicles(ownership, vehicleType)
                .map(vehicle -> {
                    info.count++;
                    info.vehicles.add(vehicle); //TODO optimize
                    return new Point2D(vehicle.v.getX(), vehicle.v.getY());
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

    static double distanceTo(int dx, int dy) {
        return Math.sqrt(dx * dx + dy * dy);
    }


}