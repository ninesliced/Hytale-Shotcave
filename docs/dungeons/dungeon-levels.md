---
title: "Dungeon Levels"
order: 1
published: true
draft: false
---

# Dungeon Levels

Every run through Unstable Rifts builds a fresh route across 3 dungeon floors: Kweebec, Desert, and Toxic. The shape changes every time, but each floor keeps its own theme, enemy pressure, and boss at the end.

You still move through corridors, challenge rooms, side branches, treasure doors, shops, altars, and a final boss room. What changes as you go deeper is the mood of the floor and the kind of pressure your party has to handle.

![Dungeon Generation Example](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/dungeon_generation.png)
_A possible floor layout during dungeon generation_
![Dungeon Map View](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/dungeon_map.png)
_In-game dungeon map showing explored rooms_

---

## Run Structure

| Floor | Theme | Boss | What Changes Here |
|-------|-------|------|-------------------|
| Level 1: Kweebec | Corrupted forest ruins | Forklift | Mixed DeadWood pressure, wolves, and the first industrial enemies |
| Level 2: Desert | Dry industrial wasteland | Excavator | Heavier industrial presence, nastier ranged pressure, harsher fights |
| Level 3: Toxic | Poisoned industrial core | CEO Tank | Final floor, biggest enemy density, drones, and the strongest regular crate tier |

---

## Level 1: Kweebec

A corrupted forest village that acts as the opening floor of a run. Kweebec is the easiest place to learn how rooms connect, how side branches work, and how DeadWood packs behave, but it is still dangerous enough to punish a sloppy party.

![Kweebec Entrance](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/kweebec_entrance.png)
_The Kweebec level entrance room_
![Kweebec Combat](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/dungeons/kweebec_combat.png)
_Fighting DeadWood mobs in the Kweebec level_

### At A Glance

| Property | Value |
|----------|-------|
| Boss | Forklift |
| Main Path Rooms Before Boss | 5 |
| Challenge Rooms | 2 |
| Treasure Rooms | 1 |
| Shop Rooms | 1 |
| Branch Paths | 2 |
| Rooms per Branch | 1 |
| Total Mobs On Main Path | ~250 |
| Total Mobs On Branches | ~60 |

### Common Enemies

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

### Atmosphere And Props

Kweebec is the most ruined-looking floor in the run. Most rooms feel like a broken village that has already lost the fight.

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

A dry industrial war zone that marks the first real difficulty jump in a run. Desert keeps the same room structure as Kweebec, but the enemy mix leans much harder into industrial control, ranged hazards, and aggressive room pressure.

The floor still contains treasure rooms, safe shop rooms, and altar encounters, but there is much less downtime between dangerous fights.

### At A Glance

| Property | Value |
|----------|-------|
| Boss | Excavator |
| Main Path Rooms Before Boss | 5 |
| Challenge Rooms | 2 |
| Treasure Rooms | 1 |
| Shop Rooms | 1 |
| Branch Paths | 2 |
| Rooms per Branch | 1 |
| Total Mobs On Main Path | ~300 |
| Total Mobs On Branches | ~80 |

### Common Enemies

| Enemy | Spawn Weight | Notes |
|-------|-------------|-------|
| DeadWood Rootling | 5 | Returning melee enemy |
| DeadWood Rootling (Sword) | 5 | Armed variant |
| DeadWood Rootling (Axe) | 5 | Armed variant |
| DeadWood Rootling (Lance) | 5 | Armed variant |
| DeadWood Sproutling | 5 | Faster melee enemy |
| DeadWood Sproutling (Sword) | 5 | Armed variant |
| DeadWood Sproutling (Axe) | 5 | Armed variant |
| DeadWood Sproutling (Lance) | 5 | Armed variant |
| DeadWood Seedling | 4 | Heavier DeadWood body |
| Kweebec Seedling | 3 | Explosive runner |
| Radioactive Wolf | 5 | Fast flanking threat |
| Industrial Nosuit | 8 | Common industrial pressure |
| Industrial Hazmat | 5 | Melee bruiser |
| Industrial Hazmat (Flamethrower) | 2 | Close-range ranged pressure |
| Industrial Hazmat (Toxic Launcher) | 2 | Mid-range toxic projectile threat |

### What Makes Desert Different

- Industrial enemies stop feeling like rare interruptions and start feeling like the main force on the floor.
- Altars are more dangerous here because the follow-up wave can include multiple ranged Hazmat variants.
- Better loot starts showing up more consistently, especially from the stronger crate tiers deeper in the run.

---

## Level 3: Toxic

A poisoned industrial core and the final floor of the current run. Toxic keeps the same overall structure as the earlier floors, but it pushes the industrial faction to the front completely and introduces the most dangerous regular-room pressure in the game.

This is where level 3 crates begin to matter, where Industrial Drones start joining fights, and where the run ends against the CEO Tank.

### At A Glance

