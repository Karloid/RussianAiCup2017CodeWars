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

                rc.circle(x, y, mys.game.getTacticalNuclearStrikeRadius(), color, 1);
                rc.circle(x, y, 10, color, 1);

                long vehicleId = player.getNextNuclearStrikeVehicleId();
                VehicleWrapper veh = mys.um.get(vehicleId);
                if (veh != null) {
                    rc.line(x, y, veh.v.getX(), veh.v.getY(), color, 1);
                    rc.circle(veh.v.getX(), veh.v.getY(), veh.v.getRadius() * 4, color, 1);
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
                rc.circle(ap.getX(), ap.getY(), 4, COLOR_MY_GROUP, 0);
                Rectangle2D rect = myGroup.pointsInfo.rect;
                rc.rect(rect.getMinX(), rect.getMinY(), rect.getMaxX(), rect.getMaxY(), COLOR_MY_GROUP, 1);
                if (myGroup.moveToPoint != null) {
                    rc.circle(myGroup.moveToPoint.getX(), myGroup.moveToPoint.getY(), 4, COLOR_MOVE_POINT, 1);
                    rc.line(ap.getX(), ap.getY(), myGroup.moveToPoint.getX(), myGroup.moveToPoint.getX(), COLOR_MOVE_POINT, 1);
                }
            }
        }


        rc.endFrame();
    }

    @Override
    public void onInitializeStrategy() {
        rc = new RewindClient();

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
        Move move = mys.move;
        switch (move.getAction()) {

            case NONE:
                break;
            case CLEAR_AND_SELECT:
                // rc.rect(move.getLeft(), move.getTop(), move.getRight(), move.getBottom(), COLOR_CLEAR_AND_SELECT, 0);
                break;
            case ADD_TO_SELECTION:
                rc.rect(move.getLeft(), move.getTop(), move.getRight(), move.getBottom(), COLOR_ADD_TO_SELECT, 1);
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
                rc.circle(move.getX(), move.getY(), 20, COLOR_MOVE, 1);
                break;
            case ROTATE:
                break;
            case SCALE:
                break;
            case SETUP_VEHICLE_PRODUCTION:
                break;
            case TACTICAL_NUCLEAR_STRIKE:
                rc.circle(move.getX(), move.getY(), 4, Color.RED, 1);
                VehicleWrapper veh = mys.um.get(move.getVehicleId());
                if (veh != null) {
                    Color color = new Color(0, 255, 0, 100);
                    rc.circle(veh.getX(), veh.getY(), 4, color, 1);
                    rc.line(move.getX(), move.getY(), veh.getX(), veh.getY(), color, 1);
                }
                break;
        }

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
