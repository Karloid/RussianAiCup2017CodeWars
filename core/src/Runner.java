import model.Game;
import model.Move;
import model.Player;
import model.PlayerContext;

import java.io.*;
import java.util.Arrays;

public final class Runner {
    private final RemoteProcessClient remoteProcessClient;
    private final String token;
    private static boolean hasArgs;

    public static void main(String[] args) throws IOException {


        hasArgs = args.length != 0;
        if (hasArgs) {
           // runProc(null, false, "./rewindviewer");
            try {
                Thread.sleep(2300);
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
                //runProc(null, true, "java", "-cp", "13.jar", "Runner");
                //runProc(null, true, "java", "-cp", "15.jar", "Runner");
                //runProc(null, true, "java", "-cp", "15.1_PP_IWHOUT_ARRV.jar", "Runner");
                //runProc(null, true, "java", "-cp", "16.jar", "Runner");
                //runProc(null, true, "java", "-cp", "17.jar", "Runner");
                //runProc(null, true, "java", "-cp", "19.jar", "Runner");
                //runProc(null, true, "java", "-cp", "21.jar", "Runner");
                //runProc(null, true, "java", "-cp", "22.jar", "Runner");
                //runProc(null, true, "java", "-cp", "22.3.jar", "Runner");
                //runProc(null, true, "java", "-cp", "22.5.jar", "Runner");
                runProc(null, true, "java", "-cp", "22.6.jar", "Runner");
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
        MyStrategy strategy = new MyStrategy();
        try {
            remoteProcessClient.writeTokenMessage(token);
            remoteProcessClient.writeProtocolVersionMessage();
            remoteProcessClient.readTeamSizeMessage();
            Game game = remoteProcessClient.readGameContextMessage();



            strategy.logsEnabled = hasArgs;
            if (hasArgs) {
                strategy.setPainter(new RewindClientWrapper());
            }

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
        System.out.println("total time elapsed: "+ strategy.elapsed);
    }

    @SuppressWarnings("SameParameterValue")
    private static void runProc(File directory, boolean readInputStream, String... runProc) {
        new Thread(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder(runProc);
                if (directory != null) {
                    processBuilder.directory(directory);
                }

                Process process = processBuilder.start();
                System.out.println("started process: " + Arrays.toString(runProc));

                if (readInputStream) {
                    InputStream inputStream = process.getInputStream();


                    readStream(process.getErrorStream(), runProc[0]);
                    readStream(inputStream, runProc[0]);
                }

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
