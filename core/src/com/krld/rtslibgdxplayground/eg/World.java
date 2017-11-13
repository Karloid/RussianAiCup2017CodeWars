package com.krld.rtslibgdxplayground.eg;

import com.krld.rtslibgdxplayground.eg.models.*;
import com.krld.rtslibgdxplayground.eg.models.Vector;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class World {
    private final int spawnUnitCount;
    private CellType[][] cells;
    public int height;
    public int width;
    public List<Player> players;
    public List<Unit> units;
    private UIDelegate uiDelegate;
    private int delayTurnMs;
    private Game game;
    private int moveCount;
    public Map<Unit, Move> moves;
    public List<Corpse> corpses;
    public ReentrantLock reentrantLock;

    public World(int width, int height, int spawnUnitCount, int cellSize, UIDelegate uiDelegate) {
        reentrantLock = new ReentrantLock();

        players = new ArrayList<Player>();
        units = new ArrayList<Unit>();
        corpses = new ArrayList<Corpse>();

        this.width = width;
        this.height = height;
        this.spawnUnitCount = spawnUnitCount;
        delayTurnMs = 100;

        initFreeCells();

        randomizeCells();
        randomizeCells();

        moves = new HashMap<>();

        this.uiDelegate = uiDelegate;
    }

    private void mapCellsArena() {
        createCellsRectangle((width / 5) * 2, (height / 5 ) * 2, (width / 5) , (height / 5) );

    }

    private void randomizeCells() {
        //...
        createCellsRectangle((width / 5) * 2, (height / 5 ) * 2, (width / 5) , (height / 5) );
        for (int i = 0; i < 15; i++)
            createCellsRectangle((int) (width * Math.random()), (int) (height * Math.random()), 1 + (int) (width / 6 * Math.random()), 1 + (int) (height / 6 * Math.random()));
    }

    private void createCellsRectangle(int x, int y, int width, int height) {
        for (int x1 = x; x1 < x + width; x1++)
            for (int y1 = y; y1 < y + height; y1++) {
                if (inFrame(x1, y1))
                    cells[x1][y1] = CellType.COVER;
            }

    }


    private void initFreeCells() {
        cells = new CellType[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++) {
                getCells()[x][y] = CellType.FREE;
            }

    }

    public void applyMove(Unit unit, Move move) {
        if (move.getAction() == ActionType.END_TURN) {
            return;
        }
        if (move.getAction() == ActionType.MOVE) {
            handleMove(unit, move);
            return;
        }
        if (move.getAction() == ActionType.SHOOT) {
            handleShoot(unit, move);
            return;
        }

        if (move.getAction() == ActionType.HEAL) {
            handleHeal(unit, move);
            return;
        }
    }

    private void handleHeal(Unit unit, Move move) {
        Vector point = getPointOnDirection(new Vector(unit), move.getDirection());
        if (inFrame(point)) {
            for (Unit unit1 : units) {
                if (unit1.getX() == point.x && unit1.getY() == point.y) {
                    if (unit1.isTeamatte(unit)) unit1.applyHeal(game.getMedicHealAmount());
                    break;
                }
            }
        }
    }

    private boolean inFrame(Vector point) {
        return inFrame(point.x, point.y);
    }

    public Vector getPointOnDirection(Vector point, Direction direction) {
        if (direction == Direction.CURRENT_CELL) {
            return point;
        }
        if (direction == Direction.EAST) {
            point.x++;
            return point;
        }
        if (direction == Direction.WEST) {
            point.x--;
            return point;
        }
        if (direction == Direction.SOUTH) {
            point.y++;
            return point;
        }
        if (direction == Direction.NORTH) {
            point.y--;
            return point;
        }
        return point;
    }

    private void handleShoot(Unit unit, Move move) {
        for (Unit unit1 : units) {
            if (unit1.x == move.getX() && unit1.y == move.getY() && getDistance(unit, unit1) <= game.getShootingRange()) {

                int damage = unit1.recieveDamage(getDamage(unit));
                if (!unit1.isTeamatte(unit)) {
                    unit.player.score += damage;
                }
                return;
            }
        }
    }

    private int getDamage(Unit unit) {
        if (unit.getType() == UnitType.SOLDIER)
            return game.getSoldierShootDamage();
        if (unit.getType() == UnitType.MEDIC)
            return game.getMedicShootDamage();
        return 0;
    }

    private void handleMove(Unit unit, Move move) {
        if (move.getDirection() == Direction.NORTH) {
            if (isFree(unit.x, unit.y - 1)) {
                unit.y -= 1;
            }
            return;
        }

        if (move.getDirection() == Direction.SOUTH) {
            if (isFree(unit.x, unit.y + 1)) {
                unit.y += 1;
            }
            return;
        }

        if (move.getDirection() == Direction.WEST) {
            if (isFree(unit.x - 1, unit.y)) {
                unit.x -= 1;
            }
            return;
        }

        if (move.getDirection() == Direction.EAST) {
            if (isFree(unit.x + 1, unit.y)) {
                unit.x += 1;
            }
            return;
        }
    }

    private boolean isFree(int x, int y) {
        if (!inFrame(x, y)) {
            return false;
        }
        if (getCells()[x][y] != CellType.FREE) {
            return false;
        }
        for (Unit unit : units) {
            if (unit.x == x && unit.y == y) {
                return false;
            }
        }
        return true;
    }

    public boolean inFrame(int x, int y) {
        return x < width && x >= 0 && y < height && y >= 0;
    }


    public void update() {
        reentrantLock.lock();
        moveCount++;
        long startUpdate = System.currentTimeMillis();
        long seed = System.nanoTime();
        Collections.shuffle(units, new Random(seed));
        moves.clear();
        for (Unit unit : units) {
            Move move = new Move();
            moves.put(unit, move);
            unit.getStrategy().move(game, this, unit, move);
            applyMove(unit, move);
        }
        removeDeadUnits();
        uiDelegate.update();

        checkEndGame();
        reentrantLock.unlock();

        if (System.currentTimeMillis() - startUpdate < delayTurnMs)
            try {
                Thread.sleep(delayTurnMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    private void checkEndGame() {
        System.out.println("Move count: " + moveCount);
        if (units.isEmpty()) {
            delayTurnMs = 100000;
            System.out.println("Game is ended");
            return;
        }
        Player player = units.get(0).player;
        for (Unit unit : units) { //strange check
            if (unit.player != player) {

                return;
            }
        }
        System.out.println("Game is ended");
        delayTurnMs = 100000;
    }

    private void removeDeadUnits() {
        List<Unit> unitsToRemove = new ArrayList<Unit>();
        for (Unit unit : units) {
            if (unit.isDead()) {
                unitsToRemove.add(unit);
                corpses.add(new Corpse(unit));
            }
        }
        units.removeAll(unitsToRemove);

    }

    public void addPlayer(Player player, int x, int y) {
        players.add(player);

        addUnits(x, y, spawnUnitCount, player);
    }

    private void addUnits(int x, int y, int count, Player player) {
        for (int i = 0; i <= count; i++) {
            Vector point = getFreePointNear(x, y);
            if (point == null) return;
            if (i % 6 == 0)
                units.add(new Unit(point.x, point.y, player, UnitType.MEDIC));
            else
                units.add(new Unit(point.x, point.y, player, UnitType.SOLDIER));
        }

    }

    public Vector getFreePointNear(int x, int y) {
        Vector point = null;

        for (int x1 = 0; x1 < width; x1++)
            for (int y1 = 0; y1 < height; y1++) {
                if ((point == null || getDistance(x1, y1, x, y) < getDistance(point.x, point.y, x, y)) && cells[x1][y1] == CellType.FREE) {
                    boolean noUnitsInCell = true;
                    for (Unit unit : units) {
                        if (unit.x == x1 && unit.y == y1) {
                            noUnitsInCell = false;
                            break;
                        }
                    }
                    if (noUnitsInCell)
                        point = new Vector(x1, y1);
                }
                //  getCells()[x1][y1] = CellType.FREE;
            }
        return point;
    }

    public double getDistance(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    public double getDistance(Unit unit1, Unit unit2) {
        return getDistance(unit1.x, unit1.y, unit2.x, unit2.y);
    }

    public CellType[][] getCells() {
        return cells;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public Game getGame() {
        return game;
    }

    public List<Unit> getUnits() {
        return units;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public Vector getFreePointNear(Vector point) {
        return getFreePointNear(point.x, point.y);
    }

    public double getDistance(Vector point, Vector point1) {
        return getDistance(point.x, point.y, point1.x, point1.y);
    }
}
