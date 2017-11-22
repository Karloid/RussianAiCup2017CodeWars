import model.*;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static model.VehicleType.*;

@SuppressWarnings({"UnsecureRandomNumberGeneration", "FieldCanBeLocal", "unused", "OverlyLongMethod"})
public final class MyStrategy implements Strategy {
    public static final double GROUP_SIZE = 50;
    public static final double GROUP_HALF_SIZE = GROUP_SIZE / 2;
    public static final int MIN_NUCLEAR_DMG = 500;
    public static final List<VehicleType> FIGHTER_PREF_TARGETS = Arrays.asList(HELICOPTER, FIGHTER);
    public static final List<VehicleType> HELI_PREF_TARGETS = Arrays.asList(TANK, ARRV, HELICOPTER, IFV, FIGHTER);
    public static final double SHOULD_HEAL_TRESHOLD = 0.64;
    private static final String SPECIAL_TASK = "SPECIAL_TASK";
    private static int constantId;

    private MyStrategyPainter painter = new EmptyPaintner();

    public static final int PLAIN_SMOOTH = constantId++;
    public static final int SMOTHY_SMOOTH = constantId++;

    public boolean logsEnabled;
    private Random random;

    public TerrainType[][] terrainTypeByCellXY;
    public WeatherType[][] weatherTypeByCellXY;

    Player me;
    World world;
    Game game;
    public Move move;


    private final ArrayDeque<Consumer<Move>> delayedMoves = new ArrayDeque<>();
    private Point2D centerPoint;
    public List<VehicleGroupInfo> enemyGroups;
    public List<VehicleGroupInfo> myGroups;

    UnitManager um = new UnitManager(this);
    private NuclearStrike scheduledStrike;
    public long elapsed;
    List<NuclearStrike> didNuclearStrikes = new ArrayList<>();
    Player opponent;
    public int movesCount;
    public Map<ActionType, Integer> movesStats = new HashMap<>();
    private int handledOpponentNuclear = -1;
    private double enemyNextNuclearStrikeX;
    private double enemyNextNuclearStrikeY;
    public List<VehicleGroupInfo> koverGroups = new ArrayList<>();
    private boolean handledOpponentNuclearShouldScaleDown;
    private int groupNextIndex = 1;


    @Override
    public void move(Player me, World world, Game game, Move move) {
        try {
            long start = System.currentTimeMillis();
            initializeTick(me, world, game, move);
            initializeStrategy(world, game);

            painter.onStartTick();


            doConstantPart();
            //TODO do something with nuclear attacks

            if (handledOpponentNuclearShouldScaleDown && handledOpponentNuclear >= world.getTickIndex()) {
                //wait for strike
            } else if (me.getRemainingActionCooldownTicks() > 0) {
                //nothing
            } else if (executeDelayedMove()) {
                delayedMovesSize();
            } else {
                oldMove();

                executeDelayedMove();
                delayedMovesSize();
            }

            if (move.getAction() != null && move.getAction() != ActionType.NONE) {
                movesCount++;
                movesStats.put(move.getAction(), movesStats.getOrDefault(move.getAction(), 0) + 1);
                printCurrentAction();
            }


            long timeTaken = System.currentTimeMillis() - start;
            elapsed += timeTaken;
            if (timeTaken > 400) {
                log("too much work " + timeTaken);
            }
            if (world.getTickIndex() % 1000 == 0) {
                log("time taken total: " + elapsed);
            }

            if (world.getTickIndex() % 2000 == 0) {
                printNuclearStats();
            }

            painter.onEndTick();
        } catch (Throwable e) {
            e.printStackTrace(); // is bad
            if (logsEnabled) {
                throw new RuntimeException(e);
            }
        }
    }

