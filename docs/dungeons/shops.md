---
title: "Shops"
order: 3
published: true
draft: false
---

# Shops

Unstable Rifts has two shop systems: the safe shop room inside each dungeon level, and the outside **Rift Merchant** that handles missions and Rift Coin trades.

Each dungeon level still contains one shop room with a shopkeeper NPC. These in-dungeon shops are safe zones - no enemies will spawn in a shop room.

![Shop Room](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/shop/shop_room.png)
_A shop room with the shopkeeper NPC and displayed items_
![Shopkeeper](https://raw.githubusercontent.com/ninesliced/UnstableRifts/refs/heads/main/img/shop/shopkeeper.png)
_The shopkeeper NPC_

---

## In-Dungeon Shops

These are the shops you visit during a run.

---

## How to Buy

1. Enter the shop room. It is always a safe zone with no enemies.
2. Walk up to the shopkeeper NPC.
3. The shop UI opens automatically when you are within range.
4. Browse available items. Each item shows its stats, rarity, and price.
5. Click an item to open the purchase confirmation panel.
6. Confirm to spend team coins and receive the item.

---

## Payment

Shops use shared party coins. Any teammate can buy items, and the cost comes from the same coin pool.

| Source | Description |
|--------|-------------|
| Enemy drops | Coins dropped by defeated enemies |
| Crate loot | Coins found in destructible crates |
| Boss drops | Guaranteed coin drops from boss kills |

See [Getting Started](getting-started-1#coins) for detailed coin drop rates.

---

## Item Categories

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
- Repeatable items (like consumables) get more expensive each time you buy them:
- Price = Base Price + (Purchase Count x Price Step)

Example: if an item starts at 50 coins and the price step is 10, then after buying it 2 times the new price becomes 70.

---

## Shop Refresh

Some shops have a refresh button to reroll the item list:

| Property | Description |
|----------|-------------|
| Refresh Cost | Costs team coins (varies by room) |
| Effect | Rerolls all unsold items |

> **Tip:** If you do not like the current items, refresh the shop to roll a new list.
---

## Shop Details

- Items are displayed as physical entities on the ground in the shop room for a preview.
- The shop UI shows full stat details before purchase: damage, protection, rarity, set bonus, elemental effect.
- Shop inventory is independent per dungeon level. Moving to the next level gives you a new shop.
- Shop state is cleared when the party leaves the dungeon or progresses to the next level.

---

## Outside the Dungeon: Rift Merchant

Outside the dungeon, Unstable Rifts also has a **Rift Merchant** NPC. This merchant gives players a longer-term loop between runs through missions and Rift Coin rewards.

You can find the Rift Merchant near party portal locations outside the dungeon.

The Rift Merchant opens a special page with 2 functions:

- view and claim active missions
- open the barter shop to spend Rift Coins

This system is separate from the shared team coin economy used inside dungeon runs.

## Missions

Each player gets **3 active missions** at a time. These missions are personal, so every player tracks and claims their own progress.

### Mission Types

| Mission Type | Example Goal |
|--------------|--------------|
| Kill Enemies | Defeat a set number of enemies across runs |
| Kill Bosses | Defeat a set number of bosses |
| Complete Dungeons | Finish a set number of dungeon runs |

When you complete a mission, you can claim it from the Rift Merchant page and receive **Rift Coins**. The claimed mission is then replaced with a new random one.

If you do not want a mission, you can skip it for **10 Rift Coins** and roll a replacement.

## Rift Coins

Rift Coins are a separate currency from the shared coins used inside dungeon shops.

- **Team coins** are earned and spent during a dungeon run.
- **Rift Coins** are earned by claiming missions and spent at the outside Rift Merchant.

## Rift Merchant Shop

The merchant's barter shop currently sells ammo and weapon crates.

| Item | Cost | Stock | Notes |
|------|------|-------|-------|
| Ammo | 5 Rift Coins | 5 | Extra ammo supply outside the run |
| Standard Weapon Crate | 10 Rift Coins | 3 | Weapon-only crate, Common to Rare |
| Advanced Weapon Crate | 25 Rift Coins | 2 | Weapon-only crate, Rare to Legendary |
| Elite Weapon Crate | 50 Rift Coins | 1 | Weapon-only crate, Legendary to Unique |

The merchant stock refreshes daily at **07:00**, so this system gives players something to work toward even when they are between dungeon runs.

---

## Related Pages

- [Weapons](weapons-1) - Weapon stats and rarity tiers
- [Armor Sets](armor-sets-1) - Armor stats and set bonuses
- [Getting Started](getting-started-1) - Coin economy basics
- [Loot and Crates](loot-and-crates-1) - What the merchant weapon crates can drop
- [Dungeon Levels](dungeon-levels-1) - Shop room placement
