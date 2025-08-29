package com.doomlootlost;

import com.doomlootlost.data.RiskedLootRecord;
import com.doomlootlost.localstorage.LTItemEntry;
import com.doomlootlost.localstorage.LootLostWriter;
import com.doomlootlost.ui.LootLoggerPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Doom Loot Lost"
)
public class DoomLootLostPlugin extends Plugin
{
	private static final String DOOM_BOSS_NAME = "Doom of Mokhaiotl";
	private static final Pattern DEATH_PATTERN = Pattern.compile("You have been defeated by (.*)!");
	private static final Pattern WAVE_COMPLETE_PATTERN = Pattern.compile("Wave (\\d+) complete!");
	private static final Pattern LOOT_CHOICE_PATTERN = Pattern.compile("(Claim|Risk) your loot");

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	public DoomLootLostConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private LootLostWriter writer;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ConfigManager configManager;

	private LootLoggerPanel panel;
	private NavigationButton navButton;

	@Getter
	private int doomDeaths = 0;
	private boolean inDoomInstance = false;

	// Risked loot tracking
	private final List<LTItemEntry> currentRiskedLoot = new ArrayList<>();
	private final List<RiskedLootRecord> riskedLootHistory = new ArrayList<>();
	private boolean hasUnclaimedLoot = false;
	private int currentWave = 0;
	private long riskedLootValue = 0L;

	// Claim tracking system to prevent re-tracking until new boss encounter
	private boolean lootClaimed = false; // Track if loot has been claimed - don't track while true

	// Enhanced instance tracking
	private boolean everSeenDoomBoss = false; // Track if we've ever seen the boss in this session
	private long lastBossSeenTime = 0L; // When we last saw the boss
	private static final long INSTANCE_TIMEOUT = 300000L; // 5 minutes in milliseconds

	// Statistics
	private int lootLostToDeaths = 0;
	@Getter
	private long totalLootValueLost = 0L;

	@Provides
	DoomLootLostConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DoomLootLostConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		// Load death count and risked loot statistics from config FIRST
		doomDeaths = config.doomDeaths();
		lootLostToDeaths = config.lootLostToDeaths();
		totalLootValueLost = config.totalLootValueLost();

		// Set up writer username FIRST (needed for data loading)
		if (client.getGameState().equals(GameState.LOGGED_IN) || client.getGameState().equals(GameState.LOADING))
		{
			updateWriterUsername();
		}

		// Load historical risked loot data AFTER writer is set up
		loadHistoricalRiskedLootData();

		// Log loaded data
		log.info("Plugin startup complete - Deaths: {}, Lost loot count: {}, Total value lost: {}",
			doomDeaths, lootLostToDeaths, totalLootValueLost);
		log.info("Loaded {} historical risked loot records", riskedLootHistory.size());

		// Create UI AFTER data is loaded
		panel = new LootLoggerPanel(itemManager, this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel-icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Doom Loot Lost")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		if (config.enableUI())
		{
			clientToolbar.addNavigation(navButton);
		}

		// Refresh UI to ensure data is displayed
		if (config.enableUI())
		{
			SwingUtilities.invokeLater(() -> panel.showMainView());
		}

		// Ensure Loot Tracker plugin is enabled
		final Optional<Plugin> mainPlugin = pluginManager.getPlugins().stream().filter(p -> p.getName().equals("Loot Tracker")).findFirst();
		if (mainPlugin.isPresent() && !pluginManager.isPluginEnabled(mainPlugin.get()))
		{
			pluginManager.setPluginEnabled(mainPlugin.get(), true);
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);

		// Ensure all pending data is saved before shutdown
		if (hasUnclaimedLoot && !currentRiskedLoot.isEmpty())
		{
			log.info("Saving pending risked loot data on shutdown");
			handleLostRiskedLoot();
		}

		// Force save all statistics to config
		configManager.setConfiguration("doomlootlost", "doomDeaths", doomDeaths);
		configManager.setConfiguration("doomlootlost", "lootLostToDeaths", lootLostToDeaths);
		configManager.setConfiguration("doomlootlost", "totalLootValueLost", totalLootValueLost);

		log.info("Plugin shutdown complete - Final stats: Deaths: {}, Lost loot: {}, Value lost: {}",
			doomDeaths, lootLostToDeaths, totalLootValueLost);

		writer.setName(null);
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event)
	{
		if (event.getGroup().equals("doomlootlost"))
		{
			if (event.getKey().equals("enableUI"))
			{
				if (config.enableUI())
				{
					clientToolbar.addNavigation(navButton);
				}
				else
				{
					clientToolbar.removeNavigation(navButton);
				}
			}
		}
	}

	// Removed general loot tracking - only tracking lost loot and deaths now