    private void printCurrentAction() {
        ActionType action = move.getAction();
        String str = "do action " + action;

        if (move.getGroup() != 0) {
            str += " group: " + move.getGroup();
        }
        switch (action) {
            case NONE:
                break;
            case CLEAR_AND_SELECT:
                str += String.format(" select: r %s l %s t %s b %s", move.getRight(), move.getLeft(), move.getTop(), move.getBottom());
                break;
            case ADD_TO_SELECTION:
                str += String.format(" select: r %s l %s t %s b %s", move.getRight(), move.getLeft(), move.getTop(), move.getBottom());
                str += "vehicle type " + move.getVehicleType();
                break;
            case DESELECT:
                str += String.format(" select: r %s l %s t %s b %s", move.getRight(), move.getLeft(), move.getTop(), move.getBottom());
                break;
            case ASSIGN:
                break;
            case DISMISS:
                break;
            case DISBAND:
                break;
            case MOVE:
                str += String.format(" x %s y %s", move.getX(), move.getY());
                break;
            case ROTATE:
                break;
            case SCALE:
                str += String.format(" scale factor: %s", move.getFactor());
                str += String.format(" x %s y %s", move.getX(), move.getY());
                break;
            case SETUP_VEHICLE_PRODUCTION:
                break;
            case TACTICAL_NUCLEAR_STRIKE:
                str += String.format(" x %s y %s", move.getX(), move.getY());
                str += "vehicle " + move.getVehicleId();
                break;
        }

        painter.drawMove();

        log(str);//TODO
    }

    private void printNuclearStats() {
        int succeed = 0;
        int canceled = 0;
        for (NuclearStrike didNuclearStrike : didNuclearStrikes) {
            log(Utils.LOG_NUCLEAR_STRIKE + " did " + didNuclearStrike);
            if (didNuclearStrike.succeed) {
                succeed++;

            }
            if (didNuclearStrike.canceled) {
                canceled++;
            }
        }
        log(Utils.LOG_NUCLEAR_STRIKE + " stats: count: " + didNuclearStrikes.size() + " succeed: " + succeed + " canceled: " + canceled);
    }

    private void doConstantPart() {
        if (scheduledStrike != null) {
            Point2D mv = scheduledStrike.myVehicle.getMoveVector();
            if (mv != VehicleWrapper.NOT_MOVING) {
                log(Utils.LOG_NUCLEAR_STRIKE + "!!! moving targeting unit" + mv);
            }

            log(Utils.LOG_NUCLEAR_STRIKE + String.format(" me.getNextNuclearStrikeTickIndex() %s x %s y %s id %s vehicle is %s ", me.getNextNuclearStrikeTickIndex(),
                    me.getNextNuclearStrikeX(),
                    me.getNextNuclearStrikeY(),
                    me.getNextNuclearStrikeVehicleId(),
                    scheduledStrike.myVehicle.v.getDurability() > 0 ? "live" : "DEAD"));

            if (scheduledStrike.startedAt != -1 && world.getTickIndex() > scheduledStrike.startedAt + game.getTacticalNuclearStrikeDelay()) {


                scheduledStrike.finish();

                scheduledStrike = null;
            }
        }
    }

