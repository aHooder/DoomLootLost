package com.doomlootlost.data;

import com.doomlootlost.localstorage.LTItemEntry;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents loot that was risked during a Doom boss wave but potentially lost due to death
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskedLootRecord
{
    private List<LTItemEntry> items;
    private Date timestamp;
    private int wave;
    private long totalValue;
	@Getter
    private boolean wasLost; // true if player died and lost this loot
}
