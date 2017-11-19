public class TickStats {
    public int healedPoints;
    public int damagedPoints;
    public int damagedUnits;
    public int destroyedUnits;
    public int remainingUnits;
    public int remainingHp;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TickStats{");
        sb.append("healedPoints=").append(healedPoints);
        sb.append(", damagedPoints=").append(damagedPoints);
        sb.append(", damagedUnits=").append(damagedUnits);
        sb.append(", destroyedUnits=").append(destroyedUnits);
        sb.append('}');
        return sb.toString();
    }

    public boolean isNonEmpty() {
        return healedPoints != 0 || damagedPoints != 0 || damagedUnits != 0 || destroyedUnits != 0;
    }
}
