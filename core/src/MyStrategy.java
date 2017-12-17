import model.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static model.FacilityType.CONTROL_CENTER;
import static model.FacilityType.VEHICLE_FACTORY;
import static model.VehicleType.*;

//TODO repeat game http://russianaicup.ru/game/view/175080 look at stupid moves in top right corners

//TODO dynamic group size
//..if
//TODO reuse group numbers
//TODO group number pool

//TODO с завода 2 ряда танков, 2 ряда хилок, 2 ряда вертолетов

//TODO делить ARRV на две группы

//TODO если в улугу есть враг то не отталкивать от этого угла

//TODO repeat game http://russianaicup.ru/game/view/196079 mb 16 facilities

@SuppressWarnings({"UnsecureRandomNumberGeneration", "FieldCanBeLocal", "unused", "OverlyLongMethod"})
public final class MyStrategy implements Strategy {
    public static final int WORLD_CELL_SIZE = 32;
    public static final double GROUP_SIZE = 50;
    public static final double GROUP_HALF_SIZE = GROUP_SIZE / 2;
    public static final List<VehicleType> FIGHTER_PREF_TARGETS = Arrays.asList(HELICOPTER, FIGHTER);
    public static final List<VehicleType> HELI_PREF_TARGETS = Arrays.asList(TANK, ARRV, HELICOPTER, IFV, FIGHTER);
    public static final double SHOULD_HEAL_TRESHOLD = 0.64;
    private static final String SPECIAL_TASK = "SPECIAL_TASK";
    private static final String WARN = "WARN";
    private static final boolean HELICS_WAIT_FOR_FIGHTES = true;
    public static final int CAN_DISABLE_FEAR_SINCE_TICK = 9300;
    public static final int CAN_DISABLE_FEAR_SINCE_COUNT = 490;
    public static final int MAX_SWITCH_COUNT = 400;
    public static final boolean ARRV_SPLIT_ENABLE = false;
    public static final boolean AERIL_INITIAL_ROTATE = false;
    private static final boolean SCALE_UP_ON_NUCLEAR = false;
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


    public int cellSize = 16;
    public int worldWidth;
    public int worldHeight;
    private Map<VehicleType, Map<Point2D, Integer>> myUnitsCount;
    private Map<VehicleType, Map<Point2D, Integer>> enemyUnitsCount;

    private Map<Long, Map<FacilityType, Map<Point2D, Integer>>> facilitiesCount;
    private final int FACILITY_SIZE_TO_GO = 25;
    private VehicleGroupInfo currentMapGroup;
    private Map<Point2D, Integer> cornersPushers;
    private Map<Point2D, Integer> sidesPushers;
    private boolean isFacilityMode;
    private boolean nextIsTankIfFog = true;
    private boolean allEnInvisible;


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

                potentialMove();

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

    private void potentialMove() {

        refreshGroups(myGroups, true);

        if (tryPickNuclearTarget()) return;  //may work badly ?

        tryEvadeNuclearTarget();

        if (trySetProduction()) return;

        for (VehicleGroupInfo myGroup : myGroups) {

            if (myGroup.isMovingToPoint()) {
                continue;
            }

            if (initialRotate(myGroup)) continue;

            if (initialScale(myGroup)) continue;

            if (scaleOperations(myGroup)) continue;

            if (myGroup.goToFacility != null && myGroup.getAveragePoint().getDistanceTo(myGroup.getGoToFacilityPoint()) < 14) {
                if (!myGroup.goToFacility.isMy() || myGroup.goToFacility.f.getCapturePoints() != game.getMaxFacilityCapturePoints()) {
                    log(Utils.LOG_MOVING + " skip move because of capturing " + myGroup);
                    continue;
                }
            }

            if (true) {
                scheduleSelectAll(myGroup);
                schedulePotentialMoveToPoint(myGroup);
            }
        }
    }

    private boolean scaleOperations(VehicleGroupInfo myGroup) {
        if (!SCALE_UP_ON_NUCLEAR) {
            return false;
        }
        if (opponent.getRemainingNuclearStrikeCooldownTicks() > 50) {
            if (myGroup.isScaledNuclear) {
                myGroup.isScaledNuclear = false;
                scheduleSelectAll(myGroup);
                delayedMoves.add(move1 -> {
                    move1.setAction(ActionType.SCALE);
                    move1.setX(myGroup.getAveragePoint().getX());
                    move1.setY(myGroup.getAveragePoint().getY());
                    move1.setFactor(0.1);
                });
                return true;
            }
        } else if (myGroup.count > 55) {
            if (!myGroup.isScaledNuclear) {
                Point2D myAp = myGroup.getCellAveragePoint();


                Map<VehicleType, Map<Point2D, Integer>> unitsCount = getUnitsCount(true);
                enemyUnitsCount = null;
                myUnitsCount = null;
                for (Map.Entry<VehicleType, Map<Point2D, Integer>> points : unitsCount.entrySet()) {
                    VehicleType key = points.getKey();
                    double visionRange = getVisionRange(key) / cellSize + 3;
                    for (Point2D point2D : points.getValue().keySet()) {
                        if (point2D.getDistanceTo(myAp) < visionRange) {
                            myGroup.isScaledNuclear = true;
                            scheduleSelectAll(myGroup);
                            delayedMoves.add(move1 -> {
                                move1.setAction(ActionType.SCALE);
                                move1.setX(myGroup.getAveragePoint().getX());
                                move1.setY(myGroup.getAveragePoint().getY());
                                move1.setFactor(1.5);
                            });
                            return true;
                        }

                    }

                }
            }
        }

        return false;
    }

    private double getVisionRange(VehicleType key) {
        switch (key) {
            case ARRV:
                return game.getArrvVisionRange();
            case FIGHTER:
                return game.getFighterVisionRange();
            case HELICOPTER:
                return game.getHelicopterVisionRange();
            case IFV:
                return game.getIfvVisionRange();
            case TANK:
                return game.getTankVisionRange();
        }
        return 100;
    }

    private boolean trySetProduction() {
        Collection<FacilityWrapper> values = um.facilityById.values();
        for (FacilityWrapper fw : values) {
            if (fw.shouldSetProduction()) {
                fw.isProductionSet = true;
                setupProduction(fw);
                return true;
            }
        }

        return false;
    }

    private void setupProduction(FacilityWrapper fw) {
        if (!fw.isMy()) {
            log(fw.f.getId() + " is no more captured");
            return;
        }
        delayedMoves.add(m -> {
            //TODO create other types of vehicles
            m.setAction(ActionType.SETUP_VEHICLE_PRODUCTION);
            m.setFacilityId(fw.f.getId());
            VehicleType type = getTypeForProductionByLess();
            if (type == null) {
                type = getTypeForProductionByContraForEnemy();
            }

            if (type == fw.productType) {
                m.setAction(ActionType.NONE);
                return;
            }
            fw.productType = type;
            m.setVehicleType(type);

        });
    }

    private VehicleType getTypeForProductionByLess() {
        Map<VehicleType, Long> allyCounts = um.streamVehicles(Ownership.ALLY)
                .collect(Collectors.groupingBy(o -> o.v.getType(), Collectors.counting()));

        for (VehicleType vehicleType : VehicleType.values()) {
            allyCounts.putIfAbsent(vehicleType, 0L);
        }
        if (allyCounts.get(TANK) <= 100) {
            return TANK;
        }
        Map.Entry<VehicleType, Long> min = Collections.min(allyCounts.entrySet(), (o1, o2) -> {
            int compare = Long.compare(o1.getValue(), o2.getValue());
            if (compare != 0) {
                return compare;
            }

            return o2.getKey().compareTo(o1.getKey());
        });
        if (min.getValue() < FACILITY_SIZE_TO_GO) {
            return min.getKey();
        }

        return null;
    }

