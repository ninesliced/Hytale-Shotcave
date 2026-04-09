package dev.ninesliced.unstablerifts.inventory;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import javax.annotation.Nullable;

/**
 * Resolves item icon assets shared across HUD and page surfaces.
 */
public final class ItemIconResolver {

    private ItemIconResolver() {
    }

    @Nullable
    public static String resolveIconPath(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        try {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) {
                return null;
            }

            String icon = item.getIcon();
            return icon != null && !icon.isBlank() ? icon : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
