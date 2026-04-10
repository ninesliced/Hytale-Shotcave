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
 * Config page for ShopKeeper blocks — allows level designers to set the action range,
 * the spawned NPC yaw, and room-level shop pricing steps.
 */
public final class ShopKeeperConfigPage extends InteractiveCustomUIPage<ShopKeeperConfigPage.ConfigEventData> {

    private static final String LAYOUT_PATH = "Pages/UnstableRifts/ShopKeeperConfig.ui";

    @Nullable
    private final BlockPosition blockPos;
    private String actionRange = "5.0";
    private String rotationYaw = "0";
    private String refreshPriceStep = Integer.toString(ShopKeeperData.DEFAULT_REFRESH_PRICE_STEP);
    private String weaponPriceStep = Integer.toString(ShopKeeperData.DEFAULT_WEAPON_PRICE_STEP);
    private String armorPriceStep = Integer.toString(ShopKeeperData.DEFAULT_ARMOR_PRICE_STEP);
    private String itemPriceStep = Integer.toString(ShopKeeperData.DEFAULT_ITEM_PRICE_STEP);
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
        ui.set("#RefreshPriceStepInput.Value", refreshPriceStep);
        ui.set("#WeaponPriceStepInput.Value", weaponPriceStep);
        ui.set("#ArmorPriceStepInput.Value", armorPriceStep);
        ui.set("#ItemPriceStepInput.Value", itemPriceStep);

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
            "#RefreshPriceStepInput",
            new EventData().put(ConfigEventData.KEY_REFRESH_PRICE_STEP_INPUT, "#RefreshPriceStepInput.Value"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
            "#WeaponPriceStepInput",
            new EventData().put(ConfigEventData.KEY_WEAPON_PRICE_STEP_INPUT, "#WeaponPriceStepInput.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ArmorPriceStepInput",
            new EventData().put(ConfigEventData.KEY_ARMOR_PRICE_STEP_INPUT, "#ArmorPriceStepInput.Value"),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#ItemPriceStepInput",
            new EventData().put(ConfigEventData.KEY_ITEM_PRICE_STEP_INPUT, "#ItemPriceStepInput.Value"),
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
            String savedRefreshPriceStep = data.getRefreshCost();
            if (savedRefreshPriceStep != null && !savedRefreshPriceStep.isBlank()) {
                refreshPriceStep = savedRefreshPriceStep;
            }
            String savedWeaponPriceStep = data.getWeaponPriceStep();
            if (savedWeaponPriceStep != null && !savedWeaponPriceStep.isBlank()) {
                weaponPriceStep = savedWeaponPriceStep;
            }
            String savedArmorPriceStep = data.getArmorPriceStep();
            if (savedArmorPriceStep != null && !savedArmorPriceStep.isBlank()) {
                armorPriceStep = savedArmorPriceStep;
            }
            String savedItemPriceStep = data.getItemPriceStep();
            if (savedItemPriceStep != null && !savedItemPriceStep.isBlank()) {
                itemPriceStep = savedItemPriceStep;
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

        ShopKeeperData data = new ShopKeeperData(
            actionRange,
            rotationYaw,
            refreshPriceStep,
            null,
            weaponPriceStep,
            armorPriceStep,
            itemPriceStep);

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
        if (data.refreshPriceStepInput != null) {
            refreshPriceStep = data.refreshPriceStepInput;
        }
        if (data.weaponPriceStepInput != null) {
            weaponPriceStep = data.weaponPriceStepInput;
        }
        if (data.armorPriceStepInput != null) {
            armorPriceStep = data.armorPriceStepInput;
        }
        if (data.itemPriceStepInput != null) {
            itemPriceStep = data.itemPriceStepInput;
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
                                    + ", refreshStep=" + refreshPriceStep
                                    + ", weaponStep=" + weaponPriceStep
                                    + ", armorStep=" + armorPriceStep
                                    + ", itemStep=" + itemPriceStep),
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
        static final String KEY_REFRESH_PRICE_STEP_INPUT = "@RefreshPriceStepInput";
        static final String KEY_WEAPON_PRICE_STEP_INPUT = "@WeaponPriceStepInput";
        static final String KEY_ARMOR_PRICE_STEP_INPUT = "@ArmorPriceStepInput";
        static final String KEY_ITEM_PRICE_STEP_INPUT = "@ItemPriceStepInput";

        static final BuilderCodec<ConfigEventData> CODEC = BuilderCodec.builder(ConfigEventData.class, ConfigEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>(KEY_RANGE_INPUT, Codec.STRING), (d, v) -> d.rangeInput = v, d -> d.rangeInput).add()
                .append(new KeyedCodec<>(KEY_ROTATION_INPUT, Codec.STRING), (d, v) -> d.rotationInput = v, d -> d.rotationInput).add()
            .append(new KeyedCodec<>(KEY_REFRESH_PRICE_STEP_INPUT, Codec.STRING), (d, v) -> d.refreshPriceStepInput = v, d -> d.refreshPriceStepInput).add()
            .append(new KeyedCodec<>(KEY_WEAPON_PRICE_STEP_INPUT, Codec.STRING), (d, v) -> d.weaponPriceStepInput = v, d -> d.weaponPriceStepInput).add()
            .append(new KeyedCodec<>(KEY_ARMOR_PRICE_STEP_INPUT, Codec.STRING), (d, v) -> d.armorPriceStepInput = v, d -> d.armorPriceStepInput).add()
            .append(new KeyedCodec<>(KEY_ITEM_PRICE_STEP_INPUT, Codec.STRING), (d, v) -> d.itemPriceStepInput = v, d -> d.itemPriceStepInput).add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String rangeInput;
        @Nullable
        private String rotationInput;
        @Nullable
        private String refreshPriceStepInput;
        @Nullable
        private String weaponPriceStepInput;
        @Nullable
        private String armorPriceStepInput;
        @Nullable
        private String itemPriceStepInput;
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
