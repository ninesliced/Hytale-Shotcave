package dev.ninesliced.unstablerifts.armor;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Logger;

/**
 * Activates and expires armor set ability buffs.
 * Each ability stores its state in the player's {@link ArmorChargeComponent}.
 * Damage, movement, and effect-duration bonuses are read from the charge
 * component by the relevant runtime systems.
 */
public final class ArmorAbilityBuffSystem {

    private static final Logger LOGGER = Logger.getLogger(ArmorAbilityBuffSystem.class.getName());
    private static final String EFFECT_ID = "Immune";
    private static final float CONTAGION_EFFECT_DURATION_BONUS_SECONDS = 2.0f;

    private ArmorAbilityBuffSystem() {
    }

    /**
     * Called when the player activates a fully charged 4/4 set ability.
     */
    public static void activateAbility(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull ComponentAccessor<EntityStore> accessor,
                                       @Nonnull ArmorSetAbility ability,
                                       @Nullable PlayerRef playerRef) {
        ArmorChargeComponent charge = accessor.getComponent(ref, ArmorChargeComponent.getComponentType());
        if (charge == null) return;

        charge.startBuff(ability);

        LOGGER.info("[UnstableRifts] Armor ability activated: " + ability.getDisplayName()
                + " (" + ability.getDurationSeconds() + "s)"
                + (playerRef != null ? " for " + playerRef.getUuid() : ""));

        if (playerRef != null) {
            Message msg = Message.raw(ability.getDisplayName() + " activated! (" + ability.getDurationSeconds() + "s)")
                    .color(ability.getColorHex()).bold(true);
            playerRef.sendMessage(msg);

            dev.ninesliced.unstablerifts.UnstableRifts plugin =
                    dev.ninesliced.unstablerifts.UnstableRifts.getInstance();
            if (plugin != null) {
                plugin.getMissionService().addProgress(
                        playerRef,
                        dev.ninesliced.unstablerifts.mission.MissionType.ACTIVATE_ARMOR_ABILITIES,
                        1);
            }
        }

        applyBuffEffect(ref, accessor, ability.getDurationSeconds());

        switch (ability) {
            case BERSERKER -> {
            } // +100% damage read by UnstableRiftsDamageInteraction/MeleeDamageEffectSystem
            case REGENERATION -> {
            } // HP regen ticked in ArmorChargeSystem
            case GUARDIAN -> {
            } // 80% damage reduction read by DungeonLethalDamageSystem
            case CONTAGION -> {
            } // extends weapon DoT durations when the held weapon has an effect
            case SWIFTNESS -> applySpeedBuff(ref, accessor, 1.5f);
            case WARDEN -> {
            } // full damage immunity + 100% reflection read by DungeonLethalDamageSystem
            default -> {
            }
        }
    }

    /**
     * Called when a buff expires after its duration.
     */
    public static void expireBuff(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull ComponentAccessor<EntityStore> accessor,
                                  @Nonnull ArmorSetAbility ability) {
        LOGGER.info("[UnstableRifts] Armor ability expired: " + ability.getDisplayName());

        switch (ability) {
            case SWIFTNESS -> applySpeedBuff(ref, accessor, 1.0f);
            default -> {
            }
        }
    }

    private static void applyBuffEffect(@Nonnull Ref<EntityStore> ref,
                                        @Nonnull ComponentAccessor<EntityStore> accessor,
                                        float durationSeconds) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        if (effect == null) return;

        EffectControllerComponent effectController = accessor.ensureAndGetComponent(
                ref, EffectControllerComponent.getComponentType());
        if (effectController != null) {
            effectController.addEffect(ref, effect, durationSeconds, OverlapBehavior.OVERWRITE, accessor);
        }
    }

    /**
     * Checks if the player currently has the given buff active.
     */
    public static boolean isBuffActive(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull ArmorSetAbility ability) {
        if (!ref.isValid()) return false;
        ArmorChargeComponent charge = ref.getStore().getComponent(ref, ArmorChargeComponent.getComponentType());
        if (charge == null) return false;
        return charge.hasActiveBuff() && charge.getActiveAbility() == ability;
    }

    /**
     * Returns the damage multiplier from active berserker buff (1.0 if not active).
     */
    public static float getDamageMultiplier(@Nonnull Ref<EntityStore> ref) {
        if (!ref.isValid()) return 1.0f;
        ArmorChargeComponent charge = ref.getStore().getComponent(ref, ArmorChargeComponent.getComponentType());
        if (charge == null || !charge.hasActiveBuff()) return 1.0f;
        return charge.getActiveAbility() == ArmorSetAbility.BERSERKER ? 2.0f : 1.0f;
    }

    /**
     * Returns the damage reduction multiplier from guardian buff (1.0 if not active).
     */
    public static float getGuardianReduction(@Nonnull Ref<EntityStore> ref) {
        if (!ref.isValid()) return 1.0f;
        ArmorChargeComponent charge = ref.getStore().getComponent(ref, ArmorChargeComponent.getComponentType());
        if (charge == null || !charge.hasActiveBuff()) return 1.0f;
        return charge.getActiveAbility() == ArmorSetAbility.GUARDIAN ? 0.1f : 1.0f;
    }

    /**
     * Returns the bonus effect duration applied by the Contagion armor buff.
     */
    public static float getWeaponEffectDurationBonus(@Nonnull Ref<EntityStore> ref) {
        return isBuffActive(ref, ArmorSetAbility.CONTAGION)
                ? CONTAGION_EFFECT_DURATION_BONUS_SECONDS
                : 0.0f;
    }

    /**
     * Returns the spike damage reflection multiplier from warden buff (0.0 if not active).
     */
    public static float getWardenReflection(@Nonnull Ref<EntityStore> ref) {
        if (!ref.isValid()) return 0.0f;
        ArmorChargeComponent charge = ref.getStore().getComponent(ref, ArmorChargeComponent.getComponentType());
        if (charge == null || !charge.hasActiveBuff()) return 0.0f;
        return charge.getActiveAbility() == ArmorSetAbility.WARDEN ? 1.0f : 0.0f;
    }

    private static void applySpeedBuff(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull ComponentAccessor<EntityStore> accessor,
                                       float multiplier) {
        MovementManager movementManager = accessor.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;
        PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        // Dungeon base speed is 10.0f (set by PlayerStateService.applyDungeonMovementSettings)
        movementManager.getSettings().baseSpeed = 10.0f * multiplier;
        movementManager.update(playerRef.getPacketHandler());
    }
}
