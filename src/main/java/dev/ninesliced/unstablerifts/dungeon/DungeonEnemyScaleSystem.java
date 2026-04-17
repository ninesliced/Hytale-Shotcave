package dev.ninesliced.unstablerifts.dungeon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Applies dungeon mob scale from the entity itself, after spawn/setup has settled.
 * Follows the same deferred-retry pattern as {@link DungeonEnemyCircleSystem}.
 */
public final class DungeonEnemyScaleSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(DungeonMobScaleComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        DungeonMobScaleComponent marker = archetypeChunk.getComponent(index, DungeonMobScaleComponent.getComponentType());
        if (marker == null) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (!ref.isValid()) {
            return;
        }

        float targetScale = marker.getTargetScale();

        EntityScaleComponent scaleComp = commandBuffer.getComponent(ref, EntityScaleComponent.getComponentType());
        if (scaleComp != null && scaleComp.getScale() == targetScale) {
            commandBuffer.removeComponent(ref, DungeonMobScaleComponent.getComponentType());
            return;
        }

        int deltaMs = Math.round(dt * 1000.0f);
        if (!marker.shouldAttempt(deltaMs)) {
            return;
        }

        commandBuffer.run(entityStore -> {
            EntityScaleComponent existing = entityStore.getComponent(ref, EntityScaleComponent.getComponentType());
            if (existing != null) {
                existing.setScale(targetScale);
            } else {
                EntityScaleComponent created = entityStore.ensureAndGetComponent(ref, EntityScaleComponent.getComponentType());
                if (created != null) {
                    created.setScale(targetScale);
                }
            }
        });
    }
}
