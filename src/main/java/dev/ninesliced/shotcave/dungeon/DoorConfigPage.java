package dev.ninesliced.shotcave.dungeon;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Door configuration UI page for level designers.
 * Shows the current door mode and allows switching between Key and Activator.
 * Swaps the block type at the target position so the mode persists in prefabs.
 */
public final class DoorConfigPage extends InteractiveCustomUIPage<DoorConfigPage.DoorEventData> {

    private static final String LAYOUT_PATH = "Pages/Shotcave/DoorConfig.ui";

    @Nullable
    private final BlockPosition blockPos;

    public DoorConfigPage(@Nonnull PlayerRef playerRef, @Nullable BlockPosition blockPos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, DoorEventData.CODEC);
        this.blockPos = blockPos;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder ui,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        ui.append(LAYOUT_PATH);

        // Determine current mode from block name
        String currentMode = "NONE";
        if (blockPos != null) {
            World world = store.getExternalData().getWorld();
            if (world != null) {
                long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z);
                WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
                if (chunk != null) {
                    BlockType blockType = chunk.getBlockType(blockPos.x, blockPos.y, blockPos.z);
                    if (blockType != null) {
                        String blockId = blockType.getId();
                        if ("Shotcave_Door_Key".equals(blockId)) {
                            currentMode = "KEY";
                        } else if ("Shotcave_Door_Activator".equals(blockId)) {
                            currentMode = "ACTIVATOR";
                        }
                    }
                }
            }
        }

        ui.set("#CurrentModeLabel.Text", "Current mode: " + currentMode);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#KeyDoorBtn",
                new EventData().put(DoorEventData.KEY_MODE, "KEY"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ActivatorDoorBtn",
                new EventData().put(DoorEventData.KEY_MODE, "ACTIVATOR"),
                false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull DoorEventData data) {
        String mode = data.mode;
        if (mode == null || mode.isBlank()) return;

        if (blockPos != null) {
            World world = store.getExternalData().getWorld();
            if (world != null) {
                String targetBlock;
                if ("KEY".equals(mode)) {
                    targetBlock = "Shotcave_Door_Key";
                } else {
                    targetBlock = "Shotcave_Door_Activator";
                }

                long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z);
                WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
                if (chunk != null) {
                    int rotation = chunk.getRotationIndex(blockPos.x, blockPos.y, blockPos.z);
                    world.setBlock(blockPos.x, blockPos.y, blockPos.z, targetBlock, rotation);
                }
            }
        }

        try {
            NotificationUtil.sendNotification(
                    this.playerRef.getPacketHandler(),
                    Message.raw("Door mode set to: " + mode),
                    null,
                    "door_config");
        } catch (Exception e) {
            // Best-effort
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    public static final class DoorEventData {
        static final String KEY_MODE = "Mode";
        static final BuilderCodec<DoorEventData> CODEC = BuilderCodec.<DoorEventData>builder(DoorEventData.class, DoorEventData::new)
                .append(new KeyedCodec<>(KEY_MODE, Codec.STRING), (d, v) -> d.mode = v, d -> d.mode).add()
                .build();

        @Nullable
        private String mode;
    }
}
