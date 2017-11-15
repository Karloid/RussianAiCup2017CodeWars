import model.*;

import java.awt.*;
import java.util.Collection;

public class RewindClientWrapper implements MyStrategyPainter {

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

                rc.circle(x, y, mys.game.getTacticalNuclearStrikeRadius(), color, 0);
                rc.circle(x, y, 10, color, 0);

                long vehicleId = player.getNextNuclearStrikeVehicleId();
                VehicleWrapper veh = mys.um.get(vehicleId);
                if (veh != null) {
                    rc.line(x, y, veh.v.getX(), veh.v.getY(), color, 0);
                    rc.circle(veh.v.getX(), veh.v.getY(), veh.v.getRadius() * 4, color, 0);
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
        if (move.getAction() == ActionType.TACTICAL_NUCLEAR_STRIKE) {
            rc.circle(move.getX(), move.getY(), 4, Color.RED, 0);
            VehicleWrapper veh = mys.um.get(move.getVehicleId());
            if (veh != null) {
                Color color = new Color(0, 255, 0, 100);
                rc.circle(veh.getX(), veh.getY(), 4, color, 0);
                rc.line(move.getX(), move.getY(), veh.getX(), veh.getY(), color, 0);
            }
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