    private void finishNuclear() {

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

            painter.onInitializeStrategy();
        }
    }

    private void initializeTick(Player me, World world, Game game, Move move) {
        this.me = me;
        this.opponent = world.getOpponentPlayer();
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
        enemyGroups = getGroups(Ownership.ENEMY);
        refreshGroups(myGroups);


        VehicleGroupInfo enHelicopters = findGroup(enemyGroups, HELICOPTER);
        VehicleGroupInfo enIFV = findGroup(enemyGroups, IFV);
        VehicleGroupInfo enFighters = findGroup(enemyGroups, FIGHTER);
        VehicleGroupInfo enArrv = findGroup(enemyGroups, ARRV);
        VehicleGroupInfo enTanks = findGroup(enemyGroups, TANK);


        if (me.getRemainingNuclearStrikeCooldownTicks() == 0 && scheduledStrike == null) {
            int remainingHp = um.enemyStats.remainingHp;
            int minNuclearDmg = (int) Math.min(MyStrategy.MIN_NUCLEAR_DMG, remainingHp * 0.7);


            NuclearStrike max = NuclearStrike.getMaxDmg(this);

            if (max != null && max.predictedDmg > minNuclearDmg) {
                scheduledStrike = max;
                scheduledStrike.createdAt = world.getTickIndex();


                delayedMoves.addFirst(move1 -> {
                    max.actualTarget = max.target.getPos(game.getTacticalNuclearStrikeDelay());
                    double distance = max.actualTarget.getDistanceTo(max.myVehicle);
                    double maxDistance = max.myVehicle.getActualVisionRange(); //TODO calc real vision range
                    if (distance > maxDistance) {
                        log(Utils.LOG_NUCLEAR_STRIKE + " correct point from " + max.actualTarget + " distance is " + max.actualTarget.getDistanceTo(max.myVehicle) + " maxDistance: " + maxDistance);
                        Point2D vector = Point2D.vector(max.myVehicle.getX(), max.myVehicle.getY(), max.actualTarget.getX(), max.actualTarget.getY());

                        double k = maxDistance / distance;
                        max.actualTarget = new Point2D(max.myVehicle.getX(0) + vector.getX() * k, max.myVehicle.getY(0) + vector.getY() * k);
                        log(Utils.LOG_NUCLEAR_STRIKE + " correct point to " + max.actualTarget + " distance is " + max.actualTarget.getDistanceTo(max.myVehicle) + " maxDistance: " + maxDistance);
                    }
                    max.recalcPredicted();
                    if (max.predictedDmg < remainingHp) {
                        log(Utils.LOG_NUCLEAR_STRIKE + " find new target or cancel");
                        scheduledStrike.finish();
                        scheduledStrike.canceled = true;
                        didNuclearStrikes.add(scheduledStrike);
                        scheduledStrike = null;
                    }

                    move1.setAction(ActionType.TACTICAL_NUCLEAR_STRIKE);
                    move1.setVehicleId(max.myVehicle.v.getId());

                    move1.setX(max.actualTarget.getX());
                    move1.setY(max.actualTarget.getY());


                    max.startedAt = world.getTickIndex();
                    log(Utils.LOG_NUCLEAR_STRIKE + " start " + max);
                });

                delayedMoves.addFirst(move1 -> {
                    move.setAction(ActionType.MOVE);
                    move.setX(0);
                    move.setY(0);
                    log(Utils.LOG_NUCLEAR_STRIKE + " stop unit " + max);
                });

                delayedMoves.addFirst(move1 -> {
                    clearAndSelectOneUnit(max, move1, max.myVehicle);  //TODO select group

                    scheduledStrike.createdAt = world.getTickIndex();
                    log(Utils.LOG_NUCLEAR_STRIKE + " select unit " + max);
                });
                return;
            }

        }

        if (opponent.getNextNuclearStrikeTickIndex() != -1 && handledOpponentNuclear != opponent.getNextNuclearStrikeTickIndex()) {
            handledOpponentNuclear = opponent.getNextNuclearStrikeTickIndex();
            enemyNextNuclearStrikeX = opponent.getNextNuclearStrikeX();
            enemyNextNuclearStrikeY = opponent.getNextNuclearStrikeY();

            delayedMoves.addFirst(move1 -> {
                move1.setAction(ActionType.SCALE);
                move1.setX(enemyNextNuclearStrikeX);
                move1.setY(enemyNextNuclearStrikeY);
                move1.setFactor(0.1);

                handledOpponentNuclearShouldScaleDown = false;
            });

            delayedMoves.addFirst(move1 -> {
                move1.setAction(ActionType.SCALE);
                move1.setX(enemyNextNuclearStrikeX);
                move1.setY(enemyNextNuclearStrikeY);
                move1.setFactor(10);
                handledOpponentNuclearShouldScaleDown = true;
            });
            delayedMoves.addFirst(move -> {
                move.setAction(ActionType.CLEAR_AND_SELECT);

                move.setRight(enemyNextNuclearStrikeX + game.getTacticalNuclearStrikeRadius());
                move.setLeft(enemyNextNuclearStrikeX - game.getTacticalNuclearStrikeRadius());
                move.setBottom(enemyNextNuclearStrikeY + game.getTacticalNuclearStrikeRadius());
                move.setTop(enemyNextNuclearStrikeY - game.getTacticalNuclearStrikeRadius());
                move.setVehicleType(null);
            });

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

            if (!myGroup.isScaled) {
                myGroup.isScaled = true;
                scheduleSelectAll(myGroup);
                delayedMoves.add(move1 -> {
                    move1.setAction(ActionType.SCALE);
                    move1.setX(myGroup.getAveragePoint().getX());
                    move1.setY(myGroup.getAveragePoint().getY());
                    move1.setFactor(0.3);
                });
                continue;
            }

          /*  if (myGroup.itsTooBig() && world.getTickIndex() - myGroup.lastShrinkI > 400) {
                scheduleShrink(myGroup);
                continue;
            }*/

            // scaleToKover(myGroup);
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
                    scheduleSelectAll(myGroup);
                    scheduleMoveToPoint(myGroup, point);
                }*/
                continue;
            }
            if (myGroup.vehicleType == HELICOPTER) {
                double currentHpPercent = myGroup.getHpPercent();

                VehicleGroupInfo arrvGroup = findGroup(myGroups, ARRV);
                if (arrvGroup != null && currentHpPercent < SHOULD_HEAL_TRESHOLD) {
                    myGroup.shouldHeal = true;
                } else if (arrvGroup == null || currentHpPercent > 0.95) {
                    myGroup.shouldHeal = false;
                }

                if (myGroup.shouldHeal) {
                    moveToAllyGroup(myGroup, ARRV);
                    log(SPECIAL_TASK + " heli going heal");
                    continue;
                }

                if (enFighters == null || enFighters.count / (myGroup.count * 1.f) <= 0.3 /*|| getSmartDistance(myGroup, enFighters) > 400*/) {

                    if (enTanks != null && getSmartDistance(enTanks, enIFV) > 100) {  //attack tanks
                        scheduleSelectAll(myGroup);
                        scheduleMoveToPoint(myGroup, enTanks);
                        log(SPECIAL_TASK + " heli attacks tanks");
                        continue;
                    }

                    if (enArrv != null && getSmartDistance(enArrv, enIFV) > 100) {  //attack arrv
                        scheduleSelectAll(myGroup);
                        scheduleMoveToPoint(myGroup, enArrv);
                        log(SPECIAL_TASK + " heli attacks arrv");
                        continue;
                    }
                }


                //TODO attack enemy if fighters are low

                boolean specialCase = moveToAllyGroup(myGroup, IFV);
                if (specialCase) {
                    continue;
                }
            }

            if (myGroup.vehicleType == FIGHTER) {
                boolean specialCase = false;
                double currentHpPercent = myGroup.getHpPercent();

                VehicleGroupInfo arrvGroup = findGroup(myGroups, ARRV);
                if (arrvGroup != null && currentHpPercent < 0.75) {
                    myGroup.shouldHeal = true;
                } else if (arrvGroup == null || currentHpPercent > 0.95) {
                    myGroup.shouldHeal = false;
                }

                if (myGroup.shouldHeal) {
                    moveToAllyGroup(myGroup, ARRV);
                    log(SPECIAL_TASK + " fighters going heal");
                    continue;
                }


                if (enHelicopters != null && enIFV != null
                        && enHelicopters.getAveragePoint().getDistanceTo(enIFV.getAveragePoint()) > GROUP_SIZE * 1.3f) {
                    scheduleSelectAll(myGroup);
                    scheduleMoveToPoint(myGroup, enHelicopters);
                    specialCase = true;
                    log(SPECIAL_TASK + " fighters attack heli");
                } else if (world.getTickIndex() > 140 && enFighters != null && enIFV != null
                        && enFighters.getAveragePoint().getDistanceTo(enIFV.getAveragePoint()) > GROUP_SIZE * 1.3f) {
                    scheduleSelectAll(myGroup);
                    scheduleMoveToPoint(myGroup, enFighters);
                    specialCase = true;
                    log(SPECIAL_TASK + " fighters attack fighters");
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
                    scheduleSelectAll(myGroup);
                    scheduleMoveToPoint(myGroup, new Point2D(x, y));
                    continue;
                } else {
                    //scheduleSelectAll(myGroup);
                    groups.removeIf(vehicleGroupInfo -> vehicleGroupInfo == myGroup);

                    if (groups.isEmpty()) {
                        scaleToKover(myGroup);
                        return;
                    }
                    VehicleGroupInfo toGroup = null;
                    for (VehicleGroupInfo group : groups) {
                        if (group.vehicleType == TANK || group.vehicleType == IFV) {
                            toGroup = group;
                            break;
                        }
                    }
                    if (toGroup == null) {
                        toGroup = groups.get((int) (groups.size() * random.nextFloat()));
                    }

                    scheduleSelectAll(myGroup);
                    if (toGroup.moveToPoint != null) {
                        // scheduleMoveToPoint(myGroup, new Point2D(toGroup.getAveragePoint().getX() + toGroup.moveToPoint.getX(),
                        //         toGroup.getAveragePoint().getY() + toGroup.moveToPoint.getY()));

                        scheduleMoveToPoint(myGroup, toGroup.moveToPoint);
                    } else {
                        scheduleMoveToPoint(myGroup, toGroup);
                    }
                    continue;
                }
            }

            List<VehicleType> targetType = getPreferredTargetType(myGroup.vehicleType);
            VehicleGroupInfo enemyGroup = priorityFilter(enemyGroups, targetType);
            if (enemyGroup == null) {
              /*  scheduleSelectAll(myGroup);

                Point2D point = this.centerPoint;
                if (myGroup.vehicleType == FIGHTER) {
                    point = new Point2D(world.getWidth(), 0);
                }

                scheduleMoveToPoint(myGroup, point);*/
                scaleToKover(myGroup);
            } else {
                scheduleSelectAll(myGroup);

                scheduleMoveToPoint(myGroup, enemyGroup);
            }
        }

        /*if (true) {
            return;
        }*/
        // Если ни один наш юнит не мог двигаться в течение 60 тиков ...
        //TODO check moveToPointAt for detecting stuck