    private VehicleType getTypeForProductionByContraForEnemy() {
        VehicleType type = null;
        ArrayList<VehicleGroupInfo> filtered = new ArrayList<>(enemyGroups);
        filtered.removeIf(vehicleGroupInfo -> vehicleGroupInfo.vehicleType == ARRV);
        if (filtered.isEmpty()) {
            type = nextIsTankIfFog ? TANK : IFV;

            nextIsTankIfFog = !nextIsTankIfFog;
            //TODO fix if needed

            type = IFV;

            log(WARN + " enemy not visible empty enemy groups!");
        } else {
            ArrayList<VehicleGroupInfo> filteredTwo = new ArrayList<>(filtered);

            filteredTwo.removeIf(veh -> {
                for (FacilityWrapper fw : um.facilityById.values()) {
                    if (fw.isMy()) {
                        if (fw.productType == IFV && (veh.vehicleType == FIGHTER)) {
                            return true;
                        }

                        if (fw.productType == FIGHTER && veh.vehicleType == FIGHTER) {
                            return true;
                        }

                        if (fw.productType == TANK && veh.vehicleType == IFV) {
                            return true;
                        }

                        if (fw.productType == HELICOPTER && veh.vehicleType == TANK) {
                            return true;
                        }

                    }
                }
                return false;
            });

            if (!filteredTwo.isEmpty()) {
                filtered = filteredTwo;
            }

            VehicleGroupInfo max = Collections.max(filtered, Comparator.comparingInt(o -> o.count));
            //TODO look at our groups
            //TODO carefull pick! look for near by enemies like fighters
            switch (max.vehicleType) {
                case ARRV:
                    type = IFV;
                    break;
                case TANK:
                    type = HELICOPTER;
                    break;
                case FIGHTER:
                    type = Math.random() < 0.34 ? FIGHTER : IFV;
                    break;
                case HELICOPTER:
                    type = FIGHTER;
                    break;
                case IFV:
                    type = TANK;
                    break;
            }
        }
        return type;
    }

