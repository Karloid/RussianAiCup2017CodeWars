import java.awt.geom.Rectangle2D;

public class PointsInfo {
    public final Point2D averagePoint;
    public final Rectangle2D rect;

    public PointsInfo(Point2DSummary summary) {
        averagePoint = summary.average();
        rect = summary.getRectangle();
    }
}