	@Subscribe
	public void onActorDeath(final ActorDeath event)
	{
		// Check if the dead actor is the local player
		if (event.getActor() == client.getLocalPlayer())
		{
			// If player has unclaimed loot, they must have died during Doom encounter
			if (hasUnclaimedLoot)
			{
				handleLostRiskedLoot();
			}

			// Check if Doom of Mokhaiotl NPC is nearby, if we're in combat with it, or if we're in the instance
			boolean nearDoom = isDoomBossNearby();
			boolean inCombat = isInCombatWithDoomBoss();

			if (nearDoom || inCombat || inDoomInstance)
			{
				doomDeaths++;
				configManager.setConfiguration("doomlootlost", "doomDeaths", doomDeaths);
				log.info("Player died to Doom of Mokhaiotl! Total deaths: {}", doomDeaths);

				// Update UI if enabled
				if (config.enableUI())
				{
					SwingUtilities.invokeLater(() -> panel.updateDeathCount());
				}
			}
		}
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		// Enhanced Doom boss instance detection
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		// If we see the boss, mark that we've seen it and update the time
		if (isDoomBossNearby())
		{
			// Reset loot claimed flag when we see the boss (new encounter)
			lootClaimed = false;
			log.debug("Boss seen - resetting loot claimed flag");

			everSeenDoomBoss = true;
			lastBossSeenTime = System.currentTimeMillis();
			inDoomInstance = true;
		}
		// If we've seen the boss before but can't see it now, stay in instance mode for a while
		else if (everSeenDoomBoss)
		{
			long timeSinceLastSeen = System.currentTimeMillis() - lastBossSeenTime;
			if (timeSinceLastSeen < INSTANCE_TIMEOUT)
			{
				// Still consider ourselves in the instance for a few minutes after boss disappears
				inDoomInstance = true;
			}
			else
			{
				// Been too long, probably left the instance
				inDoomInstance = false;
				everSeenDoomBoss = false;
				clearCurrentRiskedLoot(); // Clear any pending loot when leaving
			}
		}
		else
		{
			// Never seen the boss, not in instance
			inDoomInstance = false;
		}
	}

	@Subscribe
	public void onChatMessage(final ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		final String message = event.getMessage();

		// Check for wave completion messages
		final Matcher waveCompleteMatcher = WAVE_COMPLETE_PATTERN.matcher(message);
		if (waveCompleteMatcher.find() && inDoomInstance)
		{
			currentWave = Integer.parseInt(waveCompleteMatcher.group(1));
		}

		// Check for loot choice messages
		final Matcher lootChoiceMatcher = LOOT_CHOICE_PATTERN.matcher(message);
		if (lootChoiceMatcher.find() && inDoomInstance)
		{
			log.info("Loot choice detected: {}", lootChoiceMatcher.group(1));
		}

		// Check for death messages
		final Matcher deathMatcher = DEATH_PATTERN.matcher(message);
		if (deathMatcher.find())
		{
			final String killerName = deathMatcher.group(1);
			log.info("Death message detected! Killed by: {}", killerName);

			// Check if killed by Doom of Mokhaiotl
			if (killerName.equalsIgnoreCase(DOOM_BOSS_NAME))
			{
				doomDeaths++;
				configManager.setConfiguration("doomlootlost", "doomDeaths", doomDeaths);
				log.info("Player died to Doom of Mokhaiotl! Total deaths: {}", doomDeaths);

				// Update UI if enabled
				if (config.enableUI())
				{
					SwingUtilities.invokeLater(() -> panel.updateDeathCount());
				}
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			updateWriterUsername();

			// Load data when client logs in (in case it wasn't loaded during startup)
			if (riskedLootHistory.isEmpty())
			{
				loadHistoricalRiskedLootData();

				// Refresh UI if it exists
				if (panel != null && config.enableUI())
				{
					SwingUtilities.invokeLater(() -> panel.showMainView());
				}
			}
		}
	}

	private void updateWriterUsername()
	{
		String folder = String.valueOf(client.getAccountHash());
		RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
		if (profileType != RuneScapeProfileType.STANDARD)
		{
			folder += "-" + Text.titleCase(profileType);
		}

		if (folder.equalsIgnoreCase(writer.getName()))
		{
			return;
		}

		if (writer.setPlayerUsername(folder))
		{
			localPlayerNameChanged();
		}
	}

	private void localPlayerNameChanged()
	{
		// No longer need to load loot names since we're not tracking general loot
		if (config.enableUI())
		{
			SwingUtilities.invokeLater(panel::showMainView);
		}
	}

	private LTItemEntry createLTItemEntry(final int id, final int qty)
	{
		final ItemComposition c = itemManager.getItemComposition(id);
		final int realId = c.getNote() == -1 ? c.getId() : c.getLinkedNoteId();
		final int price = itemManager.getItemPrice(realId);

		// Handle unknown items with a fallback name
		String itemName = c.getName();
		if (itemName == null || itemName.equals("null") || itemName.trim().isEmpty())
		{
			itemName = "Unknown Item (ID: " + id + ")";
		}

		return new LTItemEntry(itemName, id, qty, price);
	}

	private boolean isDoomBossNearby()
	{
		if (client.getLocalPlayer() == null)
		{
			return false;
		}

		// Check all NPCs in the area for Doom of Mokhaiotl
		for (net.runelite.api.NPC npc : client.getNpcs())
		{
			if (npc != null && npc.getName() != null && npc.getName().equalsIgnoreCase(DOOM_BOSS_NAME))
			{
				// Check if the NPC is within a reasonable distance (e.g., 50 tiles)
				int distance = client.getLocalPlayer().getWorldLocation().distanceTo(npc.getWorldLocation());
				return distance <= 50;
			}
		}

		return false;
	}

	private boolean isInCombatWithDoomBoss()
	{
		if (client.getLocalPlayer() == null)
		{
			return false;
		}

		// Check if player is in combat
		if (client.getLocalPlayer().getInteracting() != null)
		{
			net.runelite.api.Actor target = client.getLocalPlayer().getInteracting();
			if (target instanceof net.runelite.api.NPC)
			{
				net.runelite.api.NPC npc = (net.runelite.api.NPC) target;
				if (npc.getName() != null && npc.getName().equalsIgnoreCase(DOOM_BOSS_NAME))
				{
					return true;
				}
			}
		}

		return false;
	}

	// ========== RISKED LOOT TRACKING METHODS ==========

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Check specifically for Doom loot interface (Group ID 919)
		if (event.getGroupId() == 919 && config.trackRiskedLoot())
		{
			// Don't track loot if we've already claimed loot
			if (lootClaimed)
			{
				log.debug("Ignoring widget load - loot already claimed");
				return;
			}

			// Scan for Doom loot items
			clientThread.invokeLater(() -> scanDoomLootOnly());

			// Set flag that loot is available
			hasUnclaimedLoot = true;
			currentWave = 1;
		}

	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Only process if we have unclaimed loot
		if (!hasUnclaimedLoot)
		{
			return;
		}

		String option = event.getMenuOption().toLowerCase();
		String target = event.getMenuTarget().toLowerCase();

		if (option.contains("claim") || target.contains("claim"))
		{
			handleLootClaimed();
		}
		else if (option.contains("descend") || target.contains("descend") || option.contains("risk") || option.contains("continue"))
		{
			handleLootRisked();
		}
	}

