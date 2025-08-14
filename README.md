# Doom Loot Lost

A RuneLite plugin that tracks loot specifically from "Doom of Mokhaiotl" boss encounters.

## Features

- **Doom of Mokhaiotl Death Tracking**: Tracks deaths to the Doom of Mokhaiotl boss
- **Local Data Storage**: Stores loot lost data locally in JSON Lines format
- **Side Panel UI**: Clean interface for viewing loot history

## Data Storage

Data is stored at `~/.runelite/loots/HASH/` where `HASH` is your account's unique identifier.
Records are stored in JSON Lines format in files named `doom of mokhaiotl.log`.

## Configuration

- **Enable Side-Panel**: Toggle the side panel UI on/off

## Usage

1. Enable the plugin in RuneLite
2. Ensure Loot Tracker plugin is enabled
3. Die to Doom of Mokhaiotl
4. Use the side panel to view loot you've lost
