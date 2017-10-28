package com.krld.rtslibgdxplayground.eg.models;


import com.badlogic.gdx.graphics.Color;

public class Player implements Comparable<Player> {
    private Color color;
    public Strategy strategy;
    private String classStategy;
    public int score;

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public void setClassStategy(String classStategy) {
        this.classStategy = classStategy;
    }

    public String getClassStategy() {
        return classStategy;
    }

    @Override
    public int compareTo(Player o) {
        return (score > o.score ? -1: 1);
    }
}
