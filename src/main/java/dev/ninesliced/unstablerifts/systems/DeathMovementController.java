package dev.ninesliced.unstablerifts.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;
import dev.ninesliced.unstablerifts.dungeon.Game;
import dev.ninesliced.unstablerifts.dungeon.GameState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Restores server-side movement settings after death-state overrides.
 */
public final class DeathMovementController {

    private DeathMovementController() {
    }

    public static void restore(@Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nullable PlayerRef playerRef) {
        if (!ref.isValid()) {
            return;
        }

        if (playerRef != null && playerRef.isValid()) {
            UnstableRifts unstablerifts = UnstableRifts.getInstance();
            if (unstablerifts != null) {
                unstablerifts.getCameraService().refreshMovementProfile(playerRef);
                if (shouldUseDungeonMovement(unstablerifts, store, playerRef)) {
                    unstablerifts.getGameManager().getPlayerStateService().applyDungeonMovementSettings(ref, store, playerRef);
                    store.getExternalData().getWorld().execute(() -> {
                        Ref<EntityStore> delayedRef = playerRef.getReference();
                        if (delayedRef != null && delayedRef.isValid()) {
                            Store<EntityStore> delayedStore = delayedRef.getStore();
                            if (shouldUseDungeonMovement(unstablerifts, delayedStore, playerRef)) {
                                unstablerifts.getGameManager().getPlayerStateService()
                                        .applyDungeonMovementSettings(delayedRef, delayedStore, playerRef);
                            }
                        }
                    });
                }
            } else {
                MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
                if (movementManager != null) {
                    movementManager.resetDefaultsAndUpdate(ref, store);
                }
            }
        }
    }

    private static boolean shouldUseDungeonMovement(@Nonnull UnstableRifts unstablerifts,
                                                    @Nonnull Store<EntityStore> store,
                                                    @Nonnull PlayerRef playerRef) {
        Game game = unstablerifts.getGameManager().findGameForPlayer(playerRef.getUuid());
        if (game == null) {
            return false;
        }
        if (game.getState() != GameState.ACTIVE
                && game.getState() != GameState.BOSS
                && game.getState() != GameState.TRANSITIONING) {
            return false;
        }
        return game.getInstanceWorld() == store.getExternalData().getWorld();
    }
}