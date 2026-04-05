package dev.ninesliced.unstablerifts.shop;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.ninesliced.unstablerifts.dungeon.AbstractTargetBlockPageSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ShopItemConfigPageSupplier extends AbstractTargetBlockPageSupplier {

    @Nonnull
    public static final BuilderCodec<ShopItemConfigPageSupplier> CODEC =
            BuilderCodec.builder(ShopItemConfigPageSupplier.class, ShopItemConfigPageSupplier::new).build();

    @Override
    protected CustomUIPage createPage(@Nonnull PlayerRef playerRef, @Nullable BlockPosition targetBlock) {
        return new ShopItemConfigPage(playerRef, targetBlock);
    }
}
