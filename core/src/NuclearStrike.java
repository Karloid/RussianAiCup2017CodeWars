import model.VehicleType;

import java.util.Comparator;

public class NuclearStrike {
    public static final int PREDICTION_TICK = 33;
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
    public int actualDmg;

    public NuclearStrike(VehicleWrapper myVehicle, VehicleWrapper target, MyStrategy myStrategy) {
        this.myVehicle = myVehicle;
        this.target = target;
        this.myStrategy = myStrategy;

        //TODO calc dmg
        double maxDmg = myStrategy.game.getMaxTacticalNuclearStrikeDamage();
        double maxRadius = myStrategy.game.getTacticalNuclearStrikeRadius();

        long myId = myStrategy.me.getId();


        dmg = myStrategy.um.streamVehicles()
                .mapToDouble(veh -> {
                    double distanceTo = veh.getDistanceToPredictBoth(target, PREDICTION_TICK);

                    if (distanceTo > maxRadius) {
                        return 0;
                    }

                    double dmg = (1 - distanceTo / maxRadius) * maxDmg;
                    if (dmg > veh.v.getDurability()) {
                        dmg = dmg + 20;
                    }

                    if (veh.v.getType() == VehicleType.ARRV) {
                        dmg *= 0.7f;
                    }
                    
                    boolean isEnemy = veh.v.getPlayerId() != myId;
                    if (!isEnemy) {
                        dmg = dmg * -1.5;
                    }
                    return dmg;
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

    public static NuclearStrike getMaxDmg(MyStrategy mys) {
        return mys.um.streamVehicles(Ownership.ENEMY)
                .flatMap((VehicleWrapper v) ->
                        mys.um.streamVehicles(Ownership.ALLY)
                                .filter(myVehicle ->
                                        myVehicle.getDistanceToPredictTarget(v, PREDICTION_TICK) < myVehicle.v.getVisionRange())
                                .map(myVehicle -> new NuclearStrike(myVehicle, v, mys)))
                .max(Comparator.comparingDouble(o -> o.dmg))
                .orElse(null);
    }
}
