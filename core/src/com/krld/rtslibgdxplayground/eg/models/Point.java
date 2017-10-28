package com.krld.rtslibgdxplayground.eg.models;

public class Point {
    public int y;
    public int x;

    @Override
    public String toString() {
        return "x: " + x + " y: " + y;
    }

    public Point(int x, int y) {
        this.x = x;
        this.y = y;

    }

    public Point(Unit unit) {
        x = unit.x;
        y = unit.y;
    }
}
