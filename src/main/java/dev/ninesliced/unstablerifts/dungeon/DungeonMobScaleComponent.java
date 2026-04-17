package dev.ninesliced.unstablerifts.dungeon;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Marker component for dungeon mobs that still need their scale applied.
 * Retries are delayed slightly so the mob can finish its spawn/setup pipeline first.
 */
public final class DungeonMobScaleComponent implements Component<EntityStore> {

    private static final int INITIAL_DELAY_MS = 1000;
    private static final int RETRY_INTERVAL_MS = 1000;

    private static ComponentType<EntityStore, DungeonMobScaleComponent> componentType;

    private float targetScale;
    private int remainingDelayMs = INITIAL_DELAY_MS;

    public DungeonMobScaleComponent() {
        this.targetScale = 1.0f;
    }

    @Nonnull
    public static ComponentType<EntityStore, DungeonMobScaleComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("DungeonMobScaleComponent has not been registered yet");
        }
        return componentType;
    }

    public static void setComponentType(@Nonnull ComponentType<EntityStore, DungeonMobScaleComponent> type) {
        componentType = type;
    }

    public float getTargetScale() {
        return targetScale;
    }

    public void setTargetScale(float targetScale) {
        this.targetScale = targetScale;
    }

    public boolean shouldAttempt(int deltaMs) {
        remainingDelayMs -= deltaMs;
        if (remainingDelayMs > 0) {
            return false;
        }
        remainingDelayMs = RETRY_INTERVAL_MS;
        return true;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        DungeonMobScaleComponent copy = new DungeonMobScaleComponent();
        copy.targetScale = this.targetScale;
        copy.remainingDelayMs = this.remainingDelayMs;
        return copy;
    }
}
