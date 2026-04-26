package dev.ninesliced.unstablerifts.systems;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Transient component used while an UnstableRifts player is in the custom dead/ghost state.
 * Stores the model that was visible before the ghost model was applied so it can be restored later.
 */
public final class GhostPlayerAppearanceComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, GhostPlayerAppearanceComponent> componentType;

    @Nullable
    private Model.ModelReference originalModelReference;
    @Nullable
    private PlayerSkin originalPlayerSkin;
    private boolean restoreFromPlayerSkin;

    public GhostPlayerAppearanceComponent() {
    }

    public GhostPlayerAppearanceComponent(@Nullable Model.ModelReference originalModelReference,
                                          @Nullable PlayerSkin originalPlayerSkin,
                                          boolean restoreFromPlayerSkin) {
        this.originalModelReference = originalModelReference;
        this.originalPlayerSkin = originalPlayerSkin != null ? new PlayerSkin(originalPlayerSkin) : null;
        this.restoreFromPlayerSkin = restoreFromPlayerSkin;
    }

    @Nonnull
    public static ComponentType<EntityStore, GhostPlayerAppearanceComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("GhostPlayerAppearanceComponent has not been registered yet");
        }
        return componentType;
    }

    public static void setComponentType(@Nonnull ComponentType<EntityStore, GhostPlayerAppearanceComponent> type) {
        componentType = type;
    }

    @Nullable
    public Model.ModelReference getOriginalModelReference() {
        return originalModelReference;
    }

    @Nullable
    public PlayerSkin getOriginalPlayerSkin() {
        return originalPlayerSkin;
    }

    public boolean shouldRestoreFromPlayerSkin() {
        return restoreFromPlayerSkin;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        GhostPlayerAppearanceComponent copy = new GhostPlayerAppearanceComponent();
        copy.originalModelReference = this.originalModelReference;
        copy.originalPlayerSkin = this.originalPlayerSkin != null ? new PlayerSkin(this.originalPlayerSkin) : null;
        copy.restoreFromPlayerSkin = this.restoreFromPlayerSkin;
        return copy;
    }
}