package com.doornextdoor;

import net.runelite.api.gameval.ObjectID;

/**
 * Barrows tunnel door facts. The tunnels have 16 doorways, A to P, each a pair of leaves (left and right).
 * Each leaf is a multiloc: its impostor is the unlocked, locked or inactive door, chosen by the player's
 * own door varbits, so every player gets their own layout.
 */
final class BarrowsDoors
{
	/** The crypts and the tunnels. */
	static final int UNDERGROUND_REGION = 14231;
	/** The mounds above ground. */
	static final int SURFACE_REGION = 14131;
	static final int DOORWAYS = 16;

	enum State
	{
		UNLOCKED,
		LOCKED,
		/** Any other impostor, such as the inactive door, or no impostor at all. */
		OTHER,
	}

	private BarrowsDoors()
	{
	}

	/** Doorway index, 0 for A to 15 for P, from either leaf's ID; -1 if it isn't a tunnel door leaf. */
	static int doorway(int objectId)
	{
		if (objectId >= ObjectID.BARROWS_DOOR_A_R && objectId <= ObjectID.BARROWS_DOOR_P_R)
		{
			return objectId - ObjectID.BARROWS_DOOR_A_R;
		}
		if (objectId >= ObjectID.BARROWS_DOOR_A_L && objectId <= ObjectID.BARROWS_DOOR_P_L)
		{
			return objectId - ObjectID.BARROWS_DOOR_A_L;
		}
		return -1;
	}

	static char name(int doorway)
	{
		return (char) ('A' + doorway);
	}

	/** State from the impostor ID the leaf currently shows (-1 for none counts as OTHER). */
	static State state(int impostorId)
	{
		if (impostorId == ObjectID.BARROWS_DOOR_UNLOCKED_L || impostorId == ObjectID.BARROWS_DOOR_UNLOCKED_R)
		{
			return State.UNLOCKED;
		}
		if (impostorId == ObjectID.BARROWS_DOOR_LOCKED_L || impostorId == ObjectID.BARROWS_DOOR_LOCKED_R)
		{
			return State.LOCKED;
		}
		return State.OTHER;
	}
}
