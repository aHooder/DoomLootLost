package com.doomlootlost;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("doomlootlost")
public interface DoomLootLostConfig extends Config
{
	@ConfigItem(
		keyName = "enableUI",
		name = "Enable Side-Panel",
		description = "Controls whether the side panel should be displayed, data will be logged either way"
	)
	default boolean enableUI()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackRiskedLoot",
		name = "Track Risked Loot",
		description = "Track loot that is risked during Doom boss waves",
		hidden = true
	)
	default boolean trackRiskedLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "doomDeaths",
		name = "Doom Deaths",
		description = "Number of deaths to Doom of Mokhaiotl",
		hidden = true
	)
	default int doomDeaths()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "lootLostToDeaths",
		name = "Loot Lost to Deaths",
		description = "Number of times risked loot was lost to death",
		hidden = true
	)
	default int lootLostToDeaths()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "totalLootValueLost",
		name = "Total Loot Value Lost",
		description = "Total GP value of loot lost to deaths",
		hidden = true
	)
	default long totalLootValueLost()
	{
		return 0L;
	}
}
