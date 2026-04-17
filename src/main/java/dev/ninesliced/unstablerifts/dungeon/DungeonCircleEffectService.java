package dev.ninesliced.unstablerifts.dungeon;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Applies / removes the ground-circle visibility effects for players and enemies inside dungeons.
 */
public final class DungeonCircleEffectService {

    private static final float CIRCLE_OVERLAP_THRESHOLD_SECONDS = 0.5f;

    private static final Logger LOGGER = Logger.getLogger(DungeonCircleEffectService.class.getName());

    private static final String PLAYER_EFFECT_ID = "Dungeon_Player_Circle";
    private static final String PLAYER_EFFECT_ALT_ID = "Dungeon_Player_Circle_Alt";
    private static final String ENEMY_EFFECT_ID = "Dungeon_Enemy_Circle";
    private static final String ENEMY_EFFECT_ALT_ID = "Dungeon_Enemy_Circle_Alt";

    private DungeonCircleEffectService() {
    }

    public static void applyPlayerCircle(@Nonnull Ref<EntityStore> playerRef,
                                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        applyCircle(playerRef, commandBuffer, PLAYER_EFFECT_ID, PLAYER_EFFECT_ALT_ID);
    }

    public static void removePlayerCircle(@Nonnull Ref<EntityStore> playerRef,
                                          @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        removeCircle(playerRef, commandBuffer, PLAYER_EFFECT_ID, PLAYER_EFFECT_ALT_ID);
    }

    public static void removeEnemyCircle(@Nonnull Ref<EntityStore> mobRef,
                                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        removeCircle(mobRef, commandBuffer, ENEMY_EFFECT_ID, ENEMY_EFFECT_ALT_ID);
    }

    public static void removeEnemyCircle(@Nonnull Ref<EntityStore> mobRef,
                                         @Nonnull Store<EntityStore> store) {
        removeCircle(mobRef, store, ENEMY_EFFECT_ID, ENEMY_EFFECT_ALT_ID);
    }

    public static void applyEnemyCircle(@Nonnull Ref<EntityStore> mobRef,
                                        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        applyCircle(mobRef, commandBuffer, ENEMY_EFFECT_ID, ENEMY_EFFECT_ALT_ID);
    }

    public static void applyEnemyCircle(@Nonnull Ref<EntityStore> mobRef,
                                        @Nonnull Store<EntityStore> store) {
        applyCircleEnsuringController(mobRef, store, ENEMY_EFFECT_ID, ENEMY_EFFECT_ALT_ID);
    }

    public static boolean hasEnemyCircle(EffectControllerComponent effectController) {
        return hasCircle(effectController, ENEMY_EFFECT_ID, ENEMY_EFFECT_ALT_ID);
    }

    public static boolean hasPlayerCircle(EffectControllerComponent effectController) {
        return hasCircle(effectController, PLAYER_EFFECT_ID, PLAYER_EFFECT_ALT_ID);
    }

    private static void applyCircle(@Nonnull Ref<EntityStore> ref,
                                    @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                    @Nonnull String primaryEffectId,
                                    @Nonnull String secondaryEffectId) {
        EffectControllerComponent effectController = commandBuffer.getComponent(
                ref, EffectControllerComponent.getComponentType());

        String effectId = chooseEffectVariant(effectController, primaryEffectId, secondaryEffectId);
        if (effectId == null) {
            return;
        }

        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
        if (entityEffect == null) {
            LOGGER.warning("Entity effect not found: " + effectId);
            return;
        }

        if (effectController != null) {
            effectController.addEffect(ref, entityEffect, commandBuffer);
            return;
        }

        // Most dungeon mobs do not have an effect controller until one is created for them.
        // Queue a store-side fallback so the component can be added immediately after this tick.
        String variantToApply = effectId;
        commandBuffer.run(store -> applySpecificCircleEnsuringController(ref, store, variantToApply));
    }

