package dev.ninesliced.unstablerifts.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.collision.WorldUtil;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;
import dev.ninesliced.unstablerifts.dungeon.Game;
import dev.ninesliced.unstablerifts.dungeon.GameManager;
import dev.ninesliced.unstablerifts.dungeon.GameState;
import dev.ninesliced.unstablerifts.util.VectorConversions;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies poison damage to dungeon players standing inside Fluid_Poison blocks
 * or Poison/Poison_Source fluids.
 */
public final class PoisonFluidDamageSystem extends EntityTickingSystem<EntityStore> {

    private static final float DAMAGE_AMOUNT = 2.0f;
    private static final long DAMAGE_INTERVAL_MS = 500L;

    private static final Query<EntityStore> QUERY = Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            TransformComponent.getComponentType(),
            EntityStatMap.getComponentType()
    );

    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();

    private int poisonBlockTypeIndex = -1;
    private int poisonFluidIndex = -1;
    private int poisonSourceFluidIndex = -1;
    private boolean resolved;

    private void resolveIndices() {
        if (this.resolved) {
            return;
        }
        this.resolved = true;
        this.poisonBlockTypeIndex = BlockType.getAssetMap().getIndexOrDefault("Fluid_Poison", -1);
        this.poisonFluidIndex = Fluid.getAssetMap().getIndexOrDefault("Poison", -1);
        this.poisonSourceFluidIndex = Fluid.getAssetMap().getIndexOrDefault("Poison_Source", -1);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        resolveIndices();

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (!ref.isValid()) {
            return;
        }

        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        // Only apply in active dungeon games
        UnstableRifts plugin = UnstableRifts.getInstance();
        if (plugin == null) {
            return;
        }
        GameManager gameManager = plugin.getGameManager();
        Game game = gameManager.findGameForPlayer(playerRef.getUuid());
        if (game == null) {
            lastDamageTime.remove(playerRef.getUuid());
            return;
        }
        if (game.getState() != GameState.ACTIVE && game.getState() != GameState.BOSS) {
            return;
        }

        // Dead players don't take poison damage
        DeathComponent death = store.getComponent(ref, DeathComponent.getComponentType());
        if (death != null && death.isDead()) {
            return;
        }

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Vector3d pos = VectorConversions.toJoml(transform.getPosition());
        if (pos == null) {
            return;
        }
        if (!isInPoisonBlock(store, pos)) {
            return;
        }

        // Throttle damage to the configured interval
        long now = System.currentTimeMillis();
        UUID playerId = playerRef.getUuid();
        Long lastTime = lastDamageTime.get(playerId);
        if (lastTime != null && (now - lastTime) < DAMAGE_INTERVAL_MS) {
            return;
        }
        lastDamageTime.put(playerId, now);

        Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.ENVIRONMENT, DAMAGE_AMOUNT);
        DamageSystems.executeDamage(ref, commandBuffer, damage);
    }

    private boolean isInPoisonBlock(@Nonnull Store<EntityStore> store, @Nonnull Vector3d pos) {
        World world = store.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();

        int blockX = MathUtil.floor(pos.x);
        int blockZ = MathUtil.floor(pos.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(blockX, blockZ));
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        // Check at feet and at feet + 1 (player occupies two blocks vertically)
        int feetY = MathUtil.floor(pos.y);
        return isPositionPoisoned(chunkRef, chunkComponentStore, blockX, feetY, blockZ)
                || isPositionPoisoned(chunkRef, chunkComponentStore, blockX, feetY + 1, blockZ);
    }

    private boolean isPositionPoisoned(@Nonnull Ref<ChunkStore> chunkRef,
                                       @Nonnull Store<ChunkStore> chunkComponentStore,
                                       int x, int y, int z) {
        if (y < 0 || y >= 320) {
            return false;
        }

        // Check block type
        if (this.poisonBlockTypeIndex > 0) {
            BlockChunk blockChunk = chunkComponentStore.getComponent(chunkRef, BlockChunk.getComponentType());
            if (blockChunk != null) {
                BlockSection section = blockChunk.getSectionAtBlockY(y);
                if (section != null) {
                    int blockId = section.get(x, y, z);
                    if (blockId == this.poisonBlockTypeIndex) {
                        return true;
                    }
                }
            }
        }

        // Check fluid
        long packed = WorldUtil.getPackedMaterialAndFluidAtPosition(chunkRef, chunkComponentStore, x, y, z);
        int fluidId = MathUtil.unpackRight(packed);
        return fluidId != 0
                && (fluidId == this.poisonFluidIndex || fluidId == this.poisonSourceFluidIndex);
    }
}
