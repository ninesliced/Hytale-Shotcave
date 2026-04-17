package dev.ninesliced.unstablerifts.mission;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * NPC action that opens the Rift Merchant page (missions + shop button) for the interacting player.
 */
public final class ActionOpenRiftMerchant extends ActionBase {

    @Nonnull
    private final String shopId;

    public ActionOpenRiftMerchant(@Nonnull BuilderActionOpenRiftMerchant builder,
                                  @Nonnull BuilderSupport support) {
        super(builder);
        this.shopId = builder.getShopId(support);
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref,
                              @Nonnull Role role,
                              @Nullable InfoProvider sensorInfo,
                              double dt,
                              @Nonnull Store<EntityStore> store) {
        return super.canExecute(ref, role, sensorInfo, dt, store)
                && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref,
                           @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo,
                           double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        Ref<EntityStore> playerReference = role.getStateSupport().getInteractionIterationTarget();
        if (playerReference == null) return false;

        PlayerRef playerRefComponent = store.getComponent(playerReference, PlayerRef.getComponentType());
        if (playerRefComponent == null) return false;

        Player playerComponent = store.getComponent(playerReference, Player.getComponentType());
        if (playerComponent == null) return false;

        playerComponent.getPageManager().openCustomPage(
                ref, store,
                new RiftMerchantPage(playerRefComponent, store, playerReference, shopId));
        return true;
    }
}
