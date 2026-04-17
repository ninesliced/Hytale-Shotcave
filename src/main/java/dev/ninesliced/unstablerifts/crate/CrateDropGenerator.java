package dev.ninesliced.unstablerifts.crate;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.ninesliced.unstablerifts.armor.ArmorLootRoller;
import dev.ninesliced.unstablerifts.guns.WeaponLootRoller;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Config-driven loot generator for crates. Reads per-crate settings from
 * {@link CrateLootConfig} (loaded from {@code crate_loot.json}).
 */
public final class CrateDropGenerator {

    private static final String COIN_ITEM_ID = "UnstableRifts_Props_Coin";
    private static final String AMMO_ITEM_ID = "UnstableRifts_Ammo_Item";
    private static final String HEAL_ITEM_ID = "UnstableRifts_Heal_Item";

    private CrateDropGenerator() {
    }

    /**
     * Returns true if the given block type ID is a configured crate.
     */
    public static boolean isCrate(@Nonnull String blockTypeId) {
        return CrateLootConfig.isCrate(blockTypeId);
    }

    /**
     * Generates drops for a crate block type based on its config entry.
     * Returns coins (always) and optionally ammo/heal pickups, plus a rolled
     * weapon and/or armor piece.
     */
    @Nonnull
    public static List<ItemStack> generateDrops(@Nonnull String blockTypeId) {
        CrateLootConfig.CrateLootEntry entry = CrateLootConfig.getCrateConfig(blockTypeId);
        if (entry == null) return List.of();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<ItemStack> drops = new ArrayList<>(3);

        int coinQuantity = rng.nextInt(entry.getCoinMin(), entry.getCoinMax() + 1);
        if (coinQuantity > 0) {
            drops.add(new ItemStack(COIN_ITEM_ID, coinQuantity));
        }

        if (rng.nextDouble() < entry.getAmmoChance()) {
            drops.add(new ItemStack(AMMO_ITEM_ID, 1));
        }

        if (rng.nextDouble() < entry.getHealChance()) {
            drops.add(new ItemStack(HEAL_ITEM_ID, 1));
        }

        if (rng.nextDouble() < entry.getWeaponChance()) {
            List<String> whitelist = entry.getWeaponWhitelist();
            Map<String, Double> rarityWeights = entry.getRarityWeights();
            if (rarityWeights != null && !rarityWeights.isEmpty() && !whitelist.isEmpty()) {
                drops.add(WeaponLootRoller.rollFromCrateWithWeights(rarityWeights, whitelist));
            } else if (!whitelist.isEmpty()) {
                drops.add(WeaponLootRoller.rollFromCrate(
                        entry.getMinRarity(), entry.getMaxRarity(), whitelist));
            } else {
                drops.add(WeaponLootRoller.rollRandom());
            }
        }

        if (rng.nextDouble() < entry.getArmorChance()) {
            List<String> armorWhitelist = entry.getArmorWhitelist();
            if (!armorWhitelist.isEmpty()) {
                drops.add(ArmorLootRoller.rollFromCrate(
                        entry.getArmorMinRarity(), entry.getArmorMaxRarity(), armorWhitelist));
            } else {
                drops.add(ArmorLootRoller.rollRandom());
            }
        }

        return drops;
    }

    /**
     * Returns true if the given item ID is a shop crate (opened from inventory, not broken as block).
     */
    public static boolean isShopCrate(@Nonnull String itemId) {
        return "UnstableRifts_Shop_Crate_T1".equals(itemId)
                || "UnstableRifts_Shop_Crate_T2".equals(itemId)
                || "UnstableRifts_Shop_Crate_T3".equals(itemId);
    }

    /**
     * Generates a single weapon drop from a shop crate item ID using its custom rarity weights.
     */
    @Nonnull
    public static List<ItemStack> generateShopCrateDrops(@Nonnull String itemId) {
        CrateLootConfig.CrateLootEntry entry = CrateLootConfig.getCrateConfig(itemId);
        if (entry == null) return List.of();

        List<String> whitelist = entry.getWeaponWhitelist();
        Map<String, Double> rarityWeights = entry.getRarityWeights();

        if (whitelist.isEmpty()) return List.of();

        ItemStack weapon;
        if (rarityWeights != null && !rarityWeights.isEmpty()) {
            weapon = WeaponLootRoller.rollFromCrateWithWeights(rarityWeights, whitelist);
        } else {
            weapon = WeaponLootRoller.rollFromCrate(entry.getMinRarity(), entry.getMaxRarity(), whitelist);
        }
        return List.of(weapon);
    }
}
