package dev.ninesliced.unstablerifts.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Detects when a party portal block is broken and removes the associated
 * Rift Merchant NPC.
 */
public final class PortalDestroySystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final String PARTY_PORTAL_BLOCK = "UnstableRifts_Ancient_Party_Portal";

    public PortalDestroySystem() {
        super(BreakBlockEvent.class);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {

        if (event.getBlockType() == null) return;

        String blockId = event.getBlockType().getId();
        if (blockId == null || !blockId.endsWith(PARTY_PORTAL_BLOCK)) return;

        Vector3i target = event.getTargetBlock();
        if (target == null) return;

        UnstableRifts plugin = UnstableRifts.getInstance();
        if (plugin == null) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        plugin.getRiftMerchantService().onPortalDestroyed(world, target.x, target.y, target.z);
    }
}