/*
        if (world.getTickIndex() > 1000 && false) {
            long allUnits = um.streamVehicles(Ownership.ALLY).count();
            float notUpdatedUnits = um.streamVehicles(Ownership.ALLY).filter(vehicle -> world.getTickIndex() - um.get(vehicle.v.getId()).movedAt > 60).count() * 1f;
            if (notUpdatedUnits / allUnits > 0.5) {
                /// ... находим центр нашей формации ...
                log("We stuck " + notUpdatedUnits);
                VehicleGroupInfo group = getGroup(Ownership.ALLY, null);
                delayedMoves.clear();
                scheduleSelectAll(null);
                scheduleMoveToPoint(group, centerPoint);
            }
        }
*/
    }

    private double getSmartDistance(VehicleGroupInfo g1, VehicleGroupInfo g2) {
        if (g2 == null) {
            return 2000;
        }
        return g1.getAveragePoint().getDistanceTo(g2.getAveragePoint());
    }

    private void scaleToKover(VehicleGroupInfo myGroup) {
        Rectangle2D rect = myGroup.pointsInfo.rect;
        if (rect.getWidth() < 200 || rect.getHeight() < 200) {
            scheduleSelectAll(myGroup);
            delayedMoves.add(move1 -> {
                move1.setFactor(10);
                move1.setX(myGroup.getAveragePoint().getX());
                move1.setY(myGroup.getAveragePoint().getY());
            });
        }
    }

    private void scheduleShrink(VehicleGroupInfo myGroup) {
        myGroup.lastShrinkI = world.getTickIndex();
        scheduleSelectAll(myGroup);
        delayedMoves.add(move1 -> {
            myGroup.lastShrinkI = world.getTickIndex();
            move1.setAction(ActionType.SCALE);
            move1.setX(myGroup.getAveragePoint().getX() + 25);
            move1.setY(myGroup.getAveragePoint().getY() - 15);
            move1.setFactor(0.1);
        });
        delayedMoves.add(move1 -> {
            myGroup.lastShrinkI = world.getTickIndex();
            move1.setAction(ActionType.SCALE);
            move1.setX(myGroup.getAveragePoint().getX() - 25);
            move1.setY(myGroup.getAveragePoint().getY() + 15);
            move1.setFactor(0.1);
        });
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
                scheduleSelectAll(myGroup);
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
            Point2D goToPoint = toGroup.getAveragePoint();
            if (!toGroup.vehicles.isEmpty() && toGroup.vehicles.get(0).isEnemy) {
                toGroup.vehicles.sort(Comparator.comparingDouble(o -> myGroup.getAveragePoint().getDistanceTo(o)));
                goToPoint = toGroup.vehicles.get(0).getPos(0);
            }

            actualMoveToPoint(myGroup, goToPoint, move);
        });
    }

    private void scheduleMoveToPoint(VehicleGroupInfo myGroup, Point2D point) {
        delayedMoves.add(move -> {
            actualMoveToPoint(myGroup, point, move);
        });
    }

    private void actualMoveToPoint(VehicleGroupInfo myGroup, Point2D point, Move move) {
        Point2D p = point;
        Point2D myAverage = myGroup.getAveragePoint();
        double distanceTo = myAverage.getDistanceTo(p);
        double maxDistance = 250;
        if (distanceTo > maxDistance) {
            double koeff = maxDistance / distanceTo;
            p = new Point2D((p.getX() - myAverage.getX()) * koeff + myAverage.getX(),
                    (p.getY() - myAverage.getY()) * koeff + myAverage.getY());
        }


        double dx = p.getX() - myAverage.getX();
        double dy = p.getY() - myAverage.getY();

        List<VehicleWrapper> separatedVehicles = myGroup.countWillBeFurtherThenBefore(new Point2D(dx, dy), p);
        if (separatedVehicles.size() > 0) {
            log(Utils.LOG_MOVING + " found " + separatedVehicles.size() + " separated vehicles for " + myGroup);
            move.setAction(ActionType.SCALE);
            move.setX(p.getX());
            move.setY(p.getY());
            move.setFactor(0.1);
        } else {
            move.setAction(ActionType.MOVE);

            move.setX(dx);
            move.setY(dy);
        }
        //TODO look at tanks group
        if (myGroup.vehicleType == IFV) {
            //  move.setMaxSpeed(game.getTankSpeed() * 0.6);
        }
        log("oldMove to point " + p + " group " + myGroup);
        myGroup.moveToPoint = p;
        myGroup.moveToPointAt = world.getTickIndex();
    }

    private void scheduleSelectAll(VehicleGroupInfo groupInfo) {
        if (groupInfo.groupNumber == 0) {
            delayedMoves.add(move -> {
                move.setAction(ActionType.CLEAR_AND_SELECT);
                move.setRight(world.getWidth());
                move.setBottom(world.getHeight());
                move.setVehicleType(groupInfo.vehicleType);
            });

            delayedMoves.add(move -> {
                groupInfo.groupNumber = groupNextIndex;
                groupNextIndex++;

                move.setAction(ActionType.ASSIGN);
                move.setGroup(groupInfo.groupNumber);
            });
        } else {
            boolean isAlreadySelected = true;
            List<VehicleWrapper> selectedUnits = um.streamVehicles(Ownership.ALLY).filter(vehicleWrapper -> vehicleWrapper.v.isSelected()).collect(Collectors.toList());

            if (selectedUnits.size() != groupInfo.vehicles.size()) {
                isAlreadySelected = false;
            }
            List<VehicleWrapper> vehicles = groupInfo.vehicles;
            for (int i = 0; i < vehicles.size(); i++) {
                VehicleWrapper vehicle = vehicles.get(i);
                if (!selectedUnits.contains(vehicle)) {
                    isAlreadySelected = false;
                    break;
                }
            }

            if (isAlreadySelected) {
                log("isAlreadySelected group, skip selection " + groupInfo);
            } else {
                delayedMoves.add(move -> {
                    move.setAction(ActionType.CLEAR_AND_SELECT);
                    move.setGroup(groupInfo.groupNumber);
                });
            }
        }
    }

    void log(String s) {
        if (logsEnabled) {
            System.out.println(world.getTickIndex() + ": " + s);
        }
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
                return FIGHTER_PREF_TARGETS;
            case HELICOPTER:
                return HELI_PREF_TARGETS;
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


    public void setPainter(MyStrategyPainter painter) {
        this.painter = painter;
        painter.setMYS(this);
    }

    public MyStrategyPainter getPainter() {
        return painter;
    }

    public int getDurability(VehicleType vehicleType) {
        switch (vehicleType) {
            case ARRV:
                return game.getArrvDurability();
            case FIGHTER:
                return game.getFighterDurability();
            case HELICOPTER:
                return game.getHelicopterDurability();
            case IFV:
                return game.getIfvDurability();
            case TANK:
                return game.getTankDurability();
        }
        return 100;
    }
}