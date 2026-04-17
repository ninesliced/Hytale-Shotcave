package dev.ninesliced.unstablerifts.dungeon;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Marker component for dungeon mobs that still need their visibility circle applied.
 * Retries are delayed slightly so the mob can finish its spawn/setup pipeline first.
 */
public final class DungeonMobCircleComponent implements Component<EntityStore> {

    private static final int INITIAL_DELAY_MS = 1000;
    private static final int RETRY_INTERVAL_MS = 1000;

    private static ComponentType<EntityStore, DungeonMobCircleComponent> componentType;

    private int remainingDelayMs = INITIAL_DELAY_MS;

    public DungeonMobCircleComponent() {
    }

    @Nonnull
    public static ComponentType<EntityStore, DungeonMobCircleComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("DungeonMobCircleComponent has not been registered yet");
        }
        return componentType;
    }

    public static void setComponentType(@Nonnull ComponentType<EntityStore, DungeonMobCircleComponent> type) {
        componentType = type;
    }

    public boolean shouldAttempt(int deltaMs) {
        remainingDelayMs -= deltaMs;
        if (remainingDelayMs > 0) {
            return false;
        }
        remainingDelayMs = RETRY_INTERVAL_MS;
        return true;
    }

    public void resetForImmediateAttempt() {
        remainingDelayMs = 0;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        DungeonMobCircleComponent copy = new DungeonMobCircleComponent();
        copy.remainingDelayMs = this.remainingDelayMs;
        return copy;
    }
}