/*
 * Copyright (c) 2018, TheStonedTurtle <https://github.com/TheStonedTurtle>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.doomlootlost.ui;

import com.doomlootlost.DoomLootLostPlugin;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class LootLoggerPanel extends PluginPanel
{
    private final ItemManager itemManager;
    private final DoomLootLostPlugin plugin;

    private JPanel mainPanel;
    private JLabel deathLabel;
    private JLabel valueLabel;

    public LootLoggerPanel(final ItemManager itemManager, final DoomLootLostPlugin plugin)
    {
        super(false);
        this.itemManager = itemManager;
        this.plugin = plugin;

        this.setBackground(ColorScheme.DARK_GRAY_COLOR);
        this.setLayout(new BorderLayout());

        showMainView();
    }

    public void showMainView()
    {
        this.removeAll();
        
        mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Create header with just the title
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        
        JLabel titleLabel = new JLabel("Doom Loot Lost Tracker");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        
        headerPanel.add(titleLabel, BorderLayout.CENTER);
        
        // Create main content panel with all elements
        JPanel mainContentPanel = new JPanel();
        mainContentPanel.setLayout(new javax.swing.BoxLayout(mainContentPanel, javax.swing.BoxLayout.Y_AXIS));
        mainContentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mainContentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Death count line
        int deathCount = (plugin != null) ? plugin.getDoomDeaths() : 0;
        JLabel deathLabel = new JLabel("Deaths Tracked: " + deathCount);
        deathLabel.setForeground(Color.RED);
        deathLabel.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 14));
        deathLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        
        // Store reference to death label for updates
        this.deathLabel = deathLabel;
        
        // Loot lost value line
        long totalValueLost = (plugin != null) ? plugin.getTotalLootValueLost() : 0L;
        valueLabel = new JLabel("Loot Lost Value: " + formatGoldValue(totalValueLost));
        valueLabel.setForeground(Color.ORANGE);
        valueLabel.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 14));
        valueLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        
        // Create statistics panel
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new javax.swing.BoxLayout(statsPanel, javax.swing.BoxLayout.Y_AXIS));
        statsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Add statistics to stats panel
        statsPanel.add(deathLabel);
        statsPanel.add(javax.swing.Box.createVerticalStrut(8));
        statsPanel.add(valueLabel);
        statsPanel.add(javax.swing.Box.createVerticalStrut(15));
        
        		// Get lost loot data
		java.util.List<com.doomlootlost.data.RiskedLootRecord> allRiskedRecords = plugin.getRiskedLootHistory();
		
		java.util.List<com.doomlootlost.data.RiskedLootRecord> lostLootRecords = allRiskedRecords
			.stream()
			.filter(com.doomlootlost.data.RiskedLootRecord::isWasLost)
			.sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())) // Most recent first
			.collect(java.util.stream.Collectors.toList());
        
        // Create grid display directly below the value line
        JPanel gridPanel;
        if (lostLootRecords.isEmpty()) {
            // Show empty grid
            gridPanel = createEmptyGridPanel();
        } else {
            // Show items in grid
            gridPanel = createItemsGridPanel(lostLootRecords);
        }
        
        // Create a fixed-size container for the grid to prevent vertical stretching
        JPanel gridContainer = new JPanel();
        gridContainer.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
        gridContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Calculate the size needed for the grid
        int gridWidth = gridPanel.getPreferredSize().width;
        int gridHeight = gridPanel.getPreferredSize().height;
        
        // Set a fixed size for the container to prevent expansion
        gridContainer.setPreferredSize(new Dimension(gridWidth, gridHeight));
        gridContainer.setMaximumSize(new Dimension(gridWidth, gridHeight));
        gridContainer.setMinimumSize(new Dimension(gridWidth, gridHeight));
        
        // Add the grid to the container
        gridContainer.add(gridPanel);
        
        // Center the grid container
        gridContainer.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        
        // Add components to main content panel
        mainContentPanel.add(statsPanel);
        mainContentPanel.add(javax.swing.Box.createVerticalStrut(10));
        mainContentPanel.add(gridContainer);
        
        // Add all components to main panel
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(mainContentPanel, BorderLayout.CENTER);
        
        this.add(mainPanel, BorderLayout.CENTER);
        this.revalidate();
        this.repaint();
    }

    public void updateDeathCount()
    {
        if (deathLabel != null && plugin != null)
        {
            int deathCount = plugin.getDoomDeaths();
            deathLabel.setText("Deaths Tracked: " + deathCount);
            deathLabel.revalidate();
            deathLabel.repaint();
        }
    }

    private String formatGoldValue(long value)
    {
        if (value >= 1_000_000)
        {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        else if (value >= 1_000)
        {
            return String.format("%.1fK", value / 1_000.0);
        }
        else
        {
            return String.valueOf(value);
        }
    }
    
    // ========== RISKED LOOT UI METHODS ==========

    public void updateLossStatistics()
    {
        log.info("Updating loss statistics display");
        
        if (plugin != null && valueLabel != null)
        {
            long totalValueLost = plugin.getTotalLootValueLost();
            valueLabel.setText("Loot Lost Value: " + formatGoldValue(totalValueLost));
            valueLabel.revalidate();
            valueLabel.repaint();
        }
        
        // Refresh the current view to show updated loss statistics
		SwingUtilities.invokeLater(this::showMainView);
    }

    private JPanel createEmptyGridPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new java.awt.GridLayout(1, 5, 1, 1)); // 1 row, 5 columns, 1px gaps (like original Loot-Logger)
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Create 5 empty cells (1 row x 5 columns)
        for (int i = 0; i < 5; i++) {
            final JPanel slot = new JPanel();
            slot.setLayout(new java.awt.GridLayout(1, 1, 0, 0));
            slot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            slot.setPreferredSize(new java.awt.Dimension(40, 40));
            
            JLabel emptyLabel = new JLabel("Empty");
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setVerticalAlignment(SwingConstants.CENTER);
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            slot.add(emptyLabel);
            
            panel.add(slot);
        }
        
        return panel;
    }
    
    private JPanel createItemsGridPanel(java.util.List<com.doomlootlost.data.RiskedLootRecord> lostRecords)
    {
        // Aggregate all lost items by ID and sum quantities
        Map<Integer, com.doomlootlost.localstorage.LTItemEntry> aggregatedItems = new HashMap<>();
        
        for (com.doomlootlost.data.RiskedLootRecord record : lostRecords) {
            for (com.doomlootlost.localstorage.LTItemEntry item : record.getItems()) {
                int itemId = item.getId();
                if (aggregatedItems.containsKey(itemId)) {
                    // Add to existing quantity
                    com.doomlootlost.localstorage.LTItemEntry existing = aggregatedItems.get(itemId);
                    com.doomlootlost.localstorage.LTItemEntry updated = new com.doomlootlost.localstorage.LTItemEntry(
                        existing.getName(), 
                        existing.getId(),
                        existing.getQuantity() + item.getQuantity(),
                        existing.getPrice()
                    );
                    aggregatedItems.put(itemId, updated);
                } else {
                    // Add new item
                    aggregatedItems.put(itemId, item);
                }
            }
        }
        
        // Convert to array and sort by total value (price * quantity) descending
        com.doomlootlost.localstorage.LTItemEntry[] sortedItems = aggregatedItems.values()
            .stream()
            .sorted((a, b) -> Long.compare(
                (long) b.getPrice() * b.getQuantity(), 
                (long) a.getPrice() * a.getQuantity()
            ))
            .toArray(com.doomlootlost.localstorage.LTItemEntry[]::new);
        
        // Use the original Loot-Logger grid approach
        JPanel panel = new JPanel();
        
        // Calculate how many rows need to be displayed to fit all items (5 items per row)
        final int ITEMS_PER_ROW = 5;
        final int rowSize = ((sortedItems.length % ITEMS_PER_ROW == 0) ? 0 : 1) + sortedItems.length / ITEMS_PER_ROW;
        panel.setLayout(new java.awt.GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Create stacked items from the item list, calculates total price and then displays all the items in the UI.
        for (int i = 0; i < rowSize * ITEMS_PER_ROW; i++)
        {
            final JPanel slot = new JPanel();
            slot.setLayout(new java.awt.GridLayout(1, 1, 0, 0));
            slot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            slot.setPreferredSize(new java.awt.Dimension(40, 40));
            
            if (i < sortedItems.length)
            {
                final com.doomlootlost.localstorage.LTItemEntry item = sortedItems[i];
                if (item == null)
                {
                    continue;
                }
                final JLabel itemLabel = new JLabel();
                itemLabel.setToolTipText(buildToolTip(item));
                itemLabel.setVerticalAlignment(SwingConstants.CENTER);
                itemLabel.setHorizontalAlignment(SwingConstants.CENTER);
                itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1).addTo(itemLabel);
                slot.add(itemLabel);
            }
            
            panel.add(slot);
        }
        
        return panel;
    }
    
    private String buildToolTip(final com.doomlootlost.localstorage.LTItemEntry item)
    {
        final String name = item.getName();
        final int quantity = item.getQuantity();
        final long price = item.getPrice();

        return name + " x " + quantity + "\n"
            + "Price: " + formatGoldValue(price) + "\n"
            + "Total: " + formatGoldValue(quantity * price);
    }
}
