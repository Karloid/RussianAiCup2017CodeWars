import model.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.*;

public class RewindClientWrapper implements MyStrategyPainter {

    public static final Color COLOR_CLEAR_AND_SELECT = new Color(100, 100, 100, 100);
    public static final Color COLOR_ADD_TO_SELECT = new Color(0, 100, 18, 128);
    public static final Color COLOR_MOVE = new Color(6, 100, 0, 128);
    public static final Color COLOR_MY_GROUP = new Color(3, 182, 0, 153);
    public static final Color COLOR_MOVE_POINT = new Color(211, 2, 23, 255);
    public static final Color COLOR_NUCLEAR = new Color(0, 255, 0, 100);
    private static final Color COLOR_NUCLEAR_VEH_VISION = new Color(180, 183, 76, 147);
    public static final int LAYER_GENERIC = 4;
    private MyStrategy mys;
    private RewindClient rc;

    public RewindClientWrapper() {
    }

    @Override
    public void onStartTick() {
        Collection<VehicleWrapper> vehs = mys.um.vehicleById.values();
        for (VehicleWrapper veh : vehs) {
            Vehicle v = veh.v;
            rc.livingUnit(v.getX(), v.getY(), v.getRadius(), v.getDurability(), v.getMaxDurability(), veh.isEnemy ? RewindClient.Side.ENEMY : RewindClient.Side.OUR
                    , 0, convertType(v), v.getRemainingAttackCooldownTicks(), v.getAttackCooldownTicks(), v.isSelected());
        }


        //DRAW NUCLEAR
        int tick = mys.world.getTickIndex();
        Player[] players = mys.world.getPlayers();
        for (int i = 0; i < players.length; i++) {
            Player player = players[i];
            int nextIndex = player.getNextNuclearStrikeTickIndex();
            if (nextIndex < 0) {
                continue;
            }
            int delta = tick - nextIndex;
            if (delta > 40) {
                continue;
            } else {
                double x = player.getNextNuclearStrikeX();
                double y = player.getNextNuclearStrikeY();
                Color color = getPlayerNuclearColor(player);

                rc.circle(x, y, mys.game.getTacticalNuclearStrikeRadius(), color, LAYER_GENERIC); // target
                rc.circle(x, y, 10, color, LAYER_GENERIC);                                        //center of target

                long vehicleId = player.getNextNuclearStrikeVehicleId();
                VehicleWrapper veh = mys.um.get(vehicleId);
                if (veh != null) {
                    rc.line(x, y, veh.v.getX(), veh.v.getY(), color, 1);
                    rc.circle(veh.v.getX(), veh.v.getY(), veh.v.getRadius() * 4, color, LAYER_GENERIC);
                    rc.circle(veh.v.getX(), veh.v.getY(), veh.v.getVisionRange(), COLOR_NUCLEAR_VEH_VISION, LAYER_GENERIC);
                    rc.message(String.format(Locale.US, "\\n NUCLEAR: %s distance %.2f vision range %.2f",
                            player.isMe() ? "ME" : "ENEMY",
                            Point2D.getDistance(x, y, veh.v.getX(), veh.v.getY()), veh.v.getVisionRange()));
                }
            }

        }
    }

    private Color getPlayerNuclearColor(Player player) {
        return new Color(player.isMe() ? 0 : 255, 0, player.isMe() ? 255 : 0, 100);
    }

    private RewindClient.UnitType convertType(Vehicle v) {
        switch (v.getType()) {
            case ARRV:
                return RewindClient.UnitType.ARRV;
            case FIGHTER:
                return RewindClient.UnitType.FIGHTER;
            case HELICOPTER:
                return RewindClient.UnitType.HELICOPTER;
            case IFV:
                return RewindClient.UnitType.IFV;
            case TANK:
                return RewindClient.UnitType.TANK;
        }
        return RewindClient.UnitType.UNKNOWN;
    }

    @Override
    public void setMYS(MyStrategy myStrategy) {
        mys = myStrategy;
    }

    @Override
    public void onEndTick() {
        java.util.List<VehicleGroupInfo> myGroups = mys.myGroups;
        if (myGroups != null) {
            for (int i = 0; i < myGroups.size(); i++) {
                VehicleGroupInfo myGroup = myGroups.get(i);
                Point2D ap = myGroup.getAveragePoint();
                rc.circle(ap.getX(), ap.getY(), 4, COLOR_MY_GROUP, 5);
                Rectangle2D rect = myGroup.pointsInfo.rect;
                rc.rect(rect.getMinX(), rect.getMinY(), rect.getMaxX(), rect.getMaxY(), COLOR_MY_GROUP, 5);
                if (myGroup.moveToPoint != null) {
                    rc.circle(myGroup.moveToPoint.getX(), myGroup.moveToPoint.getY(), 4, COLOR_MOVE_POINT, LAYER_GENERIC);
                    rc.line(ap.getX(), ap.getY(), myGroup.moveToPoint.getX(), myGroup.moveToPoint.getY(), COLOR_MOVE_POINT, LAYER_GENERIC);
                }


                if (myGroup.vehicleType == VehicleType.FIGHTER) {
                    drawPP(myGroup);
                }
            }
        }

        rc.message(String.format(Locale.US, "\\nMe: %s Opponent: %s\\nActionCooldown: %s\\nNuclearCooldown: %s", mys.me.getScore(), mys.opponent.getScore(), mys.me.getRemainingActionCooldownTicks(), mys.me.getRemainingNuclearStrikeCooldownTicks()));

        ArrayList<Map.Entry<ActionType, Integer>> entries = new ArrayList<>(mys.movesStats.entrySet());
        String msg = "\\nMy moves count: " + mys.movesCount + "\\n";
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map.Entry<ActionType, Integer> e = entries.get(i);
            msg += e.getKey() + " " + e.getValue() + "\\n";
        }
        rc.message(msg);

