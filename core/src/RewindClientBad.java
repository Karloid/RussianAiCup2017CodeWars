import java.io.PrintWriter;
import java.net.Socket;

public class RewindClientBad {

    private Socket socket;
    private PrintWriter out;

    public RewindClientBad(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Example
     *
     * @param args
     */
    public static void main(String[] args) {
        RewindClientBad rc = new RewindClientBad("localhost", 7000);

        rc.circle(10, 10, 10, 0xffffaa);

        rc.line(0.3, 0.5, 15, 33, 0xffaaff);
        rc.rectangle(0.3, 0.5, 15, 33, 0xffaaff);
        rc.livingUnit(0.3, 0.5, 15, 33, true, 0);
        rc.endFrame();
        rc.close();
    }

    private void rectangle(double x1, double y1, double x2, double y2, int color) {
        send("{\"type\": \"rectangle\"" +
                ", \"x1\": " + formatDouble(x1) +
                ", \"y1\": " + formatDouble(y1) +
                ", \"x2\": " + formatDouble(x2) +
                ", \"y2\": " + formatDouble(y2) +
                ", \"color\": " + color + "}");
    }

    private void line(double x1, double y1, double x2, double y2, int color) {
        send("{\"type\": \"line\"" +
                ", \"x1\": " + formatDouble(x1) +
                ", \"y1\": " + formatDouble(y1) +
                ", \"x2\": " + formatDouble(x2) +
                ", \"y2\": " + formatDouble(y2) +
                ", \"color\": " + color + "}");
    }

    private void circle(double x, double y, double radius, int color) {
        send("{\"type\": \"circle\"" +
                ", \"x\": " + formatDouble(x) +
                ", \"y\": " + formatDouble(y) +
                ", \"r\": " + formatDouble(radius) +
                ", \"color\": " + color + "}");
    }

    private void livingUnit(double x, double y, double hp, double maxHp, boolean enemy, int unitType) {
        send("{\"type\": \"unit\"" +
                ", \"x\": " + formatDouble(x) +
                ", \"y\": " + formatDouble(y) +
                ", \"hp\": " + formatDouble(hp) +
                ", \"max_hp\": " + formatDouble(maxHp) +
                ", \"enemy\": " + enemy +
                ", \"course\": " + formatDouble(hp) +
                ", \"unit_type\": " + unitType + "}");
    }

    private void endFrame() {
        send("{\"type\": \"end\"}");
    }

    private String formatDouble(double v) {
        return String.format("%.5f", v);
    }

    private void send(String s) {
        out.println(s);
    }

    private void close() {
        try {
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
