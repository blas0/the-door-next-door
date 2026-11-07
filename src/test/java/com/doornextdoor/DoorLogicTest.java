package com.doornextdoor;

import static com.doornextdoor.DoorTracker.WallObjectOrientation.EAST;
import static com.doornextdoor.DoorTracker.WallObjectOrientation.NORTH;
import static com.doornextdoor.DoorTracker.WallObjectOrientation.SOUTH;
import static com.doornextdoor.DoorTracker.WallObjectOrientation.WEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

public class DoorLogicTest
{
	private static final BarrowsDoors.State UNLOCKED = BarrowsDoors.State.UNLOCKED;
	private static final BarrowsDoors.State LOCKED = BarrowsDoors.State.LOCKED;

	private static WorldPoint at(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	/** Doorway C: two leaves on the west edge of tiles (3550, 9700) and (3550, 9701). */
	private static DoorTracker trackerWithWestDoor(BarrowsDoors.State state)
	{
		DoorTracker tracker = new DoorTracker();
		tracker.leaf(2, at(3550, 9700), WEST, state);
		tracker.leaf(2, at(3550, 9701), WEST, state);
		return tracker;
	}

	@Test
	public void doorwayFromEitherLeaf()
	{
		assertEquals(0, BarrowsDoors.doorway(ObjectID.BARROWS_DOOR_A_R));
		assertEquals(0, BarrowsDoors.doorway(ObjectID.BARROWS_DOOR_A_L));
		assertEquals(15, BarrowsDoors.doorway(ObjectID.BARROWS_DOOR_P_R));
		assertEquals(15, BarrowsDoors.doorway(ObjectID.BARROWS_DOOR_P_L));
		assertEquals(7, BarrowsDoors.doorway(ObjectID.BARROWS_DOOR_H_L));
		assertEquals('H', BarrowsDoors.name(7));
		// The impostors themselves and neighbouring objects aren't doorways
		assertEquals(-1, BarrowsDoors.doorway(ObjectID.BARROWS_DOOR_UNLOCKED_R));
		assertEquals(-1, BarrowsDoors.doorway(ObjectID.BARROWS_DOOR_LOCKED_L));
		assertEquals(-1, BarrowsDoors.doorway(ObjectID.BARROWS_DOOR_INACTIVE_L));
		assertEquals(-1, BarrowsDoors.doorway(ObjectID.BARROWS_TORCH));
	}

	@Test
	public void stateFromImpostor()
	{
		assertEquals(UNLOCKED, BarrowsDoors.state(ObjectID.BARROWS_DOOR_UNLOCKED_L));
		assertEquals(UNLOCKED, BarrowsDoors.state(ObjectID.BARROWS_DOOR_UNLOCKED_R));
		assertEquals(LOCKED, BarrowsDoors.state(ObjectID.BARROWS_DOOR_LOCKED_L));
		assertEquals(LOCKED, BarrowsDoors.state(ObjectID.BARROWS_DOOR_LOCKED_R));
		assertEquals(BarrowsDoors.State.OTHER, BarrowsDoors.state(ObjectID.BARROWS_DOOR_INACTIVE_R));
		assertEquals(BarrowsDoors.State.OTHER, BarrowsDoors.state(-1));
	}

	@Test
	public void highlightsUnlockedOnly()
	{
		assertTrue(trackerWithWestDoor(UNLOCKED).shouldHighlight(2));
		assertFalse(trackerWithWestDoor(LOCKED).shouldHighlight(2));
		assertFalse(new DoorTracker().shouldHighlight(2));
	}

	@Test
	public void walkingThroughUnhighlights()
	{
		DoorTracker tracker = trackerWithWestDoor(UNLOCKED);
		assertEquals(2, tracker.moved(at(3549, 9700), at(3550, 9700)));
		assertTrue(tracker.isUsed(2));
		assertFalse(tracker.shouldHighlight(2));
		// It stays off after the door closes behind you and its leaves load again
		tracker.leaf(2, at(3550, 9700), WEST, UNLOCKED);
		assertFalse(tracker.shouldHighlight(2));
	}

	@Test
	public void eitherDirectionAndEitherLeaf()
	{
		DoorTracker back = trackerWithWestDoor(UNLOCKED);
		assertEquals(2, back.moved(at(3550, 9701), at(3549, 9701)));
		DoorTracker running = trackerWithWestDoor(UNLOCKED);
		assertEquals(2, running.moved(at(3548, 9701), at(3550, 9701)));
		DoorTracker diagonal = trackerWithWestDoor(UNLOCKED);
		assertEquals(2, diagonal.moved(at(3549, 9699), at(3550, 9700)));
	}

	@Test
	public void movesThatDontPassThrough()
	{
		DoorTracker tracker = trackerWithWestDoor(UNLOCKED);
		// Walking up to it, along it, and past the wall line far from the doorway
		assertEquals(-1, tracker.moved(at(3547, 9700), at(3549, 9700)));
		assertEquals(-1, tracker.moved(at(3550, 9700), at(3550, 9701)));
		assertEquals(-1, tracker.moved(at(3549, 9710), at(3550, 9710)));
		assertEquals(-1, tracker.moved(at(3549, 9700), at(3549, 9700)));
		assertEquals(-1, tracker.moved(null, at(3550, 9700)));
		// Too far to be one tick's move through the door: a teleport or a scene change
		assertEquals(-1, tracker.moved(at(3540, 9700), at(3560, 9700)));
		assertTrue(tracker.shouldHighlight(2));
	}

	@Test
	public void everyWallDirection()
	{
		assertTrue(DoorTracker.crosses(at(10, 10), WEST, at(9, 10), at(10, 10)));
		assertFalse(DoorTracker.crosses(at(10, 10), WEST, at(10, 10), at(11, 10)));
		assertTrue(DoorTracker.crosses(at(10, 10), EAST, at(10, 10), at(11, 10)));
		assertFalse(DoorTracker.crosses(at(10, 10), EAST, at(9, 10), at(10, 10)));
		assertTrue(DoorTracker.crosses(at(10, 10), NORTH, at(10, 10), at(10, 11)));
		assertFalse(DoorTracker.crosses(at(10, 10), NORTH, at(10, 9), at(10, 10)));
		assertTrue(DoorTracker.crosses(at(10, 10), SOUTH, at(10, 9), at(10, 10)));
		assertFalse(DoorTracker.crosses(at(10, 10), SOUTH, at(10, 10), at(10, 11)));
		// Diagonal walls aren't doors
		assertFalse(DoorTracker.crosses(at(10, 10), 16, at(9, 10), at(10, 10)));
		// Another floor
		assertFalse(DoorTracker.crosses(new WorldPoint(10, 10, 1), WEST, at(9, 10), at(10, 10)));
	}

	@Test
	public void openingTheDoorYouClicked()
	{
		DoorTracker tracker = trackerWithWestDoor(UNLOCKED);
		tracker.clickedOpen(2, 100);
		assertTrue(tracker.leafGone(2, 104));
		assertFalse(tracker.shouldHighlight(2));
	}

	@Test
	public void someoneElsesDoorSwinging()
	{
		DoorTracker tracker = trackerWithWestDoor(UNLOCKED);
		tracker.leaf(5, at(3560, 9690), NORTH, UNLOCKED);
		// Not the door you clicked
		tracker.clickedOpen(5, 100);
		assertFalse(tracker.leafGone(2, 101));
		// You clicked it, then walked off somewhere else
		tracker.clickedOpen(2, 102);
		tracker.clickedElsewhere();
		assertFalse(tracker.leafGone(2, 103));
		// You clicked it ages ago
		tracker.clickedOpen(2, 110);
		assertFalse(tracker.leafGone(2, 110 + DoorTracker.OPEN_CLICK_TICKS + 1));
		assertTrue(tracker.shouldHighlight(2));
		assertTrue(tracker.shouldHighlight(5));
	}

	@Test
	public void clickingALockedDoorDoesNothing()
	{
		DoorTracker tracker = trackerWithWestDoor(LOCKED);
		tracker.clickedOpen(2, 100);
		// A locked door never swings, so there's no leafGone, and it was never highlighted
		assertFalse(tracker.isUsed(2));
		assertFalse(tracker.shouldHighlight(2));
	}

	@Test
	public void newLayoutBringsDoorsBack()
	{
		DoorTracker tracker = trackerWithWestDoor(UNLOCKED);
		tracker.moved(at(3549, 9700), at(3550, 9700));
		assertFalse(tracker.shouldHighlight(2));
		tracker.state(2, LOCKED);
		assertFalse(tracker.isUsed(2));
		tracker.state(2, UNLOCKED);
		assertTrue(tracker.shouldHighlight(2));
	}

	@Test
	public void clearRunKeepsDoorsButForgetsUse()
	{
		DoorTracker tracker = trackerWithWestDoor(UNLOCKED);
		tracker.moved(at(3549, 9700), at(3550, 9700));
		tracker.clearRun();
		assertTrue(tracker.shouldHighlight(2));
		// The leaves are still known, so walking through again still counts
		assertEquals(2, tracker.moved(at(3550, 9700), at(3549, 9700)));
		tracker.clear();
		assertFalse(tracker.shouldHighlight(2));
		assertEquals(-1, tracker.moved(at(3549, 9700), at(3550, 9700)));
	}
}
