package dev.ninesliced.unstablerifts.hud;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UnstableRiftsMultipleHud extends UnstableRiftsCustomHud {

    private final Map<String, UnstableRiftsCustomHud> activeHuds = new LinkedHashMap<>();

    public UnstableRiftsMultipleHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    public synchronized void setHud(@Nonnull String hudId, @Nonnull UnstableRiftsCustomHud hud) {
        this.activeHuds.put(hudId, hud);
    }

    public synchronized void removeHud(@Nonnull String hudId) {
        this.activeHuds.remove(hudId);
    }

    public synchronized boolean isEmpty() {
        return this.activeHuds.isEmpty();
    }

    @Override
    protected synchronized void build(@Nonnull UICommandBuilder builder) {
        for (UnstableRiftsCustomHud hud : this.activeHuds.values()) {
            hud.appendTo(builder);
        }
    }
}