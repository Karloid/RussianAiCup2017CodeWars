package com.krld.rtslibgdxplayground.eg.models;

public class Vector {
    public int y;
    public int x;

    @Override
    public String toString() {
        return "x: " + x + " y: " + y;
    }

    public Vector(int x, int y) {
        this.x = x;
        this.y = y;

    }

    public Vector(Unit unit) {
        x = unit.x;
        y = unit.y;
    }
}
