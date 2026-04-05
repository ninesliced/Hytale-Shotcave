package dev.ninesliced.unstablerifts.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;
import dev.ninesliced.unstablerifts.dungeon.Game;
import dev.ninesliced.unstablerifts.pickup.ItemPickupTracker;
import dev.ninesliced.unstablerifts.shop.ShopDisplayItemComponent;

import javax.annotation.Nonnull;

/**
 * Removes sold shop display props from the world even if the immediate purchase
 * cleanup missed them.
 */
public final class ShopDisplayCleanupSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(ShopDisplayItemComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UnstableRifts plugin = UnstableRifts.getInstance();
        if (plugin == null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        Game game = plugin.getGameManager().findGameForWorld(world);
        if (game == null) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        ShopDisplayItemComponent displayComponent =
                archetypeChunk.getComponent(index, ShopDisplayItemComponent.getComponentType());
        if (displayComponent == null || !plugin.getShopService().shouldRemoveDisplay(game, ref, displayComponent)) {
            return;
        }

        plugin.getShopService().untrackDisplayRef(ref);
        ItemPickupTracker.untrack(ref);
        commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
    }
}
