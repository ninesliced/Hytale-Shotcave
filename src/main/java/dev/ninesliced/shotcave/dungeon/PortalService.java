package dev.ninesliced.shotcave.dungeon;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Manages portal block placement and removal in dungeon rooms,
 * and provides collision detection for portal teleportation.
 */
public final class PortalService {

    private static final Logger LOGGER = Logger.getLogger(PortalService.class.getName());
    private static final String PORTAL_BLOCK = "Shotcave_Dungeon_Portal";

    public PortalService() {
    }

    /**
     * Places portal blocks at all portal positions in the room.
     * No-op if already spawned.
     */
    public void spawnPortal(@Nonnull RoomData room, @Nonnull World world) {
        if (room.isPortalSpawned()) return;

        // If no portal positions were marked in the prefab, use a fallback position
        // based on the room's bounds (center) or anchor ONLY for boss rooms.
        if (room.getPortalPositions().isEmpty()) {
            if (room.getType() == RoomType.BOSS) {
                Vector3i fallback;
                if (room.hasBounds()) {
                    fallback = new Vector3i(
                        (room.getBoundsMinX() + room.getBoundsMaxX()) / 2,
                        room.getBoundsMinY() + 1,
                        (room.getBoundsMinZ() + room.getBoundsMaxZ()) / 2
                    );
                } else {
                    Vector3i anchor = room.getAnchor();
                    fallback = new Vector3i(anchor.x + 5, anchor.y + 1, anchor.z + 5);
                }
                room.addPortalPosition(fallback);
                LOGGER.info("Room " + room.getType() + " at " + room.getAnchor() + " has no portal markers. Using fallback at " + fallback);
            } else {
                return;
            }
        }

        for (Vector3i pos : room.getPortalPositions()) {
            try {
                world.setBlock(pos.x, pos.y, pos.z, PORTAL_BLOCK, 0);
            } catch (Exception e) {
                LOGGER.warning("Failed to place portal at " + pos + ": " + e.getMessage());
            }
        }
        room.setPortalSpawned(true);
    }

    /**
     * Removes portal blocks (sets to Empty) at all portal positions in the room.
     */
    public void removePortal(@Nonnull RoomData room, @Nonnull World world) {
        if (!room.isPortalSpawned()) return;

        for (Vector3i pos : room.getPortalPositions()) {
            try {
                world.setBlock(pos.x, pos.y, pos.z, "Empty", 0);
            } catch (Exception e) {
                LOGGER.warning("Failed to remove portal at " + pos + ": " + e.getMessage());
            }
        }
        room.setPortalSpawned(false);
    }

    /**
     * Checks if a block position matches any portal position in the boss room.
     * Checks exact Y and Y-1 to account for player feet vs block placement.
     */
    public boolean isPlayerOnPortal(@Nonnull Level level, int bx, int by, int bz) {
        RoomData bossRoom = level.getBossRoom();
        if (bossRoom == null || !bossRoom.isPortalSpawned()) return false;

        for (Vector3i pos : bossRoom.getPortalPositions()) {
            if (pos.x == bx && pos.z == bz && (pos.y == by || pos.y == by - 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true while the player is still within the portal's immediate area.
     * Used to force players to step away from a freshly spawned boss portal
     * before re-entering can trigger the level transition.
     */
    public boolean isPlayerNearPortal(@Nonnull Level level, @Nonnull Vector3d playerPos, double horizontalRadius) {
        RoomData bossRoom = level.getBossRoom();
        if (bossRoom == null || !bossRoom.isPortalSpawned()) return false;

        double maxDistanceSq = horizontalRadius * horizontalRadius;
        for (Vector3i pos : bossRoom.getPortalPositions()) {
            double centerX = pos.x + 0.5;
            double centerZ = pos.z + 0.5;
            double dx = playerPos.x - centerX;
            double dz = playerPos.z - centerZ;
            double dy = Math.abs(playerPos.y - (pos.y + 1.0));
            if (dx * dx + dz * dz <= maxDistanceSq && dy <= 2.0) {
                return true;
            }
        }
        return false;
    }
}
