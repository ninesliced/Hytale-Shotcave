---
title: "Shops"
order: 3
published: true
draft: false
---

# Shops

Unstable Rifts has 2 different shop loops.

Inside the dungeon, every floor has a safe shop room where the party spends shared run coins for immediate upgrades. Outside the dungeon, the **Rift Merchant** handles personal missions, Rift Coin rewards, weapon crates, ammo, and boss trophies.

One shop helps you survive the current run. The other builds your account up between runs.

![Shop Room](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/shop/shop_room.png)
_A shop room with the shopkeeper NPC and displayed items_
![Shopkeeper](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/shop/shopkeeper.png)
_The shopkeeper NPC_

---

## Shop Systems At A Glance

| System | Where It Lives | Currency | What It Does |
|--------|----------------|----------|--------------|
| In-Dungeon Shop | One safe room on each dungeon floor | Shared team coins | Mid-run weapons, armor, healing, ammo, and utility |
| Rift Merchant | Outside the dungeon | Personal Rift Coins | Missions, weapon crates, ammo, and boss trophies |

---

## In-Dungeon Shops

These are the shops you visit during a run. Every floor includes one shop room, and that room is always a safe zone.

No enemies spawn inside the shop room, so it becomes the natural place to regroup, compare builds, and decide whether the party should spend now or save for a later floor.

---

## How to Buy

1. Enter the shop room. It is always a safe zone with no enemies.
2. Walk up to the shopkeeper NPC.
3. The shop UI opens automatically when you are within range.
4. Browse the available stock.
5. Click an item to open the purchase confirmation panel.
6. Confirm to spend team coins and receive the item.

---

## Payment

In-dungeon shops use **shared party coins**. Any teammate can buy items, and the cost always comes from the same team pool.

| Source | Description |
|--------|-------------|
| Enemy drops | Coins dropped by defeated enemies |
| Crate loot | Coins found in destructible crates |
| Boss drops | Guaranteed coin drops from boss kills |

