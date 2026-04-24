package dev.ninesliced.unstablerifts.mission;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Interactive UI page shown when a player interacts with the Rift Merchant NPC.
 * Displays 3 active random quests with claim/skip buttons, plus a button to open the barter shop.
 */
public final class RiftMerchantPage extends InteractiveCustomUIPage<RiftMerchantPage.MerchantEventData> {

    private static final String LAYOUT_PATH = "Pages/UnstableRifts/RiftMerchantPage.ui";
    private static final String MISSION_TEMPLATE = "Pages/UnstableRifts/RiftMerchantMissionEntry.ui";
    private static final String MISSION_LIST_PATH = "#MissionList";
    private static final int BAR_MAX_WIDTH = 340;
    private static final int BAR_HEIGHT = 12;

    private final Store<EntityStore> store;
    private final Ref<EntityStore> playerEntityRef;
    private final String shopId;

    public RiftMerchantPage(@Nonnull PlayerRef playerRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> playerEntityRef,
                            @Nonnull String shopId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, MerchantEventData.CODEC);
        this.store = store;
        this.playerEntityRef = playerEntityRef;
        this.shopId = shopId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder ui,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        ui.append(LAYOUT_PATH);

        ui.set("#MerchantTitle.TextSpans", Message.raw("RIFT MERCHANT"));

        // Close button
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MerchantCloseBtn",
                new EventData().put(MerchantEventData.KEY_ACTION, "CLOSE"),
                false
        );

        // Open Shop button
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MerchantShopBtn",
                new EventData().put(MerchantEventData.KEY_ACTION, "SHOP"),
                false
        );

        buildQuestList(ui, events, store);
    }

    private void buildQuestList(@Nonnull UICommandBuilder ui,
                                @Nonnull UIEventBuilder events,
                                @Nonnull Store<EntityStore> store) {
        ui.clear(MISSION_LIST_PATH);

        UnstableRifts plugin = UnstableRifts.getInstance();
        if (plugin == null) return;

        MissionService missionService = plugin.getMissionService();
        List<MissionService.QuestStatus> statuses = missionService.getActiveQuestStatuses(store, playerEntityRef);
        int skipCost = missionService.getSkipCostCoins();

        for (int i = 0; i < statuses.size(); i++) {
            MissionService.QuestStatus status = statuses.get(i);
            MissionDefinition def = status.definition();
            String entryPath = MISSION_LIST_PATH + "[" + i + "]";

            ui.append(MISSION_LIST_PATH, MISSION_TEMPLATE);

            // Quest name
            ui.set(entryPath + " #MissionName.TextSpans", Message.raw(def.displayName()));

            // Description
            ui.set(entryPath + " #MissionDesc.TextSpans", Message.raw(def.description()));

            // Progress bar text
            int clamped = Math.min(status.currentProgress(), def.target());
            ui.set(entryPath + " #MissionProgress.TextSpans",
                    Message.raw(clamped + " / " + def.target()));

            // Progress fill width (pixel-based, full Anchor replacement)
            float pct = def.target() > 0 ? (float) clamped / def.target() : 0f;
            pct = Math.min(pct, 1f);
            int fillWidth = Math.round(BAR_MAX_WIDTH * pct);
            Anchor fillAnchor = new Anchor();
            fillAnchor.setLeft(Value.of(0));
            fillAnchor.setTop(Value.of(0));
            fillAnchor.setWidth(Value.of(fillWidth));
            fillAnchor.setHeight(Value.of(BAR_HEIGHT));
            ui.setObject(entryPath + " #MissionProgressFill.Anchor", fillAnchor);

            // Reward text
            ui.set(entryPath + " #MissionReward.TextSpans",
                    Message.raw(def.rewardCoins() + " Rift Coin" + (def.rewardCoins() != 1 ? "s" : "")));

            if (status.isComplete()) {
                // Show claim button, hide skip
                ui.set(entryPath + " #MissionClaimBtn.Visible", true);
                ui.set(entryPath + " #MissionSkipBtn.Visible", false);
                ui.set(entryPath + " #MissionProgressFill.Background", "#FFD700");

                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        entryPath + " #MissionClaimBtn",
                        new EventData()
                                .put(MerchantEventData.KEY_ACTION, "CLAIM")
                                .put(MerchantEventData.KEY_SLOT, String.valueOf(status.slotIndex())),
                        false
                );
            } else {
                // Show skip button, hide claim
                ui.set(entryPath + " #MissionClaimBtn.Visible", false);
                ui.set(entryPath + " #MissionSkipBtn.Visible", true);
                ui.set(entryPath + " #MissionSkipBtn.TextSpans",
                        Message.raw("SKIP (" + skipCost + " coins)"));
                ui.set(entryPath + " #MissionProgressFill.Background", "#4488FF");

                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        entryPath + " #MissionSkipBtn",
                        new EventData()
                                .put(MerchantEventData.KEY_ACTION, "SKIP")
                                .put(MerchantEventData.KEY_SLOT, String.valueOf(status.slotIndex())),
                        false
                );
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull MerchantEventData data) {
        String action = data.action;
        if (action == null) return;

        switch (action) {
            case "CLOSE" -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().setPage(ref, store, Page.None);
                }
                return;
            }
            case "SHOP" -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().openCustomPage(
                            ref, store, new RiftMerchantShopPage(playerRef, playerEntityRef, shopId));
                }
                return;
            }
            case "CLAIM" -> {
                int slot = parseSlot(data.slot);
                if (slot >= 0) {
                    UnstableRifts plugin = UnstableRifts.getInstance();
                    if (plugin != null) {
                        plugin.getMissionService().claimQuest(slot, playerRef, store, playerEntityRef);
                    }
                }
            }
            case "SKIP" -> {
                int slot = parseSlot(data.slot);
                if (slot >= 0) {
                    UnstableRifts plugin = UnstableRifts.getInstance();
                    if (plugin != null) {
                        plugin.getMissionService().skipQuest(slot, playerRef, store, playerEntityRef);
                    }
                }
            }
        }

        // Refresh the page after claim/skip
        refreshMerchantPage(ref, store);
    }

    private static int parseSlot(@Nullable String s) {
        if (s == null) return -1;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void refreshMerchantPage(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull Store<EntityStore> store) {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        buildQuestList(ui, events, store);
        sendUpdate(ui, events, false);
    }

    public static final class MerchantEventData {
        static final String KEY_ACTION = "Action";
        static final String KEY_SLOT = "Slot";

        static final BuilderCodec<MerchantEventData> CODEC = BuilderCodec.builder(
                        MerchantEventData.class, MerchantEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                        (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>(KEY_SLOT, Codec.STRING),
                        (d, v) -> d.slot = v, d -> d.slot).add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String slot;
    }
}
