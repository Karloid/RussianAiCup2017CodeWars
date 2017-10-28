package com.krld.rtslibgdxplayground.eg.models;

import java.awt.*;

public class Corpse extends Unit {
    public Corpse(Unit unit) {
        super(unit.getX(), unit.getY(), unit.player, unit.getType());
    }

    public Corpse(int x, int y, Player player, UnitType type) {
        super(x, y, player, type);
    }

    @Override
    public void draw(Graphics g, int cellSize) {
/*
      //  System.out.println(player.getColor().getRed() + "");
        g.setColor(new Color(player.getColor().getRed() / 255f, player.getColor().getGreen() / 255f, player.getColor().getBlue() / 255f, 0.5f));
        String str = "";
        if (type == UnitType.SOLDIER) {
            str = "S";
        } else if (type == UnitType.MEDIC) {
            str = "M";
        }

        g.drawString(str, x * cellSize + cellSize / 4, y * cellSize + cellSize);
*/
    }
}