See [Getting Started](getting-started-1#coins) for detailed coin drop rates.

---

## What You Usually Find

| Category | Description |
|----------|-------------|
| Weapons | Randomly generated with rarity and elemental effects |
| Armor | Armor pieces with set bonuses and stat rolls |
| Consumables | Healing items and ammo refills |
| Utility | Special items and buffs |

---

## Pricing

- Base prices are set when the run starts.
- One-time purchase items become unavailable after being bought.
- Repeatable items, like consumables, get more expensive each time you buy them.

Example: if an item starts at 50 coins and the price step is 10, then after buying it 2 times the new price becomes 70.

---

## Shop Refresh

Some shops have a refresh button that rerolls the current stock.

| Property | Description |
|----------|-------------|
| Refresh Cost | Costs team coins (varies by room) |
| Effect | Rerolls all unsold items |

> **Tip:** If the current stock does not fit your build, a refresh can be worth more than forcing a weak purchase.

---

## Shop Details

- Items are displayed as physical entities on the ground in the shop room for a preview.
- The shop UI shows full stat details before purchase: damage, protection, rarity, set bonus, and elemental effect.
- Shop inventory is independent per dungeon level. Moving to the next level gives you a new shop.
- Shop state is cleared when the party leaves the dungeon or progresses to the next level.

---

## Outside the Dungeon: Rift Merchant

Outside the dungeon, the **Rift Merchant** handles the between-run progression loop.

You can find the merchant near the party portal area. Talking to the merchant opens a mission page first, and from there you can move to the trade page.

This is where long-term rewards live. If you want weapon crates between runs, Rift Coin progression, or placeable boss trophies, this is the NPC that matters.

### Merchant Basics

| Property | Value |
|----------|-------|
| Currency | Rift Coins |
| Active Missions | 3 per player |
| Skip Cost | 10 Rift Coins |
| Stock Refresh | Daily at 07:00 |
| Main Rewards | Ammo, weapon crates, boss trophies |

This entire system is separate from the shared team coin economy used inside dungeon runs.

## Missions

Each player gets **3 active missions** at a time. These are personal missions, not party-wide missions, so every player tracks and claims their own progress separately.

When you complete one, you claim it for Rift Coins and that slot is immediately replaced with a new random mission from the pool.

### How Missions Work

1. Open the Rift Merchant page.
2. Check your 3 active missions.
3. Make progress across one or more runs.
4. Claim the mission when it is complete.
5. A new random mission replaces it.

If you do not want a mission, you can skip it for **10 Rift Coins** and roll a replacement immediately.

### Mission Categories

| Mission Type | Example Goal |
|--------------|--------------|
| Kill Enemies | Defeat 25, 50, 100, 200, or 500 enemies |
| Kill Bosses | Defeat 3, 5, 10, or 25 bosses |
| Complete Dungeons | Finish 1, 3, 5, 10, or 20 runs |
| Clear Challenge Rooms | Finish challenge encounters across runs |
| Open Key Rooms | Clear locked treasure routes |
| Claim Altar Weapons | Take altar rewards during a run |
| Buy Shop Items | Purchase items from in-dungeon shops |
| Collect Dungeon Coins | Pick up shared run coins |
| Activate Armor Abilities | Trigger full-set armor powers |

### Mission Rewards

Mission rewards scale with the size of the objective. Small missions pay only a few Rift Coins, while the biggest ones can pay well over 100.

Examples from the current mission pool include **Pest Control**, **Boss Hunter**, **Rift Veteran**, **Trial Champion**, **Rift Baron**, and **Unstoppable**.

## Rift Coins

Rift Coins are a separate currency from the shared coins used inside dungeon shops.

- **Team coins** are earned and spent during a dungeon run.
- **Rift Coins** are earned by claiming missions and spent at the outside Rift Merchant.

## Rift Merchant Shop

The merchant shop sells practical supplies first and long-term rewards second.

| Item | Cost | Stock | Notes |
|------|------|-------|-------|
| Gun Ammo | 5 Rift Coins | 500 | Straight ammo refill for your next run |
| Standard Weapon Crate | 10 Rift Coins | 300 | Weapon-only crate for lower-tier rolls |
| Advanced Weapon Crate | 25 Rift Coins | 200 | Weapon-only crate for stronger mid-to-high rolls |
| Elite Weapon Crate | 50 Rift Coins | 100 | Weapon-only crate aimed at top-end weapon rolls |

This stock refreshes daily at **07:00**, but the quantities are high enough that normal play rarely hits the cap.

### Weapon Crates

Merchant crates are different from regular dungeon crates.

- They are **weapon-only**.
- They do **not** drop armor, healing, ammo, or coins.
- They give you a reason to keep progressing even when you are between dungeon runs.

See [Loot and Crates](loot-and-crates-1) for the full merchant crate breakdown.

## Boss Trophies

The Rift Merchant also sells boss trophies once you have cleared the right boss enough times. These are placeable decorations that show long-term progress rather than help in combat.

Every boss has its own trophy line:

| Trophy Line | Boss | Floor |
|-------------|------|-------|
| Forklift Trophy | Forklift | Level 1 |
| Excavator Trophy | Excavator | Level 2 |
| CEO Trophy | CEO Tank | Level 3 |

### Trophy Unlock Tiers

| Tier | Unlock Requirement | Cost | Quality |
|------|--------------------|------|---------|
| Copper | 1 boss clear | 20 Rift Coins | Common |
| Silver | 3 boss clears | 40 Rift Coins | Uncommon |
| Gold | 7 boss clears | 80 Rift Coins | Rare |
| Mythril | 15 boss clears | 160 Rift Coins | Legendary |

Boss trophy progress is tracked separately for each boss. Forklift clears only help with Forklift trophies, Excavator clears only help with Excavator trophies, and CEO clears only help with CEO trophies.

---

## Related Pages

- [Weapons](weapons-1) - Weapon stats and rarity tiers
- [Armor Sets](armor-sets-1) - Armor stats and set bonuses
- [Getting Started](getting-started-1) - Coin economy basics
- [Loot and Crates](loot-and-crates-1) - What the merchant weapon crates can drop
- [Dungeon Levels](dungeon-levels-1) - Shop room placement
