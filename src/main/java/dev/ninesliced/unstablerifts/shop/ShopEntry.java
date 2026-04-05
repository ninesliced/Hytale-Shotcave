package dev.ninesliced.unstablerifts.shop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A single item for sale in a shop room.
 * Generated at dungeon start from {@link ShopItemData} block config.
 */
public final class ShopEntry {

    private final ShopItemType type;
    private final int price;
    @Nullable
    private final ItemStack itemStack;
    @Nullable
    private Ref<EntityStore> displayRef;
    private boolean sold;

    public ShopEntry(@Nonnull ShopItemType type, int price, @Nullable ItemStack itemStack) {
        this.type = type;
        this.price = price;
        this.itemStack = itemStack;
        this.displayRef = null;
        this.sold = false;
    }

    @Nonnull
    public ShopItemType getType() {
        return type;
    }

    public int getPrice() {
        return price;
    }

    @Nullable
    public ItemStack getItemStack() {
        return itemStack;
    }

    @Nullable
    public Ref<EntityStore> getDisplayRef() {
        return displayRef;
    }

    public void setDisplayRef(@Nullable Ref<EntityStore> displayRef) {
        this.displayRef = displayRef;
    }

    public boolean isSold() {
        return sold;
    }

    public void setSold(boolean sold) {
        this.sold = sold;
    }
}
