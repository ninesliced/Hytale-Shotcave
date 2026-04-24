package dev.ninesliced.unstablerifts.hud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HudCompat {

    private static final ConcurrentHashMap<UUID, UnstableRiftsMultipleHud> LOCAL_HUDS = new ConcurrentHashMap<>();

    private HudCompat() {
    }

    public static void setCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                    @Nonnull String hudId, @Nonnull UnstableRiftsCustomHud hud) {
        if (MultiHudCompat.setCustomHud(player, playerRef, hudId, hud)) {
            LOCAL_HUDS.remove(playerRef.getUuid());
            return;
        }

        UnstableRiftsMultipleHud multipleHud = LOCAL_HUDS.compute(playerRef.getUuid(), (uuid, existing) -> {
            if (existing != null && existing.getPlayerRef() == playerRef) {
                return existing;
            }
            return new UnstableRiftsMultipleHud(playerRef);
        });

        multipleHud.setHud(hudId, hud);
        applyLocalHud(player, playerRef, multipleHud);
    }

    public static void hideCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull String hudId) {
        if (MultiHudCompat.hideCustomHud(player, playerRef, hudId)) {
            return;
        }

        UnstableRiftsMultipleHud multipleHud = LOCAL_HUDS.get(playerRef.getUuid());
        if (multipleHud == null) {
            return;
        }

        multipleHud.removeHud(hudId);
        if (multipleHud.isEmpty()) {
            LOCAL_HUDS.remove(playerRef.getUuid(), multipleHud);
            if (player.getHudManager().getCustomHud() == multipleHud) {
                player.getHudManager().setCustomHud(playerRef, null);
            }
            return;
        }

        applyLocalHud(player, playerRef, multipleHud);
    }

    public static void clear(@Nonnull UUID playerUuid) {
        LOCAL_HUDS.remove(playerUuid);
    }

    private static void applyLocalHud(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                      @Nonnull UnstableRiftsMultipleHud hud) {
        CustomUIHud currentHud = player.getHudManager().getCustomHud();
        if (currentHud != hud) {
            player.getHudManager().setCustomHud(playerRef, hud);
        } else {
            hud.show();
        }
    }
}