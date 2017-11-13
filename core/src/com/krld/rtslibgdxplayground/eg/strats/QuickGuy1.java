package com.krld.rtslibgdxplayground.eg.strats;


import com.krld.rtslibgdxplayground.eg.*;
import com.krld.rtslibgdxplayground.eg.models.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import static com.krld.rtslibgdxplayground.eg.models.Direction.*;



public class QuickGuy1 implements Strategy {
    public static final int MAX_PATH = 999;
    public static final int DISTANCE_TO_WAYPOINT = 5;
    private static final int SELF_MEDIC_MAX_HP = 100;
    private static final double MEDIC_HEAL_DISTANCE = 6;
    private World world;
    private Game game;
    private Move move;
    private Unit self;
    private List<Unit> units;
    private static Unit target;

    private boolean firstRun = true;

    public int[][] path;
    private boolean[][] check;
    public boolean[][] available;
    public static int[][] shootingMatrix;
    public static int[][] grenadeDamage;
    private int captainId;
    private static ArrayList<Vector> movePoints;
    private static int movePointIndex = 0;

    private static Set<Unit> targets;


    @Override
    public void move(Game game, World world, Unit self, Move move) {
        this.game = game;
        this.world = world;
        this.self = self;
        this.move = move;
        this.units = world.getUnits();

        if (firstRun) {
            initWayPoints();
            setFirstMoveIndex();
            initCaptain();
            firstRun = false;

            targets = new HashSet<Unit>();
        }
        if (captainIsDead()) {
            initCaptain();
        }


        if (shootActions()) {
            return;
        }


        if (medicActions()) {
            return;
        }

        if (moveToTargetActions())
            return;

        if (moveActions())
            return;

    }

    private boolean moveToTargetActions() {
        Unit moveTarget = null;
        removeDeadTargets();
        for (Unit unit : targets) {
            if (moveTarget == null || world.getDistance(self, moveTarget)
                    > world.getDistance(self, unit))
                moveTarget = unit;
        }
        if (moveTarget != null) {
            move.setAction(ActionType.MOVE);
            moveReal(new Vector(moveTarget), 0, 0, false, true);
            return true;
        }
        return false;
    }

    private void removeDeadTargets() {
        Set<Unit> deadUnits = new HashSet<Unit>();
        for (Unit unit : targets) {
            if (unit.isDead()) {
                deadUnits.add(unit);
            }
        }
        targets.removeAll(deadUnits);


    }

    private boolean captainIsDead() {
        for (Unit unit : units) {
            if (unit.getId() == captainId) {
                return unit.isDead();
            }
        }
        return true;
    }

    private void initCaptain() {
        //  captainId = self.getId();
        Unit captain = null;
        for (Unit unit : units) {
            if (unit.isTeamatte(self)) {
                if (captain == null || world.getDistance(captain.x, captain.y, movePoints.get(movePointIndex).x, movePoints.get(movePointIndex).y)
                        > world.getDistance(unit.x, unit.y, world.getWidth() / 2, world.getHeight() / 2)) {
                    captain = unit;
                    captainId = unit.getId();
                }
            }
        }
    }


    private void setFirstMoveIndex() {
        Vector tmpPoint = null;
        for (Vector point : movePoints) {
            if ((tmpPoint == null || world.getDistance(point, new Vector(world.getUnits().get(0))) <
                    world.getDistance(tmpPoint, new Vector(world.getUnits().get(0))))
                //   && movePoints.indexOf(point) != movePoints.size() -1
                //   && getDistance(point, new Point(troopers[0])) > 4
                    ) {
                tmpPoint = point;
                movePointIndex = movePoints.indexOf(tmpPoint);
            }
        }
        System.out.println(" movePointIndex" + movePointIndex);
        log("tmpPoint" + tmpPoint);
        log("movepointIndex: " + movePointIndex);
        //  movePointIndex = 2;

    }


