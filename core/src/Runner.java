import model.Game;
import model.Move;
import model.Player;
import model.PlayerContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public final class Runner {
    private final RemoteProcessClient remoteProcessClient;
    private final String token;
    private static boolean hasArgs;

    public static void main(String[] args) throws IOException {


        hasArgs = args.length != 0;
        if (hasArgs) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        new Thread(() -> {
            if (hasArgs) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runProc("java", "-cp", "6.5.jar", "Runner");
            }
        }).start();
        new Runner(new String[]{"127.0.0.1", hasArgs ? "31001" : "31002", "0000000000000000"}).run();


    }

    private Runner(String[] args) throws IOException {
        remoteProcessClient = new RemoteProcessClient(args[0], Integer.parseInt(args[1]));
        token = args[2];
    }

    @SuppressWarnings("WeakerAccess")
    public void run() throws IOException {
        try {
            remoteProcessClient.writeTokenMessage(token);
            remoteProcessClient.writeProtocolVersionMessage();
            remoteProcessClient.readTeamSizeMessage();
            Game game = remoteProcessClient.readGameContextMessage();

            MyStrategy strategy = new MyStrategy();

            strategy.logsEnabled = hasArgs;

            PlayerContext playerContext;

            while ((playerContext = remoteProcessClient.readPlayerContextMessage()) != null) {
                Player player = playerContext.getPlayer();
                if (player == null) {
                    break;
                }

                Move move = new Move();
                strategy.move(player, playerContext.getWorld(), game, move);

                remoteProcessClient.writeMoveMessage(move);
            }
        } finally {
            remoteProcessClient.close();
        }
    }

    private static void runProc(String... runProc) {
        new Thread(() -> {
            try {
                Process process = new ProcessBuilder(runProc).start();
                System.out.println("started process: " + Arrays.toString(runProc));

                InputStream inputStream = process.getInputStream();


                readStream(process.getErrorStream(), runProc[0]);
                readStream(inputStream, runProc[0]);

            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();

    }

    private static void readStream(InputStream inputStream, String s) throws IOException {
        new Thread(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    System.out.println(s + ": " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

    }

}
