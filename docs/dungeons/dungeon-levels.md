---
title: "Dungeon Levels"
order: 1
published: true
draft: false
---

# Dungeon Levels

Each run builds a fresh dungeon layout. Rooms connect with corridors, side paths, locked doors, and special rooms. No two runs are exactly the same.

![Dungeon Generation Example](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/dungeon_generation.png)
_Example of level 1 possible rooms_
![Dungeon Map View](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/dungeon_map.png)
_In-game dungeon map showing explored rooms_

---

## Level 1: Kweebec

A corrupted forest full of DeadWood enemies and radioactive wildlife.
![Kweebec Entrance](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/kweebec_entrance.png)
_The Kweebec level entrance room_
![Kweebec Combat](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/kweebec_combat.png)
_Fighting DeadWood mobs in the Kweebec level_

### Layout

| Property | Value |
|----------|-------|
| Main Path Rooms | 5 |
| Challenge Rooms | 2 |
| Treasure Rooms | 1 |
| Shop Rooms | 1 |
| Branch Paths | 2 |
| Rooms per Branch | 1 |
| Total Mobs (main) | ~250 |
| Total Mobs (branches) | ~60 |

### Mob Pool

| Enemy | Spawn Weight | Notes |
|-------|-------------|-------|
| DeadWood Rootling | 10 | Basic melee enemy |
| DeadWood Rootling (Sword) | 5 | Armed variant |
| DeadWood Rootling (Axe) | 5 | Armed variant |
| DeadWood Rootling (Lance) | 5 | Armed variant |
| DeadWood Sproutling | 10 | Small, fast enemy |
| DeadWood Sproutling (Sword) | 5 | Armed variant |
| DeadWood Sproutling (Axe) | 5 | Armed variant |
| DeadWood Sproutling (Lance) | 5 | Armed variant |
| DeadWood Seedling | 4 | Larger DeadWood enemy |
| Kweebec Seedling | 3 | Explosive Kweebec |
| Radioactive Wolf | 5 | Fast pack animal |
| Industrial Nosuit | 3 | Unarmored worker |

**Total Weight:** 65. Bigger weight means that enemy appears more often.

### Decorative Props

The Kweebec level includes themed environmental props:

| Block | Description |
|-------|-------------|
| Ruined Kweebec Bed | Damaged Kweebec furniture |
| Ruined Kweebec Candle | Extinguished candle |
| Ruined Kweebec Chest | Broken storage chest |
| Ruined Kweebec Door | Damaged doorway |
| Ruined Kweebec Lantern | Shattered lantern |
| Ruined Kweebec Plush | Tattered plush toy |
| Ruined Kweebec Sign | Weathered sign post |
| Ruined Kweebec Statue | Crumbled statue |
| Ruined Kweebec Stool | Broken stool |
| Ruined Kweebec Table | Damaged table |
| Ruined Kweebec Wardrobe | Ruined wardrobe |
![Kweebec Props](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/props/kweebec_props.png)
_Ruined Kweebec furniture props in a dungeon room_
![Radioactive Barrels](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/props/radioactive_barrels.png)
_Radioactive barrels - breakable environmental hazards_
---

## Level 2: Desert

A dry industrial zone with tougher enemies and harder fights.

The Desert is the second dungeon level and feels more like enemy-held territory than a ruined forest. It keeps the same dungeon structure as Kweebec, but the pressure is higher and the industrial faction has a much stronger presence.

### What Makes Desert Different

- Industrial encounters are much more common here, with heavier pressure from hostile workers and Hazmat-style enemies.
- The level is tuned to be harsher than Kweebec, so mistakes are punished more quickly.
- Important rooms can include altar encounters, giving your party a powerful weapon choice before the fight fully starts.
- The overall pacing is more aggressive, with less downtime between dangerous rooms.

### Desert Overview

| Feature | Description |
|---------|-------------|
| Theme | Dry industrial wasteland taken over by the invaders |
| Combat Feel | Harder, more aggressive, and more industrial-focused |
| Core Flow | Corridors, challenge rooms, shops, altar encounters, and a boss room |
| Enemy Mix | Returning Kweebec threats plus stronger industrial pressure |
| Role In A Run | The difficulty spike after Kweebec |

---

## Room Types

### Gameplay Rooms

| Room Type | Description |
|-----------|-------------|
| Spawn | Starting room for each level |
| Corridor | Connecting passages between rooms |
| Challenge | Combat arenas with reward triggers |
| Treasure | High-value loot behind locked doors |
| Shop | Safe room with a shopkeeper NPC |
| Boss | Final room with the level boss |
| Branch | Side path rooms off the main path |
| Wall | Dead-end seals |

### Door Types

| Door Type | Description |
|-----------|-------------|
| Key Door | Requires a key item found elsewhere in the level |
| Activation Door | Opens when a nearby trigger is activated |
| Lock Door | Locked until a specific condition is met |
| Sealed Door | Permanently sealed passage |
![Challenge Room](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/challenge_room.png)
_A challenge room with mob spawners_
![Treasure Room](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/treasure_room.png)
_A treasure room behind a key door_
![Shop Room](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/shop_room.png)
_Shop room with the shopkeeper NPC_

## Challenge Rooms

Challenge rooms are special combat encounters that lock the party in until the objective is finished.

When a challenge starts, the room activates, the doors stay shut, and the challenge HUD tells the party what to do next. These rooms are meant to break the normal corridor flow and force a focused fight or objective push.

### Challenge Objectives

| Objective Type | How It Works |
|----------------|--------------|
| Activation Zone | Reach the marked zone inside the room |
| Mob Clear | Defeat all enemies, or the required percentage of enemies |

Once every objective in the room is complete, the room is marked as cleared, the doors reopen, and the run continues.

## Altar Rooms

Altar rooms are special risk-and-reward encounters found in important room slots.

When your party enters one, the altar spawns 3 Unique-rarity weapon choices on the ground. The first player to pick one claims that reward, and the other altar weapons disappear.

After the choice is made, the room can fully lock and the encounter begins. In other words, altar rooms ask your team to choose the reward first and then survive the fight that comes with it.

### Why Altars Matter

- They guarantee a very strong weapon reward.
- Only one altar weapon can be kept from that room.
- They add a high-risk power spike to both Kweebec and Desert runs.

---

## Dungeon Generation

The game builds each run in this order:

1. Placing the spawn room
2. Building the main corridor path up to the max room count
3. Inserting challenge rooms at random positions along the path
4. Adding treasure rooms, shop rooms, and other important encounters such as altars
5. Generating branch paths from splitter positions
6. Placing the boss room at the end of the main path
7. Connecting rooms with doors based on type assignments
8. Spawning enemies using the level's weight table

> **Note:** Side paths are usually shorter and have fewer enemies, but they can still include challenge rooms.
---

## Level Comparison

| Property | Kweebec | Desert |
|----------|---------|--------|
| Theme | Corrupted forest ruins | Industrial wasteland |
| Main Feel | Intro level with mixed enemy pressure | Harder follow-up level with stronger industrial pressure |
| Special Encounters | Treasure rooms, shops, altars | Challenge-heavy fights, shops, altars |
| Enemy Focus | DeadWood, wolves, early industrial presence | Returning Kweebec enemies plus stronger industrial control |
| Difficulty | Lower | Higher |

---

## Related Pages

- [Enemies and Bosses](enemies-and-bosses-1) - Detailed enemy stats and boss mechanics
- [Loot and Crates](loot-and-crates-1) - What drops in each level
- [Shops](shops-1) - In-dungeon shop mechanics
- [HUD and Interface](hud-and-interface-1) - Dungeon map and minimap features