    private boolean medicActions() {
        if (self.getType() == UnitType.MEDIC) {
            if (self.getHp() < SELF_MEDIC_MAX_HP) {
                move.setAction(ActionType.HEAL);
                move.setDirection(Direction.CURRENT_CELL);
                //    Game.log(self.player.getClassStategy() + "medic heals!");
                return true;
            }
        }
        if (self.getType() == UnitType.MEDIC)
            for (Unit unit : units) {
                if (unit.isTeamatte(self) && unit != self) {
                    if (unit.getHp() < game.getMaximumHP() && world.getDistance(self, unit) == 1) {
                        Direction direction = Direction.CURRENT_CELL;
                        if (unit.getX() > self.getX()) {
                            direction = Direction.EAST;
                        } else if (unit.getX() < self.getX()) {
                            direction = Direction.WEST;
                        } else if (unit.getY() < self.getY()) {
                            direction = Direction.NORTH;
                        } else if (unit.getY() > self.getY()) {
                            direction = Direction.SOUTH;
                        }
                        move.setAction(ActionType.HEAL);
                        move.setDirection(direction);
                   /*     System.out.println(self.player.getColor() + " heal teamatte!: " + unit.getType()
                                + " id: " + unit.getId() + " current hp: " + unit.getHp());    */
                        return true;
                    }
                }
            }

        if (self.getType() == UnitType.MEDIC) {
            Unit unitToHeal = null;
            for (Unit unit : units) {
                if (unit.isTeamatte(self) && unit != self) {
                    if (unit.getHp() < game.getMaximumHP() && world.getDistance(self, unit) == MEDIC_HEAL_DISTANCE) {
                        if (unitToHeal == null || world.getDistance(unit, self) < world.getDistance(unitToHeal, self) ) {
                            unitToHeal = unit;
                        }
                    }
                }
            }
            if (unitToHeal != null) {
                move.setAction(ActionType.MOVE);
                moveReal(new Vector(unitToHeal), 0, 0, false, true);
          //      log("move to heal target");
                return true;
            }
        }
        return false;
    }

    private boolean shootActions() {
        for (Unit unit : units) {
            if (!unit.isTeamatte(self) && world.getDistance(self, unit) <= game.getShootingRange() && !unit.isDead()) {
                move.setAction(ActionType.SHOOT);
                move.setX(unit.x);
                move.setY(unit.y);
                target = unit;
                targets.add(unit);
                return true;
            }
        }
        return false;
    }

    private boolean moveActions() {
        if (self.getId() == captainId || true) {

            moveActionCaptain();
            return true;
        } else {
            for (Unit unit : units) {
                if (unit.getId() == captainId) {
                    moveReal(world.getFreePointNear(new Vector(unit)), 10, 0, true, false);
                    break;
                }
            }

        }
        //  printPath();
        return true;
    }

    private void moveActionCaptain() {
        if (world.getDistance(new Vector(self), movePoints.get(movePointIndex)) < DISTANCE_TO_WAYPOINT) {
            if (Math.random() > 0.5f)
                movePointIndex++;
            else
                movePointIndex--;
            if (movePointIndex == movePoints.size() || movePointIndex < 0) {
                movePointIndex = 0;
            }
        }


        //moveReal(movePoints.get(movePointIndex), 1, 0);

        moveReal(new Vector(self), 0, 0, false, false);
        if (path[movePoints.get(movePointIndex).x][movePoints.get(movePointIndex).y] != 999) {
            moveReal(movePoints.get(movePointIndex), 0, 0, true, false);
        } else {
            Vector point = null;
            for (int x = 0; x < world.getWidth(); x++) {
                for (int y = 0; y < world.getHeight(); y++) {
                    if (path[x][y] != 999) {
                        Vector currentPoint = new Vector(x, y);
                        if (point == null || world.getDistance(currentPoint, movePoints.get(movePointIndex))
                                < world.getDistance(point, movePoints.get(movePointIndex))) {
                            point = currentPoint;
                        }
                    }
                }
            }
            moveReal(point, 0, 0, true, false);
        }
        //  log("captain move " + move + " " + self);
    }

