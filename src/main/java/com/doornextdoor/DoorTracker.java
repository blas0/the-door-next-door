package com.doornextdoor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;

/**
 * What the plugin knows about each tunnel doorway this run: where its leaves are, whether it's unlocked for
 * you, and whether you've already been through it. No client calls, so it can be unit tested.
 *
 * <p>A doorway is a letter, A to P, and one letter can be more than one door: every door with that letter
 * shares the varbit that locks it. So use is kept two ways: per letter, and per door (the tiles of the leaves
 * you went through). The plugin's setting picks which one the highlight follows.
 */
final class DoorTracker
{
	/** How long after you click Open the door may take to swing: long enough to walk there across a room. */
	static final int OPEN_CLICK_TICKS = 20;
	/** Tiles from a door's edge that still count as passing through it. Running covers 2 tiles a tick. */
	static final double CROSS_REACH = 2.5;
	/** Tiles between a door's two leaves, and between a leaf and where you clicked to open it. */
	static final int SAME_DOOR = 1;
	/** A swinging leaf this close to the tile you clicked Open on is the door you clicked. */
	static final int CLICK_REACH = 2;

	private static final class Doorway
	{
		/** Leaf tile to wall orientation (WallObject bitfield: 1 west, 2 north, 4 east, 8 south). */
		final Map<WorldPoint, Integer> leaves = new HashMap<>();
		BarrowsDoors.State state = BarrowsDoors.State.OTHER;
		/** You went through a door with this letter. */
		boolean used;
		/** Leaf tiles of the doors with this letter you went through. */
		final Set<WorldPoint> usedAt = new HashSet<>();

		void use(WorldPoint tile)
		{
			used = true;
			usedAt.add(tile);
		}

		void forget()
		{
			used = false;
			usedAt.clear();
		}

		boolean usedNear(WorldPoint tile)
		{
			for (WorldPoint used : usedAt)
			{
				if (near(used, tile, SAME_DOOR))
				{
					return true;
				}
			}
			return false;
		}
	}

	private final Doorway[] doorways = new Doorway[BarrowsDoors.DOORWAYS];
	private int pendingOpen = -1;
	private int pendingOpenTick;
	@Nullable
	private WorldPoint pendingOpenTile;

	DoorTracker()
	{
		for (int i = 0; i < doorways.length; i++)
		{
			doorways[i] = new Doorway();
		}
	}

	/** A leaf of this doorway is loaded at this tile, facing this way, currently in this state. */
	void leaf(int doorway, WorldPoint tile, int orientation, BarrowsDoors.State state)
	{
		doorways[doorway].leaves.put(tile, orientation);
		state(doorway, state);
	}

	void state(int doorway, BarrowsDoors.State state)
	{
		Doorway d = doorways[doorway];
		if (state == BarrowsDoors.State.LOCKED)
		{
			// A door you went through that's now locked means the layout changed: a new run
			d.forget();
		}
		d.state = state;
	}

	BarrowsDoors.State state(int doorway)
	{
		return doorways[doorway].state;
	}

	/**
	 * Unlocked for you and you haven't been through it yet this run.
	 *
	 * @param leaf the tile of the leaf being drawn
	 * @param eachDoor true to count only the door you went through, false for every door with its letter
	 */
	boolean shouldHighlight(int doorway, WorldPoint leaf, boolean eachDoor)
	{
		Doorway d = doorways[doorway];
		return d.state == BarrowsDoors.State.UNLOCKED && !(eachDoor ? d.usedNear(leaf) : d.used);
	}

	/** You went through a door with this letter. */
	boolean isUsed(int doorway)
	{
		return doorways[doorway].used;
	}

	/** You went through the door this leaf belongs to. */
	boolean isUsed(int doorway, WorldPoint leaf)
	{
		return doorways[doorway].usedNear(leaf);
	}

	/**
	 * You clicked Open on this doorway. It only counts once the door actually swings open.
	 *
	 * @param tile the tile you clicked, or null if it isn't known
	 */
	void clickedOpen(int doorway, @Nullable WorldPoint tile, int tick)
	{
		pendingOpen = doorway;
		pendingOpenTile = tile;
		pendingOpenTick = tick;
	}

