package dev.ninesliced.unstablerifts.dungeon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Applies dungeon enemy circles from the enemy entity itself, after spawn/setup has settled.
 */
public final class DungeonEnemyCircleSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(DungeonMobCircleComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        DungeonMobCircleComponent marker = archetypeChunk.getComponent(index, DungeonMobCircleComponent.getComponentType());
        if (marker == null) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (!ref.isValid()) {
            return;
        }

        EffectControllerComponent effectController = commandBuffer.getComponent(ref, EffectControllerComponent.getComponentType());
        if (DungeonCircleEffectService.hasEnemyCircle(effectController)) {
            commandBuffer.removeComponent(ref, DungeonMobCircleComponent.getComponentType());
            return;
        }

        int deltaMs = Math.round(dt * 1000.0f);
        if (!marker.shouldAttempt(deltaMs)) {
            return;
        }

        commandBuffer.run(entityStore -> DungeonCircleEffectService.applyEnemyCircle(ref, entityStore));
    }
}