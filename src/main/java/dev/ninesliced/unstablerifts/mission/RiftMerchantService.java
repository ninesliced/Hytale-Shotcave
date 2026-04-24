package dev.ninesliced.unstablerifts.mission;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import dev.ninesliced.unstablerifts.util.VectorConversions;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages spawning and tracking of Rift Merchant NPCs near party portals.
 * Persists known portal positions so merchants can be re-spawned on server restart.
 */
public final class RiftMerchantService {

    private static final String MERCHANT_ROLE = "UnstableRifts_Rift_Merchant";
    private static final double SEARCH_RADIUS = 5.0;
    private static final Gson GSON = new Gson();
    private static final Type POSITIONS_TYPE = new TypeToken<List<int[]>>() {}.getType();

    /** Packed portal position → spawned merchant entity ref. */
    private final Map<Long, Ref<EntityStore>> spawnedMerchants = new ConcurrentHashMap<>();

    /** All known portal block positions (persisted). */
    private final Set<Long> knownPortals = ConcurrentHashMap.newKeySet();

    /** Worlds we have already initialized merchants in this session. */
    private final Set<World> initializedWorlds = ConcurrentHashMap.newKeySet();

    /** Positions currently being spawned (prevents duplicate async spawns). */
    private final Set<Long> pendingSpawns = ConcurrentHashMap.newKeySet();

    private Path dataFile;

    public void init(@Nonnull Path dataDir) {
        this.dataFile = dataDir.resolve("rift_merchant_portals.json");
        load();
    }

    /**
     * Called when a player uses a party portal block.
     * Records the position (persists) and ensures a merchant is spawned nearby.
     */
    public void onPortalUsed(@Nonnull World world, int bx, int by, int bz) {
        long key = packPos(bx, by, bz);
        if (knownPortals.add(key)) {
            save();
        }
        ensureMerchant(world, key, bx, by, bz);
    }

    /**
     * Spawns merchants at all known portal positions in the given world.
     * Only runs once per world per server session — NPCs persist in the world save.
     */
    public void spawnAllKnown(@Nonnull World world) {
        if (!initializedWorlds.add(world)) {
            return; // already initialized this world
        }
        for (long key : knownPortals) {
            int[] pos = unpackPos(key);
            ensureMerchant(world, key, pos[0], pos[1], pos[2]);
        }
    }

    public void onWorldRemoved(@Nonnull World world) {
        initializedWorlds.remove(world);
        spawnedMerchants.entrySet().removeIf(e -> !e.getValue().isValid());
    }

