public class NuclearStrike {
    public final VehicleWrapper myVehicle;
    public final VehicleWrapper target;
    public final MyStrategy myStrategy;
    /**
     * or points
     */
    public final double dmg;
    public int scheduledAt;
    public int startedAt = -1;
    public Point2D actualTarget;

    public NuclearStrike(VehicleWrapper myVehicle, VehicleWrapper target, MyStrategy myStrategy) {
        this.myVehicle = myVehicle;
        this.target = target;
        this.myStrategy = myStrategy;

        //TODO calc dmg
        dmg = myStrategy.um.streamVehicles(Ownership.ENEMY)
                .mapToDouble(enemyVeh -> {
                    double distanceTo = enemyVeh.getDistanceTo(target);
                    if (distanceTo > 50) {
                        return 0;
                    }
                    return 10; //TODO calc dmg
                }).sum();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NuclearStrike{");
        sb.append("myVehicle=").append(myVehicle);
        sb.append(", target=").append(target);
        sb.append(", dmg=").append(dmg);
        sb.append('}');
        return sb.toString();
    }
}
