package dev.ninesliced.unstablerifts.dungeon;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;
import dev.ninesliced.unstablerifts.guns.GunItemMetadata;
import dev.ninesliced.unstablerifts.guns.WeaponLootRoller;
import dev.ninesliced.unstablerifts.guns.WeaponRarity;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * Handles altar markers: spawns Unique-rarity weapon drops at altar positions
 * on room entry, and processes first-pickup to despawn remaining weapons and
 * notify the party.
 */
public final class AltarService {

    private final UnstableRifts plugin;

    public AltarService(@Nonnull UnstableRifts plugin) {
        this.plugin = plugin;
    }

    /**
     * Spawns one Unique-rarity weapon ground item per altar position in the room.
     * Idempotent — subsequent calls for the same room are no-ops.
     */
    public void spawnAltarWeapons(@Nonnull RoomData room, @Nonnull World world) {
        if (room.isAltarWeaponsSpawned() || room.getAltarPositions().isEmpty()) {
            return;
        }
        room.setAltarWeaponsSpawned(true);

        world.execute(() -> {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            for (Vector3i pos : room.getAltarPositions()) {
                ItemStack weapon = WeaponLootRoller.rollRandom(WeaponRarity.UNIQUE);
                weapon = GunItemMetadata.setFromAltar(weapon, true);

                Vector3d dropPosition = new Vector3d(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5);
                Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                        entityStore, weapon, dropPosition, Rotation3f.ZERO, 0.0f, 0.0f, 0.0f);
                if (holder == null) {
                    continue;
                }
                Ref<EntityStore> ref = entityStore.addEntity(holder, AddReason.SPAWN);
                if (ref != null) {
                    room.addSpawnedAltarItem(ref);
                }
            }
        });
    }

    /**
     * Call when an altar-tagged weapon is picked up. Despawns the remaining altar
     * weapons in the room, broadcasts a title to the party, and flips the pickup
     * flag so deferred room locking can proceed on the next tick.
     */
    public void handleAltarPickup(@Nonnull RoomData room,
                                  @Nonnull Game game,
                                  @Nonnull String pickerName) {
        if (room.isAltarPickedUp()) {
            return;
        }
        room.setAltarPickedUp(true);

        World world = game.getInstanceWorld();
        if (world != null) {
            world.execute(() -> {
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                for (Ref<EntityStore> ref : room.getSpawnedAltarItems()) {
                    if (ref != null && ref.isValid()) {
                        entityStore.removeEntity(ref, RemoveReason.REMOVE);
                    }
                }
                room.clearSpawnedAltarItems();
            });
        }

        plugin.getGameManager().broadcastToParty(
                game.getPartyId(),
                "Altar Weapon Claimed",
                pickerName + " took the altar weapon");
    }
}
