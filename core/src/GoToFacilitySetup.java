import java.util.HashMap;
import java.util.Map;

public class GoToFacilitySetup {
    public Map<VehicleGroupInfo, FacilityWrapper> map = new HashMap<>();
    private Double sum;

    public double getSum() {
        if (sum == null) {
            sum = 0d;
            for (Map.Entry<VehicleGroupInfo, FacilityWrapper> entry : map.entrySet()) {
                sum += entry.getKey().getAveragePoint().getDistanceTo(entry.getValue());
            }
        }
        return sum;
    }
}
