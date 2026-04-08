package dev.ninesliced.unstablerifts.dungeon.map;

import com.hypixel.hytale.protocol.packets.interface_.UpdateAnchorUI;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ninesliced.unstablerifts.dungeon.Game;
import dev.ninesliced.unstablerifts.dungeon.Level;
import dev.ninesliced.unstablerifts.dungeon.RoomData;
import dev.ninesliced.unstablerifts.dungeon.RoomType;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Injects a non-interactive legend panel into the dungeon world map.
 */
public final class DungeonMapLegendService {

    public static final String MAP_ANCHOR_ID = "MapServerContent";
    private static final Logger LOGGER = Logger.getLogger(DungeonMapLegendService.class.getName());
    private static final String UI_PATH = "Hud/UnstableRifts/DungeonMapLegend.ui";

    public void sendLegend(@Nonnull PlayerRef playerRef, @Nonnull Game game) {
        Level level = game.getCurrentLevel();
        if (level == null) {
            clear(playerRef);
            return;
        }

        try {
            UICommandBuilder ui = new UICommandBuilder();
            ui.append(UI_PATH);
            ui.set("#LegendSubtitle.TextSpans", Message.raw(level.getName()));

            setLegendRow(ui, level, RoomType.SPAWN, "Spawn");
            setLegendRow(ui, level, RoomType.CORRIDOR, "Corridor");
            setLegendRow(ui, level, RoomType.CHALLENGE, "Challenge");
            setLegendRow(ui, level, RoomType.TREASURE, "Treasure");
            setLegendRow(ui, level, RoomType.SHOP, "Shop");
            setLegendRow(ui, level, RoomType.BOSS, "Boss");

            playerRef.getPacketHandler().writeNoCache(
                    new UpdateAnchorUI(MAP_ANCHOR_ID, true, ui.getCommands(), null)
            );
        } catch (Exception e) {
            LOGGER.warning("Failed to send dungeon map legend to " + playerRef.getUsername() + ": " + e.getMessage());
        }
    }

    public void clear(@Nonnull PlayerRef playerRef) {
        try {
            playerRef.getPacketHandler().writeNoCache(
                    new UpdateAnchorUI(MAP_ANCHOR_ID, true, null, null)
            );
        } catch (Exception e) {
            LOGGER.warning("Failed to clear dungeon map legend for " + playerRef.getUsername() + ": " + e.getMessage());
        }
    }

    private static void setLegendRow(@Nonnull UICommandBuilder ui,
                                     @Nonnull Level level,
                                     @Nonnull RoomType roomType,
                                     @Nonnull String rowIdPrefix) {
        ui.set("#" + rowIdPrefix + "Swatch.Background", DungeonMapRenderer.getLegendColorHex(roomType));
        ui.set("#" + rowIdPrefix + "Count.TextSpans", Message.raw(formatRoomCount(countRooms(level, roomType))));
    }

    private static int countRooms(@Nonnull Level level, @Nonnull RoomType roomType) {
        int count = 0;
        for (RoomData room : level.getRooms()) {
            if (room.getType() == roomType) {
                count++;
            }
        }
        return count;
    }

    @Nonnull
    private static String formatRoomCount(int count) {
        return count == 1 ? "1 room" : count + " rooms";
    }
}