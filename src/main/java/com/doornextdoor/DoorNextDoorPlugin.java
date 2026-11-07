package com.doornextdoor;

import com.google.inject.Provides;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "The Door Next Door",
	description = "Highlights the unlocked doors in the Barrows tunnels, and drops each one once you've been through it",
	tags = {"barrows", "doors", "tunnels", "crypt", "chest", "pvm", "highlight"}
)
public class DoorNextDoorPlugin extends Plugin
{
	// After a scene load, leaves leaving the scene are part of the load, not doors swinging open
	private static final int SETTLE_TICKS = 3;
	// A click on a door leaf lands on its tile; match it to a tracked leaf if the ID didn't say which doorway
	private static final int CLICK_MATCH_DISTANCE = 1;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DoorOverlay overlay;

	@Inject
	private ClientThread clientThread;

	@Getter
	private final DoorTracker tracker = new DoorTracker();

	// Client thread only
	private final Map<WorldPoint, WallObject> leaves = new HashMap<>();
	@Nullable
	private WorldPoint lastTile;
	private int settleTicks;

	@Provides
	DoorNextDoorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DoorNextDoorConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				scanScene();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		leaves.clear();
		tracker.clear();
		lastTile = null;
	}

	/** Leaves already loaded don't fire spawn events, so pick them up when the plugin starts. */
	private void scanScene()
	{
		WorldView wv = client.getTopLevelWorldView();
		for (Tile[] column : wv.getScene().getTiles()[wv.getPlane()])
		{
			for (Tile tile : column)
			{
				if (tile != null && tile.getWallObject() != null)
				{
					addLeaf(tile.getWallObject());
				}
			}
		}
	}

	Collection<WallObject> getLeaves()
	{
		return Collections.unmodifiableCollection(leaves.values());
	}

	/** In the Barrows tunnels: the ground floor of the region that also holds the brothers' crypts. */
	boolean inTunnels()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}
		WorldPoint tile = player.getWorldLocation();
		return tile.getRegionID() == BarrowsDoors.UNDERGROUND_REGION && tile.getPlane() == 0;
	}

	private void addLeaf(WallObject leaf)
	{
		int doorway = BarrowsDoors.doorway(leaf.getId());
		if (doorway < 0)
		{
			return;
		}
		WorldPoint location = leaf.getWorldLocation();
		leaves.put(location, leaf);
		tracker.leaf(doorway, location, leaf.getOrientationA(), stateOf(leaf));
	}

	/** Which door this leaf shows you right now: the multiloc's impostor, picked by your door varbits. */
	private BarrowsDoors.State stateOf(WallObject leaf)
	{
		ObjectComposition definition = client.getObjectDefinition(leaf.getId());
		if (definition == null || definition.getImpostorIds() == null)
		{
			return BarrowsDoors.State.OTHER;
		}
		ObjectComposition impostor = definition.getImpostor();
		return impostor == null ? BarrowsDoors.State.OTHER : BarrowsDoors.state(impostor.getId());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING)
		{
			settleTicks = SETTLE_TICKS;
			leaves.clear();
			lastTile = null;
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			leaves.clear();
			tracker.clear();
			lastTile = null;
		}
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		addLeaf(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		WallObject leaf = event.getWallObject();
		int doorway = BarrowsDoors.doorway(leaf.getId());
		if (doorway < 0)
		{
			return;
		}
		leaves.remove(leaf.getWorldLocation(), leaf);
		// The door you clicked Open on swung open: its leaves leave the scene while it stands open
		if (settleTicks == 0 && tracker.leafGone(doorway, leaf.getWorldLocation(), client.getTickCount()))
		{
			log.debug("Opened door {}", BarrowsDoors.name(doorway));
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (settleTicks > 0)
		{
			settleTicks--;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint tile = player.getWorldLocation();
		int region = tile.getRegionID();
		if (region == BarrowsDoors.SURFACE_REGION)
		{
			// Back above ground: the next trip into the tunnels starts fresh
			tracker.clearRun();
		}
		if (region != BarrowsDoors.UNDERGROUND_REGION)
		{
			lastTile = null;
			return;
		}

		// Door varbits can change without the leaf respawning, so re-read every leaf's state
		for (WallObject leaf : leaves.values())
		{
			tracker.state(BarrowsDoors.doorway(leaf.getId()), stateOf(leaf));
		}

		int crossed = tracker.moved(lastTile, tile);
		if (crossed >= 0)
		{
			log.debug("Went through door {}", BarrowsDoors.name(crossed));
		}
		lastTile = tile;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Read-only: we note which door you chose, and never change or add menu entries
		if (!inTunnels() || !movesYou(event.getMenuAction()))
		{
			return;
		}
		WorldPoint clicked = clickedTile(event);
		int doorway = "Open".equals(event.getMenuOption()) ? clickedDoorway(event, clicked) : -1;
		if (doorway >= 0)
		{
			tracker.clickedOpen(doorway, clicked, client.getTickCount());
		}
		else
		{
			tracker.clickedElsewhere();
		}
	}

	/** Clicks that send you somewhere in the world, so they replace a door you were heading for. */
	private static boolean movesYou(MenuAction action)
	{
		String name = action.name();
		return action == MenuAction.WALK
			|| name.startsWith("GAME_OBJECT_")
			|| name.startsWith("NPC_")
			|| name.startsWith("GROUND_ITEM_")
			|| name.startsWith("PLAYER_");
	}

	/** For object clicks, param0 and param1 are the object's scene tile. */
	private WorldPoint clickedTile(MenuOptionClicked event)
	{
		WorldView wv = client.getTopLevelWorldView();
		return WorldPoint.fromScene(wv, event.getParam0(), event.getParam1(), wv.getPlane());
	}

	private int clickedDoorway(MenuOptionClicked event, WorldPoint clicked)
	{
		int doorway = BarrowsDoors.doorway(event.getId());
		if (doorway >= 0)
		{
			return doorway;
		}
		for (Map.Entry<WorldPoint, WallObject> leaf : leaves.entrySet())
		{
			if (leaf.getKey().getPlane() == clicked.getPlane() && leaf.getKey().distanceTo2D(clicked) <= CLICK_MATCH_DISTANCE)
			{
				return BarrowsDoors.doorway(leaf.getValue().getId());
			}
		}
		return -1;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Looting the chest ends the run; the next run gets a new door layout
		if (event.getGroupId() == InterfaceID.BARROWS_REWARD)
		{
			tracker.clearRun();
		}
	}
}
