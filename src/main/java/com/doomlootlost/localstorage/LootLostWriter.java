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
package com.doomlootlost.localstorage;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;

import static net.runelite.client.RuneLite.RUNELITE_DIR;

/**
 * Reads & Writes LootRecord data from `*name*.log` files located in `.runelite/doomlootlost/`.
 * Data is stored as json delimited by newlines, aka JSON Lines {@see <a href="http://jsonlines.org">http://jsonlines.org</a>}
 */
@Slf4j
@Singleton
public class LootLostWriter
{
    private static final File LOOT_RECORD_DIR = new File(RUNELITE_DIR, "doomlootlost");

    // Data is stored in a folder with the players username (login name)
    @Getter
    private File playerFolder;

    @Setter
    @Getter
    private String name;

    // The default date format does not allow migrating between Java 17 and Java 20+ (in either direction)
    // Java 20+ uses unicode character U+202f while java 17- use a normal space before the AM/PM part of the date string
    // This date adapter will attempt to match between both variants but will always write with a space
    public final Gson CUSTOM_GSON = RuneLiteAPI.GSON.newBuilder()
            .registerTypeAdapter(Date.class, new LootRecordDateAdapter())
            .create();

    @Inject
    public LootLostWriter()
    {
        LOOT_RECORD_DIR.mkdir();
    }

    public boolean setPlayerUsername(final String username)
    {
        if (username.equalsIgnoreCase(name))
        {
            return false;
        }

        playerFolder = new File(LOOT_RECORD_DIR, username);
        playerFolder.mkdir();
        name = username;
        return true;
    }

    // ========== RISKED LOOT METHODS ==========

    /**
     * Add a risked loot record to storage using JSON Lines format (like Loot-Logger)
     */
    public synchronized boolean addRiskedLootRecord(com.doomlootlost.data.RiskedLootRecord record)
    {
        if (playerFolder == null || name == null)
        {
            log.warn("Player directory is null, cannot save risked loot record");
            return false;
        }

        final File file = new File(playerFolder, "risked_loot.log");
        
        // Convert record to JSON
        final String dataAsString = CUSTOM_GSON.toJson(record);

        // Open File in append mode and write new data (JSON Lines format)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true)))
        {
            writer.append(dataAsString);
            writer.newLine();
            return true;
        }
        catch (IOException e)
        {
            log.warn("Failed to save risked loot record", e);
            return false;
        }
    }

    /**
     * Load all risked loot records for the current player using JSON Lines format
     */
    public synchronized Collection<com.doomlootlost.data.RiskedLootRecord> loadRiskedLootRecords()
    {
        if (playerFolder == null || name == null)
        {
            return new ArrayList<>();
        }

        final File logFile = new File(playerFolder, "risked_loot.log");
        if (!logFile.exists())
        {
            return new ArrayList<>();
        }

        final Collection<com.doomlootlost.data.RiskedLootRecord> data = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                // Skip empty lines
                if (line.length() > 0)
                {
					try
                     {
                         final com.doomlootlost.data.RiskedLootRecord record = CUSTOM_GSON.fromJson(line, com.doomlootlost.data.RiskedLootRecord.class);
                         if (record != null && record.getItems() != null && record.getTimestamp() != null)
                         {
                             data.add(record);
                         }
                         else
                         {
                             log.warn("Skipping invalid risked loot record: {}", record);
                         }
                     }
                     catch (Exception e)
                     {
                         log.warn("Failed to parse risked loot record line: {}", line, e);
                     }
                }
            }
        }
        catch (FileNotFoundException e)
        {
            log.debug("Risked loot file not found: {}", logFile.getAbsolutePath());
        }
        catch (IOException e)
        {
            log.warn("IOException for file {}: {}", logFile.getAbsolutePath(), e.getMessage());
        }

        return data;
    }
}
