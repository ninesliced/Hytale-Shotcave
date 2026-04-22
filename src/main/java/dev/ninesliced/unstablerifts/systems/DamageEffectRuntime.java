package dev.ninesliced.unstablerifts.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.armor.ArmorAbilityBuffSystem;
import dev.ninesliced.unstablerifts.guns.DamageEffect;
import dev.ninesliced.unstablerifts.guns.GunItemMetadata;
import dev.ninesliced.unstablerifts.guns.WeaponRarity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageEffectRuntime {
    private static final float DEFAULT_DAMAGE_PER_TICK = 1.0f;

    private DamageEffectRuntime() {
    }

    public static void apply(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                             @Nonnull Ref<EntityStore> target,
                             @Nonnull DamageEffect effect) {
        if (effect == DamageEffect.NONE) {
            return;
        }

        apply(commandBuffer, target, effect, rollDuration(effect), DEFAULT_DAMAGE_PER_TICK);
    }

    /**
     * Applies a damage effect with rarity-scaled duration bonus.
     */
    public static void apply(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                             @Nonnull Ref<EntityStore> target,
                             @Nonnull DamageEffect effect,
                             @Nonnull WeaponRarity rarity) {
        apply(commandBuffer, target, effect, rarity, null);
    }

    /**
     * Applies a damage effect with rarity-scaled duration bonus and optional
     * armor set bonus from the attacking player.
     */
    public static void apply(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                             @Nonnull Ref<EntityStore> target,
                             @Nonnull DamageEffect effect,
                             @Nonnull WeaponRarity rarity,
                             @Nullable Ref<EntityStore> sourceRef) {
        if (effect == DamageEffect.NONE) {
            return;
        }

        float duration = rollDuration(effect)
                + rarity.getEffectDurationBonus()
                + getSourceEffectDurationBonus(sourceRef);
        apply(commandBuffer, target, effect, duration, DEFAULT_DAMAGE_PER_TICK);
    }

    private static float getSourceEffectDurationBonus(@Nullable Ref<EntityStore> sourceRef) {
        if (sourceRef == null || !sourceRef.isValid()) {
            return 0.0f;
        }

        Player player = sourceRef.getStore().getComponent(sourceRef, Player.getComponentType());
        if (player == null || player.getInventory() == null) {
            return 0.0f;
        }

        ItemStack heldItem = player.getInventory().getItemInHand();
        if (heldItem == null || ItemStack.isEmpty(heldItem)) {
            return 0.0f;
        }

        if (GunItemMetadata.getEffect(heldItem) == DamageEffect.NONE) {
            return 0.0f;
        }

        return ArmorAbilityBuffSystem.getWeaponEffectDurationBonus(sourceRef);
    }

    public static void apply(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                             @Nonnull Ref<EntityStore> target,
                             @Nonnull DamageEffect effect,
                             float durationSeconds,
                             float damagePerTick) {
        if (effect == DamageEffect.NONE) {
            return;
        }

        if (commandBuffer.getComponent(target, Player.getComponentType()) != null) {
            return;
        }

        DamageEffectComponent activeEffect = commandBuffer.getComponent(target, DamageEffectComponent.getComponentType());
        if (activeEffect != null) {
            DamageEffect previousEffect = DamageEffect.fromOrdinal(activeEffect.getEffectOrdinal());
            boolean sameEffect = previousEffect == effect;
            if (!sameEffect && previousEffect != DamageEffect.NONE) {
                clearVisual(commandBuffer, target, previousEffect);
            }
            activeEffect.apply(effect.ordinal(), Math.max(1, Math.round(durationSeconds * 1000.0f)), damagePerTick,
                    effect == DamageEffect.ICE);
            applyVisual(commandBuffer, target, effect, durationSeconds);
        } else {
            DamageEffectComponent newEffect = new DamageEffectComponent();
            newEffect.apply(effect.ordinal(), Math.max(1, Math.round(durationSeconds * 1000.0f)), damagePerTick,
                    effect == DamageEffect.ICE);
            commandBuffer.putComponent(target, DamageEffectComponent.getComponentType(), newEffect);
            applyVisual(commandBuffer, target, effect, durationSeconds);
        }
    }

    public static void clearVisual(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                   @Nonnull Ref<EntityStore> target,
                                   @Nonnull DamageEffect effect) {
        String entityEffectId = effect.getEntityEffectId();
        if (entityEffectId == null) {
            return;
        }

        int effectIndex = EntityEffect.getAssetMap().getIndex(entityEffectId);
        if (effectIndex == Integer.MIN_VALUE) {
            return;
        }

        EffectControllerComponent effectController = commandBuffer.getComponent(target, EffectControllerComponent.getComponentType());
        if (effectController != null) {
            effectController.removeEffect(target, effectIndex, RemovalBehavior.COMPLETE, commandBuffer);
        }
    }

    private static void applyVisual(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                    @Nonnull Ref<EntityStore> target,
                                    @Nonnull DamageEffect effect,
                                    float durationSeconds) {
        String entityEffectId = effect.getEntityEffectId();
        if (entityEffectId == null) {
            return;
        }

        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(entityEffectId);
        if (entityEffect == null) {
            return;
        }

        EffectControllerComponent effectController = commandBuffer.ensureAndGetComponent(
                target, EffectControllerComponent.getComponentType());
        if (effectController != null) {
            effectController.addEffect(target, entityEffect, durationSeconds, OverlapBehavior.OVERWRITE, commandBuffer);
        }
    }

    private static float rollDuration(@Nonnull DamageEffect effect) {
        float minDuration = effect.getDotDurationMin();
        float maxDuration = effect.getDotDurationMax();
        if (maxDuration <= minDuration) {
            return minDuration;
        }

        return minDuration + (float) (ThreadLocalRandom.current().nextDouble() * (maxDuration - minDuration));
    }
}