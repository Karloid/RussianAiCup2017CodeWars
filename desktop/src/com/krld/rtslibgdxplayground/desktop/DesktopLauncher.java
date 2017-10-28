package com.krld.rtslibgdxplayground.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.krld.rtslibgdxplayground.EpicGameGL;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.foregroundFPS = 60;
        config.width = 800;
        config.height = 800;
		new LwjglApplication(new EpicGameGL(), config);
	}
}