| Property | Value |
|----------|-------|
| Boss | CEO Tank |
| Main Path Rooms Before Boss | 5 |
| Challenge Rooms | 2 |
| Treasure Rooms | 1 |
| Shop Rooms | 1 |
| Branch Paths | 2 |
| Rooms per Branch | 1 |
| Total Mobs On Main Path | ~350 |
| Total Mobs On Branches | ~100 |

### Common Enemies

| Enemy | Spawn Weight | Notes |
|-------|-------------|-------|
| DeadWood Rootling | 2 | Rare returning melee enemy |
| DeadWood Rootling (Sword) | 2 | Armed variant |
| DeadWood Rootling (Axe) | 2 | Armed variant |
| DeadWood Rootling (Lance) | 2 | Armed variant |
| DeadWood Sproutling | 2 | Rare fast melee enemy |
| DeadWood Sproutling (Sword) | 2 | Armed variant |
| DeadWood Sproutling (Axe) | 2 | Armed variant |
| DeadWood Sproutling (Lance) | 2 | Armed variant |
| DeadWood Seedling | 2 | Heavy DeadWood body |
| Kweebec Seedling | 1 | Rare explosive runner |
| Radioactive Wolf | 5 | Fast flanking threat |
| Industrial Nosuit | 5 | Frontline industrial pressure |
| Industrial Hazmat | 8 | Common melee industrial enemy |
| Industrial Hazmat (Flamethrower) | 3 | Fire zone control |
| Industrial Hazmat (Toxic Launcher) | 3 | Toxic ranged pressure |
| Industrial Drone | 3 | Flying enemy that throws radioactive barrels |

### What Makes Toxic Different

- It is the most industrial-heavy floor in the run, with DeadWood reduced to a background threat instead of the main enemy group.
- Industrial Drones add vertical pressure and explosive barrel throws that force more movement.
- Level 3 crates can roll the full weapon pool and all 6 armor sets, including Void and Warden.
- The floor ends with the CEO Tank, the largest and longest boss fight in the current dungeon path.

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

Challenge rooms are set-piece encounters that lock the party in until the objective is finished.

When one starts, the room activates, the doors stay shut, and the challenge HUD tells the party what to do next. They are meant to interrupt normal corridor flow and force a focused objective or survival push.

### Challenge Objectives

| Objective Type | How It Works |
|----------------|--------------|
| Activation Zone | Reach the marked zone inside the room |
| Mob Clear | Defeat all enemies, or the required percentage of enemies |

Once every objective in the room is complete, the room is marked as cleared, the doors reopen, and the run continues.

## Altar Rooms

Altar rooms are risk-and-reward encounters that can appear on every floor.

When your party enters one, the altar spawns 3 Unique-rarity weapon choices on the ground. The first player to pick one claims the reward, and the other altar weapons disappear.

After the choice is made, the room can fully lock and the encounter begins. In practice, altar rooms ask your team to choose the reward first and then survive the wave that comes with it.

### Why Altars Matter

- They guarantee a very strong weapon reward.
- Only one altar weapon can be kept from that room.
- They add a high-risk power spike to Kweebec, Desert, and Toxic runs.

---

## Dungeon Generation

The game builds each floor in this order:

1. Placing the spawn room
2. Building the main corridor path up to the max room count
3. Inserting challenge rooms at random positions along the path
4. Adding treasure rooms, shop rooms, and other important encounters such as altars
5. Generating branch paths from splitter positions
6. Placing the boss room at the end of the main path
7. Connecting rooms with doors based on type assignments
8. Spawning enemies using the level's weight table

> **Note:** Side paths are shorter and lighter than the main route, but they still matter for keys, loot, and extra combat.

After a floor boss dies, the run moves forward to the next floor until the party reaches Toxic and defeats the CEO Tank.

---

## Level Comparison

| Property | Kweebec | Desert | Toxic |
|----------|---------|--------|-------|
| Theme | Corrupted forest ruins | Dry industrial wasteland | Poisoned industrial core |
| Boss | Forklift | Excavator | CEO Tank |
| Main Feel | Opening floor with mixed enemy pressure | Bigger industrial push and harsher room fights | Final floor with the densest enemy pressure |
| Best Loot Hook | Early altars and first build upgrades | Better crate quality and more dangerous altars | Level 3 crates and final-floor boss clear |
| Enemy Focus | DeadWood, wolves, early industrial presence | Strong industrial control with Hazmat support | Industrial-heavy roster with Drones and ranged hazards |
| Difficulty | Lower | Medium | High |

---

## Related Pages

- [Enemies and Bosses](enemies-and-bosses-1) - Detailed enemy stats and boss mechanics
- [Loot and Crates](loot-and-crates-1) - What drops in each level
- [Shops](shops-1) - In-dungeon shop mechanics
- [HUD and Interface](hud-and-interface-1) - Dungeon map and minimap features
