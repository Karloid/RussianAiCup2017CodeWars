import java.util.stream.Collector;

public class Utils {
    public static final Collector<Point2D, Point2DSummary, Point2D> POINT_COLLECTOR =
            Collector.of(
                    () -> new Point2DSummary(),          // supplier
                    (j, p) -> j.accept(p),  // accumulator
                    (j1, j2) -> {
                        Point2DSummary v = new Point2DSummary();
                        v.accept(j1.average());
                        v.accept(j2.average());
                        return v;
                    },               // combiner
                    summary -> summary.average());

    public static String format(double v) {
        return String.format("%.2f", v);
    }
}
