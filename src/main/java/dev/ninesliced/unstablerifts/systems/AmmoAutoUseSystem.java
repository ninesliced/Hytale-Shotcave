package dev.ninesliced.unstablerifts.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ninesliced.unstablerifts.armor.ArmorStatResolver;
import dev.ninesliced.unstablerifts.guns.GunItemMetadata;
import dev.ninesliced.unstablerifts.guns.WeaponDefinition;
import dev.ninesliced.unstablerifts.guns.WeaponDefinitions;
import dev.ninesliced.unstablerifts.hud.AmmoHudService;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Scans player inventories each tick for ammo items. If found, removes the
 * ammo from inventory and either recharges the held weapon or drops it on
 * the ground when no weapon is equipped.
 */
public final class AmmoAutoUseSystem extends EntityTickingSystem<EntityStore> {

    private static final String AMMO_ITEM_ID = "UnstableRifts_Ammo_Item";

    @Nonnull
    private final ComponentType<EntityStore, PlayerRef> playerRefType;
    @Nonnull
    private final ComponentType<EntityStore, Player> playerType;
    @Nonnull
    private final ComponentType<EntityStore, TransformComponent> transformType;
    @Nonnull
    private final Query<EntityStore> query;

    public AmmoAutoUseSystem() {
        this.playerRefType = PlayerRef.getComponentType();
        this.playerType = Player.getComponentType();
        this.transformType = TransformComponent.getComponentType();
        this.query = Query.and(this.playerRefType, this.playerType, this.transformType);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        PlayerRef playerRef = archetypeChunk.getComponent(index, this.playerRefType);
        if (playerRef == null || !playerRef.isValid()) return;

        Player player = archetypeChunk.getComponent(index, this.playerType);
        if (player == null) return;

        Ref<EntityStore> playerEntityRef = archetypeChunk.getReferenceTo(index);
        if (playerEntityRef == null || !playerEntityRef.isValid()) return;

        // Check hotbar + inventory for ammo items
        CombinedItemContainer combined = InventoryComponent.getCombined(store, playerEntityRef, InventoryComponent.HOTBAR_FIRST);
        if (combined == null) return;

        int ammoCount = combined.countItemStacks(stack -> AMMO_ITEM_ID.equals(stack.getItemId()));
        if (ammoCount <= 0) return;

        // Remove the ammo item(s) from inventory
        combined.removeItemStack(new ItemStack(AMMO_ITEM_ID, ammoCount));

        // Check if holding a weapon
        ItemStack heldItem = player.getInventory() != null ? player.getInventory().getItemInHand() : null;
        if (heldItem == null || ItemStack.isEmpty(heldItem)) {
            dropAmmo(archetypeChunk, index, commandBuffer, ammoCount);
            notify(playerRef, "Equip a weapon to use ammo!");
            return;
        }

        String weaponId = heldItem.getItemId();
        WeaponDefinition definition = weaponId != null ? WeaponDefinitions.getById(weaponId) : null;

        int baseMaxAmmo = definition != null && definition.baseMaxAmmo() > 0
                ? definition.baseMaxAmmo()
                : GunItemMetadata.getBaseMaxAmmo(heldItem, -1);

        if (baseMaxAmmo <= 0) {
            // Not a ranged weapon — drop the ammo
            dropAmmo(archetypeChunk, index, commandBuffer, ammoCount);
            notify(playerRef, "Equip a weapon to use ammo!");
            return;
        }

        // Compute effective max ammo with armor bonus
        double armorAmmoCapacityBonus = 0.0;
        InventoryComponent.Armor armorComp = store.getComponent(playerEntityRef, InventoryComponent.Armor.getComponentType());
        if (armorComp != null) {
            armorAmmoCapacityBonus = ArmorStatResolver.getTotalAmmoCapacityBonus(armorComp.getInventory());
        }
        int effectiveMaxAmmo = GunItemMetadata.getEffectiveMaxAmmo(heldItem, baseMaxAmmo, armorAmmoCapacityBonus);
        int currentAmmo = GunItemMetadata.getInt(heldItem, GunItemMetadata.AMMO_KEY, effectiveMaxAmmo);

        if (currentAmmo >= effectiveMaxAmmo) {
            // Already full — drop ammo back
            dropAmmo(archetypeChunk, index, commandBuffer, ammoCount);
            notify(playerRef, "Ammo already full!");
            return;
        }

        // Refill ammo to max
        ItemStack updated = GunItemMetadata.setInt(heldItem, GunItemMetadata.AMMO_KEY, effectiveMaxAmmo);
        updated = GunItemMetadata.ensureAmmo(updated, baseMaxAmmo, effectiveMaxAmmo);

        // Apply to held slot
        InventoryComponent.Hotbar hotbarComp = store.getComponent(playerEntityRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbarComp != null) {
            byte activeSlot = hotbarComp.getActiveSlot();
            ItemContainer hotbar = hotbarComp.getInventory();
            hotbar.replaceItemStackInSlot(activeSlot, heldItem, updated);
        }

        // Update ammo HUD
        AmmoHudService.clear(playerRef);
        AmmoHudService.updateForHeldItem(player, playerRef, updated, false, playerEntityRef);

        int refilled = effectiveMaxAmmo - currentAmmo;
        notify(playerRef, "Ammo refilled! (+" + refilled + ")");
    }

    private void dropAmmo(@Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                           int index,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           int quantity) {
        TransformComponent transform = archetypeChunk.getComponent(index, this.transformType);
        if (transform == null) return;
        Vector3d pos = transform.getPosition();
        Vector3d dropPos = new Vector3d(pos.x, pos.y + 0.5, pos.z);
        List<ItemStack> items = List.of(new ItemStack(AMMO_ITEM_ID, quantity));
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(commandBuffer, items, dropPos, Rotation3f.ZERO);
        commandBuffer.addEntities(holders, AddReason.SPAWN);
    }

    private static void notify(@Nonnull PlayerRef playerRef, @Nonnull String message) {
        try {
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw(message),
                    null,
                    "ammo_auto_use");
        } catch (Exception ignored) {
        }
    }
}
