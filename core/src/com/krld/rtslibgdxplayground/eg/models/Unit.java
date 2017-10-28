package com.krld.rtslibgdxplayground.eg.models;

import java.awt.*;

public class Unit {
    public final Player player;
    private final int id;
    private Strategy strategy;
    public int y;
    public int x;
    private static int currentId = 0;
    private int hp;
    protected UnitType type;

    public Unit(int x, int y, Player player, UnitType type) {
        this.id = currentId++;
        this.player = player;
        this.x = x;
        this.y = y;
        this.type = type;
        hp = 100;
        try {
            this.strategy = (Strategy) Class.forName(player.getClassStategy()).newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "unit  - x: " + x + " y: " + y + " hp: " + hp;
    }

    public void draw(Graphics g, int cellSize) {
/*
        g.setColor(player.getColor());
        int hpBarHeight = (int) (((cellSize * 1f) / 100) * hp);
        //   System.out.println(" hpBarHeight" + hpBarHeight);
        g.fillRect(x * cellSize, y * cellSize + cellSize - hpBarHeight, cellSize, hpBarHeight);
        g.setColor(Color.BLACK);
        String str = "";
        if (type == UnitType.SOLDIER) {
            str = "S";
        }  else if (type == UnitType.MEDIC) {
            str = "M";
        }

        g.drawString(str, x * cellSize + cellSize / 4, y * cellSize + cellSize);
*/
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public int getId() {
        return id;
    }

    public boolean isTeamatte(Unit unit) {
        return this.player == unit.player;
    }


    public int recieveDamage(int damage) {
        int oldHp = hp;
        hp -= damage;
        if (hp < 0) hp = 0;
        return oldHp - hp;
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public UnitType getType() {
        return type;
    }

    public void setType(UnitType type) {
        this.type = type;
    }

    public int getHp() {
        return hp;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void applyHeal(int amount) {
        hp += amount;
        if (hp > 100) hp = 100;
    }
}
