package com.doomlootlost.data;

import com.doomlootlost.localstorage.LTItemEntry;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Date;
import java.util.List;

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
    private boolean wasLost; // true if player died and lost this loot
    
    /**
     * Calculate the total value of all items in this risked loot record
     */
    public long calculateTotalValue()
    {
        return items.stream()
                .mapToLong(item -> (long) item.getPrice() * item.getQuantity())
                .sum();
    }
    
    /**
     * Get the number of items in this risked loot record
     */
    public int getItemCount()
    {
        return items.stream()
                .mapToInt(LTItemEntry::getQuantity)
                .sum();
    }
    
    /**
     * Check if this loot was lost to death
     */
    public boolean isWasLost()
    {
        return wasLost;
    }
    
    /**
     * Set whether this loot was lost to death
     */
    public void setWasLost(boolean wasLost)
    {
        this.wasLost = wasLost;
    }
}