    private static void applySpecificCircleEnsuringController(@Nonnull Ref<EntityStore> ref,
                                                              @Nonnull Store<EntityStore> store,
                                                              @Nonnull String effectId) {
        EffectControllerComponent effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effectController == null) {
            effectController = store.ensureAndGetComponent(ref, EffectControllerComponent.getComponentType());
        }

        if (effectController == null || getActiveEffect(effectController, effectId) != null) {
            return;
        }

        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
        if (entityEffect == null) {
            LOGGER.warning("Entity effect not found: " + effectId);
            return;
        }

        effectController.addEffect(ref, entityEffect, store);
    }

    private static void applyCircleEnsuringController(@Nonnull Ref<EntityStore> ref,
                                                      @Nonnull Store<EntityStore> store,
                                                      @Nonnull String primaryEffectId,
                                                      @Nonnull String secondaryEffectId) {
        EffectControllerComponent effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effectController == null) {
            effectController = store.ensureAndGetComponent(ref, EffectControllerComponent.getComponentType());
        }

        String effectId = chooseEffectVariant(effectController, primaryEffectId, secondaryEffectId);
        if (effectId == null) {
            return;
        }

        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
        if (entityEffect == null) {
            LOGGER.warning("Entity effect not found: " + effectId);
            return;
        }

        if (effectController != null) {
            effectController.addEffect(ref, entityEffect, store);
        }
    }

    private static String chooseEffectVariant(EffectControllerComponent effectController,
                                              @Nonnull String primaryEffectId,
                                              @Nonnull String secondaryEffectId) {
        if (effectController == null) {
            return primaryEffectId;
        }

        ActiveEntityEffect primaryEffect = getActiveEffect(effectController, primaryEffectId);
        ActiveEntityEffect secondaryEffect = getActiveEffect(effectController, secondaryEffectId);

        if (primaryEffect == null && secondaryEffect == null) {
            return primaryEffectId;
        }
        if (primaryEffect != null && secondaryEffect == null) {
            return primaryEffect.getRemainingDuration() <= CIRCLE_OVERLAP_THRESHOLD_SECONDS
                    ? secondaryEffectId
                    : null;
        }
        if (primaryEffect == null && secondaryEffect != null) {
            return secondaryEffect.getRemainingDuration() <= CIRCLE_OVERLAP_THRESHOLD_SECONDS
                    ? primaryEffectId
                    : null;
        }

        return null;
    }

    private static ActiveEntityEffect getActiveEffect(EffectControllerComponent effectController,
                                                      @Nonnull String effectId) {
        if (effectController == null) {
            return null;
        }

        int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
        if (effectIndex == Integer.MIN_VALUE) {
            return null;
        }

        return effectController.getActiveEffects().get(effectIndex);
    }

    private static boolean hasCircle(EffectControllerComponent effectController,
                                     @Nonnull String primaryEffectId,
                                     @Nonnull String secondaryEffectId) {
        if (effectController == null) {
            return false;
        }

        return getActiveEffect(effectController, primaryEffectId) != null
                || getActiveEffect(effectController, secondaryEffectId) != null;
    }

    private static void removeCircle(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull ComponentAccessor<EntityStore> componentAccessor,
                                     @Nonnull String primaryEffectId,
                                     @Nonnull String secondaryEffectId) {
        removeSingleCircle(ref, componentAccessor, primaryEffectId);
        removeSingleCircle(ref, componentAccessor, secondaryEffectId);
    }

    private static void removeSingleCircle(@Nonnull Ref<EntityStore> ref,
                                           @Nonnull ComponentAccessor<EntityStore> componentAccessor,
                                           @Nonnull String effectId) {
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
        if (effectIndex == Integer.MIN_VALUE) {
            return;
        }

        EffectControllerComponent effectController = componentAccessor.getComponent(
                ref, EffectControllerComponent.getComponentType());
        if (effectController != null && effectController.hasEffect(effectIndex)) {
            effectController.removeEffect(ref, effectIndex, RemovalBehavior.COMPLETE, componentAccessor);
        }
    }
}