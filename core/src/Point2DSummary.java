import java.awt.geom.Rectangle2D;
import java.util.DoubleSummaryStatistics;
import java.util.function.Consumer;

// summary will go along the lines of:
class Point2DSummary implements Consumer<Point2D> {
    DoubleSummaryStatistics x, y; // summary of stats collected so far

    Point2DSummary() {
        x = new DoubleSummaryStatistics();
        y = new DoubleSummaryStatistics();
    }

    // this is how we collect it:
    @Override
    public void accept(Point2D stat) {
        x.accept(stat.getX());
        y.accept(stat.getY());
    }

    public void combine(Point2DSummary other) {
        x.combine(other.x);
        y.combine(other.y);
    }

    // now for actual methods that return stuff. I will implement only average and min
    // but rest of them are not hard
    public Point2D average() {
        return new Point2D(x.getAverage(), y.getAverage());
    }

    public Point2D min() {
        return new Point2D(x.getMin(), y.getMin());
    }

    public Point2D max() {
        return new Point2D(x.getMax(), y.getMax());
    }

    public Rectangle2D getRectangle() {
        return new Rectangle2D.Double(x.getMin(), y.getMin(), x.getMax() - x.getMin(), y.getMax() - y.getMin());
    }
}
