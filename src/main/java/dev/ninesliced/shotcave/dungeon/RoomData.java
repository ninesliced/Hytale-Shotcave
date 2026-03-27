package dev.ninesliced.shotcave.dungeon;

import com.hypixel.hytale.component.Ref;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single room in the dungeon generation graph.
 * Tracks spatial placement, mob spawning, and parent–child relationships.
 */
public final class RoomData {

    private final RoomType type;
    private final Vector3i anchor;
    private final int rotation;
    private final List<Vector3i> spawnerPositions;
    private final List<Vector3d> prefabMobMarkerPositions;
    private final List<String> mobsToSpawn;
    private final List<Ref<EntityStore>> spawnedMobs = new ArrayList<>();
    private final List<RoomData> children = new ArrayList<>();

    private int branchDepth;
    private String branchId;

    private final List<Vector3i> mobSpawnPoints = new ArrayList<>();
    private final List<PinnedMobSpawn> pinnedMobSpawns = new ArrayList<>();
    private final List<Vector3i> keySpawnerPositions = new ArrayList<>();
    private final List<Vector3i> portalPositions = new ArrayList<>();
    private final List<Vector3i> portalExitPositions = new ArrayList<>();

    // ── Door & lock system ──
    private final List<Vector3i> lockDoorBlockPositions = new ArrayList<>();
    private final List<Vector3i> doorPositions = new ArrayList<>();
    private final List<Vector3i> activationZonePositions = new ArrayList<>();
    private final List<Vector3i> mobActivatorPositions = new ArrayList<>();
    private boolean hasMobClearActivator = false;
    private boolean locked = false;
    private boolean doorsSealed = false;
    private DoorMode doorMode = DoorMode.NONE;

    // ── Portal ──
    private boolean portalSpawned = false;

    // ── Challenge system ──
    private boolean challengeActive = false;
    private final List<ChallengeObjective> challenges = new ArrayList<>();

    // ── Lock door prefab blocks (pasted at runtime, removed on clear) ──

    @Nonnull
    public List<Vector3i> getLockDoorBlockPositions() {
        return lockDoorBlockPositions;
    }

    public void addLockDoorBlockPosition(@Nonnull Vector3i pos) {
        lockDoorBlockPositions.add(pos);
    }

    // ── Boss walk-in tracking ──
    private boolean playerEnteredRoom = false;
    private double distanceWalkedInRoom = 0.0;
    @Nullable
    private Vector3d lastTrackedPosition = null;

    // Bounding box (world coordinates)
    private int boundsMinX, boundsMinZ, boundsMaxX, boundsMaxZ;
    private int boundsMinY, boundsMaxY;
    private boolean hasBounds = false;

    /** How many mobs were assigned to this room (set once during distribution). */
    private int expectedMobCount = 0;
    /** Mobs confirmed killed: ref was observed valid then became invalid. */
    private int confirmedKills = 0;
    /** Refs we have seen as valid at least once (so we can detect valid→invalid transitions). */
    private final Set<Ref<EntityStore>> seenAlive = new HashSet<>();

    @Nullable
    private RoomData parent;
    private boolean cleared = false;

    public RoomData(@Nonnull RoomType type,
                    @Nonnull Vector3i anchor,
                    int rotation,
                    @Nonnull List<Vector3i> spawnerPositions,
                    @Nonnull List<Vector3d> prefabMobMarkerPositions,
                    @Nonnull List<String> mobsToSpawn) {
        this(type, anchor, rotation, spawnerPositions, prefabMobMarkerPositions, mobsToSpawn, 0, "main");
    }

    public RoomData(@Nonnull RoomType type,
                    @Nonnull Vector3i anchor,
                    int rotation,
                    @Nonnull List<Vector3i> spawnerPositions,
                    @Nonnull List<Vector3d> prefabMobMarkerPositions,
                    @Nonnull List<String> mobsToSpawn,
                    int branchDepth,
                    @Nonnull String branchId) {
        this.type = type;
        this.anchor = anchor;
        this.rotation = rotation;
        this.spawnerPositions = new ArrayList<>(spawnerPositions);
        this.prefabMobMarkerPositions = new ArrayList<>(prefabMobMarkerPositions);
        this.mobsToSpawn = new ArrayList<>(mobsToSpawn);
        this.branchDepth = branchDepth;
        this.branchId = branchId;
    }

    @Nonnull
    public RoomType getType() {
        return type;
    }

    @Nonnull
    public Vector3i getAnchor() {
        return anchor;
    }

    public int getRotation() {
        return rotation;
    }

    @Nonnull
    public List<Vector3i> getSpawnerPositions() {
        return Collections.unmodifiableList(spawnerPositions);
    }

    @Nonnull
    public List<Vector3d> getPrefabMobMarkerPositions() {
        return Collections.unmodifiableList(prefabMobMarkerPositions);
    }

