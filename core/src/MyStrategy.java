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


    public int cellSize = 16;
    public int worldWidth;
    public int worldHeight;
    private Map<VehicleType, Map<Point2D, Integer>> myUnitsCount;
    private Map<VehicleType, Map<Point2D, Integer>> enemyUnitsCount;


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

        refreshGroups(myGroups);

        if (tryPickNuclearTarget()) return;

        tryEvadeNuclearTarget();


        //TODO calc potentials

        myUnitsCount = null;
        enemyUnitsCount = null;
        for (VehicleGroupInfo myGroup : myGroups) {

            if (myGroup.isMovingToPoint()) {
                continue;
            }

            if (initialScale(myGroup)) continue;


            if (true) {

                myGroup.potentialMap = calcMap(myGroup);
                myGroup.potentialMapCalcAt = world.getTickIndex();


                Point2D averagePoint = myGroup.getAveragePoint();

                averagePoint = averagePoint.div(cellSize);

                int myX = averagePoint.getIntX();
                int myY = averagePoint.getIntY();

                Point2D bestChoice = null;
                int half = 11 / 2;
                for (int x = myX - half; x <= myX + half; x++) {
                    for (int y = myY - half; y <= myY + half; y++) {
                        if (y == myY && x == myX) {
                            continue;
                        }
                        Point2D currentChoice = new Point2D(x, y);
                        if (Math.ceil(currentChoice.getDistanceTo(myX, myY)) > half + 0.01) {
                            continue;
                        }

                        currentChoice.setVal(myGroup.potentialMap.get(x, y));
                        if (bestChoice == null || bestChoice.getVal() < currentChoice.getVal()) {
                            //TODO check safety
                            bestChoice = currentChoice;
                        }
                    }
                }

                if (bestChoice != null) {
                    scheduleSelectAll(myGroup);
                    scheduleMoveToPoint(myGroup, bestChoice.mul(cellSize).add(cellSize / 2, cellSize / 2));
                }
            }
        }
    }

    private PlainArray calcMap(VehicleGroupInfo vehicleGroupInfo) {
        PlainArray plainArray = new PlainArray((int) game.getWorldWidth() / cellSize, (int) game.getWorldHeight() / cellSize);


        refreshShouldHeal(myGroups);

        boolean ifvShouldHeal = shouldHeal(IFV);
        boolean tankShouldHeal = shouldHeal(TANK);
        boolean heliShouldHeal = shouldHeal(HELICOPTER);
        boolean fighterShouldHeal = shouldHeal(FIGHTER);


        if (vehicleGroupInfo.vehicleType == FIGHTER) {

            Map<Point2D, Integer> fighterAndHelics = new HashMap<>(getUnitsCount(true).get(FIGHTER));
            {

                Map<Point2D, Integer> helics = getUnitsCount(true).get(HELICOPTER);

                for (Map.Entry<Point2D, Integer> entry : helics.entrySet()) {
                    fighterAndHelics.put(entry.getKey(), fighterAndHelics.getOrDefault(entry.getValue(), 0) + entry.getValue()); //TODO tune
                }


                Set<Map.Entry<Point2D, Integer>> figAndHelicsSet = fighterAndHelics.entrySet();


                double range = plainArray.cellsWidth * 1.2;

                addToArray(plainArray, figAndHelicsSet, range, 1.f);


                Set<Map.Entry<Point2D, Integer>> enemyTanks = getUnitsCount(true).get(TANK).entrySet();
                Set<Map.Entry<Point2D, Integer>> enemyArrvs = getUnitsCount(true).get(ARRV).entrySet();
                Set<Map.Entry<Point2D, Integer>> enemyIfv = getUnitsCount(true).get(IFV).entrySet();
                addToArray(plainArray, enemyIfv, range, .1f);
                addToArray(plainArray, enemyTanks, range, .1f);
                addToArray(plainArray, enemyArrvs, range, .1f);

                subFromArray(plainArray, enemyTanks, (game.getIfvAerialAttackRange() + 10) / cellSize, 1.4f);
                subFromArray(plainArray, enemyArrvs, (game.getIfvAerialAttackRange() + 10) / cellSize, 1.4f);
            }


            {
                Set<Map.Entry<Point2D, Integer>> ifvCount = getUnitsCount(true).get(IFV).entrySet();

                double range = (game.getIfvAerialAttackRange() * 1.8) / cellSize;

                int factor = 6;

                if (!ifvCount.isEmpty()) {
                    subFromArray(plainArray, ifvCount, range, factor);
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
                            Integer fighterOrHelicsCount = fighterAndHelics.get(new Point2D(x, y));
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

                double range = (GROUP_SIZE * 1.8) / cellSize;

                int factor = 6;

                if (!myHelic.isEmpty()) {
                    subFromArray(plainArray, myHelic, range, factor);
                }
            }
        }

        if (vehicleGroupInfo.vehicleType == HELICOPTER) {

            {

                double range = plainArray.cellsWidth * 1.2;

                //addToArray(plainArray, tanksAndArrvSet, range, 1.f);
                addToArray(plainArray, getUnitsCount(true).get(TANK).entrySet(), range, .8f);
                addToArray(plainArray, getUnitsCount(true).get(ARRV).entrySet(), range, 1.f);


                Set<Map.Entry<Point2D, Integer>> enemyHelics = getUnitsCount(true).get(HELICOPTER).entrySet();
                Set<Map.Entry<Point2D, Integer>> enemyIfv = getUnitsCount(true).get(IFV).entrySet();
                addToArray(plainArray, enemyIfv, range, .1f); //TODO FEAR
                addToArray(plainArray, enemyHelics, range, .3f);

                subFromArray(plainArray, enemyIfv, (game.getHelicopterAerialAttackRange() + 30) / cellSize, 3.2f);
                subFromArray(plainArray, enemyHelics, (game.getIfvAerialAttackRange() + 10) / cellSize, 1.4f);
            }


            {
                Set<Map.Entry<Point2D, Integer>> fighters = getUnitsCount(true).get(FIGHTER).entrySet();

                double range = (game.getFighterAerialAttackRange() * 2.8) / cellSize;

                int factor = 6;

                if (!fighters.isEmpty()) {
                    subFromArray(plainArray, fighters, range, factor);
                }
            }

            {
                HashMap<Point2D, Integer> myFightersMap = new HashMap<>(getUnitsCount(false).get(FIGHTER));

                Set<Map.Entry<Point2D, Integer>> myFighters = myFightersMap.entrySet();

                double range = (GROUP_SIZE) / cellSize;

                int factor = 2;

                if (!myFighters.isEmpty()) {
                    subFromArray(plainArray, myFighters, range, factor);
                }
            }
        }

        if (vehicleGroupInfo.vehicleType == IFV) {
            {

                double range = plainArray.cellsWidth * 1.2;

                //addToArray(plainArray, tanksAndArrvSet, range, 1.f);
                addToArray(plainArray, getUnitsCount(true).get(HELICOPTER).entrySet(), range, .9f);
                addToArray(plainArray, getUnitsCount(true).get(FIGHTER).entrySet(), range, 1.f);
                addToArray(plainArray, getUnitsCount(true).get(ARRV).entrySet(), range, 0.75f);


                Set<Map.Entry<Point2D, Integer>> enemyTank = getUnitsCount(true).get(TANK).entrySet();
                Set<Map.Entry<Point2D, Integer>> enemyIfv = getUnitsCount(true).get(IFV).entrySet();

                addToArray(plainArray, enemyIfv, range, .4f);
                addToArray(plainArray, enemyTank, range, .1f);

                subFromArray(plainArray, enemyIfv, (game.getIfvGroundAttackRange() + 20) / cellSize, .3f);
                subFromArray(plainArray, enemyTank, (game.getTankGroundAttackRange() * 4.5) / cellSize, 3.2f);
            }

            {
                HashMap<Point2D, Integer> myGroundUnits = new HashMap<>(getUnitsCount(false).get(TANK));

                if (ifvShouldHeal) {
                    myGroundUnits.putAll(getUnitsCount(false).get(ARRV));
                }

                Set<Map.Entry<Point2D, Integer>> myFighters = myGroundUnits.entrySet();

                double range = (GROUP_SIZE * 1.3) / cellSize;

                int factor = 2;

                if (!myFighters.isEmpty()) {
                    subFromArray(plainArray, myFighters, range, factor);
                }
            }
        }

        if (vehicleGroupInfo.vehicleType == TANK) {
            {

                double range = plainArray.cellsWidth * 1.2;

                //addToArray(plainArray, tanksAndArrvSet, range, 1.f);
                //targets
                addToArray(plainArray, getUnitsCount(true).get(IFV).entrySet(), range, 1.f);
                addToArray(plainArray, getUnitsCount(true).get(TANK).entrySet(), range, .9f);
                addToArray(plainArray, getUnitsCount(true).get(ARRV).entrySet(), range, 0.8f);


                //secondary targets
                Set<Map.Entry<Point2D, Integer>> helics = getUnitsCount(true).get(HELICOPTER).entrySet();
                Set<Map.Entry<Point2D, Integer>> fighters = getUnitsCount(true).get(FIGHTER).entrySet();

                addToArray(plainArray, helics, range, .1f);
                addToArray(plainArray, getUnitsCount(true).get(FIGHTER).entrySet(), range, .4f);

                //keep away from secondary targets
                subFromArray(plainArray, helics, (game.getHelicopterGroundAttackRange() * 3) / cellSize, 4.5f);
                subFromArray(plainArray, fighters, (GROUP_SIZE) / cellSize, .1f);
            }

            {   // my units as obstacle
                HashMap<Point2D, Integer> myGroundUnits = new HashMap<>(getUnitsCount(false).get(IFV));

                if (!tankShouldHeal) {
                    myGroundUnits.putAll(getUnitsCount(false).get(ARRV));
                }

                Set<Map.Entry<Point2D, Integer>> myGroundUnits2 = myGroundUnits.entrySet();

                double range = (GROUP_SIZE * 1.3) / cellSize;

                int factor = 2;

                if (!myGroundUnits2.isEmpty()) {
                    subFromArray(plainArray, myGroundUnits2, range, factor);
                }
            }
        }

        if (vehicleGroupInfo.vehicleType == ARRV) {

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

                //TODO chase enemies

                //keep away from secondary targets
                Set<Map.Entry<Point2D, Integer>> enHel = getUnitsCount(true).get(HELICOPTER).entrySet();
                Set<Map.Entry<Point2D, Integer>> enTanks = getUnitsCount(true).get(TANK).entrySet();
                Set<Map.Entry<Point2D, Integer>> enIfv = getUnitsCount(true).get(IFV).entrySet();
                subFromArray(plainArray, enHel, (game.getHelicopterGroundAttackRange() * 3) / cellSize, 1.5f);
                subFromArray(plainArray, enTanks, (game.getTankGroundAttackRange() * 3) / cellSize, 1.5f);
                subFromArray(plainArray, enIfv, (game.getIfvGroundAttackRange() * 3) / cellSize, 1.5f);
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

                Set<Map.Entry<Point2D, Integer>> myGroundUnits2 = grounUnits.entrySet();

                double range = (GROUP_SIZE * 1.3) / cellSize;

                int factor = 2;

                if (!myGroundUnits2.isEmpty()) {
                    subFromArray(plainArray, myGroundUnits2, range, factor);
                }
            }
        }


        return plainArray;
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
                    float val = (100 - entry.getValue()) * factor; //TODO extract 100?
                    double value = (1 - entry.getKey().squareDistance(x, y) / squareDelta) * val;
                    plainArray.set(x, y, Math.max(plainArray.get(x, y), value));
                }
            }
        }
    }

    private void subFromArray(PlainArray plainArray, Set<Map.Entry<Point2D, Integer>> ifvCount, double range, float factor) {
        double squareDelta = range * range;
        for (int x = 0; x < plainArray.cellsWidth; x++) {
            for (int y = 0; y < plainArray.cellsHeight; y++) {

                for (Map.Entry<Point2D, Integer> entry : ifvCount) {
                    float val = entry.getValue() * factor;
                    double value = (1 - entry.getKey().squareDistance(x, y) / squareDelta) * val;
                    if (value > 1) {
                        plainArray.set(x, y, plainArray.get(x, y) - value);
                    }
                }
            }
        }
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


        if (tryPickNuclearTarget()) return;

        tryEvadeNuclearTarget();

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

            if (initialScale(myGroup)) continue;

            // scaleToKover(myGroup);
            if (isArrvMoving && (myGroup.vehicleType == IFV || myGroup.vehicleType == TANK)) {

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
                scaleToKover(myGroup);
            } else {
                scheduleSelectAll(myGroup);

                scheduleMoveToPoint(myGroup, enemyGroup);
            }
        }

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
                return true;
            }

        }
        return false;
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