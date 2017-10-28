package com.krld.rtslibgdxplayground.eg.models;

import com.krld.rtslibgdxplayground.eg.World;

public interface Strategy {
    void move(Game game, World world, Unit unit, Move move);
}
