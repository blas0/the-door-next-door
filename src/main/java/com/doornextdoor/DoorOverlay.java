package com.doornextdoor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.WallObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Outlines both leaves of every unlocked door you haven't been through. Outline only, no fill. Drawing only. */
class DoorOverlay extends Overlay
{
	private static final Stroke STROKE = new BasicStroke(2f);
	private static final Color NO_FILL = new Color(0, 0, 0, 0);

	private final DoorNextDoorPlugin plugin;
	private final DoorNextDoorConfig config;

	@Inject
	DoorOverlay(DoorNextDoorPlugin plugin, DoorNextDoorConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.inTunnels())
		{
			return null;
		}
		DoorTracker tracker = plugin.getTracker();
		Color color = config.doorColor();
		boolean eachDoor = config.eachDoorSeparately();
		for (WallObject leaf : plugin.getLeaves())
		{
			if (!tracker.shouldHighlight(BarrowsDoors.doorway(leaf.getId()), leaf.getWorldLocation(), eachDoor))
			{
				continue;
			}
			Shape hull = leaf.getConvexHull();
			if (hull != null)
			{
				OverlayUtil.renderPolygon(g, hull, color, NO_FILL, STROKE);
			}
		}
		return null;
	}
}
