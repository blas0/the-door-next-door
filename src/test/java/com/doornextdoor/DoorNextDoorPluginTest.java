package com.doornextdoor;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DoorNextDoorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(DoorNextDoorPlugin.class);
		RuneLite.main(args);
	}
}
