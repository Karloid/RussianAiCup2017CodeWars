import model.TerrainType;
import model.Vehicle;
import model.VehicleType;
import model.WeatherType;

public class VehicleWrapper {
    public static final Point2D NOT_MOVING = new Point2D(0, 0);
    public final boolean isEnemy;
    private Point2D moveVector;
    public Vehicle v;
    private final MyStrategy mys;
    public int hpChangedAt;
    public int movedAt;
    int hpDelta;

    public VehicleWrapper(Vehicle v, MyStrategy mys) {
        this.v = v;
        this.mys = mys;
        setUpdatedAt(mys.world.getTickIndex());
        moveVector = NOT_MOVING;
        isEnemy = mys.me.getId() != v.getPlayerId();
    }

    public void setUpdatedAt(int tickIndex) {
        hpChangedAt = tickIndex;
        movedAt = tickIndex;
    }

    public void update(Vehicle newVehicle) {
        int currentTick = mys.world.getTickIndex();
        if (v.getX() != newVehicle.getX() || v.getY() != newVehicle.getY()) {
            moveVector = Point2D.vector(v.getX(), v.getY(), newVehicle.getX(), newVehicle.getY());
            movedAt = currentTick;
        }

        hpDelta = newVehicle.getDurability() - v.getDurability();
        if (hpDelta != 0) {
            hpChangedAt = currentTick;
        }

        v = newVehicle;

     /*   if (v.getId() == 10) {
            mys.log("DEBUG my terrain " + getCurrentPlace() + " my type " + v.getType() + " x y " + (int) (getX() / 32) + " " + (int) (getY() / 32) );
        }*/
    }

    public double getDistanceTo(VehicleWrapper sec) {
        return Point2D.getDistance(v, sec.v);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("VW{");
        sb.append(", type=").append(v.getType());
        sb.append(", hp=").append(v.getDurability());
        sb.append(", XY=").append((int) v.getX()).append(" - ").append((int) v.getY());
        sb.append(", moveVector=").append(moveVector);
        sb.append(", hpChangedAt=").append(hpChangedAt);
        sb.append(", movedAt=").append(movedAt);
        sb.append('}');
        return sb.toString();
    }

    public Point2D getPos(int ticks) {
        return new Point2D(getX(ticks), getY(ticks));
    }

    public double getY(int ticks) {
        Point2D mv = getMoveVector();
        return v.getY() + mv.getY() * ticks;
    }

    public double getX(int ticks) {
        Point2D mv = getMoveVector();
        return v.getX() + mv.getX() * ticks;
    }

    public double getDistanceToPredictTarget(VehicleWrapper target, int tick) {
        return Point2D.getDistance(getX(), getY(), target.getX(tick), target.getY(tick));
    }

    public double getDistanceToPredictBoth(VehicleWrapper target, int tick) {
        return Point2D.getDistance(getX(tick), getY(tick), target.getX(tick), target.getY(tick));
    }

    double getX() {
        return v.getX();
    }

    double getY() {
        return v.getY();
    }

    public Point2D getMoveVector() {
        return movedAt == mys.world.getTickIndex() ? moveVector : NOT_MOVING;
    }

    public double getActualVisionRange() {
        double d = v.getVisionRange();

        double k = getVisibleKoeff();

        d *= k;

        return d - .5;
    }

    public String getCurrentPlace() {
        int x = (int) (getX() / 32);
        int y = (int) (getY() / 32);

        if (v.getType() == VehicleType.TANK || v.getType() == VehicleType.IFV || v.getType() == VehicleType.ARRV) {
            TerrainType terrainType = mys.terrainTypeByCellXY[x][y];
            return terrainType.toString();
        } else {
            WeatherType weatherType = mys.weatherTypeByCellXY[x][y];
            return weatherType.toString();
        }
    }

    private double getVisibleKoeff() {
        int x = (int) (getX() / 32);
        int y = (int) (getY() / 32);

        double k = 1;
        if (v.getType() == VehicleType.TANK || v.getType() == VehicleType.IFV || v.getType() == VehicleType.ARRV) {
            TerrainType terrainType = mys.terrainTypeByCellXY[x][y];
            switch (terrainType) {
                case PLAIN:
                    break;
                case SWAMP:
                    k = mys.game.getSwampTerrainVisionFactor();
                    break;
                case FOREST:
                    k = mys.game.getForestTerrainSpeedFactor();
                    break;
            }
        } else {
            WeatherType weatherType = mys.weatherTypeByCellXY[x][y];
            switch (weatherType) {
                case CLEAR:
                    break;
                case CLOUD:
                    k = mys.game.getCloudWeatherVisionFactor();
                    break;
                case RAIN:
                    k = mys.game.getRainWeatherVisionFactor();
                    break;
            }
        }
        return k;
    }

    public int getCellX(int cellSize) {
        return (int) (getX() / cellSize);
    }

    public int getCellY(int cellSize) {
        return (int) (getY() / cellSize);
    }
}
