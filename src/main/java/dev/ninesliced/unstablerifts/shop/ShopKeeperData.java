package dev.ninesliced.unstablerifts.shop;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Block component that stores shopkeeper and room-level shop pricing configuration
 * on a UnstableRifts_Shop_Keeper block.
 * Persists through chunk save/load and prefab save/load.
 */
public class ShopKeeperData implements Component<ChunkStore> {

    public static final int DEFAULT_REFRESH_PRICE_STEP = 10;
    public static final int DEFAULT_WEAPON_PRICE_STEP = 25;
    public static final int DEFAULT_ARMOR_PRICE_STEP = 25;
    public static final int DEFAULT_ITEM_PRICE_STEP = 10;

    @Nonnull
    public static final BuilderCodec<ShopKeeperData> CODEC = BuilderCodec.builder(ShopKeeperData.class, ShopKeeperData::new)
            .append(new KeyedCodec<>("ActionRange", Codec.STRING), (d, v) -> d.actionRange = v, d -> d.actionRange).add()
            .append(new KeyedCodec<>("RotationYaw", Codec.STRING), (d, v) -> d.rotationYaw = v, d -> d.rotationYaw).add()
            .append(new KeyedCodec<>("RefreshCost", Codec.STRING), (d, v) -> d.refreshCost = v, d -> d.refreshCost).add()
        .append(new KeyedCodec<>("WeaponPriceStep", Codec.STRING), (d, v) -> d.weaponPriceStep = v, d -> d.weaponPriceStep).add()
        .append(new KeyedCodec<>("ArmorPriceStep", Codec.STRING), (d, v) -> d.armorPriceStep = v, d -> d.armorPriceStep).add()
        .append(new KeyedCodec<>("ItemPriceStep", Codec.STRING), (d, v) -> d.itemPriceStep = v, d -> d.itemPriceStep).add()
            .append(new KeyedCodec<>("RefreshCount", Codec.STRING), (d, v) -> d.refreshCount = v, d -> d.refreshCount).add()
            .build();

    private static ComponentType<ChunkStore, ShopKeeperData> componentType;

    @Nullable
    private String actionRange;
    @Nullable
    private String rotationYaw;
    @Nullable
    private String refreshCost;
    @Nullable
    private String weaponPriceStep;
    @Nullable
    private String armorPriceStep;
    @Nullable
    private String itemPriceStep;
    @Nullable
    private String refreshCount;

    public ShopKeeperData() {
    }

    public ShopKeeperData(@Nullable String actionRange,
                          @Nullable String rotationYaw,
                          @Nullable String refreshCost,
                          @Nullable String refreshCount,
                          @Nullable String weaponPriceStep,
                          @Nullable String armorPriceStep,
                          @Nullable String itemPriceStep) {
        this.actionRange = actionRange;
        this.rotationYaw = rotationYaw;
        this.refreshCost = refreshCost;
        this.refreshCount = refreshCount;
        this.weaponPriceStep = weaponPriceStep;
        this.armorPriceStep = armorPriceStep;
        this.itemPriceStep = itemPriceStep;
    }

    public static ComponentType<ChunkStore, ShopKeeperData> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<ChunkStore, ShopKeeperData> type) {
        componentType = type;
    }

    @Nullable
    public String getActionRange() {
        return actionRange;
    }

    public double parseActionRange() {
        if (actionRange == null || actionRange.isBlank()) return 5.0;
        try {
            return Double.parseDouble(actionRange);
        } catch (NumberFormatException e) {
            return 5.0;
        }
    }

    @Nullable
    public String getRotationYaw() {
        return rotationYaw;
    }

    public double parseRotationYawDegrees(double fallbackDegrees) {
        if (rotationYaw == null || rotationYaw.isBlank()) return fallbackDegrees;
        try {
            return Double.parseDouble(rotationYaw);
        } catch (NumberFormatException e) {
            return fallbackDegrees;
        }
    }

    @Nullable
    public String getRefreshCost() {
        return refreshCost;
    }

    public int parseRefreshCost() {
        return parseNonNegativeInt(refreshCost, DEFAULT_REFRESH_PRICE_STEP);
    }

    @Nullable
    public String getWeaponPriceStep() {
        return weaponPriceStep;
    }

    public int parseWeaponPriceStep() {
        return parseNonNegativeInt(weaponPriceStep, DEFAULT_WEAPON_PRICE_STEP);
    }

    @Nullable
    public String getArmorPriceStep() {
        return armorPriceStep;
    }

    public int parseArmorPriceStep() {
        return parseNonNegativeInt(armorPriceStep, DEFAULT_ARMOR_PRICE_STEP);
    }

    @Nullable
    public String getItemPriceStep() {
        return itemPriceStep;
    }

    public int parseItemPriceStep() {
        return parseNonNegativeInt(itemPriceStep, DEFAULT_ITEM_PRICE_STEP);
    }

    @Nullable
    public String getRefreshCount() {
        return refreshCount;
    }

    public int parseRefreshCount() {
        return parseNonNegativeInt(refreshCount, 0);
    }

    private static int parseNonNegativeInt(@Nullable String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new ShopKeeperData(
                this.actionRange,
                this.rotationYaw,
                this.refreshCost,
                this.refreshCount,
                this.weaponPriceStep,
                this.armorPriceStep,
                this.itemPriceStep);
    }
}
