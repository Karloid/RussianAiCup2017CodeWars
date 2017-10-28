package com.krld.rtslibgdxplayground;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.krld.rtslibgdxplayground.eg.*;

import java.awt.*;

public class EpicGameGL extends ApplicationAdapter implements UIDelegate {
    public static final int WORLD_SIZE = 2000;
    SpriteBatch batch;
    Texture img;

    BitmapFont font;

    private OrthographicCamera cam;
    private float rotationSpeed;
    private ShapeRenderer shapes;
    private int gameWidth;
    private int gameHeight;
    private boolean isNotReady;

    @Override
    public void create() {
        isNotReady = true;

        startGameStuff();



        batch = new SpriteBatch();
        font = new BitmapFont(true);

        shapes = new ShapeRenderer();

        rotationSpeed = 0.5f;

        // Constructs a new OrthographicCamera, using the given viewport width and height
        // Height is multiplied by aspect ratio.
        cam = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.setToOrtho(true, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        cam.update();
    }

    private void startGameStuff() {
        new Thread(() -> {
            System.out.println("Starting...");
            // test github
            gameWidth = 30;
            gameHeight = 30;
            int spawnUnitCount = 5;
            int cellSize = 16;
            World world = new World(gameWidth, gameHeight, spawnUnitCount, cellSize, this);
            Game game = new Game(world);
            Player player1 = new Player();

            player1.setColor(Color.RED);
            player1.setClassStategy(MyStrategy.class.getName());

            Player player2 = new Player();

            player2.setColor(Color.BLUE);
            player2.setClassStategy(DummyStrategy.class.getName());


            world.addPlayer(player1, 2, 2);
            world.addPlayer(player2, gameWidth - 2, gameHeight - 2);

            System.out.println("Run game...");
            game.run();
        }).start();

    }

    @Override
    public void render() {
        isNotReady = true;
        handleInput();
        cam.update();
        batch.setProjectionMatrix(cam.combined);

        Gdx.gl.glClearColor(0.95f, 0.95f, 0.95f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), cam.position.x - (cam.viewportWidth * cam.zoom) / 2
                , cam.position.y - (cam.viewportHeight * cam.zoom) / 2);
        font.draw(batch, "0x0", 0, 0);
        font.draw(batch, "100x100", 100, 100);
        font.draw(batch, "100x150", 100, 150);
        font.draw(batch, WORLD_SIZE + "x" + WORLD_SIZE, WORLD_SIZE, WORLD_SIZE);
        batch.end();

        shapes.setProjectionMatrix(cam.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(com.badlogic.gdx.graphics.Color.BLUE);
        shapes.circle(200, 200, 15);
        shapes.end();
        isNotReady = false;
    }

    private void handleInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            cam.zoom += 0.02;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
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
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            cam.rotate(-rotationSpeed, 0, 0, 1);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            cam.rotate(rotationSpeed, 0, 0, 1);
        }

        cam.zoom = MathUtils.clamp(cam.zoom, 0.1f, WORLD_SIZE / cam.viewportWidth);

        float effectiveViewportWidth = cam.viewportWidth * cam.zoom;
        float effectiveViewportHeight = cam.viewportHeight * cam.zoom;

        cam.position.x = MathUtils.clamp(cam.position.x, effectiveViewportWidth / 2f, WORLD_SIZE - effectiveViewportWidth / 2f);
        cam.position.y = MathUtils.clamp(cam.position.y, effectiveViewportHeight / 2f, WORLD_SIZE - effectiveViewportHeight / 2f);

    }

    @Override
    public void dispose() {
        batch.dispose();
    }

    @Override
    public void update() {
        //TODO something
    }

    @Override
    public boolean isNotReady() {
        return isNotReady;
    }
}