    @Nonnull
    public List<String> getMobsToSpawn() {
        return Collections.unmodifiableList(mobsToSpawn);
    }

    public void addMobToSpawn(@Nonnull String mobId) {
        mobsToSpawn.add(mobId);
    }

    @Nonnull
    public List<Ref<EntityStore>> getSpawnedMobs() {
        return spawnedMobs;
    }

    public void addSpawnedMob(@Nonnull Ref<EntityStore> ref) {
        UUID refUuid = null;
        if (ref.isValid()) {
            try {
                UUIDComponent uuidComponent = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
                if (uuidComponent != null) {
                    refUuid = uuidComponent.getUuid();
                }
            } catch (Exception e) {
                // Ref may have become invalid between check and access
            }
        }

        for (Ref<EntityStore> existing : spawnedMobs) {
            if (existing == ref) {
                return;
            }
            if (refUuid != null && existing.isValid()) {
                try {
                    UUIDComponent existingUuidComponent = existing.getStore().getComponent(existing, UUIDComponent.getComponentType());
                    if (existingUuidComponent != null && refUuid.equals(existingUuidComponent.getUuid())) {
                        return;
                    }
                } catch (Exception e) {
                    // Existing ref may have become invalid during iteration
                }
            }
        }
        spawnedMobs.add(ref);
    }

