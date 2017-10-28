package com.krld.rtslibgdxplayground;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.krld.rtslibgdxplayground.eg.UIDelegate;
import com.krld.rtslibgdxplayground.eg.World;
import com.krld.rtslibgdxplayground.eg.models.*;
import com.krld.rtslibgdxplayground.eg.strats.DummyStrategy;
import com.krld.rtslibgdxplayground.eg.strats.MyStrategy;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class EpicGameGL extends ApplicationAdapter implements UIDelegate {
    public static final int WORLD_SIZE = 2000;
    public static final int CELL_SIZE = 12;

    SpriteBatch batch;

    Texture img;

    BitmapFont font;
    private OrthographicCamera cam;
    private float rotationSpeed;
    private ShapeRenderer g;
    private int gameWidth = 60;
    private int gameHeight = 60;
    private World world;
    private int spawnUnitCount = 30;

    @Override
    public void create() {

        startGameStuff();


        batch = new SpriteBatch();
        font = new BitmapFont(true);

        g = new ShapeRenderer();

        rotationSpeed = 0.5f;

        // Constructs a new OrthographicCamera, using the given viewport width and height
        // Height is multiplied by aspect ratio.
        cam = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.zoom = 0.9f;
        cam.update();
    }

    private void startGameStuff() {
        CountDownLatch cdl = new CountDownLatch(1);
        new Thread(() -> {
            System.out.println("Starting...");
            // test github
            world = new World(gameWidth, gameHeight, spawnUnitCount, CELL_SIZE, this);
            Game game = new Game(world);
            Player player1 = new Player();

            player1.setColor(Color.BLACK);
            player1.setClassStategy(MyStrategy.class.getName());

            Player player2 = new Player();

            player2.setColor(Color.BLUE);
            player2.setClassStategy(DummyStrategy.class.getName());


            world.addPlayer(player1, 2, 2);
            world.addPlayer(player2, gameWidth - 2, gameHeight - 2);

            System.out.println("Run game...");
            cdl.countDown();
            game.run();
        }).start();
        try {
            cdl.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render() {
        world.reentrantLock.lock();
        handleInput();
        cam.update();
        g.setProjectionMatrix(cam.combined);

        batch.setProjectionMatrix(cam.combined);

        Gdx.gl.glClearColor(0.95f, 0.95f, 0.95f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        drawCorpses();
        batch.end();

        g.begin(ShapeRenderer.ShapeType.Filled);

        drawTerrain();
        drawGrid();
        drawUnitsShape();

        drawMoves();
        drawDebug();

        g.end();
        batch.begin();

        drawUnitsFont();

        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), cam.position.x - (cam.viewportWidth * cam.zoom) / 2
                , cam.position.y - (cam.viewportHeight * cam.zoom) / 2);
        //font.draw(batch, "0x0", 0, 0);
        //font.draw(batch, "100x100", 100, 100);
        //font.draw(batch, "100x150", 100, 150);
        //font.draw(batch, WORLD_SIZE + "x" + WORLD_SIZE, WORLD_SIZE, WORLD_SIZE);

        drawScore();
        batch.end();
        world.reentrantLock.unlock();
    }

    private void drawUnitsFont() {

        for (Unit unit : world.units) {
            font.setColor(unit.player.getInvertedColor());
            String str = "";
            if (unit.type == UnitType.SOLDIER) {
                str = "S";
            } else if (unit.type == UnitType.MEDIC) {
                str = "M";
            }

            font.draw(batch, str, unit.x * CELL_SIZE + CELL_SIZE / 4, unit.y * CELL_SIZE /*+ CELL_SIZE*/);
        }
    }

    private void drawDebug() {

      /*  g.setColor(Color.GRAY);
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                if ((!world.units.get(1).getStrategy()).available[x][y]) {
                    g.setColor(Color.YELLOW);
                    g.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
                g.setColor(Color.GRAY);
                g.drawString(strategy.path[x][y] + " ", x * CELL_SIZE + CELL_SIZE / 3, y * CELL_SIZE + CELL_SIZE / 2);
            }
            //     System.out.println();
        }       */
    }

    private void drawScore() {
        Collections.sort(world.players);


        float topX = cam.position.x - (cam.viewportWidth * cam.zoom) / 2;
        float topY = cam.position.y - (cam.viewportHeight * cam.zoom) / 2;

        int i = 1;
        for (Player player : world.players) {
            i++;
            font.setColor(player.getColor());

            font.draw(batch, player.getClassStategy().substring(player.getClassStategy().lastIndexOf(".")) +
                    " : " + player.score, 10 + topX, 30 * i + topY);
        }
        font.setColor(com.badlogic.gdx.graphics.Color.BLACK);
        font.draw(batch, world.getMoveCount() + "", 10, world.getHeight() * CELL_SIZE - 30);
    }

    private void drawTerrain() {
        g.setColor(com.badlogic.gdx.graphics.Color.LIGHT_GRAY);
        for (int x = 0; x < world.width; x++)
            for (int y = 0; y < world.height; y++) {
                if (world.getCells()[x][y] == CellType.COVER) {
                    g.rect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }
            }

    }

    private void drawGrid() {
        g.setColor(Color.DARK_GRAY);
        int scaledHeight = world.height * CELL_SIZE;
        int scaledWidth = world.width * CELL_SIZE;
        for (int x = 0; x <= scaledWidth; x = x + CELL_SIZE) {
            g.rectLine(x, 0, x, scaledHeight, 1.1f);
        }
        for (int y = 0; y <= world.height * CELL_SIZE; y = y + CELL_SIZE) {
            g.rectLine(0, y, scaledWidth, y, 1.1f);
        }
    }

    private void drawUnitsShape() {
        for (Unit unit : world.units) {
            g.setColor(unit.player.getColor());
            int hpBarHeight = (int) (((CELL_SIZE * 1f) / 100) * unit.hp);
            //   System.out.println(" hpBarHeight" + hpBarHeight);
            g.rect(unit.x * CELL_SIZE, unit.y * CELL_SIZE + CELL_SIZE - hpBarHeight, CELL_SIZE, hpBarHeight);
        }
    }

    private void drawCorpses() {
        for (Corpse corpse : world.corpses) {
            Color newColor = new Color(corpse.player.getColor());
            newColor.a = .5f;
            font.setColor(newColor);
            String str = "";
            if (corpse.type == UnitType.SOLDIER) {
                str = "S";
            } else if (corpse.type == UnitType.MEDIC) {
                str = "M";
            }

            font.draw(batch, str, corpse.x * CELL_SIZE + CELL_SIZE / 4, corpse.y * CELL_SIZE);
        }

    }

    private void drawMoves() {
        for (Map.Entry<Unit, Move> entry : world.moves.entrySet()) {
            Unit unit = entry.getKey();
            Move move = entry.getValue();
            if (move.getAction() == ActionType.SHOOT) {
                g.setColor(Color.RED);
                g.rect(move.getX() * CELL_SIZE + 2, move.getY() * CELL_SIZE + CELL_SIZE / 2 + 1, CELL_SIZE - 3, CELL_SIZE / 4);
                g.setColor(unit.player.getColor());
                g.line(move.getX() * CELL_SIZE + CELL_SIZE / 2, move.getY() * CELL_SIZE + CELL_SIZE / 2, unit.getX() * CELL_SIZE + CELL_SIZE / 2, unit.getY() * CELL_SIZE + CELL_SIZE / 2);
            } else if (move.getAction() == ActionType.HEAL) {
                g.setColor(Color.GREEN);
                com.krld.rtslibgdxplayground.eg.models.Point pointOnDirection = world.getPointOnDirection(new com.krld.rtslibgdxplayground.eg.models.Point(unit), move.getDirection());
                g.line(pointOnDirection.x * CELL_SIZE + 2, pointOnDirection.y * CELL_SIZE + CELL_SIZE / 2 - 1, CELL_SIZE - 3, CELL_SIZE / 4);
            }
        }

    }

    private void handleInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.PLUS)) {
            cam.zoom += 0.02;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Q) || Gdx.input.isKeyPressed(Input.Keys.MINUS)) {
            cam.zoom -= 0.02;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            cam.translate(-3, 0, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            cam.translate(3, 0, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            cam.translate(0, -3, 0);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            cam.translate(0, 3, 0);
        }
       /* if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            cam.rotate(-rotationSpeed, 0, 0, 1);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            cam.rotate(rotationSpeed, 0, 0, 1);
        }*/

        cam.zoom = MathUtils.clamp(cam.zoom, 0.05f, WORLD_SIZE / cam.viewportWidth);

        float effectiveViewportWidth = cam.viewportWidth * cam.zoom;
        float effectiveViewportHeight = cam.viewportHeight * cam.zoom;

        cam.position.x = MathUtils.clamp(cam.position.x, effectiveViewportWidth / -2f, WORLD_SIZE - effectiveViewportWidth / 2f);
        cam.position.y = MathUtils.clamp(cam.position.y, effectiveViewportHeight / -2f, WORLD_SIZE - effectiveViewportHeight / 2f);

    }

    @Override
    public void dispose() {
        batch.dispose();
    }

    @Override
    public void update() {
        //TODO something
    }

}