    private void printPath() {
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                System.out.print(path[x][y] + " ");
            }
            System.out.println();
        }

    }


    private int moveReal(Vector point, int distance, int safeWay, boolean fastFind, boolean instantFind) {
        if (!instantFind && path != null && Math.random() > 0.5) {
            return 0;
        }
        path = new int[world.getWidth()][world.getHeight()];
        //  log(path.length + " length");
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                path[x][y] = 999;
            }
        }
        check = new boolean[world.getWidth()][world.getHeight()];
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                check[x][y] = false;
            }
        }
        available = new boolean[world.getWidth()][world.getHeight()];
        flowAvalible(available, safeWay);
        check[point.x][point.y] = true;
        path[point.x][point.y] = 0;

        checkCells(fastFind);
        move.setDirection(CURRENT_CELL);
        move.setAction(ActionType.MOVE);
        int result = makeMove();
        // big misstake probality
        return result;
    }

    private void checkCells(boolean fastFind) {
        while (!allChecked(check)) {
            for (int x = 0; x < world.getWidth(); x++) {
                for (int y = 0; y < world.getHeight(); y++) {
                    if (check[x][y]) {

                        handleNear(x + 1, y, x, y);
                        handleNear(x - 1, y, x, y);
                        handleNear(x, y + 1, x, y);
                        handleNear(x, y - 1, x, y);

                        check[x][y] = false;
                        if (fastFind && self.getX() == x && self.getY() == y) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private void log(String s) {
        System.out.println(world.getMoveCount() + " " + self.getType() + " id: " + self.getId() + " " + s);

    }

    private void handleNear(int xTmp, int yTmp, int x, int y) {
        if (world.inFrame(xTmp, yTmp) && available[xTmp][yTmp] && path[xTmp][yTmp] > path[x][y] + 1) {
            path[xTmp][yTmp] = path[x][y] + 1;
            check[xTmp][yTmp] = true;
        }
    }

    private void flowAvalible(boolean[][] availible, int safeWay) {
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                availible[x][y] = true;
            }
        }
        for (Unit trooper : units) {
            if (trooper.getId() != self.getId())
                availible[trooper.getX()][trooper.getY()] = false;
        }

        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                if (world.getCells()[x][y] != CellType.FREE) {
                    availible[x][y] = false;
                }
            }
        }
    }

    private boolean allChecked(boolean[][] check) {
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                if (check[x][y]) {
                    return false;
                }
            }
        }
        return true;
    }

    private int makeMove() {
        int xTmp;
        int yTmp;

        int north;
        int west; // <<<<-
        int east;  // --->>>>>
        int south;
        xTmp = self.getX();
        yTmp = self.getY() - 1;
        north = (world.inFrame(xTmp, yTmp) ? path[xTmp][yTmp] : MAX_PATH);

        xTmp = self.getX() - 1;
        yTmp = self.getY();
        west = (world.inFrame(xTmp, yTmp) ? path[xTmp][yTmp] : MAX_PATH);

        xTmp = self.getX() + 1;
        yTmp = self.getY();
        east = (world.inFrame(xTmp, yTmp) ? path[xTmp][yTmp] : MAX_PATH);

        xTmp = self.getX();
        yTmp = self.getY() + 1;
        south = (world.inFrame(xTmp, yTmp) ? path[xTmp][yTmp] : MAX_PATH);
        double random = Math.random();
        if (random < 0.25f) {
            if (north <= south && north <= west && north <= east) {
                move.setDirection(NORTH);
                return north;
            }
            if (south <= north && south <= west && south <= east) {
                move.setDirection(SOUTH);
                return south;
            }
            if (west <= south && west <= north && west <= east) {
                move.setDirection(WEST);
                return west;
            }
            if (east <= south && east <= west && east <= north) {
                move.setDirection(EAST);
                return east;
            }
        } else if (random < 0.5f) {
            if (south <= north && south <= west && south <= east) {
                move.setDirection(SOUTH);
                return south;
            }

            if (north <= south && north <= west && north <= east) {
                move.setDirection(NORTH);
                return north;
            }
            if (east <= south && east <= west && east <= north) {
                move.setDirection(EAST);
                return east;
            }
            if (west <= south && west <= north && west <= east) {
                move.setDirection(WEST);
                return west;
            }
        } else if (random < 0.75f) {
            if (east <= south && east <= west && east <= north) {
                move.setDirection(EAST);
                return east;
            }
            if (west <= south && west <= north && west <= east) {
                move.setDirection(WEST);
                return west;
            }
            if (south <= north && south <= west && south <= east) {
                move.setDirection(SOUTH);
                return south;
            }

            if (north <= south && north <= west && north <= east) {
                move.setDirection(NORTH);
                return north;
            }
        } else if (true) {
            if (west <= south && west <= north && west <= east) {
                move.setDirection(WEST);
                return west;
            }
            if (east <= south && east <= west && east <= north) {
                move.setDirection(EAST);
                return east;
            }
            if (north <= south && north <= west && north <= east) {
                move.setDirection(NORTH);
                return north;
            }
            if (south <= north && south <= west && south <= east) {
                move.setDirection(SOUTH);
                return south;
            }
        }
        return 0;
    }

    private void initWayPoints() {
        movePoints = new ArrayList<Vector>();

        System.out.println("NORMAL MAP");
        movePoints.add(new Vector(0, world.getHeight() - 1));
        movePoints.add(new Vector(0, 0));
        movePoints.add(new Vector(world.getWidth() - 1, 0));
        movePoints.add(new Vector(world.getWidth() - 1, world.getHeight() - 1));
        //   movePoints.add(new Point(world.getWidth() / 2, world.getHeight() / 2));

    }
}