    public boolean hasPrefabMobMarkerNear(@Nonnull Vector3d position, double maxDistanceSq) {
        for (Vector3d markerPosition : prefabMobMarkerPositions) {
            double dx = markerPosition.x - position.x;
            double dy = markerPosition.y - position.y;
            double dz = markerPosition.z - position.z;
            if (dx * dx + dy * dy + dz * dz <= maxDistanceSq) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public RoomData getParent() {
        return parent;
    }

    public void setParent(@Nullable RoomData parent) {
        this.parent = parent;
    }

    @Nonnull
    public List<RoomData> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(@Nonnull RoomData child) {
        children.add(child);
        child.setParent(this);
    }

    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    public void setExpectedMobCount(int count) {
        this.expectedMobCount = count;
    }

    public int getExpectedMobCount() {
        return expectedMobCount;
    }

    /**
     * Tick-level update: detect mobs that transitioned from valid to invalid
     * (i.e. they died while their chunk was loaded). Mobs in unloaded chunks
     * are never marked as "seen alive", so they won't be miscounted as dead.
     */
    public void updateMobTracking() {
        for (Ref<EntityStore> mob : spawnedMobs) {
            if (mob.isValid()) {
                seenAlive.add(mob);
            } else if (seenAlive.remove(mob)) {
                confirmedKills++;
            }
        }
    }

    /**
     * Returns true if all expected mobs in this room have been confirmed killed.
     */
    public boolean areAllMobsDead() {
        if (expectedMobCount == 0) {
            return true;
        }
        return confirmedKills >= expectedMobCount;
    }

    /**
     * Count of mobs still alive in this room (expected minus confirmed kills).
     */
    public int getAliveMobCount() {
        return Math.max(0, expectedMobCount - confirmedKills);
    }

    public int getBranchDepth() {
        return branchDepth;
    }

    public void setBranchDepth(int branchDepth) {
        this.branchDepth = branchDepth;
    }

    @Nonnull
    public String getBranchId() {
        return branchId != null ? branchId : "main";
    }

    public void setBranchId(@Nonnull String branchId) {
        this.branchId = branchId;
    }

    public boolean isMainBranch() {
        return branchDepth == 0;
    }

    @Nonnull
    public List<Vector3i> getMobSpawnPoints() {
        return mobSpawnPoints;
    }

    public void addMobSpawnPoint(@Nonnull Vector3i pos) {
        mobSpawnPoints.add(pos);
    }

    @Nonnull
    public List<Vector3i> getKeySpawnerPositions() {
        return keySpawnerPositions;
    }

    public void addKeySpawnerPosition(@Nonnull Vector3i pos) {
        keySpawnerPositions.add(pos);
    }

    @Nonnull
    public List<Vector3i> getPortalPositions() {
        return portalPositions;
    }

    public void addPortalPosition(@Nonnull Vector3i pos) {
        portalPositions.add(pos);
    }

    @Nonnull
    public List<Vector3i> getPortalExitPositions() {
        return portalExitPositions;
    }

    public void addPortalExitPosition(@Nonnull Vector3i pos) {
        portalExitPositions.add(pos);
    }

    public boolean isPortalSpawned() {
        return portalSpawned;
    }

    public void setPortalSpawned(boolean portalSpawned) {
        this.portalSpawned = portalSpawned;
    }

    // ── Door & lock system ──

    @Nonnull
    public List<Vector3i> getDoorPositions() {
        return doorPositions;
    }

    public void addDoorPosition(@Nonnull Vector3i pos) {
        doorPositions.add(pos);
    }

    @Nonnull
    public List<Vector3i> getActivationZonePositions() {
        return activationZonePositions;
    }

    public void addActivationZonePosition(@Nonnull Vector3i pos) {
        activationZonePositions.add(pos);
    }

    @Nonnull
    public List<Vector3i> getMobActivatorPositions() {
        return mobActivatorPositions;
    }

    public void addMobActivatorPosition(@Nonnull Vector3i pos) {
        mobActivatorPositions.add(pos);
    }

    public boolean hasMobClearActivator() {
        return hasMobClearActivator;
    }

    public void setHasMobClearActivator(boolean hasMobClearActivator) {
        this.hasMobClearActivator = hasMobClearActivator;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isDoorsSealed() {
        return doorsSealed;
    }

    public void setDoorsSealed(boolean doorsSealed) {
        this.doorsSealed = doorsSealed;
    }

    @Nonnull
    public DoorMode getDoorMode() {
        return doorMode;
    }

    public void setDoorMode(@Nonnull DoorMode doorMode) {
        this.doorMode = doorMode;
    }

    // ── Challenge system ──

    public boolean isChallengeActive() {
        return challengeActive;
    }

    public void setChallengeActive(boolean challengeActive) {
        this.challengeActive = challengeActive;
    }

    @Nonnull
    public List<ChallengeObjective> getChallenges() {
        return challenges;
    }

    public void addChallenge(@Nonnull ChallengeObjective objective) {
        challenges.add(objective);
    }

    // ── Boss walk-in tracking ──

    public boolean isPlayerEnteredRoom() {
        return playerEnteredRoom;
    }

    public void setPlayerEnteredRoom(boolean playerEnteredRoom) {
        this.playerEnteredRoom = playerEnteredRoom;
    }

    public double getDistanceWalkedInRoom() {
        return distanceWalkedInRoom;
    }

    public void addDistanceWalked(double distance) {
        this.distanceWalkedInRoom += distance;
    }

    public void setDistanceWalkedInRoom(double distance) {
        this.distanceWalkedInRoom = distance;
    }

    @Nullable
    public Vector3d getLastTrackedPosition() {
        return lastTrackedPosition;
    }

    public void setLastTrackedPosition(@Nullable Vector3d lastTrackedPosition) {
        this.lastTrackedPosition = lastTrackedPosition;
    }

    // ── Map bounds ──

    public boolean hasBounds() {
        return hasBounds;
    }

    public int getBoundsMinX() {
        return boundsMinX;
    }

    public int getBoundsMinZ() {
        return boundsMinZ;
    }

    public int getBoundsMaxX() {
        return boundsMaxX;
    }

    public int getBoundsMaxZ() {
        return boundsMaxZ;
    }

    public int getBoundsMinY() {
        return boundsMinY;
    }

    public int getBoundsMaxY() {
        return boundsMaxY;
    }

    public void setBounds(int minX, int minZ, int maxX, int maxZ) {
        this.boundsMinX = minX;
        this.boundsMinZ = minZ;
        this.boundsMaxX = maxX;
        this.boundsMaxZ = maxZ;
        this.hasBounds = true;
    }

    public void setYBounds(int minY, int maxY) {
        this.boundsMinY = minY;
        this.boundsMaxY = maxY;
    }

    public boolean containsY(int y) {
        return hasBounds && y >= boundsMinY && y <= boundsMaxY;
    }

    public boolean containsXZ(int x, int z) {
        return hasBounds && x >= boundsMinX && x <= boundsMaxX && z >= boundsMinZ && z <= boundsMaxZ;
    }

    public boolean contains(int x, int y, int z) {
        return containsXZ(x, z) && containsY(y);
    }

    /**
     * Check if a Y coordinate is within the room's Y bounds with a margin.
     * The margin shrinks the valid range inward (player must be further inside).
     */
    public boolean containsY(int y, int margin) {
        return hasBounds && y >= (boundsMinY + margin) && y <= (boundsMaxY - margin);
    }

    // ── Pinned mob spawns (from configured Shotcave_Mob_Spawner blocks) ──

    public record PinnedMobSpawn(@Nonnull Vector3i position, @Nonnull String mobId) {}

    public void addPinnedMobSpawn(@Nonnull Vector3i position, @Nonnull String mobId) {
        pinnedMobSpawns.add(new PinnedMobSpawn(position, mobId));
    }

    @Nonnull
    public List<PinnedMobSpawn> getPinnedMobSpawns() {
        return pinnedMobSpawns;
    }

    @Nonnull
    @Override
    public String toString() {
        return "RoomData{type=" + type + ", anchor=" + anchor
                + ", branch=" + branchId + "(d" + branchDepth + ")"
                + ", prefabMarkers=" + prefabMobMarkerPositions.size()
                + ", mobs=" + mobsToSpawn.size() + "}";
    }
}
