import model.Facility;
import model.VehicleType;

public class FacilityWrapper {
    public Facility f;
    private final MyStrategy mys;
    public boolean isProductionSet;
    private Point2D point2D;
    public VehicleType productType;

    public FacilityWrapper(Facility facility, MyStrategy mys) {
        f = facility;
        this.mys = mys;
    }

    public void update(Facility facility) {
        //TODO track capturing?
        boolean oldIsMy = isMy();
        f = facility;

        if (isMy() != oldIsMy) {
            isProductionSet = false;
            productType = null;
        }
    }

    public boolean isMy() {
        return f.getOwnerPlayerId() == mys.me.getId();
    }

    public boolean shouldSetProduction() {
        return isMy() && !isProductionSet;
    }

    public Point2D getCenterPos() {
        if (point2D == null) {
            point2D = new Point2D(f.getLeft() + mys.game.getFacilityWidth() / 2, f.getTop() + mys.game.getFacilityHeight() / 2);
        }
        return point2D;
    }

    public Point2D getCenterCellPos() {
        return new Point2D((f.getLeft() + MyStrategy.WORLD_CELL_SIZE) / mys.cellSize,
                (f.getTop() + MyStrategy.WORLD_CELL_SIZE) / mys.cellSize);
    }
}
