import model.Facility;

public class FacilityWrapper {
    public Facility f;
    private final MyStrategy mys;
    public boolean isProductionSet;

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
        }
    }

    private boolean isMy() {
        return f.getOwnerPlayerId() == mys.me.getId();
    }

    public boolean shouldSetProduction() {
        return isMy() && !isProductionSet;
    }
}
