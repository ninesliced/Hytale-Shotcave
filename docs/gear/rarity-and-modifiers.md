---
title: Rarity, Modifiers & Effects
order: 2
published: true
---

# Rarity, Modifiers & Effects

Every weapon drop can have three parts: a rarity tier, an element effect, and bonus stats (modifiers). So two drops of the same weapon can still feel different.

---

## Rarity Tiers

When a weapon drops, the game rolls its rarity from a weighted list. Higher tiers are harder to get, but they give more power.

| Tier | Spawn Chance | Modifier Slots | Effect Chance | Effect DoT Duration | Glow |
|------|:-----------:|:--------------:|:-------------:|:-------------------:|------|
| <span class="rarity-basic">Basic</span> | 45% | 0 | 0% | — | None |
| <span class="rarity-uncommon">Uncommon</span> | 25% | 1 | 5% | 0.55 s | Green |
| <span class="rarity-rare">Rare</span> | 15% | 2 | 10% | 0.60 s | Blue |
| <span class="rarity-epic">Epic</span> | 8% | 3 | 20% | 0.70 s | Purple |
| <span class="rarity-legendary">Legendary</span> | 5% | 4 | 50% | 0.85 s | Gold |
| <span class="rarity-unique">Unique</span> | 2% | 5 | 100% | 1.00 s | Red |

**Effect duration** starts at 0.5 s and grows with rarity.

> [!NOTE]
> BASIC weapons always drop without an effect or any modifiers. UNIQUE weapons always carry a damage effect.

---

## Elemental Effects

If a weapon passes its effect roll, one of the 5 effects below is picked at random. Some weapons (like Muskets) have a fixed effect and always use that one.

| Effect | Trail | Entity Status | Description |
|--------|-------|--------------|-------------|
| <span class="effect-fire">FIRE</span> | Orange | `Flame_Staff_Burn` | Sets the target ablaze, dealing damage over time. |
| <span class="effect-ice">ICE</span> | Blue | `Slow` | Chills the target, reducing movement speed. |
| <span class="effect-electricity">ELECTRICITY</span> | Yellow | `Stun` | Stuns the target briefly on each hit. |
| <span class="effect-void">VOID</span> | Purple | `UnstableRifts_Void_Portal_DOT` | Inflicts void corruption, dealing periodic void damage. |
| <span class="effect-acid">ACID</span> | Green | `UnstableRifts_Poison` | Poisons the target, dealing damage over time. |

Higher rarity means effects last longer.

---

## Weapon Modifiers

Modifiers are extra stat boosts. Weapons start getting modifier slots at Uncommon rarity. Each slot rolls one random bonus that matches that weapon type.

| Modifier | Display Name | Applies To | Rolled Bonus |
|----------|-------------|-----------|:------------:|
| `MAX_BULLETS` | Max Ammo | Laser, Bullet, Summoning | +10 – 30% |
| `ATTACK_SPEED` | Speed | All categories | +20% (fixed) |
| `ADDITIONAL_BULLETS` | Pellets | Laser only | +1 – 2 pellets |
| `WEAPON_DAMAGE` | Damage | Laser, Bullet, Melee | +10 – 30% |
| `PRECISION` | Precision | Laser, Bullet | +30 – 50% |
| `KNOCKBACK` | Knockback | Laser, Bullet, Melee | +10 – 20% |
| `MAX_RANGE` | Range | Laser, Bullet | +30 – 50% |
| `MOB_HEALTH` | Mob HP | Summoning only | +20 – 50% |
| `MOB_DAMAGE` | Mob Dmg | Summoning only | +20 – 50% |
| `MOB_LIFETIME` | Mob Life | Summoning only | +20 – 50% |

### Modifier Eligibility Rules

Some modifiers are skipped automatically when they do not make sense for that weapon:

- **Precision** is skipped on very accurate weapons (spread ≤ 0.05).
- **Pellets** is skipped on single-shot weapons (pellets ≤ 1).
- **Knockback** is skipped if the weapon has no base knockback.

So each weapon has its own valid modifier pool.

---

## Rolling Summary

1. **Rarity:** the game rolls a tier from the rarity table.
2. **Effect:** fixed-effect weapons keep their element; others roll by rarity chance.
3. **Modifiers:** each modifier slot rolls one valid bonus for that weapon type.
