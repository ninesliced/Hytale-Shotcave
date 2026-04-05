package dev.ninesliced.unstablerifts.shop;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Holder;
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
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import dev.ninesliced.unstablerifts.dungeon.RotationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Config page for ShopKeeper blocks — allows level designers to set the action range
 * and the spawned NPC yaw.
 */
public final class ShopKeeperConfigPage extends InteractiveCustomUIPage<ShopKeeperConfigPage.ConfigEventData> {

    private static final String LAYOUT_PATH = "Pages/UnstableRifts/ShopKeeperConfig.ui";

    @Nullable
    private final BlockPosition blockPos;
    private String actionRange = "5.0";
    private String rotationYaw = "0";
    private String refreshCost = "0";
    private String refreshCount = "0";
    private boolean initialized = false;

    public ShopKeeperConfigPage(@Nonnull PlayerRef playerRef, @Nullable BlockPosition blockPos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ConfigEventData.CODEC);
        this.blockPos = blockPos;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder ui,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        ui.append(LAYOUT_PATH);

        if (!initialized) {
            loadFromBlock(store);
            initialized = true;
        }

        ui.set("#ActionRangeInput.Value", actionRange);
        ui.set("#RotationYawInput.Value", rotationYaw);
        ui.set("#RefreshCostInput.Value", refreshCost);
        ui.set("#RefreshCountInput.Value", refreshCount);

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ActionRangeInput",
                new EventData().put(ConfigEventData.KEY_RANGE_INPUT, "#ActionRangeInput.Value"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#RotationYawInput",
                new EventData().put(ConfigEventData.KEY_ROTATION_INPUT, "#RotationYawInput.Value"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#RefreshCostInput",
                new EventData().put(ConfigEventData.KEY_REFRESH_COST_INPUT, "#RefreshCostInput.Value"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#RefreshCountInput",
                new EventData().put(ConfigEventData.KEY_REFRESH_COUNT_INPUT, "#RefreshCountInput.Value"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveBtn",
                new EventData().put(ConfigEventData.KEY_ACTION, "SAVE"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseBtn",
                new EventData().put(ConfigEventData.KEY_ACTION, "CLOSE"),
                false
        );
    }

    private void loadFromBlock(@Nonnull Store<EntityStore> store) {
        if (blockPos == null) return;
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z);
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) return;

        double fallbackDegrees = RotationUtil.rotationIndexToYawDegrees(chunk.getRotationIndex(blockPos.x, blockPos.y, blockPos.z));
        rotationYaw = formatRotationDegrees(fallbackDegrees);

        Holder<ChunkStore> holder = chunk.getBlockComponentHolder(blockPos.x, blockPos.y, blockPos.z);
        if (holder == null) return;

        ShopKeeperData data = holder.getComponent(ShopKeeperData.getComponentType());
        if (data != null) {
            String range = data.getActionRange();
            if (range != null && !range.isBlank()) {
                actionRange = range;
            }
            rotationYaw = formatRotationDegrees(data.parseRotationYawDegrees(fallbackDegrees));
            String savedRefreshCost = data.getRefreshCost();
            if (savedRefreshCost != null && !savedRefreshCost.isBlank()) {
                refreshCost = savedRefreshCost;
            }
            String savedRefreshCount = data.getRefreshCount();
            if (savedRefreshCount != null && !savedRefreshCount.isBlank()) {
                refreshCount = savedRefreshCount;
            }
        }
    }

    private void saveToBlock(@Nonnull Store<EntityStore> store) {
        if (blockPos == null) return;
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z);
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) return;

        BlockType blockType = chunk.getBlockType(blockPos.x, blockPos.y, blockPos.z);
        if (blockType == null) return;
        int rotation = chunk.getRotationIndex(blockPos.x, blockPos.y, blockPos.z);

        ShopKeeperData data = new ShopKeeperData(actionRange, rotationYaw, refreshCost, refreshCount);

        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.putComponent(ShopKeeperData.getComponentType(), data);
        chunk.setState(blockPos.x, blockPos.y, blockPos.z, blockType, rotation, holder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ConfigEventData data) {
        if (data.rangeInput != null) {
            actionRange = data.rangeInput;
        }
        if (data.rotationInput != null) {
            rotationYaw = data.rotationInput;
        }
        if (data.refreshCostInput != null) {
            refreshCost = data.refreshCostInput;
        }
        if (data.refreshCountInput != null) {
            refreshCount = data.refreshCountInput;
        }

        String action = data.action;
        if (action == null) return;

        switch (action) {
            case "CLOSE" -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().setPage(ref, store, Page.None);
                }
            }
            case "SAVE" -> {
                saveToBlock(store);
                try {
                    NotificationUtil.sendNotification(
                            this.playerRef.getPacketHandler(),
                            Message.raw("ShopKeeper config saved: range=" + actionRange
                                    + ", yaw=" + rotationYaw + " deg"
                                    + ", refreshCost=" + refreshCost
                                    + ", refreshCount=" + refreshCount),
                            null,
                            "shopkeeper_config");
                } catch (Exception ignored) {
                }
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().setPage(ref, store, Page.None);
                }
            }
        }
    }

    public static final class ConfigEventData {
        static final String KEY_ACTION = "Action";
        static final String KEY_RANGE_INPUT = "@RangeInput";
        static final String KEY_ROTATION_INPUT = "@RotationInput";
        static final String KEY_REFRESH_COST_INPUT = "@RefreshCostInput";
        static final String KEY_REFRESH_COUNT_INPUT = "@RefreshCountInput";

        static final BuilderCodec<ConfigEventData> CODEC = BuilderCodec.builder(ConfigEventData.class, ConfigEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>(KEY_RANGE_INPUT, Codec.STRING), (d, v) -> d.rangeInput = v, d -> d.rangeInput).add()
                .append(new KeyedCodec<>(KEY_ROTATION_INPUT, Codec.STRING), (d, v) -> d.rotationInput = v, d -> d.rotationInput).add()
                .append(new KeyedCodec<>(KEY_REFRESH_COST_INPUT, Codec.STRING), (d, v) -> d.refreshCostInput = v, d -> d.refreshCostInput).add()
                .append(new KeyedCodec<>(KEY_REFRESH_COUNT_INPUT, Codec.STRING), (d, v) -> d.refreshCountInput = v, d -> d.refreshCountInput).add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String rangeInput;
        @Nullable
        private String rotationInput;
        @Nullable
        private String refreshCostInput;
        @Nullable
        private String refreshCountInput;
    }

    @Nonnull
    private static String formatRotationDegrees(double degrees) {
        long rounded = Math.round(degrees);
        if (Math.abs(degrees - rounded) < 0.0001) {
            return Long.toString(rounded);
        }
        return Double.toString(degrees);
    }
}
