package dev.ninesliced.shotcave.pickup;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ninesliced.shotcave.Shotcave;
import dev.ninesliced.shotcave.dungeon.Game;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Tick system that auto-collects nearby key items for players.
 * Keys bypass inventory and increment the team key counter on {@link Game}.
 */
public final class KeyItemCollectionSystem extends EntityTickingSystem<EntityStore> {

    private static final String KEY_ITEM_ID = "Shotcave_Key_Item";

    public KeyItemCollectionSystem() {
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }

    @Override
    public void tick(float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        if (ItemPickupTracker.size() == 0) {
            return;
        }

        Ref<EntityStore> playerEntityRef = archetypeChunk.getReferenceTo(index);
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null || player.wasRemoved()) {
            return;
        }

        TransformComponent playerTransform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        Vector3d playerPos = playerTransform.getPosition();

        double collectRadius = ItemPickupConfig.getCoinCollectRadius(playerRef.getUuid());
        double collectRadiusSq = collectRadius * collectRadius;

        List<ItemPickupTracker.TrackedItem> toCollect = null;

        for (ItemPickupTracker.TrackedItem tracked : ItemPickupTracker.getAll()) {
            if (!tracked.isScoreCollect()) {
                continue;
            }
            if (!KEY_ITEM_ID.equals(tracked.getItemId())) {
                continue;
            }
            if (!tracked.getRef().isValid()) {
                continue;
            }

            Vector3d itemPos = tracked.getPosition(store);
            if (itemPos == null) {
                continue;
            }

            double dx = playerPos.x - itemPos.x;
            double dy = playerPos.y - itemPos.y;
            double dz = playerPos.z - itemPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= collectRadiusSq) {
                if (toCollect == null) {
                    toCollect = new ArrayList<>(4);
                }
                toCollect.add(tracked);
            }
        }

        if (toCollect == null) {
            return;
        }

        for (ItemPickupTracker.TrackedItem tracked : toCollect) {
            collectKey(tracked, playerRef, store, commandBuffer);
        }
    }

    private static void collectKey(@Nonnull ItemPickupTracker.TrackedItem tracked,
            @Nonnull PlayerRef playerRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> keyRef = tracked.getRef();

        // Atomic untrack to prevent double-collection.
        ItemPickupTracker.TrackedItem removed = ItemPickupTracker.entries().remove(keyRef);
        if (removed == null) {
            return;
        }

        if (!keyRef.isValid()) {
            return;
        }

        ItemComponent itemComponent = store.getComponent(keyRef, ItemComponent.getComponentType());
        if (itemComponent != null) {
            itemComponent.setRemovedByPlayerPickup(true);
        }

        commandBuffer.removeEntity(keyRef, RemoveReason.REMOVE);

        // Increment team key counter.
        Shotcave shotcave = Shotcave.getInstance();
        int totalKeys = 0;
        if (shotcave != null) {
            Game game = shotcave.getGameManager().findGameForPlayer(playerRef.getUuid());
            if (game != null) {
                game.addKey();
                totalKeys = game.getTeamKeys();
            }
        }

        try {
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw("Key collected! (" + totalKeys + " key" + (totalKeys != 1 ? "s" : "") + " total)"),
                    null,
                    "key_collected");
        } catch (Exception e) {
            // Best-effort notification
        }
    }
}
