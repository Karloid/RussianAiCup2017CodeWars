import model.VehicleType;

import java.util.Comparator;

public class NuclearStrike {
    public static final int PREDICTION_TICK = 0;
    public static final int KILL_BONUS = 45;
    public final VehicleWrapper myVehicle;
    public final VehicleWrapper target;
    public final MyStrategy myStrategy;
    /**
     * or points
     */
    public double predictedDmg;
    public int createdAt;
    public int startedAt = -1;
    public Point2D actualTarget;
    public int actualTs;
    public boolean myVehicleDidSurvive;
    public TickStats myStats;
    public TickStats enemyStats;
    public boolean succeed;
    public boolean canceled;
    public int predictedAffectedUnits;

    public NuclearStrike(VehicleWrapper myVehicle, VehicleWrapper target, MyStrategy myStrategy) {
        this.myVehicle = myVehicle;
        this.target = target;
        this.myStrategy = myStrategy;
        calcPredicted(myVehicle, target, myStrategy);
    }

    private void calcPredicted(VehicleWrapper myVehicle, VehicleWrapper target, MyStrategy myStrategy) {
        //TODO calc predictedDmg
        double maxDmg = myStrategy.game.getMaxTacticalNuclearStrikeDamage();
        double maxRadius = myStrategy.game.getTacticalNuclearStrikeRadius();

        long myId = myStrategy.me.getId();

        if (myVehicle.v.getDurability() < Math.min(50, myStrategy.um.maxMyHp * 0.8f)) {
            predictedDmg = 0;
            return;
        }

        predictedAffectedUnits = 0;
        predictedDmg = myStrategy.um.streamVehicles()
                .mapToDouble(veh -> {
                    double distanceTo;
                    if (actualTarget != null) {
                        if (veh.isEnemy) {
                            distanceTo = veh.getPos(0).getDistanceTo(actualTarget);
                        } else {
                            distanceTo = veh.getPos(30).getDistanceTo(actualTarget);
                        }
                    } else {
                        if (veh.isEnemy) {
                            distanceTo = veh.getDistanceToPredictBoth(target, 0);
                        } else {
                            distanceTo = veh.getPos(30).getDistanceTo(target);
                        }
                    }

                    if (distanceTo > maxRadius) {
                        return 0;
                    }

                    double dmg = (1 - distanceTo / maxRadius) * maxDmg;
                    if (dmg > veh.v.getDurability()) {
                        dmg = dmg + KILL_BONUS;
                    }

                    if (veh.v.getType() == VehicleType.ARRV) {
                        dmg *= 0.2f;
                    }

                    boolean isEnemy = veh.isEnemy;
                    if (!isEnemy) {
                        dmg = dmg * -1.5;
                    }
                    predictedAffectedUnits += 1;
                    return dmg;
                }).sum();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NuclearStrike{");
        sb.append("myVehicle=").append(myVehicle);
        sb.append(", target=").append(target);
        sb.append(", predictedDmg=").append(predictedDmg);
        sb.append(", predictedAffectedUnits=").append(predictedAffectedUnits);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", startedAt=").append(startedAt);
        sb.append(", actualTarget=").append(actualTarget);
        sb.append(", actualTs=").append(actualTs);
        sb.append(", myVehicleDidSurvive=").append(myVehicleDidSurvive);
        sb.append(", myStats=").append(myStats);
        sb.append(", enemyStats=").append(enemyStats);
        sb.append(", ").append(canceled ? "CANCELED" : succeed ? "SUCCEED" : actualTs == 0 ? "NOT_FIRED_YET" : "FAILED");
        sb.append('}');
        return sb.toString();
    }

    public static NuclearStrike getMaxDmg(MyStrategy mys, int minNuclearDmg) {
        return mys.um.streamVehicles(Ownership.ENEMY)
                .flatMap((VehicleWrapper v) ->
                        mys.um.streamVehicles(Ownership.ALLY)
                                .filter(myVehicle ->
                                        myVehicle.getDistanceToPredictTarget(v, PREDICTION_TICK) < myVehicle.getActualVisionRange())
                                .map(myVehicle -> new NuclearStrike(myVehicle, v, mys)))
                .filter(nuclearStrike -> nuclearStrike.predictedDmg > minNuclearDmg)
                .max(Comparator.comparingDouble(o -> o.predictedDmg))
                .orElse(null);
    }

    public void finish() {
        actualTs = myStrategy.world.getTickIndex();
        myStats = myStrategy.um.myStats;
        enemyStats = myStrategy.um.enemyStats;

        myVehicleDidSurvive = myVehicle.v.getDurability() > 0;

        if (enemyStats.damagedPoints < -myStrategy.getMinNuclearDmg()) {
            succeed = true;
        }
        myStrategy.didNuclearStrikes.add(this);
        myStrategy.log(Utils.LOG_NUCLEAR_STRIKE + " was done " + this);
    }

    public void recalcPredicted() {

    }
}