    private PlainArray calcMap(VehicleGroupInfo group) { //TODO improve logic at final stages
        PlainArray plainArray = new PlainArray((int) game.getWorldWidth() / cellSize, (int) game.getWorldHeight() / cellSize);

        facilitiesCount = null;
        myUnitsCount = null;
        enemyUnitsCount = null;

        currentMapGroup = group;

        refreshShouldHeal(myGroups);

        boolean ifvShouldHeal = shouldHeal(IFV);
        boolean tankShouldHeal = shouldHeal(TANK);
        boolean heliShouldHeal = shouldHeal(HELICOPTER);
        boolean fighterShouldHeal = shouldHeal(FIGHTER);

        boolean disableFear = isFacilityMode && world.getTickIndex() >= CAN_DISABLE_FEAR_SINCE_TICK && um.getUnitCount(Ownership.ALLY) > CAN_DISABLE_FEAR_SINCE_COUNT;


        int minValForContra = group.count > 12 && isFacilityMode ? 3 : -1;  //TODO disable when no facilities


        Set<Map.Entry<Point2D, Integer>> enemyTanks = getUnitsCount(true).get(TANK).entrySet();
        Set<Map.Entry<Point2D, Integer>> enemyArrvs = getUnitsCount(true).get(ARRV).entrySet();
        Set<Map.Entry<Point2D, Integer>> enemyIfv = getUnitsCount(true).get(IFV).entrySet();

        Set<Map.Entry<Point2D, Integer>> enemyHelics = getUnitsCount(true).get(HELICOPTER).entrySet();
        Set<Map.Entry<Point2D, Integer>> enemyFighters = getUnitsCount(true).get(FIGHTER).entrySet();

        allEnInvisible = enemyTanks.isEmpty() && enemyArrvs.isEmpty() && enemyIfv.isEmpty() && enemyHelics.isEmpty() && enemyFighters.isEmpty();


        if (group.vehicleType == FIGHTER) {

            Map<Point2D, Integer> enFighterAndHelics = new HashMap<>(getUnitsCount(true).get(FIGHTER));
            {

                Map<Point2D, Integer> helics = getUnitsCount(true).get(HELICOPTER);

                for (Map.Entry<Point2D, Integer> entry : helics.entrySet()) {
                    enFighterAndHelics.put(entry.getKey(), enFighterAndHelics.getOrDefault(entry.getValue(), 0) + entry.getValue()); //TODO tune
                }


                Set<Map.Entry<Point2D, Integer>> figAndHelicsSet = enFighterAndHelics.entrySet();


                double range = plainArray.cellsWidth * 1.2;

                addToArray(plainArray, figAndHelicsSet, range, 1.f);


                if (enemyTanks.isEmpty() && enemyArrvs.isEmpty() && enemyIfv.isEmpty() && figAndHelicsSet.isEmpty()) {

                    boolean goToFacilities = true;

                    Set<Map.Entry<Point2D, Integer>> myArrvs = getUnitsCount(false).get(ARRV).entrySet();
                    if (myArrvs.isEmpty()) {
                        addToArrayNotOurFacilities(plainArray, range, 1, group);
                    } else {
                        VehicleGroupInfo maxArrvG = myGroups.stream().filter(g -> g.vehicleType == ARRV).max(Comparator.comparingInt(g -> g.vehicles.size())).orElse(null);

                        HashSet<Map.Entry<Point2D, Integer>> entries = new HashSet<>();
                        if (maxArrvG != null) {
                            entries.add(new AbstractMap.SimpleEntry<>(maxArrvG.getCellAveragePoint(), 1));
                        } else {
                            entries.addAll(myArrvs);
                            for (Map.Entry<Point2D, Integer> en : entries) {
                                en.setValue(1);
                            }
                        }
                        addToArray(plainArray, entries, range, .1f);

                    }

                } else {
                    addToArray(plainArray, enemyIfv, range, .1f);
                    addToArray(plainArray, enemyTanks, range, .1f);
                    addToArray(plainArray, enemyArrvs, range, .1f);

                    if (enemyFighters.isEmpty() && enemyHelics.isEmpty()) {
                        subFromArray(plainArray, enemyTanks, (game.getFighterVisionRange() - 15) / cellSize, 1.4f, -1);
                        subFromArray(plainArray, enemyArrvs, (game.getFighterVisionRange() - 15) / cellSize, 1.4f, -1);
                    } else {
                        subFromArray(plainArray, enemyTanks, (game.getIfvAerialAttackRange() + 10) / cellSize, 1.4f, -1);
                        subFromArray(plainArray, enemyArrvs, (game.getIfvAerialAttackRange() + 10) / cellSize, 1.4f, -1);
                    }
                }
            }


            {
                Set<Map.Entry<Point2D, Integer>> ifvCount = getUnitsCount(true).get(IFV).entrySet();

                double range = (game.getIfvAerialAttackRange() * 2.4) / cellSize;

                int factor = 6;

                if (!ifvCount.isEmpty()) {
                    subFromArray(plainArray, ifvCount, range, factor, -1);
                }
            }

            {
                HashMap<Point2D, Integer> myHelicsMap = new HashMap<>(getUnitsCount(false).get(HELICOPTER));
                for (Map.Entry<Point2D, Integer> entry : myHelicsMap.entrySet()) {

                    Point2D center = entry.getKey();

                    int half = 3;
                    boolean intruders = false;
                    for (int x = center.getIntX() - half; x <= center.getIntX() + half; x++) {
                        for (int y = center.getIntY() - half; y <= center.getIntY() + half; y++) {
                            Integer fighterOrHelicsCount = enFighterAndHelics.get(new Point2D(x, y));
                            if (fighterOrHelicsCount != null && fighterOrHelicsCount > 0) {
                                intruders = true;
                                //entry.setValue(0)
                                break;
                            }
                        }
                    }

                    if (intruders) {
                        myHelicsMap.clear();
                        break;
                    }
                }

                Set<Map.Entry<Point2D, Integer>> myHelic = myHelicsMap.entrySet();

                double range = (GROUP_SIZE * 1.2) / cellSize;

                int factor = 6;

                if (!myHelic.isEmpty()) {
                    subFromArray(plainArray, myHelic, range, factor, -1);
                }

                Set<Map.Entry<Point2D, Integer>> otherFighters = getUnitsCount(false).get(FIGHTER).entrySet();
                if (!otherFighters.isEmpty()) {
                    subFromArray(plainArray, otherFighters, range * .8f, factor, -1);
                }
            }
        }

        if (group.vehicleType == HELICOPTER) {

            {

                double range = plainArray.cellsWidth * 1.2;

                //addToArray(plainArray, tanksAndArrvSet, range, 1.f);

                addToArray(plainArray, enemyTanks, range, 1.f);
                addToArray(plainArray, enemyArrvs, range, 1.f);


                addToArray(plainArray, enemyIfv, range, .1f);
                addToArray(plainArray, enemyHelics, range, .3f);

                if (enemyTanks.isEmpty() && enemyArrvs.isEmpty() && enemyIfv.isEmpty() && enemyHelics.isEmpty()) {

                    Set<Map.Entry<Point2D, Integer>> myIfvs = getUnitsCount(false).get(IFV).entrySet();
                    if (myIfvs.isEmpty()) {
                        addToArrayNotOurFacilities(plainArray, range, 1, group);
                    } else {
                        VehicleGroupInfo maxIfvG = myGroups.stream().filter(g -> g.vehicleType == IFV).max(Comparator.comparingInt(g -> g.vehicles.size())).orElse(null);

                        HashSet<Map.Entry<Point2D, Integer>> entries = new HashSet<>();
                        if (maxIfvG != null) {
                            entries.add(new AbstractMap.SimpleEntry<>(maxIfvG.getCellAveragePoint(), 1));
                        } else {
                            entries.addAll(myIfvs);
                            for (Map.Entry<Point2D, Integer> en : entries) {
                                en.setValue(1);
                            }
                        }
                        addToArray(plainArray, entries, range, .1f);
                    }
                }

                if (!disableFear) {
                    subFromArray(plainArray, enemyIfv, (game.getHelicopterAerialAttackRange() * 2.4) / cellSize, 3.2f, minValForContra);
                    subFromArray(plainArray, enemyHelics, (game.getIfvAerialAttackRange() + 10) / cellSize, 1.4f, minValForContra);
                }

                if (!disableFear && HELICS_WAIT_FOR_FIGHTES) {
                    VehicleGroupInfo myFighters = findGroup(myGroups, FIGHTER);
                    if (myFighters != null && myFighters.count > 30) {
                        double smallestDistance = Double.MAX_VALUE;
                        Point2D ap = new Point2D(myFighters.getAveragePoint().getX() / cellSize, myFighters.getAveragePoint().getY() / cellSize);
                        for (Map.Entry<Point2D, Integer> enemyFighter : enemyFighters) {
                            double distanceTo = enemyFighter.getKey().getDistanceTo(ap);
                            if (distanceTo < smallestDistance) {
                                smallestDistance = distanceTo;
                            }
                        }
                        if (smallestDistance < world.getWidth()) {
                            subFromArray(plainArray, enemyFighters, smallestDistance * 1.1, 1.4f, -1); //TODO NOT TUNED
                        }
                    }
                }

                HashMap<Point2D, Integer> myHelicsMap = new HashMap<>(getUnitsCount(false).get(HELICOPTER));
                //for (Map.Entry<Point2D, Integer> entry : myHelicsMap.entrySet()) {
                Map<Point2D, Integer> enFighters = getUnitsCount(true).get(FIGHTER);

                Point2D center = group.getCellAveragePoint();

                int half = 9;
                boolean intruders = false;
                int count = 0;
                for (int x = center.getIntX() - half; x <= center.getIntX() + half; x++) {
                    for (int y = center.getIntY() - half; y <= center.getIntY() + half; y++) {
                        Integer fighterOrHelicsCount = enFighters.get(new Point2D(x, y));

                        if (fighterOrHelicsCount != null) {
                            count += fighterOrHelicsCount;
                            if (fighterOrHelicsCount > 2) {
                                intruders = true;
                                //entry.setValue(0)
                            }
                        }
                    }

                    if (intruders && count > 0.05 * group.count && opponent.getRemainingNuclearStrikeCooldownTicks() > 150) {
                        Set<Map.Entry<Point2D, Integer>> myIfvs = new HashMap<>(getUnitsCount(false).get(IFV)).entrySet();
                        for (Map.Entry<Point2D, Integer> myIfv : myIfvs) {
                            myIfv.setValue(Math.max(15 - myIfv.getValue(), 1));
                        }
                        addToArray(plainArray, myIfvs, plainArray.cellsWidth * 1.2, 6);
                        break;
                    }
                }
            }


            {
                Set<Map.Entry<Point2D, Integer>> fighters = getUnitsCount(true).get(FIGHTER).entrySet();

                double range = (game.getFighterAerialAttackRange() * 2.8) / cellSize;

                int factor = 6;

                if (!disableFear && !fighters.isEmpty()) {
                    subFromArray(plainArray, fighters, range, factor, minValForContra);
                }

            }

            {
                HashMap<Point2D, Integer> myFightersMap = new HashMap<>(getUnitsCount(false).get(FIGHTER));

                Set<Map.Entry<Point2D, Integer>> myFighters = myFightersMap.entrySet();

                double range = (GROUP_SIZE) / cellSize;

                int factor = 2;

                if (!myFighters.isEmpty()) {
                    subFromArray(plainArray, myFighters, range, factor, -1);
                }

                Set<Map.Entry<Point2D, Integer>> otherHelicopters = getUnitsCount(false).get(HELICOPTER).entrySet();
                if (!otherHelicopters.isEmpty()) {
                    subFromArray(plainArray, otherHelicopters, range, factor, minValForContra);
                }
            }
        }

        if (group.vehicleType == IFV) {
            {

                double range = plainArray.cellsWidth * 1.2;

                //addToArray(plainArray, tanksAndArrvSet, range, 1.f);
                addToArray(plainArray, getUnitsCount(true).get(HELICOPTER).entrySet(), range, .9f);
                addToArray(plainArray, getUnitsCount(true).get(FIGHTER).entrySet(), range, 1.f);
                addToArray(plainArray, getUnitsCount(true).get(ARRV).entrySet(), range, 0.82f);


                addToArrayNotOurFacilities(plainArray, range, 0.88f, group);


                addToArray(plainArray, enemyIfv, range, .4f);
                addToArray(plainArray, enemyTanks, range, .1f);

                if (!disableFear) {
                    subFromArray(plainArray, enemyIfv, (game.getIfvGroundAttackRange() + 20) / cellSize, .3f, minValForContra);
                    subFromArray(plainArray, enemyTanks, (game.getTankGroundAttackRange() * 4.5) / cellSize, 3.2f, minValForContra);
                }

            }

            {
                HashMap<Point2D, Integer> myGroundUnits = new HashMap<>(getUnitsCount(false).get(TANK));

                if (!ifvShouldHeal) {
                    myGroundUnits.putAll(getUnitsCount(false).get(ARRV));
                }

                myGroundUnits.putAll(getUnitsCount(false).get(IFV));
                //TODO fix problems with overlaping?


                Set<Map.Entry<Point2D, Integer>> myGroundUnitsSet = myGroundUnits.entrySet();

                double range = (GROUP_SIZE * 1.3) / cellSize;

                int factor = 2;

                if (!myGroundUnitsSet.isEmpty()) {
                    subFromArray(plainArray, myGroundUnitsSet, range, factor, -1);
                }
            }
        }

        if (group.vehicleType == TANK) {
            {

                double range = plainArray.cellsWidth * 1.2;

                //addToArray(plainArray, tanksAndArrvSet, range, 1.f);
                //targets
                addToArray(plainArray, getUnitsCount(true).get(IFV).entrySet(), range, 1.f);
                addToArray(plainArray, getUnitsCount(true).get(TANK).entrySet(), range, .9f);
                addToArray(plainArray, getUnitsCount(true).get(ARRV).entrySet(), range, 0.8f);


                //if someone closer then my then pass that facility
                //removeFacilityIfSomeoneCloser(group);

                addToArrayNotOurFacilities(plainArray, range, .9f, group);


                //secondary targets
                Set<Map.Entry<Point2D, Integer>> helics = getUnitsCount(true).get(HELICOPTER).entrySet();
                Set<Map.Entry<Point2D, Integer>> fighters = getUnitsCount(true).get(FIGHTER).entrySet();

                addToArray(plainArray, helics, range, .1f);
                addToArray(plainArray, getUnitsCount(true).get(FIGHTER).entrySet(), range, .4f);

                //keep away from secondary targets
                if (!disableFear) {
                    subFromArray(plainArray, helics, (game.getHelicopterGroundAttackRange() * 3) / cellSize, 4.5f, minValForContra);
                }
                subFromArray(plainArray, fighters, (GROUP_SIZE) / cellSize, .4f, -1);

            }

            {   // my units as obstacle
                HashMap<Point2D, Integer> myGroundUnits = new HashMap<>(getUnitsCount(false).get(IFV));

                if (!tankShouldHeal) {
                    myGroundUnits.putAll(getUnitsCount(false).get(ARRV));
                }

                myGroundUnits.putAll(getUnitsCount(false).get(TANK));
                //TODO fix problems with overlaping1?

                Set<Map.Entry<Point2D, Integer>> myGroundUnits2 = myGroundUnits.entrySet();

                double range = (GROUP_SIZE * 1.05) / cellSize;

                int factor = 2;

                if (!myGroundUnits2.isEmpty()) {
                    subFromArray(plainArray, myGroundUnits2, range, factor, -1);
                }
            }
        }

        if (group.vehicleType == ARRV) {

            {

                double range = plainArray.cellsWidth * 1.2;

                //addToArray(plainArray, tanksAndArrvSet, range, 1.f);
                //targets
                if (ifvShouldHeal) {
                    addToArray(plainArray, getUnitsCount(false).get(IFV).entrySet(), range, 1.f);
                }
                if (tankShouldHeal) {
                    addToArray(plainArray, getUnitsCount(false).get(TANK).entrySet(), range, 1f);
                }
              /*  addToArray(plainArray, getUnitsCount(false).get(FIGHTER).entrySet(), range, 0.8f);
                addToArray(plainArray, getUnitsCount(false).get(HELICOPTER).entrySet(), range, 0.8f);*/


                //secondary targets
                Set<Map.Entry<Point2D, Integer>> helics = getUnitsCount(false).get(HELICOPTER).entrySet();
                Set<Map.Entry<Point2D, Integer>> fighters = getUnitsCount(false).get(FIGHTER).entrySet();
                Set<Map.Entry<Point2D, Integer>> tanks = getUnitsCount(false).get(TANK).entrySet();
                Set<Map.Entry<Point2D, Integer>> ifvs = getUnitsCount(false).get(IFV).entrySet();

                addToArray(plainArray, helics, range, .2f);
                addToArray(plainArray, fighters, range, .2f);
                addToArray(plainArray, tanks, range, .3f);
                addToArray(plainArray, ifvs, range, .3f);

                addToArrayNotOurFacilities(plainArray, range, .95f, group);

                //TODO chase enemies

                //keep away from secondary targets
                Set<Map.Entry<Point2D, Integer>> enHel = getUnitsCount(true).get(HELICOPTER).entrySet();
                Set<Map.Entry<Point2D, Integer>> enTanks = getUnitsCount(true).get(TANK).entrySet();
                Set<Map.Entry<Point2D, Integer>> enIfv = getUnitsCount(true).get(IFV).entrySet();
                subFromArray(plainArray, enHel, (game.getHelicopterGroundAttackRange() * 3) / cellSize, 1.5f, -1);
                subFromArray(plainArray, enTanks, (game.getTankGroundAttackRange() * 3) / cellSize, 1.5f, -1);
                subFromArray(plainArray, enIfv, (game.getIfvGroundAttackRange() * 3) / cellSize, 1.5f, -1);
            }

            {   // my units as obstacle
                HashMap<Point2D, Integer> grounUnits = new HashMap<>();

                if (!tankShouldHeal) {
                    grounUnits.putAll(getUnitsCount(false).get(TANK));
                }
                if (!ifvShouldHeal) {
                    grounUnits.putAll(getUnitsCount(false).get(IFV));
                }

                grounUnits.putAll(getUnitsCount(true).get(TANK));
                grounUnits.putAll(getUnitsCount(true).get(IFV));
                grounUnits.putAll(getUnitsCount(true).get(ARRV));

                grounUnits.putAll(getUnitsCount(false).get(ARRV));
                //TODO fix problems with overlaping1?

                Set<Map.Entry<Point2D, Integer>> myGroundUnits2 = grounUnits.entrySet();

                double range = (GROUP_SIZE * 1.3) / cellSize;

                int factor = 2;

                if (!myGroundUnits2.isEmpty()) {
                    subFromArray(plainArray, myGroundUnits2, range, factor, -1);
                }
            }
        }

        { //add negative to corners
            int maxDistanceSquare = 8 * 8;

            int distanceToFac = 6;

            int maxIndex = plainArray.cellsWidth - 1;
            Map<Point2D, Integer> allFacCounts;


            allFacCounts = new HashMap<>();
            for (Map<Point2D, Integer> counts : getFacilitiesCount().get(opponent.getId()).values()) {
                allFacCounts.putAll(counts);
            }

            for (Map<Point2D, Integer> counts : getFacilitiesCount().get(-1L).values()) {
                allFacCounts.putAll(counts);
            }

            if (cornersPushers == null) {
                cornersPushers = new HashMap<>();

                cornersPushers.put(new Point2D(0, 0), 1);
                cornersPushers.put(new Point2D(0, maxIndex), 1);
                cornersPushers.put(new Point2D(maxIndex, 0), 1);
                cornersPushers.put(new Point2D(maxIndex, maxIndex), 1);


            }
            HashMap<Point2D, Integer> cornerPushersFiltered = new HashMap<>(cornersPushers);
            cornerPushersFiltered.keySet().removeIf(corner -> {
                for (Point2D facPoint : allFacCounts.keySet()) {
                    if (facPoint.squareDistance(corner) < maxDistanceSquare) {
                        return true;
                    }
                }
                return false;
            });


            cornerPushersFiltered.keySet().removeIf(corner -> {
                int distanceThreshold = 13 * 13;
                for (Point2D facPoint : getUnitsCount(true).get(ARRV).keySet()) {
                    if (facPoint.squareDistance(corner) < distanceThreshold) {
                        return true;
                    }
                }

                for (Point2D facPoint : getUnitsCount(true).get(HELICOPTER).keySet()) {
                    if (facPoint.squareDistance(corner) < distanceThreshold) {
                        return true;
                    }
                }

                for (Point2D facPoint : getUnitsCount(true).get(FIGHTER).keySet()) {
                    if (facPoint.squareDistance(corner) < distanceThreshold) {
                        return true;
                    }
                }

                for (Point2D facPoint : getUnitsCount(true).get(TANK).keySet()) {
                    if (facPoint.squareDistance(corner) < distanceThreshold) {
                        return true;
                    }
                }
                for (Point2D unit : getUnitsCount(true).get(IFV).keySet()) {
                    if (unit.squareDistance(corner) < distanceThreshold) {
                        return true;
                    }
                }
                return false;
            });


            subFromArray(plainArray, cornerPushersFiltered.entrySet(), 3 * 4, 3, -1);

            if (sidesPushers == null) {
                sidesPushers = new HashMap<>();
                int[] coordinates = {0, maxIndex};
                for (int i = 0; i < plainArray.cellsWidth; i++) {
                    sidesPushers.put(new Point2D(i, 0), 1);
                    sidesPushers.put(new Point2D(i, maxIndex), 1);
                    sidesPushers.put(new Point2D(0, i), 1);
                    sidesPushers.put(new Point2D(maxIndex, i), 1);

                }
            }
            HashMap<Point2D, Integer> sidesPushersFiltered = new HashMap<>(sidesPushers);

            sidesPushersFiltered.keySet().removeIf(side -> {
                for (Point2D facPoint : allFacCounts.keySet()) {
                    if (facPoint.squareDistance(side) < maxDistanceSquare) {
                        return true;
                    }
                }
                return false;
            });


            subFromArray(plainArray, sidesPushersFiltered.entrySet(), 3 * 3, 1, -1);


            //strict {

            for (int x = 0; x < plainArray.cellsWidth; x++) {
                for (int y = 0; y < plainArray.cellsHeight; y++) {

                    if (x == 0 || y == 0 || x == plainArray.cellsWidth - 1 || y == plainArray.cellsHeight - 1) {
                        plainArray.set(x, y, plainArray.get(x, y) - 30);
                    }
                }
            }
        }


        return plainArray;
    }