	private void updateCurrentRiskedLoot(List<LTItemEntry> newLoot)
	{
		currentRiskedLoot.clear();
		currentRiskedLoot.addAll(newLoot);

		riskedLootValue = currentRiskedLoot.stream()
			.mapToLong(item -> (long) item.getPrice() * item.getQuantity())
			.sum();

		hasUnclaimedLoot = !currentRiskedLoot.isEmpty();

		log.info("Updated risked loot: {} items worth {} GP", currentRiskedLoot.size(), riskedLootValue);

		// Update UI if enabled
		if (config.enableUI())
		{
			SwingUtilities.invokeLater(panel::showMainView);
		}
	}

		private void handleLootClaimed()
	{
		log.info("Player claimed loot: {} items worth {} GP", currentRiskedLoot.size(), riskedLootValue);

		// Set loot claimed flag - won't track new loot until we see the boss again
		lootClaimed = true;

		// Record this as successfully claimed loot
		if (!currentRiskedLoot.isEmpty())
		{
			RiskedLootRecord record = new RiskedLootRecord(
				new ArrayList<>(currentRiskedLoot),
				new Date(),
				currentWave,
				riskedLootValue,
				false // not lost
			);
			riskedLootHistory.add(record);

			// Save to storage
			writer.addRiskedLootRecord(record);
		}

		clearCurrentRiskedLoot();
	}

	private void handleLootRisked()
	{
		log.info("Player risked loot: {} items worth {} GP for wave {}",
			currentRiskedLoot.size(), riskedLootValue, currentWave + 1);

		// Loot remains in currentRiskedLoot for potential loss tracking
		currentWave++;
	}

	private void handleLostRiskedLoot()
	{
		// Record this as lost loot
		RiskedLootRecord lostRecord = new RiskedLootRecord(
			new ArrayList<>(currentRiskedLoot),
			new Date(),
			currentWave,
			riskedLootValue,
			true // lost to death
		);
		riskedLootHistory.add(lostRecord);

		// Save to storage
		writer.addRiskedLootRecord(lostRecord);

		// Update statistics
		lootLostToDeaths++;
		totalLootValueLost += riskedLootValue;

		// Save to config for persistence
		configManager.setConfiguration("doomlootlost", "lootLostToDeaths", lootLostToDeaths);
		configManager.setConfiguration("doomlootlost", "totalLootValueLost", totalLootValueLost);

		clearCurrentRiskedLoot();

		// Update UI
		if (config.enableUI())
		{
			SwingUtilities.invokeLater(() -> panel.updateLossStatistics());
		}
	}

