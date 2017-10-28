package com.krld.rtslibgdxplayground.eg.models;


import com.badlogic.gdx.graphics.Color;

public class Player implements Comparable<Player> {
    private Color color;
    public Strategy strategy;
    private String classStategy;
    public int score;
    private Color invertedColor;

    public void setColor(Color color) {
        invertedColor = new Color(1 - color.r, 1 - color.g, 1 - color.b, 1);
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
        return (score > o.score ? -1 : 1);
    }

    public Color getInvertedColor() {
        return invertedColor;
    }
}