    private void removeFacilityIfSomeoneCloser(VehicleGroupInfo group) {
        Point2D myCellAvgPoint = group.getCellAveragePoint();

        Map<Long, Map<FacilityType, Map<Point2D, Integer>>> fc = getFacilitiesCount();
        for (Map.Entry<Long, Map<FacilityType, Map<Point2D, Integer>>> longMapEntry : fc.entrySet()) {
            if (longMapEntry.getKey() != me.getId()) {
                for (Map.Entry<FacilityType, Map<Point2D, Integer>> entry : longMapEntry.getValue().entrySet()) {
                    entry.getValue().entrySet()
                            .removeIf(en1 -> {
                                for (VehicleGroupInfo myGroup : myGroups) {
                                    if (myGroup != group && (myGroup.vehicleType == ARRV || myGroup.vehicleType == IFV) && myGroup.getCellAveragePoint()
                                            .getDistanceTo(en1.getKey()) < myCellAvgPoint.getDistanceTo(en1.getKey())) {
                                        Point2D point = en1.getKey(); //TODO mb check its is optimal for this group or not


                                        return true;
                                    }
                                }
                                return false;
                            });
                }
            }
        }


        int countAfter = fc.get(opponent.getId()).get(CONTROL_CENTER).size() +
                fc.get(-1L).get(CONTROL_CENTER).size() +
                fc.get(opponent.getId()).get(VEHICLE_FACTORY).size() +
                fc.get(-1L).get(VEHICLE_FACTORY).size();
        if (countAfter == 0) {
            facilitiesCount = null;
            log("removeFacilityIfSomeoneCloser reseting count due lack of variants " + group);
        }
    }

