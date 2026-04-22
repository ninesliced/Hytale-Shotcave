package dev.ninesliced.unstablerifts.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.armor.ArmorAbilityBuffSystem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies armor ability damage modifiers before the engine subtracts health.
 * Guardian reduces incoming damage, while Warden grants full immunity and
 * reflects the hit back to the attacker.
 */
public final class ArmorAbilityDamageSystem extends DamageEventSystem {

    private static final MetaKey<Boolean> WARDEN_REFLECTION_DAMAGE =
            Damage.META_REGISTRY.registerMetaObject(data -> Boolean.FALSE);

    private static final Query<EntityStore> QUERY = Query.and(Player.getComponentType());

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        if (damage.isCancelled()) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (!ref.isValid()) return;

        float wardenReflect = ArmorAbilityBuffSystem.getWardenReflection(ref);
        if (wardenReflect > 0.0f) {
            boolean reflectedDamage = damage.getMetaStore().getMetaObject(WARDEN_REFLECTION_DAMAGE);
            if (!reflectedDamage && damage.getSource() instanceof Damage.EntitySource entitySource) {
                Ref<EntityStore> attackerRef = entitySource.getRef();
                if (attackerRef.isValid() && !attackerRef.equals(ref) && damage.getAmount() > 0.0f) {
                    Damage reflected = new Damage(
                            new Damage.EntitySource(ref),
                            damage.getDamageCauseIndex(),
                            damage.getAmount() * wardenReflect);
                    reflected.putMetaObject(WARDEN_REFLECTION_DAMAGE, Boolean.TRUE);
                    DamageSystems.executeDamage(attackerRef, commandBuffer, reflected);
                }
            }

            damage.setCancelled(true);
            return;
        }

        float guardianMul = ArmorAbilityBuffSystem.getGuardianReduction(ref);
        if (guardianMul < 1.0f) {
            damage.setAmount(damage.getAmount() * guardianMul);
        }
    }
}