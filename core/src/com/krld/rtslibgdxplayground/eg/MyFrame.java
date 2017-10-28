package com.krld.rtslibgdxplayground.eg;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Map;


public class MyFrame extends JFrame {
    private static int cellSize;
    public static final int Y_OFFSET = 25;
    public static final int X_OFFSET = 5;
    private final World world;
    boolean isPainting;
    private final MyPanel panel;

    public MyFrame(World world, int cellSize) throws HeadlessException {
        this.world = world;
        MyFrame.cellSize = cellSize;
        setSize(world.width * MyFrame.cellSize + X_OFFSET * 2, world.height * MyFrame.cellSize + Y_OFFSET + 7);
        setVisible(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        panel = new MyPanel();
        this.add(panel);
        panel.setSize(getSize());

        isPainting = false;
    }

    public void update() {
        panel.repaint();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
    }

    private void drawDebug(Graphics g) {

      /*  g.setColor(Color.GRAY);
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                if ((!world.units.get(1).getStrategy()).available[x][y]) {
                    g.setColor(Color.YELLOW);
                    g.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
                }
                g.setColor(Color.GRAY);
                g.drawString(strategy.path[x][y] + " ", x * cellSize + cellSize / 3, y * cellSize + cellSize / 2);
            }
            //     System.out.println();
        }       */
    }

    private void drawScore(Graphics g) {
        Collections.sort(world.players);
        int i = 1;
        g.setFont(new Font("Lucida Sans Typewriter", Font.BOLD, 12));
        for (Player player : world.players) {
            i++;
            g.setColor(player.getColor());
            g.drawString(player.getClassStategy() + " : " + player.score, 10, 30 * i);
        }
        g.setColor(Color.BLACK);
        g.drawString(world.getMoveCount() + "", 10, world.getHeight() * cellSize - 30);
    }

    private void drawTerrain(Graphics g) {
        g.setColor(Color.LIGHT_GRAY);
        for (int x = 0; x < world.width; x++)
            for (int y = 0; y < world.height; y++) {
                if (world.getCells()[x][y] == CellType.COVER) {
                    g.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
                }
            }

    }

    private void drawGrid(Graphics g) {
        g.setColor(Color.DARK_GRAY);
        int scaledHeight = world.height * cellSize;
        int scaledWidth = world.width * cellSize;
        for (int x = 0; x <= scaledWidth; x = x + cellSize) {
            g.drawLine(x, 0, x, scaledHeight);
        }
        for (int y = 0; y <= world.height * cellSize; y = y + cellSize) {
            g.drawLine(0, y, scaledWidth, y);
        }
    }

    private void drawUnits(Graphics g) {
        g.setFont(new Font("Lucida Sans Typewriter", Font.BOLD, cellSize));
        for (Unit unit : world.units) {
            unit.draw(g, cellSize);
        }
    }

    private class MyPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            MyFrame.this.isPainting = true;
            //   g.translate(X_OFFSET, Y_OFFSET);
            drawTerrain(g);
            drawCorpses(g);
            //  drawGrid(g);
            drawUnits(g);
            drawScore(g);
            drawMoves(g);
            drawDebug(g);
            MyFrame.this.isPainting = false;

        }
    }

    private void drawCorpses(Graphics g) {
        g.setFont(new Font("Lucida Sans Typewriter", Font.BOLD, cellSize));
        for (Corpse corpse : world.corpses) {
            corpse.draw(g, cellSize);
        }

    }

    private void drawMoves(Graphics g) {
        for (Map.Entry<Unit, Move> entry : world.moves.entrySet()) {
            Unit unit = entry.getKey();
            Move move = entry.getValue();
            if (move.getAction() == ActionType.SHOOT) {
                g.setColor(Color.RED);
                g.fillRect(move.getX() * cellSize + 2, move.getY() * cellSize + cellSize / 2 + 1, cellSize - 3, cellSize / 4);
                g.setColor(unit.player.getColor());
                g.drawLine(move.getX() * cellSize + cellSize / 2, move.getY() * cellSize + cellSize / 2, unit.getX() * cellSize + cellSize / 2, unit.getY() * cellSize + cellSize / 2);
            } else if (move.getAction() == ActionType.HEAL) {
                g.setColor(Color.GREEN);
                Point pointOnDirection = world.getPointOnDirection(new Point(unit), move.getDirection());
                g.fillRect(pointOnDirection.x * cellSize + 2, pointOnDirection.y * cellSize + cellSize / 2 - 1, cellSize - 3, cellSize / 4);
            }
        }

    }
}