        drawMoveActual();

        rc.endFrame();
    }

    private void drawPP(VehicleGroupInfo myGroup) {
        for (Map.Entry<VehicleType, PlainArray> entry : myGroup.potentialMaps.entrySet()) {

            PlainArray plainArray = entry.getValue();

            int cellSize = mys.cellSize;

            int cellsX = mys.worldWidth / cellSize;
            int cellsY = mys.worldHeight / cellSize;

            double max = plainArray.getMax();
            double min = plainArray.getMin();
            double delta = max - min;
            if (delta == 0) {
                delta = 1;
            }

            for (int x = 0; x < cellsX; x++) {
                for (int y = 0; y < cellsY; y++) {
                    double v = plainArray.get(x, y);

                    int alpha =  (int) (((v - min) / delta) * 220);
                    if (alpha > 0) {
                        rc.rect(x * cellSize, y * cellSize, x * cellSize + cellSize, y * cellSize + cellSize, new Color(200, 255, 0, alpha), 1);
                    }
                }
            }

        }
    }

    private void drawMoveActual() {
        Move move = mys.move;

        ActionType action = move.getAction();
        if (action == null) {
            return;
        }

        switch (action) {

            case NONE:
                break;
            case CLEAR_AND_SELECT:
                // rc.rect(move.getLeft(), move.getTop(), move.getRight(), move.getBottom(), COLOR_CLEAR_AND_SELECT, 0);
                break;
            case ADD_TO_SELECTION:
                rc.rect(move.getLeft(), move.getTop(), move.getRight(), move.getBottom(), COLOR_ADD_TO_SELECT, LAYER_GENERIC);
                break;
            case DESELECT:
                break;
            case ASSIGN:
                break;
            case DISMISS:
                break;
            case DISBAND:
                break;
            case MOVE:
                //TODO find center of selected group?
                //  rc.circle(move.getX(), move.getY(), 20, COLOR_MOVE, 1);
                rc.message(String.format("\\nmove %.2f %.2f", move.getX(), move.getY()));
                break;
            case ROTATE:
                break;
            case SCALE:
                break;
            case SETUP_VEHICLE_PRODUCTION:
                break;
            case TACTICAL_NUCLEAR_STRIKE:
                rc.circle(move.getX(), move.getY(), 4, Color.RED, LAYER_GENERIC);
                VehicleWrapper veh = mys.um.get(move.getVehicleId());
                if (veh != null) {
                    Color color = COLOR_NUCLEAR;
                    rc.circle(veh.getX(), veh.getY(), 4, color, 1);
                    rc.line(move.getX(), move.getY(), veh.getX(), veh.getY(), color, LAYER_GENERIC);
                }
                break;
        }
    }

    @Override
    public void onInitializeStrategy() {
        try {
            rc = new RewindClient();
        } catch (Exception e) {
            e.printStackTrace();
            mys.setPainter(new EmptyPaintner());
            return;
        }
        for (int x = 0; x < mys.terrainTypeByCellXY.length; x++) {
            for (int y = 0; y < mys.terrainTypeByCellXY[x].length; y++) {
                RewindClient.AreaType areaType = getAreaType(mys.terrainTypeByCellXY[x][y]);
                if (areaType != RewindClient.AreaType.UNKNOWN) {
                    rc.areaDescription(x, y, areaType);
                }
                RewindClient.AreaType weatherType = getAreaType(mys.weatherTypeByCellXY[x][y]);
                if (weatherType != RewindClient.AreaType.UNKNOWN) {
                    rc.areaDescription(x, y, weatherType);
                }
            }
        }


    }

    @Override
    public void drawMove() {

    }

    private RewindClient.AreaType getAreaType(WeatherType weatherType) {
        switch (weatherType) {
            case CLEAR:
                return RewindClient.AreaType.UNKNOWN;
            case CLOUD:
                return RewindClient.AreaType.CLOUD;
            case RAIN:
                return RewindClient.AreaType.RAIN;
        }
        return RewindClient.AreaType.UNKNOWN;
    }

    private RewindClient.AreaType getAreaType(TerrainType terrainType) {
        switch (terrainType) {
            case PLAIN:
                return RewindClient.AreaType.UNKNOWN;
            case SWAMP:
                return RewindClient.AreaType.SWAMP;
            case FOREST:
                return RewindClient.AreaType.FOREST;
        }
        return RewindClient.AreaType.UNKNOWN;
    }
}
