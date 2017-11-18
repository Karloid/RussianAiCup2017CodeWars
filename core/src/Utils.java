import java.util.stream.Collector;

public class Utils {
    public static final Collector<Point2D, Point2DSummary, PointsInfo> POINT_COLLECTOR =
            Collector.of(
                    () -> new Point2DSummary(),          // supplier
                    (j, p) -> j.accept(p),  // accumulator
                    (j1, j2) -> {
                        Point2DSummary v = new Point2DSummary();
                        v.accept(j1.average());
                        v.accept(j2.average());
                        return v;
                    },               // combiner
                    summary -> new PointsInfo(summary));
    
    public static final String LOG_NUCLEAR_STRIKE = "NUCLEAR_STRIKE";
    static final String LOG_MOVING = "MOVING";

    public static String format(double v) {
        return String.format("%.2f", v);
    }
}
