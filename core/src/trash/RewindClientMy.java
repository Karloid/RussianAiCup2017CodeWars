package trash;

import java.io.PrintWriter;
import java.net.Socket;

public class RewindClientMy {

    private Socket socket;
    private PrintWriter out;

    public RewindClientMy(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Should be send on end of move function
     * all turn primitives can be rendered after that point
     */
    public void endFrame() {
        send("{\"type\":\"end\"}");
    }

    public void circle(double x, double y, double r, int color) {
        String fmt =
                "{\"type\": \"circle\", \"x\": %f, \"y\": %f, \"r\": %f, \"color\": %o}";
        send(String.format(fmt, x, y, r, color));
    }

    public void rect(double x1, double y1, double x2, double y2, int color) {
        String fmt =
                "{\"type\": \"rectangle\", \"x1\": %f, \"y1\": %f, \"x2\": %f, \"y2\": %f, \"color\": %o}";
        send(String.format(fmt, x1, y1, x2, y2, color));
    }

    public void line(double x1, double y1, double x2, double y2, int color) {
        String fmt =
                "{\"type\": \"line\", \"x1\": %f, \"y1\": %f, \"x2\": %f, \"y2\": %f, \"color\": %o}";
        send(String.format(fmt, x1, y1, x2, y2, color));
    }


    /**
     * Living unit - circle with HP bar
     *
     * @param x      .
     * @param y      .
     * @param r      .
     * @param hp     current life level
     * @param max_hp maximum life level
     * @param enemy  3 state variable: 1 - for enemy; -1 - for friend; 0 - neutral.
     * @param course parameter needed only to properly rotate textures (it unused by untextured units)
     * @param utype  define used texture, value 0 means 'no texture'. For supported textures see enum UnitType in Frame.h
     */
    public void livingUnit(double x, double y, double r, int hp, int max_hp, int enemy, double course, int utype) {
        String fmt =
                "{\"type\": \"unit\", \"x\": %f, \"y\": %f, \"r\": %f, \"hp\": %o, \"max_hp\": %o, \"enemy\": %o," +
                        " \"unit_type\":%o, \"course\": %.3f}";
        send(String.format(fmt, x, y, r, hp, max_hp, enemy, utype, course));
    }

    /**
     * Pass arbitrary user message to be stored in frame
     * Message content displayed in separate window inside viewer
     * Can be used several times per frame
     *
     * @param msg .
     */
    public void message(String msg) {
        String s = "{\"type\": \"message\", \"message\" : \"" + msg + " \"}";
        send(s);
    }

    private void send(String s) {
        out.println(s);
        System.out.println("sent " + s);
    }

    public void close() {
        try {
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Example of usage
     *
     * @param args
     */
    public static void main(String[] args) {
        RewindClientMy rc = new RewindClientMy("localhost", 21000);

        for (int i = 0; i < 200; i++) {
            try {
                rc.circle(10, 10, 100 * Math.random() + 1, 0xffffaa);
                rc.rect(0.3, 0.5, 15, 33, 0xffaaff);
                rc.line(0.3, 0.5, 15, 33, 0xffaaff);
                rc.livingUnit(50, 100, 15, 33, 100, 1, 0.6, 0);
                rc.message("test");
                rc.endFrame();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        rc.close();
    }
}