	/** You clicked something else, so you're no longer heading for the door you clicked before. */
	void clickedElsewhere()
	{
		pendingOpen = -1;
	}

	/**
	 * A leaf of this doorway left the scene outside a scene load: the door swung open. It counts as yours
	 * only if it's the door you last clicked Open on, since other players in the tunnels open doors too.
	 *
	 * @return true if this marked the doorway as used
	 */
	boolean leafGone(int doorway, WorldPoint leaf, int tick)
	{
		if (doorway != pendingOpen || tick - pendingOpenTick > OPEN_CLICK_TICKS
			|| (pendingOpenTile != null && !near(pendingOpenTile, leaf, CLICK_REACH)))
		{
			return false;
		}
		pendingOpen = -1;
		doorways[doorway].use(leaf);
		return true;
	}

	/**
	 * You moved between these tiles in one tick. If that took you across a doorway's edge, it's used.
	 *
	 * @return the doorway you went through, or -1
	 */
	int moved(@Nullable WorldPoint from, @Nullable WorldPoint to)
	{
		if (from == null || to == null || from.equals(to) || from.getPlane() != to.getPlane())
		{
			return -1;
		}
		for (int i = 0; i < doorways.length; i++)
		{
			for (Map.Entry<WorldPoint, Integer> leaf : doorways[i].leaves.entrySet())
			{
				if (crosses(leaf.getKey(), leaf.getValue(), from, to))
				{
					doorways[i].use(leaf.getKey());
					if (pendingOpen == i)
					{
						pendingOpen = -1;
					}
					return i;
				}
			}
		}
		return -1;
	}

	/** Whether a move from one tile to another passes through the wall edge of this leaf. */
	static boolean crosses(WorldPoint leaf, int orientation, WorldPoint from, WorldPoint to)
	{
		if (leaf.getPlane() != from.getPlane())
		{
			return false;
		}
		boolean alongX;
		double edge;
		switch (orientation)
		{
			case WallObjectOrientation.WEST:
				alongX = true;
				edge = leaf.getX() - 0.5;
				break;
			case WallObjectOrientation.EAST:
				alongX = true;
				edge = leaf.getX() + 0.5;
				break;
			case WallObjectOrientation.NORTH:
				alongX = false;
				edge = leaf.getY() + 0.5;
				break;
			case WallObjectOrientation.SOUTH:
				alongX = false;
				edge = leaf.getY() - 0.5;
				break;
			default:
				return false;
		}
		double fromAcross = (alongX ? from.getX() : from.getY()) - edge;
		double toAcross = (alongX ? to.getX() : to.getY()) - edge;
		if (fromAcross * toAcross > 0 || Math.abs(fromAcross) > CROSS_REACH || Math.abs(toAcross) > CROSS_REACH)
		{
			return false;
		}
		int lateral = alongX ? leaf.getY() : leaf.getX();
		int fromLateral = alongX ? from.getY() : from.getX();
		int toLateral = alongX ? to.getY() : to.getX();
		// The doorway is two leaves wide; one tile of slack covers a diagonal step into it
		return Math.abs(fromLateral - lateral) <= 1 && Math.abs(toLateral - lateral) <= 1;
	}

	/** A new run, or you left the tunnels: every door counts as not yet used. */
	void clearRun()
	{
		for (Doorway d : doorways)
		{
			d.forget();
		}
		pendingOpen = -1;
	}

	void clear()
	{
		for (int i = 0; i < doorways.length; i++)
		{
			doorways[i] = new Doorway();
		}
		pendingOpen = -1;
	}

	/** Same floor, and within this many tiles each way. */
	static boolean near(WorldPoint a, WorldPoint b, int tiles)
	{
		return a.getPlane() == b.getPlane()
			&& Math.abs(a.getX() - b.getX()) <= tiles
			&& Math.abs(a.getY() - b.getY()) <= tiles;
	}

	/** The {@link WallObject#getOrientationA()} bits for straight walls. */
	static final class WallObjectOrientation
	{
		static final int WEST = 1;
		static final int NORTH = 2;
		static final int EAST = 4;
		static final int SOUTH = 8;

		private WallObjectOrientation()
		{
		}
	}
}
