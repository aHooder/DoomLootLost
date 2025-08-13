# Doom Loot Lost

A RuneLite plugin that tracks loot specifically from "Doom of Mokhaiotl" boss encounters.

## Features

- **Doom of Mokhaiotl Tracking**: Only tracks loot from the Doom of Mokhaiotl boss
- **Local Data Storage**: Stores loot data locally in JSON Lines format
- **Session Tracking**: Separates current session loot from historical data
- **Kill Count Tracking**: Automatically tracks and displays kill counts
- **Side Panel UI**: Clean interface for viewing loot history
- **Value Calculation**: Shows individual item values and total loot value

## Data Storage

Data is stored at `~/.runelite/loots/HASH/` where `HASH` is your account's unique identifier.
Records are stored in JSON Lines format in files named `doom of mokhaiotl.log`.

## Requirements

- Requires the built-in **Loot Tracker** plugin to be enabled
- Compatible with RuneLite client

## Configuration

- **Enable Side-Panel**: Toggle the side panel UI on/off
- **Show Kill Count**: Display current kill count for Doom of Mokhaiotl
- **Track Session Data**: Keep current session loot separate from historical data
- **Auto-save on Death**: Automatically save loot data when player dies

## Usage

1. Enable the plugin in RuneLite
2. Ensure Loot Tracker plugin is enabled
3. Die to Doom of Mokhaiotl
4. Use the side panel to view loot you've lost