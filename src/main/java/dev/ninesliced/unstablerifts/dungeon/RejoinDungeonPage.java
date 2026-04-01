package dev.ninesliced.unstablerifts.dungeon;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Popup shown to a reconnecting player asking whether they want to rejoin
 * an active dungeon run or leave the party.
 */
public final class RejoinDungeonPage extends InteractiveCustomUIPage<RejoinDungeonPage.RejoinEventData> {

    private static final String LAYOUT_PATH = "Pages/UnstableRifts/RejoinDungeon.ui";
    private static final Map<UUID, RejoinDungeonPage> OPEN_PAGES = new ConcurrentHashMap<>();

    private final UnstableRifts plugin;
    private volatile boolean handled = false;

    public RejoinDungeonPage(@Nonnull UnstableRifts plugin, @Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, RejoinEventData.CODEC);
        this.plugin = plugin;
    }

    /**
     * Close any open rejoin page for a player (e.g. on disconnect or party disband).
     */
    public static void closeForPlayer(@Nonnull UUID playerId) {
        OPEN_PAGES.remove(playerId);
    }

    public static boolean hasOpenPage(@Nonnull UUID playerId) {
        return OPEN_PAGES.containsKey(playerId);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder ui,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        OPEN_PAGES.put(this.playerRef.getUuid(), this);
        ui.append(LAYOUT_PATH);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RejoinYesBtn",
                new EventData().put(RejoinEventData.KEY_ACTION, "REJOIN"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RejoinNoBtn",
                new EventData().put(RejoinEventData.KEY_ACTION, "DECLINE"),
                false
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull RejoinEventData data) {
        if (handled) return;
        handled = true;

        UUID playerId = this.playerRef.getUuid();
        OPEN_PAGES.remove(playerId);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }

        if ("REJOIN".equals(data.action)) {
            plugin.getGameManager().handleRejoinAccepted(this.playerRef);
        } else {
            plugin.getGameManager().handleRejoinDeclined(this.playerRef);
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (handled) return;
        handled = true;

        UUID playerId = this.playerRef.getUuid();
        OPEN_PAGES.remove(playerId);
        // Dismissing / closing without clicking a button = decline
        plugin.getGameManager().handleRejoinDeclined(this.playerRef);
    }

    public static final class RejoinEventData {
        static final String KEY_ACTION = "Action";
        static final BuilderCodec<RejoinEventData> CODEC = BuilderCodec.builder(RejoinEventData.class, RejoinEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .build();

        @Nullable
        private String action;
    }
}
