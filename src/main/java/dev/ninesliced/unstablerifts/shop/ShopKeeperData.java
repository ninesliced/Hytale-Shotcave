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
 * Block component that stores shopkeeper configuration on a UnstableRifts_Shop_Keeper block.
 * Persists through chunk save/load and prefab save/load.
 */
public class ShopKeeperData implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<ShopKeeperData> CODEC = BuilderCodec.builder(ShopKeeperData.class, ShopKeeperData::new)
            .append(new KeyedCodec<>("ActionRange", Codec.STRING), (d, v) -> d.actionRange = v, d -> d.actionRange).add()
            .append(new KeyedCodec<>("RotationYaw", Codec.STRING), (d, v) -> d.rotationYaw = v, d -> d.rotationYaw).add()
            .append(new KeyedCodec<>("RefreshCost", Codec.STRING), (d, v) -> d.refreshCost = v, d -> d.refreshCost).add()
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
    private String refreshCount;

    public ShopKeeperData() {
    }

    public ShopKeeperData(@Nullable String actionRange,
                          @Nullable String rotationYaw,
                          @Nullable String refreshCost,
                          @Nullable String refreshCount) {
        this.actionRange = actionRange;
        this.rotationYaw = rotationYaw;
        this.refreshCost = refreshCost;
        this.refreshCount = refreshCount;
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
        if (refreshCost == null || refreshCost.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(refreshCost));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Nullable
    public String getRefreshCount() {
        return refreshCount;
    }

    public int parseRefreshCount() {
        if (refreshCount == null || refreshCount.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(refreshCount));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    @Nullable
    public Component<ChunkStore> clone() {
        return new ShopKeeperData(this.actionRange, this.rotationYaw, this.refreshCost, this.refreshCount);
    }
}