	private void clearCurrentRiskedLoot()
	{
		currentRiskedLoot.clear();
		hasUnclaimedLoot = false;
		riskedLootValue = 0L;
		currentWave = 0;

		// Update UI
		if (config.enableUI())
		{
			SwingUtilities.invokeLater(panel::showMainView);
		}
	}

	// ========== GETTER METHODS FOR UI ==========

	public List<RiskedLootRecord> getRiskedLootHistory()
	{
		return new ArrayList<>(riskedLootHistory);
	}

	private void loadHistoricalRiskedLootData()
	{
		try
		{
			Collection<RiskedLootRecord> historicalRecords = writer.loadRiskedLootRecords();

			riskedLootHistory.clear();

			// Validate and filter records
			for (RiskedLootRecord record : historicalRecords)
			{
				if (isValidRiskedLootRecord(record))
				{
					riskedLootHistory.add(record);
				}
				else
				{
					log.warn("Skipping invalid risked loot record: {}", record);
				}
			}

			log.info("Loaded {} valid historical risked loot records", riskedLootHistory.size());

			// Recalculate statistics from loaded data
			long recalculatedValueLost = 0L;
			int recalculatedLostCount = 0;

			for (RiskedLootRecord record : riskedLootHistory)
			{
				if (record.isWasLost())
				{
					recalculatedValueLost += record.getTotalValue();
					recalculatedLostCount++;
				}
			}

			// Update statistics if they don't match (config might be out of sync)
			if (recalculatedValueLost != totalLootValueLost || recalculatedLostCount != lootLostToDeaths)
			{
				log.info("Statistics mismatch detected - Recalculated: {} lost, {} value vs Config: {} lost, {} value",
					recalculatedLostCount, recalculatedValueLost, lootLostToDeaths, totalLootValueLost);

				totalLootValueLost = recalculatedValueLost;
				lootLostToDeaths = recalculatedLostCount;

				// Update config to match
				configManager.setConfiguration("doomlootlost", "lootLostToDeaths", lootLostToDeaths);
				configManager.setConfiguration("doomlootlost", "totalLootValueLost", totalLootValueLost);
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load historical risked loot data", e);
		}
	}

	private boolean isValidRiskedLootRecord(RiskedLootRecord record)
	{
		if (record == null)
		{
			return false;
		}

		if (record.getItems() == null || record.getItems().isEmpty())
		{
			return false;
		}

		if (record.getTimestamp() == null)
		{
			return false;
		}

		if (record.getWave() <= 0)
		{
			return false;
		}

		// Validate each item in the record
		for (LTItemEntry item : record.getItems())
		{
			if (item == null || item.getName() == null || item.getName().trim().isEmpty())
			{
				return false;
			}

			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				return false;
			}
		}

		return true;
	}



	private void scanDoomLootOnly()
	{
		List<LTItemEntry> doomLoot = new ArrayList<>();

		// Scan all components of widget 919 to find the loot
		for (int componentId = 0; componentId < 50; componentId++)
		{
			Widget widget = client.getWidget(919, componentId);
			if (widget != null)
			{
				// Check if this widget has item data
				if (widget.getItemId() > 0)
				{
					int itemId = widget.getItemId();
					int quantity = widget.getItemQuantity();
					if (quantity <= 0) quantity = 1;

					// Skip unknown items like ID 6512
					if (itemId == 6512) {
						continue;
					}

					// Check if we already found this item (avoid duplicates)
					boolean alreadyFound = doomLoot.stream()
						.anyMatch(item -> item.getId() == itemId);

					if (!alreadyFound)
					{
						LTItemEntry item = createLTItemEntry(itemId, quantity);
						doomLoot.add(item);
					}
				}

				// Also check children for items
				if (widget.getChildren() != null && widget.getChildren().length > 0)
				{
					for (Widget child : widget.getChildren())
					{
						if (child != null && child.getItemId() > 0)
						{
							int itemId = child.getItemId();
							int quantity = child.getItemQuantity();
							if (quantity <= 0) quantity = 1;

							// Skip unknown items like ID 6512
							if (itemId == 6512) {
								continue;
							}

							// Check if we already found this item (avoid duplicates)
							boolean alreadyFound = doomLoot.stream()
								.anyMatch(item -> item.getId() == itemId);

							if (!alreadyFound)
							{
								LTItemEntry item = createLTItemEntry(itemId, quantity);
								doomLoot.add(item);
							}
						}
					}
				}
			}
		}

		if (!doomLoot.isEmpty())
		{
			updateCurrentRiskedLoot(doomLoot);
		}
	}


}
