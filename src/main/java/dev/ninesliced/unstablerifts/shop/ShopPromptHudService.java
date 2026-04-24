package dev.ninesliced.unstablerifts.shop;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ninesliced.unstablerifts.hud.HudCompat;
import dev.ninesliced.unstablerifts.hud.HudVisibilityService;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Show/hide logic for the shop interaction prompt HUD with per-player deduplication.
 */
public final class ShopPromptHudService {

    private static final String HUD_IDENTIFIER = ShopPromptHud.HUD_ID;
    private static final long STATE_HIDDEN = 0L;

    private static final ConcurrentHashMap<UUID, Long> LAST_STATE = new ConcurrentHashMap<>();

    private ShopPromptHudService() {
    }

    public static void show(@Nonnull Player player,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull String title,
                            @Nonnull String detail) {
        if (HudVisibilityService.isHidden(playerRef.getUuid())) {
            return;
        }
        long state = computeState(title, detail);
        UUID uuid = playerRef.getUuid();

        Long previous = LAST_STATE.get(uuid);
        if (previous != null && previous == state) {
            return;
        }
        LAST_STATE.put(uuid, state);

        ShopPromptHud hud = new ShopPromptHud(playerRef, title, detail);
        HudCompat.setCustomHud(player, playerRef, HUD_IDENTIFIER, hud);
    }

    public static void hide(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        Long previous = LAST_STATE.get(uuid);
        if (previous == null || previous == STATE_HIDDEN) {
            return;
        }
        LAST_STATE.put(uuid, STATE_HIDDEN);

        HudCompat.hideCustomHud(player, playerRef, HUD_IDENTIFIER);
    }

    public static void clear(@Nonnull PlayerRef playerRef) {
        LAST_STATE.remove(playerRef.getUuid());
    }

    public static boolean isActive(@Nonnull UUID playerUuid) {
        Long state = LAST_STATE.get(playerUuid);
        return state != null && state != STATE_HIDDEN;
    }

    public static void clearAll() {
        LAST_STATE.clear();
    }

    private static long computeState(@Nonnull String title, @Nonnull String detail) {
        long h = 8831L;
        h = 31L * h + title.hashCode();
        h = 31L * h + detail.hashCode();
        return h;
    }
}