    private void addToArrayNotOurFacilities(PlainArray plainArray, double range, float factor, VehicleGroupInfo myGroup) {
        //TODO ignore facility if some other ally myGroup is nearby !!
        //TODO stay still when capturing
        float enemyFactor = 1f;
        float controlCenterFactor = 1;

        Map<Long, Map<FacilityType, Map<Point2D, Integer>>> fc = getFacilitiesCount();

        if (myGroup.goToFacility != null && myGroup.goToFacility.isMy() && myGroup.goToFacility.f.getCapturePoints() == game.getMaxFacilityCapturePoints()) {
            myGroup.goToFacility = null;
        }

        // switchGoToFacility(myGroup);

        //if (myGroup.isAeral() || !allEnIn
        //
        //
        //
        // visible) { // or check how far is enemy
        if (myGroup.isAeral()) { // or check how far is enemy

            addToArray(plainArray, fc.get(opponent.getId()).get(CONTROL_CENTER).entrySet(), range, factor * controlCenterFactor * enemyFactor);
            addToArray(plainArray, fc.get(-1L).get(CONTROL_CENTER).entrySet(), range, factor * controlCenterFactor);
            addToArray(plainArray, fc.get(opponent.getId()).get(VEHICLE_FACTORY).entrySet(), range, factor * enemyFactor);
            addToArray(plainArray, fc.get(-1L).get(VEHICLE_FACTORY).entrySet(), range, factor);

            return;
        }


        if (myGroup.goToFacility == null || myGroup.getAveragePoint().getDistanceTo(myGroup.goToFacility) > 64 * 5) {
            Point2D ap = myGroup.getAveragePoint();
            FacilityWrapper closestFree = null;

            closestFree = getClosestFacility(um.facilityById.values().stream().filter(f -> f.f.getOwnerPlayerId() == -1 && f.f.getType() == VEHICLE_FACTORY), myGroup, ap);

            if (closestFree == null) {
                closestFree = getClosestFacility(um.facilityById.values().stream().filter(f -> f.f.getOwnerPlayerId() == opponent.getId() && f.f.getType() == VEHICLE_FACTORY), myGroup, ap);
            }

            if (closestFree == null) {
                closestFree = getClosestFacility(um.facilityById.values().stream().filter(f -> f.isMy() && f.f.getCapturePoints() < game.getMaxFacilityCapturePoints()), myGroup, ap);
            }

            if (closestFree == null) {
                closestFree = getClosestFacility(um.facilityById.values().stream().filter(f -> f.f.getOwnerPlayerId() == -1 && f.f.getType() == CONTROL_CENTER), myGroup, ap);
            }

            if (closestFree == null) {
                closestFree = getClosestFacility(um.facilityById.values().stream().filter(f -> f.f.getOwnerPlayerId() == opponent.getId() && f.f.getType() == CONTROL_CENTER), myGroup, ap);
            }

            if (closestFree != null) {
                myGroup.goToFacility = closestFree;
            }
        }

        switchGoToFacility(myGroup);


        if (myGroup.goToFacility == null) {
            addToArray(plainArray, fc.get(opponent.getId()).get(CONTROL_CENTER).entrySet(), range, factor * controlCenterFactor * enemyFactor);
            addToArray(plainArray, fc.get(-1L).get(CONTROL_CENTER).entrySet(), range, factor * controlCenterFactor);
            addToArray(plainArray, fc.get(opponent.getId()).get(VEHICLE_FACTORY).entrySet(), range, factor * enemyFactor);
            addToArray(plainArray, fc.get(-1L).get(VEHICLE_FACTORY).entrySet(), range, factor);
        } else {
            if (myGroup.goToFacility.f.getType() == VEHICLE_FACTORY) {
                factor = 1.1f;
            }
            HashSet<Map.Entry<Point2D, Integer>> counts = new HashSet<>();
            Point2D cf = myGroup.goToFacility.getCenterCellPos();
            counts.add(new AbstractMap.SimpleEntry<>(cf, 1));
            addToArray(plainArray, counts, range, factor);

            counts.clear();
            // if (myGroup.count > 50) {
            counts.add(new AbstractMap.SimpleEntry<>(cf.add(1, 1), 1));
            // } else {
            //     counts.add(new AbstractMap.SimpleEntry<>(cf.add(2, 2), 1)); //TOO FAR
            // }
            addToArray(plainArray, counts, 2, factor);
        }
    }

