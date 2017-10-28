package com.krld.rtslibgdxplayground.eg.strats;

import com.krld.rtslibgdxplayground.eg.*;
import com.krld.rtslibgdxplayground.eg.models.*;

import java.util.List;

public class DummyStrategy implements Strategy {
    private World world;
    private Game game;
    private Move move;
    private Unit self;
    private List<Unit> units;

    @Override
    public void move(Game game, World world, Unit self, Move move) {
        move.setAction(ActionType.MOVE);
        this.game = game;
        this.world = world;
        this.self = self;
        this.move = move;
        this.units = world.getUnits();

        if (shootActions())
            return;

        if (moveActions())
             return;


          //move.setDirection(Direction.EAST);

    }

    private boolean shootActions() {
        for (Unit unit : units) {
            if (!unit.isTeamatte(self) && world.getDistance(self, unit) <= game.getShootingRange()) {
               move.setAction(ActionType.SHOOT);
               move.setX(unit.x);
               move.setY(unit.y);
               return true;
            }
        }
        return false;
    }

    private boolean moveActions() {
        double random = Math.random();

        if (random < 0.25f) {
            move.setDirection(Direction.NORTH);
        } else if (random < 0.5f) {
            move.setDirection(Direction.SOUTH);
        }   else if (random < 0.75f) {
            move.setDirection(Direction.WEST); // запад
        }  else {
            move.setDirection(Direction.EAST); // восток
        }
        return true;
    }
}
