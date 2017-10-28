package com.krld.rtslibgdxplayground.eg;


public class Move {
    private ActionType action;
    private Direction direction;
    private int x;
    private int y;

    public Move() {
        setAction(ActionType.END_TURN);
        setDirection(Direction.CURRENT_CELL);
    }

    public void setAction(ActionType action) {
        this.action = action;
    }

    public ActionType getAction() {
        return action;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getX() {
        return x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getY() {
        return y;
    }

    @Override
    public String toString() {
        return "move:" + getAction() + " " + getDirection();
    }
}
