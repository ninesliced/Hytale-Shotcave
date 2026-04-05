package dev.ninesliced.unstablerifts.shop;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.ninesliced.unstablerifts.guns.WeaponRarity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Block component that stores shop item configuration on a UnstableRifts_Shop_Item block.
 * Persists through chunk save/load and prefab save/load.
 * <p>
 * Each shop item block defines a single item slot in the shop:
 * what type of item, its price, and for weapons/armor the available pool and rarity range.
 */
public class ShopItemData implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<ShopItemData> CODEC = BuilderCodec.builder(ShopItemData.class, ShopItemData::new)
            .append(new KeyedCodec<>("ItemType", Codec.STRING), (d, v) -> d.itemType = v, d -> d.itemType).add()
            .append(new KeyedCodec<>("Price", Codec.STRING), (d, v) -> d.price = v, d -> d.price).add()
            .append(new KeyedCodec<>("Weapons", Codec.STRING), (d, v) -> d.weapons = v, d -> d.weapons).add()
            .append(new KeyedCodec<>("Armors", Codec.STRING), (d, v) -> d.armors = v, d -> d.armors).add()
            .append(new KeyedCodec<>("MinRarity", Codec.STRING), (d, v) -> d.minRarity = v, d -> d.minRarity).add()
            .append(new KeyedCodec<>("MaxRarity", Codec.STRING), (d, v) -> d.maxRarity = v, d -> d.maxRarity).add()
            .build();

    private static ComponentType<ChunkStore, ShopItemData> componentType;

    @Nullable
    private String itemType;
    @Nullable
    private String price;
    @Nullable
    private String weapons;
    @Nullable
    private String armors;
    @Nullable
    private String minRarity;
    @Nullable
    private String maxRarity;

    public ShopItemData() {
    }

    public ShopItemData(@Nullable String itemType, @Nullable String price,
                        @Nullable String weapons, @Nullable String armors,
                        @Nullable String minRarity, @Nullable String maxRarity) {
        this.itemType = itemType;
        this.price = price;
        this.weapons = weapons;
        this.armors = armors;
        this.minRarity = minRarity;
        this.maxRarity = maxRarity;
    }

    public static ComponentType<ChunkStore, ShopItemData> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<ChunkStore, ShopItemData> type) {
        componentType = type;
    }

    @Nullable
    public String getItemType() {
        return itemType;
    }

    @Nullable
    public ShopItemType parseItemType() {
        return ShopItemType.fromString(itemType);
    }

    @Nullable
    public String getPrice() {
        return price;
    }

    public int parsePrice() {
        if (price == null || price.isBlank()) return 10;
        try {
            return Integer.parseInt(price.trim());
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    @Nullable
    public String getWeapons() {
        return weapons;
    }

    @Nonnull
    public List<String> parseWeapons() {
        return parseIdList(weapons);
    }

    @Nonnull
    public List<WeightedEntry> parseWeaponEntries() {
        return parseWeightedEntries(weapons);
    }

    @Nullable
    public String getArmors() {
        return armors;
    }

    @Nonnull
    public List<String> parseArmors() {
        return parseIdList(armors);
    }

    @Nonnull
    public List<WeightedEntry> parseArmorEntries() {
        return parseWeightedEntries(armors);
    }

    @Nullable
    public String getMinRarity() {
        return minRarity;
    }

    @Nonnull
    public WeaponRarity parseMinRarity() {
        return minRarity != null ? WeaponRarity.fromString(minRarity) : WeaponRarity.BASIC;
    }

    @Nullable
    public String getMaxRarity() {
        return maxRarity;
    }

    @Nonnull
    public WeaponRarity parseMaxRarity() {
        return maxRarity != null ? WeaponRarity.fromString(maxRarity) : WeaponRarity.LEGENDARY;
    }

    /**
     * Parses a weighted entry string like "id1:3,id2:1" into just the ID list.
     * Also supports plain "id1,id2" (weight defaults to 1).
     */
    @Nonnull
    private static List<String> parseIdList(@Nullable String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(':');
            result.add(colon >= 0 ? trimmed.substring(0, colon).trim() : trimmed);
        }
        return result;
    }

    /**
     * Parses "id1:3,id2:1" into a list of WeightedEntry records.
     * Plain "id1" defaults to weight 1.
     */
    @Nonnull
    public static List<WeightedEntry> parseWeightedEntriesStatic(@Nullable String raw) {
        return parseWeightedEntries(raw);
    }

    @Nonnull
    private static List<WeightedEntry> parseWeightedEntries(@Nullable String raw) {
        List<WeightedEntry> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(':');
            if (colon >= 0) {
                String id = trimmed.substring(0, colon).trim();
                int weight;
                try {
                    weight = Integer.parseInt(trimmed.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    weight = 1;
                }
                if (!id.isEmpty()) result.add(new WeightedEntry(id, Math.max(1, weight)));
            } else {
                result.add(new WeightedEntry(trimmed, 1));
            }
        }
        return result;
    }

    /**
     * Serializes a list of weighted entries back to the "id:weight,id:weight" format.
     */
    @Nonnull
    public static String serializeWeightedEntries(@Nonnull List<WeightedEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(',');
            WeightedEntry e = entries.get(i);
            sb.append(e.id()).append(':').append(e.weight());
        }
        return sb.toString();
    }

    public record WeightedEntry(@Nonnull String id, int weight) {
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new ShopItemData(this.itemType, this.price, this.weapons, this.armors,
                this.minRarity, this.maxRarity);
    }
}