    /**
     * Called when a party portal block is destroyed.
     * Removes the associated merchant NPC and the persisted position.
     */
    public void onPortalDestroyed(@Nonnull World world, int bx, int by, int bz) {
        long key = packPos(bx, by, bz);
        if (!knownPortals.remove(key)) {
            return;
        }
        save();

        Ref<EntityStore> merchantRef = spawnedMerchants.remove(key);
        if (merchantRef != null && merchantRef.isValid()) {
            world.execute(() -> {
                if (merchantRef.isValid()) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    store.removeEntity(merchantRef, RemoveReason.REMOVE);
                }
            });
        } else {
            // No tracked ref — scan for any nearby merchant and remove it
            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();
                NPCPlugin npcPlugin = NPCPlugin.get();
                int roleIndex = npcPlugin.getIndex(MERCHANT_ROLE);
                Vector3d center = new Vector3d(bx + 2.5, by + 1.0, bz + 0.5);
                Ref<EntityStore> found = findNearbyMerchant(store, npcPlugin, center, roleIndex);
                if (found != null && found.isValid()) {
                    store.removeEntity(found, RemoveReason.REMOVE);
                }
            });
        }
    }

    public void shutdown() {
        spawnedMerchants.clear();
        initializedWorlds.clear();
        pendingSpawns.clear();
    }

    // ── Internal ──────────────────────────────────────────────────

    private void ensureMerchant(@Nonnull World world, long key, int bx, int by, int bz) {
        Ref<EntityStore> existing = spawnedMerchants.get(key);
        if (existing != null && existing.isValid()) {
            return;
        }
        // Prevent duplicate async spawns for the same position
        if (!pendingSpawns.add(key)) {
            return;
        }
        world.execute(() -> {
            try {
                // Double-check after reaching the world thread
                Ref<EntityStore> doubleCheck = spawnedMerchants.get(key);
                if (doubleCheck != null && doubleCheck.isValid()) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                Vector3d spawnPos = new Vector3d(bx + 2.5, by + 1.0, bz + 0.5);
                NPCPlugin npcPlugin = NPCPlugin.get();
                int roleIndex = npcPlugin.getIndex(MERCHANT_ROLE);

                // Check if a merchant NPC already exists nearby (e.g. persisted from last session)
                Ref<EntityStore> existingNearby = findNearbyMerchant(store, npcPlugin, spawnPos, roleIndex);
                if (existingNearby != null) {
                    spawnedMerchants.put(key, existingNearby);
                    return;
                }

                npcPlugin.validateSpawnableRole(MERCHANT_ROLE);
                npcPlugin.prepareRoleBuilderInfo(roleIndex);
                var result = npcPlugin.spawnNPC(store, MERCHANT_ROLE, null, VectorConversions.toHytale(spawnPos), Rotation3f.ZERO);
                if (result != null) {
                    spawnedMerchants.put(key, result.first());
                }
            } finally {
                pendingSpawns.remove(key);
            }
        });
    }

    @javax.annotation.Nullable
    private Ref<EntityStore> findNearbyMerchant(@Nonnull Store<EntityStore> store,
                                                @Nonnull NPCPlugin npcPlugin,
                                                @Nonnull Vector3d center,
                                                int targetRoleIndex) {
        double radiusSq = SEARCH_RADIUS * SEARCH_RADIUS;
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();
        Ref<EntityStore>[] found = new Ref[]{null};

        store.forEachChunk(npcType, (archetypeChunk, commandBuffer) -> {
            if (found[0] != null) return;
            for (int i = 0; i < archetypeChunk.size(); i++) {
                NPCEntity npc = archetypeChunk.getComponent(i, npcType);
                if (npc == null || npc.getRoleIndex() != targetRoleIndex) continue;
                TransformComponent transform = archetypeChunk.getComponent(i, transformType);
                if (transform == null) continue;
                Vector3d pos = VectorConversions.toJoml(transform.getPosition());
                if (pos == null) continue;
                if (pos.distanceSquared(center) <= radiusSq) {
                    found[0] = archetypeChunk.getReferenceTo(i);
                    return;
                }
            }
        });
        return found[0];
    }

    // ── Persistence ───────────────────────────────────────────────

    private void load() {
        if (dataFile == null || !Files.exists(dataFile)) return;
        try {
            String json = Files.readString(dataFile);
            List<int[]> positions = GSON.fromJson(json, POSITIONS_TYPE);
            if (positions != null) {
                for (int[] pos : positions) {
                    if (pos.length == 3) {
                        knownPortals.add(packPos(pos[0], pos[1], pos[2]));
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void save() {
        if (dataFile == null) return;
        try {
            Files.createDirectories(dataFile.getParent());
            List<int[]> positions = new ArrayList<>();
            for (long key : knownPortals) {
                positions.add(unpackPos(key));
            }
            Files.writeString(dataFile, GSON.toJson(positions));
        } catch (IOException ignored) {
        }
    }

    // ── Position packing ──────────────────────────────────────────

    private static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (long) (y & 0xFFF);
    }

    private static int[] unpackPos(long packed) {
        int y = (int) (packed & 0xFFF);
        int z = (int) ((packed >> 12) & 0x3FFFFFF);
        int x = (int) ((packed >> 38) & 0x3FFFFFF);
        if (x >= (1 << 25)) x -= (1 << 26);
        if (z >= (1 << 25)) z -= (1 << 26);
        return new int[]{x, y, z};
    }
}
