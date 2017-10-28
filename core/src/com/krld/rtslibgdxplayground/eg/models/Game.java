package com.krld.rtslibgdxplayground.eg.models;


import com.krld.rtslibgdxplayground.eg.World;

public class Game {
    private World world;
    private int shootingRange = 4;
    private int soldierShootDamage = 15;
    private int maximumHP = 100;
    private int medicHealAmount = 10;
    private int medicShootDamage = 5;

    public Game(World world) {
        this.world = world;
        world.setGame(this);
    }

    public World getWorld() {
        return world;
    }

    public void run() {
        while (true) {
            world.update();
        }
    }

    public double getShootingRange() {
        return shootingRange;
    }


    public int getSoldierShootDamage() {
        return soldierShootDamage;
    }


    public static void log(String s) {
        System.out.println(s);
    }

    public int getMaximumHP() {
        return maximumHP;
    }

    public void setMaximumHP(int maximumHP) {
        this.maximumHP = maximumHP;
    }

    public int getMedicHealAmount() {
        return medicHealAmount;
    }


    public int getMedicShootDamage() {
        return medicShootDamage;
    }
}