    private void switchGoToFacility(VehicleGroupInfo myGroup) {
        if (myGroup.goToFacility != null && myGroup.switchCount < MAX_SWITCH_COUNT) {
            //try to switch

            for (int i = 0; i < 4; i++) {
                for (VehicleGroupInfo other : myGroups) {
                    if (other == myGroup) {
                        continue;
                    }
                    if (other.goToFacility == null) {
                        continue;
                    }

                    if (other.switchCount >= MAX_SWITCH_COUNT) {
                        continue;
                    }
                    FacilityWrapper myF = myGroup.goToFacility;
                    Point2D myAp = myGroup.getAveragePoint();

                    Point2D otherAp = other.getAveragePoint();
                    FacilityWrapper otherF = other.goToFacility;


                    double distanceTo = otherAp.getDistanceTo(otherF);
                    if (distanceTo == 0) {
                        distanceTo = 1;
                    }
                    if (myAp.getDistanceTo(otherF) / distanceTo < 0.78 && distanceTo > 64 && distanceTo < myAp.getDistanceTo(myF) - 32) {
                        myGroup.goToFacility = other.goToFacility;
                        other.goToFacility = null; //TODO mb do not reset
                    }

                   /* boolean f1 = otherAp.getDistanceTo(otherF) > otherAp.getDistanceTo(myF);
                    boolean f2 = myAp.getDistanceTo(myF) > myAp.getDistanceTo(otherAp);

                    double currentDistance = myAp.getDistanceTo(myF) + otherAp.getDistanceTo(otherF);
                    double switchedDistance = myAp.getDistanceTo(otherF) + otherAp.getDistanceTo(myAp);

                    if (currentDistance > switchedDistance * 1.03) {
                        log(" SWITCH GO TO FACILITY " + myGroup + " and " + other);
                        myGroup.goToFacility = otherF;
                        other.goToFacility = myF;
                        other.switchCount++;
                        myGroup.switchCount++;
                    }*/
                }
            }
        }
    }

    private FacilityWrapper getClosestFacility(Stream<FacilityWrapper> facilities, VehicleGroupInfo group, Point2D ap) {
        return facilities.filter(f -> {

            if (f.isMy()) {
                return false;
            }
            for (VehicleGroupInfo groupInfo : myGroups) {
                if (groupInfo != group && groupInfo.goToFacility == f && groupInfo.vehicles.size() > 20) {
                    return false;
                }
            }
            return true;
        }).min(Comparator.comparingDouble(value -> value.getCenterPos().squareDistance(ap))).orElse(null);
    }

    private boolean shouldHeal(VehicleType ifv) {
        VehicleGroupInfo ifvGroup = findGroup(myGroups, ifv);
        return ifvGroup != null && ifvGroup.shouldHeal;
    }

    private void refreshShouldHeal(List<VehicleGroupInfo> groups) {
        for (VehicleGroupInfo group : groups) {
            if (group.vehicleType != ARRV) {
                continue;
            }
            double currentHpPercent = group.getHpPercent();
            if (currentHpPercent < SHOULD_HEAL_TRESHOLD) {
                group.shouldHeal = true;
            } else if (currentHpPercent > 0.95) {
                group.shouldHeal = false;
            }
        }
    }

    private void addToArray(PlainArray plainArray, Set<Map.Entry<Point2D, Integer>> counts, double range, float factor) {
        double squareDelta = range * range; //1.4 - hypot
        for (int x = 0; x < plainArray.cellsWidth; x++) {
            for (int y = 0; y < plainArray.cellsHeight; y++) {

                for (Map.Entry<Point2D, Integer> entry : counts) {
                    float count;
                    count = Math.max(100 - entry.getValue(), 1) * factor;

                    double value = (1 - entry.getKey().squareDistance(x, y) / squareDelta) * count;
                    plainArray.set(x, y, Math.max(plainArray.get(x, y), value));
                }
            }
        }
    }

    /**
     * @param plainArray
     * @param unitsCount
     * @param range      in cells
     * @param factor
     * @param minVal
     */
    private void subFromArray(PlainArray plainArray, Set<Map.Entry<Point2D, Integer>> unitsCount, double range, float factor, float minVal) {
        double squareDelta = range * range;
        for (int x = 0; x < plainArray.cellsWidth; x++) {
            for (int y = 0; y < plainArray.cellsHeight; y++) {

                for (Map.Entry<Point2D, Integer> entry : unitsCount) {
                    float val = entry.getValue();
                    if (val < minVal) {
                        continue;
                    }
                    val = val * factor;
                    double value = (1 - entry.getKey().squareDistance(x, y) / squareDelta) * val;
                    if (value > 1) {
                        plainArray.set(x, y, plainArray.get(x, y) - value);
                    }
                }
            }
        }
    }

    private Map<Long, Map<FacilityType, Map<Point2D, Integer>>> getFacilitiesCount() {
        if (facilitiesCount == null) {
            facilitiesCount = new HashMap<>();

            facilitiesCount.put(me.getId(), new HashMap<>());
            facilitiesCount.put(opponent.getId(), new HashMap<>());
            facilitiesCount.put(-1L, new HashMap<>());

            for (Map<FacilityType, Map<Point2D, Integer>> map : facilitiesCount.values()) {
                map.put(CONTROL_CENTER, new HashMap<>());
                map.put(VEHICLE_FACTORY, new HashMap<>());
            }

            Facility[] facilities = world.getFacilities();
            for (Facility facility : facilities) {
                Point2D key = new Point2D((facility.getLeft() + WORLD_CELL_SIZE) / cellSize,
                        (facility.getTop() + WORLD_CELL_SIZE) / cellSize);

                Map<Point2D, Integer> map = facilitiesCount.get(facility.getOwnerPlayerId()).get(facility.getType());
                map.put(key, map.getOrDefault(key, 0) + 1);
            }
        }


        return facilitiesCount;
    }

