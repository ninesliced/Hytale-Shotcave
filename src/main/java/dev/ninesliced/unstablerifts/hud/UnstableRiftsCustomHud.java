package dev.ninesliced.unstablerifts.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public abstract class UnstableRiftsCustomHud extends CustomUIHud {

    protected UnstableRiftsCustomHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    public final void appendTo(@Nonnull UICommandBuilder builder) {
        this.build(builder);
    }
}