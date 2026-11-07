package com.doornextdoor;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DoorNextDoorConfig.GROUP)
public interface DoorNextDoorConfig extends Config
{
	String GROUP = "doornextdoor";

	@Alpha
	@ConfigItem(keyName = "doorColor", name = "Door color",
		description = "Outline color for unlocked doors you haven't been through yet", position = 1)
	default Color doorColor()
	{
		return new Color(120, 255, 160, 230);
	}

	@ConfigItem(keyName = "eachDoorSeparately", name = "Each door on its own",
		description = "Going through a door drops only that door's outline. Turn off to also drop every other door"
			+ " with the same letter, such as the one at the far end of the passage.", position = 2)
	default boolean eachDoorSeparately()
	{
		return true;
	}
}