    private Map<VehicleType, Map<Point2D, Integer>> getUnitsCount(boolean enemy) {
        if (enemyUnitsCount == null) {
            enemyUnitsCount = new HashMap<>();
            myUnitsCount = new HashMap<>();
            for (VehicleType vehicleType : VehicleType.values()) {   //TODO respect heals, respect movement
                enemyUnitsCount.put(vehicleType, new HashMap<>());
                myUnitsCount.put(vehicleType, new HashMap<>());
            }


            for (VehicleWrapper vehicle : um.vehicleById.values()) {
                if (currentMapGroup != null && currentMapGroup.vehicles.contains(vehicle)) {
                    continue;
                }

                Point2D key = new Point2D(vehicle.getCellX(cellSize), vehicle.getCellY(cellSize));

                Map<VehicleType, Map<Point2D, Integer>> map = vehicle.isEnemy ? enemyUnitsCount : myUnitsCount;
                Map<Point2D, Integer> countMap = map.get(vehicle.v.getType());
                countMap.put(key, countMap.getOrDefault(key, 0) + 1);
            }
        }


        return enemy ? enemyUnitsCount : myUnitsCount;
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

            worldWidth = (int) world.getWidth();
            worldHeight = (int) world.getHeight();
            centerPoint = new Point2D(worldWidth / 2, worldHeight / 2);

            enemyGroups = getGroupsWithoutGeometry(Ownership.ENEMY);
            myGroups = getGroups(Ownership.ALLY);

            painter.onInitializeStrategy();
            isFacilityMode = world.getFacilities().length > 0;
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

    private boolean initialRotate(VehicleGroupInfo myGroup) {
        if (!myGroup.isRotated && myGroup.vehicles.get(0).v.isAerial() && AERIL_INITIAL_ROTATE) {
            myGroup.isRotated = true;
            scheduleSelectAll(myGroup);
            delayedMoves.add(move1 -> {
                move1.setAction(ActionType.ROTATE);
                move1.setX(myGroup.getAveragePoint().getX());
                move1.setY(myGroup.getAveragePoint().getY());
                move1.setAngle(Math.PI / 4);
            });

            delayedMoves.add(move1 -> {
                //pass
            });

            delayedMoves.add(move1 -> {
                //pass
            });

            return true;
        }
        return false;
    }


    private boolean initialScale(VehicleGroupInfo myGroup) {
        if (!myGroup.isScaled) {
            myGroup.isScaled = true;
            scheduleSelectAll(myGroup);
            delayedMoves.add(move1 -> {
                move1.setAction(ActionType.SCALE);
                move1.setX(myGroup.getAveragePoint().getX());
                move1.setY(myGroup.getAveragePoint().getY());
                move1.setFactor(0.3);
            });

            delayedMoves.add(move1 -> {
                //pass
            });

            delayedMoves.add(move1 -> {
                //pass
            });

            return true;
        }
        return false;
    }

    private void tryEvadeNuclearTarget() {
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
    }

    private boolean tryPickNuclearTarget() {
        if (me.getRemainingNuclearStrikeCooldownTicks() == 0 && scheduledStrike == null) {
            int remainingHp = um.enemyStats.remainingHp;

            int minNuclearDmg = world.getTickIndex() > 5_000 ? (int) Math.min(getMinNuclearDmg(), remainingHp * 0.35) : getMinNuclearDmg();

            NuclearStrike max = NuclearStrike.getMaxDmg(this, minNuclearDmg);

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
                    if (max.predictedDmg < minNuclearDmg) {
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

                double startDistance = max.myVehicle.getPos(0).getDistanceTo(max.target);
                double nextTickDistance = max.myVehicle.getPos(1).getDistanceTo(max.target);

                if (nextTickDistance > startDistance) { //stop right there
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
                }
                return true;
            }

        }
        return false;
    }

    public int getMinNuclearDmg() {
        return world.getTickIndex() > 6000 ? 3000 : 3700;
    }

    private double getSmartDistance(VehicleGroupInfo g1, VehicleGroupInfo g2) {
        if (g2 == null) {
            return 2000;
        }
        return g1.getAveragePoint().getDistanceTo(g2.getAveragePoint());
    }

    /*private void scaleToKover(VehicleGroupInfo myGroup) {
        Rectangle2D rect = myGroup.pointsInfo.rect;
        if (rect.getWidth() < 200 || rect.getHeight() < 200) {
            scheduleSelectAll(myGroup);
            delayedMoves.add(move1 -> {
                move1.setFactor(10);
                move1.setX(myGroup.getAveragePoint().getX());
                move1.setY(myGroup.getAveragePoint().getY());
            });
        }
    }*/

    /*private void scheduleShrink(VehicleGroupInfo myGroup) {
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
    }*/

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

    private void refreshGroups(List<VehicleGroupInfo> groups, boolean findNewGroups) {
        for (VehicleGroupInfo group : groups) {
            refreshGroup(group);
        }

        groups.removeIf(vehicleGroupInfo -> vehicleGroupInfo.count == 0);


        if (!findNewGroups) {
            return;
        }

        Collection<VehicleWrapper> freeUnits = um.vehicleById.values().stream()
                .filter(vehicleWrapper -> {

                    if (vehicleWrapper.isEnemy) {
                        return false;
                    }

                    int[] unitGroups = vehicleWrapper.v.getGroups();
                    if (unitGroups != null && unitGroups.length > 0) {
                        return false;
                    }

                    for (VehicleGroupInfo group : groups) {
                        if (group.vehicles.contains(vehicleWrapper)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (freeUnits.isEmpty()) {
            return;
        }

        if (um.facilityById.isEmpty()) {
            log(WARN + " facilities is not initialized!!");
            return;
        }

        List<VehicleGroupInfo> groupsCandidates = new ArrayList<>(0);

        for (VehicleWrapper freeUnit : freeUnits) {
            Collection<FacilityWrapper> values = um.facilityById.values();
            FacilityWrapper min = Collections.min(values, Comparator.comparingDouble(value -> freeUnit.getPos(0).getDistanceTo(value.getCenterPos())));
            long facilityId = min.f.getId();
            VehicleType vehType = freeUnit.v.getType();

            boolean found = false;
            for (VehicleGroupInfo group : groupsCandidates) {
                if (group.facilityId == facilityId && group.vehicleType == vehType) {
                    found = true;
                    group.vehicles.add(freeUnit);
                    break;
                }
            }
            if (!found) {
                VehicleGroupInfo e = new VehicleGroupInfo(Ownership.ALLY, vehType, this);
                e.facilityId = facilityId;
                e.vehicles = new ArrayList<>();
                e.vehicles.add(freeUnit);
                groupsCandidates.add(e);
            }
        }

        for (VehicleGroupInfo gc : groupsCandidates) {
            FacilityWrapper fw = um.facilityById.get(gc.facilityId);
            int sizeToGo = FACILITY_SIZE_TO_GO;
            switch (gc.vehicleType) {
                case ARRV:
                    sizeToGo = 10;
                    break;
                case FIGHTER:
                    sizeToGo = 15;
                    break;
                case HELICOPTER:
                case IFV:
                case TANK:
                    sizeToGo = 32;
                    break;
            }

            if (myGroups.size() > 13) {
                sizeToGo *= 1.8;
            }

            if (gc.vehicles.size() > sizeToGo || !fw.isMy()) {
                refreshGroup(gc);
                groups.add(0, gc);
                setupProduction(fw);

            }
        }
    }

    private void refreshGroup(VehicleGroupInfo group) {
        group.vehicles.removeIf(vehicleWrapper -> vehicleWrapper.v.getDurability() == 0);

        group.count = 0;
        group.pointsInfo = group.vehicles.stream()
                .map(vehicle -> new Point2D(vehicle.v.getX(), vehicle.v.getY()))
                .collect(Utils.POINT_COLLECTOR);

        group.count = group.vehicles.size();
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
        int scheduledAt = world.getTickIndex();
        delayedMoves.add(move -> {
            log("executed move with delay " + (world.getTickIndex() - scheduledAt));
            actualMoveToPoint(myGroup, point, move);
        });
    }

    private void schedulePotentialMoveToPoint(VehicleGroupInfo myGroup) {
        int scheduledAt = world.getTickIndex();
        delayedMoves.add(move -> {
            ArrayList<VehicleGroupInfo> groups = new ArrayList<>();
            groups.add(myGroup);
            refreshGroups(groups, false);
            enemyGroups = getGroupsWithoutGeometry(Ownership.ENEMY);

            if (groups.isEmpty()) {
                log(WARN + "group " + myGroup + " is destroyed");
                return;
            }

            log("executed potential move with delay " + (world.getTickIndex() - scheduledAt));

            myGroup.potentialMap = calcMap(myGroup);
            myGroup.potentialMapCalcAt = world.getTickIndex();


            Point2D averagePoint = myGroup.getAveragePoint();

            averagePoint = averagePoint.div(cellSize);

            int myX = averagePoint.getIntX();
            int myY = averagePoint.getIntY();

            Point2D bestChoice = null;
            int half = 3;
        /*    switch (myGroup.vehicleType) {
                case ARRV:
                    half = 3;
                    break;
                case FIGHTER:
                    half = 4;
                    break;
                case HELICOPTER:
                    half = 4;
                    break;
                case IFV:
                    half = 3;
                    break;
                case TANK:
                    half = 3;
                    break;
            }*/

            long count = um.facilityById.values().stream().filter(v -> v.isMy() && v.f.getType() == CONTROL_CENTER).count();

            if (myGroup.count > 20) {
                half++;
                half++;
            } else if (myGroup.count > 13) {
                half++;
            } else if (count >= 2) {
                half--;
            } else if (myGroups.size() < 5) {
                half--;
            }

            for (int x = myX - half; x <= myX + half; x++) {
                for (int y = myY - half; y <= myY + half; y++) {
                    Point2D currentChoice = new Point2D(x, y);
                    if (Math.ceil(currentChoice.getDistanceTo(myX, myY)) > half + 0.01) {
                        continue;
                    }

                    if (y == myY && x == myX && myGroup.noMoveCount > 10) {
                        continue;
                    }

                    currentChoice.setVal(myGroup.potentialMap.get(x, y));
                    if (true) {
                        if (bestChoice == null || bestChoice.getVal() < currentChoice.getVal()) {
                            //TODO check safety
                            bestChoice = currentChoice;
                        }
                    }
                }
            }

            boolean noMove = bestChoice != null && !bestChoice.equals(new Point2D(myX, myY));
            if (noMove) {
                myGroup.noMoveCount++;
            } else {
                myGroup.noMoveCount = 0;
            }

            if (bestChoice != null && noMove) {
                scheduleSelectAll(myGroup);
                actualMoveToPoint(myGroup, bestChoice.mul(cellSize).add(cellSize / 2, cellSize / 2), move); //TODO scale\rotate when needed
            } else {
                log(WARN + "POTENTIAL BEST CHOICE NOT FOUND");
            }
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

        // List<VehicleWrapper> separatedVehicles = myGroup.countWillBeFurtherThenBefore(new Point2D(dx, dy), p);
        // boolean shouldScale = separatedVehicles.size() > 0;
        boolean shouldScale = Math.sqrt(myGroup.count) * 8 < Math.max(myGroup.pointsInfo.rect.getWidth(), myGroup.pointsInfo.rect.getHeight());

        shouldScale = world.getTickIndex() - myGroup.lastShrinkForGatherI > 350 && shouldScale && myGroup.shrinkCount < 3;
        if (shouldScale) {
            myGroup.lastShrinkForGatherI = world.getTickIndex();

            myGroup.shrinkCount++;
            myGroup.nextShrinkIsScale = !myGroup.nextShrinkIsScale;
            if (myGroup.nextShrinkIsScale) {
                // log(Utils.LOG_MOVING + " found " + separatedVehicles.size() + " separated vehicles for " + myGroup);
                log(Utils.LOG_MOVING + " try SCALE " + myGroup);
                move.setAction(ActionType.SCALE);
                move.setX(myAverage.getX());
                move.setY(myAverage.getY());
                move.setFactor(0.1);
            } else {
                // log(Utils.LOG_MOVING + " found " + separatedVehicles.size() + " separated vehicles for " + myGroup);
                log(Utils.LOG_MOVING + " try ROTATE " + myGroup);
                move.setAction(ActionType.ROTATE);
                move.setX(myAverage.getX());
                move.setY(myAverage.getY());
                move.setAngle((myGroup.shrinkRotateToRight ? Math.PI : -Math.PI) * 0.12f);
                myGroup.shrinkRotateToRight = !myGroup.shrinkRotateToRight;
            }

        } else {
            move.setAction(ActionType.MOVE);

            move.setX(dx);
            move.setY(dy);
        }
        log("oldMove to point " + p + " group " + myGroup);
        myGroup.moveToPoint = p;
        myGroup.moveToPointAt = world.getTickIndex();
    }

    private void scheduleSelectAll(VehicleGroupInfo groupInfo) {
        if (groupInfo.groupNumber == 0) {
            delayedMoves.add(move -> {
                move.setAction(ActionType.CLEAR_AND_SELECT);

                move.setLeft(groupInfo.pointsInfo.rect.getMinX());
                move.setRight(groupInfo.pointsInfo.rect.getMaxX());
                move.setTop(groupInfo.pointsInfo.rect.getMinY());
                move.setBottom(groupInfo.pointsInfo.rect.getMaxY());

                move.setVehicleType(groupInfo.vehicleType);
            });
            //TODO fix possible bug when addFirst breaks order
            if (groupNextIndex != 101) {
                delayedMoves.add(move -> {
                    groupInfo.groupNumber = groupNextIndex;
                    groupNextIndex++;

                    move.setAction(ActionType.ASSIGN);
                    move.setGroup(groupInfo.groupNumber);
                });
            }
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

        for (VehicleType vehicleType : VehicleType.values()) {
            VehicleGroupInfo info1 = new VehicleGroupInfo(ownership, vehicleType, this);
            info1.pointsInfo = um.streamVehicles(ownership, vehicleType)
                    .map(vehicle -> {
                        info1.count++;
                        info1.vehicles.add(vehicle); //TODO optimize
                        return new Point2D(vehicle.v.getX(), vehicle.v.getY());
                    })
                    .collect(Utils.POINT_COLLECTOR);

            if (info1.count > 0) {
                groups.add(info1);
            }
        }

        if (world.getFacilities().length > 6 && ARRV_SPLIT_ENABLE) {
            //TODO split arrvs

            for (VehicleGroupInfo group : groups) {
                if (group.vehicleType == ARRV) {
                    groups.remove(group);

                    {
                        VehicleGroupInfo g1 = new VehicleGroupInfo(ownership, ARRV, this);
                        g1.pointsInfo = um.streamVehicles(ownership, ARRV)
                                .filter(vehicle -> vehicle.v.getX() > group.getAveragePoint().getX())
                                .map(vehicle -> {
                                    g1.count++;
                                    g1.vehicles.add(vehicle); //TODO optimize
                                    return new Point2D(vehicle.v.getX(), vehicle.v.getY());
                                })
                                .collect(Utils.POINT_COLLECTOR);
                        groups.add(g1);
                    }

                    {
                        VehicleGroupInfo g1 = new VehicleGroupInfo(ownership, ARRV, this);
                        g1.pointsInfo = um.streamVehicles(ownership, ARRV)
                                .filter(vehicle -> vehicle.v.getX() <= group.getAveragePoint().getX())
                                .map(vehicle -> {
                                    g1.count++;
                                    g1.vehicles.add(vehicle); //TODO optimize
                                    return new Point2D(vehicle.v.getX(), vehicle.v.getY());
                                })
                                .collect(Utils.POINT_COLLECTOR);
                        groups.add(g1);
                    }

                    break;
                }
            }
        }

        groups.sort((o1, o2) -> o2.vehicleType.compareTo(o1.vehicleType));


        //precalc gotofacilities
        List<VehicleGroupInfo> groundGroups = groups.stream().filter(v -> !v.vehicles.get(0).v.isAerial()).collect(Collectors.toList());
        Collection<FacilityWrapper> values = um.facilityById.values().stream().filter(v -> v.f.getType() == VEHICLE_FACTORY).collect(Collectors.toList());
        if (!values.isEmpty()) {
            GoToFacilitySetup bestSetup = null;

            for (int i = 0; i < 100; i++) {
                ArrayList<FacilityWrapper> fw = new ArrayList<>(values);
                GoToFacilitySetup setup = new GoToFacilitySetup();

                for (VehicleGroupInfo groundGroup : groundGroups) {
                    if (fw.isEmpty()) {
                        continue;
                    }

                    int index = (int) (Math.random() * fw.size());
                    setup.map.put(groundGroup, fw.get(index));
                    fw.remove(index);
                }

                if (bestSetup == null || bestSetup.getSum() > setup.getSum()) {
                    bestSetup = setup;
                }
            }

            for (Map.Entry<VehicleGroupInfo, FacilityWrapper> e : bestSetup.map.entrySet()) {
                e.getKey().goToFacility = e.getValue();
            }
        }


        return groups;
    }

    private List<VehicleGroupInfo> getGroupsWithoutGeometry(Ownership ownership) {
        List<VehicleGroupInfo> groups = new ArrayList<>();

        for (VehicleType vehicleType : VehicleType.values()) {
            VehicleGroupInfo info1 = new VehicleGroupInfo(ownership, vehicleType, this);
            um.streamVehicles(ownership, vehicleType)
                    .forEach(vehicle -> {
                        info1.count++;
                        info1.vehicles.add(vehicle);
                    });

            if (info1.count > 0) {
                groups.add(info1);
            }
        }
        return groups;
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