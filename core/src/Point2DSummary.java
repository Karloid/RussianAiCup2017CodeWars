import java.util.DoubleSummaryStatistics;
import java.util.function.Consumer;

// summary will go along the lines of:
class Point2DSummary implements Consumer<Point2D> {
    DoubleSummaryStatistics a, b; // summary of stats collected so far

    Point2DSummary() {
        a = new DoubleSummaryStatistics();
        b = new DoubleSummaryStatistics();
    }

    // this is how we collect it:
    @Override
    public void accept(Point2D stat) {
        a.accept(stat.getX());
        b.accept(stat.getY());
    }

    public void combine(Point2DSummary other) {
        a.combine(other.a);
        b.combine(other.b);
    }

    // now for actual methods that return stuff. I will implement only average and min
    // but rest of them are not hard
    public Point2D average() {
        return new Point2D(a.getAverage(), b.getAverage());
    }

    public Point2D min() {
        return new Point2D(a.getMin(), b.getMin());
    }
}
