package dev.ninesliced.unstablerifts.mission;

import com.hypixel.hytale.builtin.adventure.shop.barter.BarterItemStack;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopAsset;
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterTrade;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import dev.ninesliced.unstablerifts.UnstableRifts;
import dev.ninesliced.unstablerifts.inventory.ItemIconResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Per-player Rift Merchant shop page that merges the static merchant stock with
 * trophy offers unlocked from boss completion counts.
 */
public final class RiftMerchantShopPage extends InteractiveCustomUIPage<RiftMerchantShopPage.ShopEventData> {

    private static final String LAYOUT_PATH = "Pages/UnstableRifts/ShopPage.ui";
    private static final String ITEM_TEMPLATE = "Pages/UnstableRifts/ShopEntry.ui";
    private static final String ITEM_LIST_PATH = "#ShopItemList";

    @Nonnull
    private final Ref<EntityStore> playerEntityRef;
    @Nonnull
    private final String shopId;
    @Nullable
    private final BarterShopAsset baseShopAsset;

    public RiftMerchantShopPage(@Nonnull PlayerRef playerRef,
                                @Nonnull Ref<EntityStore> playerEntityRef,
                                @Nonnull String shopId) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ShopEventData.CODEC);
        this.playerEntityRef = playerEntityRef;
        this.shopId = shopId;
        this.baseShopAsset = BarterShopAsset.getAssetMap().getAsset(shopId);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder ui,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        ui.append(LAYOUT_PATH);
        ui.set("#ShopTitle.TextSpans", Message.raw("RIFT MERCHANT"));
        ui.set("#ShopMoney.TextSpans", Message.raw("Rift Coins: " + countPlayerCoins(store)));
        ui.set("#ShopConfirmPanel.Visible", false);

        ui.set("#ShopRefreshBtn.TextSpans", Message.raw("BACK"));
        ui.set("#ShopRefreshBtnDisabled.TextSpans", Message.raw("BACK"));
        ui.set("#ShopRefreshBtn.Visible", true);
        ui.set("#ShopRefreshBtnDisabled.Visible", false);

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ShopRefreshBtn",
                new EventData().put(ShopEventData.KEY_ACTION, "BACK"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ShopCloseBtn",
                new EventData().put(ShopEventData.KEY_ACTION, "CLOSE"),
                false
        );

        buildOfferList(ui, events, store);
    }

    private void buildOfferList(@Nonnull UICommandBuilder ui,
                                @Nonnull UIEventBuilder events,
                                @Nonnull Store<EntityStore> store) {
        ui.clear(ITEM_LIST_PATH);

        int playerCoins = countPlayerCoins(store);
        List<MerchantOffer> offers = getOffers(store);

        for (int displayIndex = 0; displayIndex < offers.size(); displayIndex++) {
            MerchantOffer offer = offers.get(displayIndex);
            String itemPath = ITEM_LIST_PATH + "[" + displayIndex + "]";

            ui.append(ITEM_LIST_PATH, ITEM_TEMPLATE);
            ui.set(itemPath + " #ShopEntryName.TextSpans", Message.raw(offer.displayName()).color(offer.displayColor()));
            ui.set(itemPath + " #ShopEntryPrice.TextSpans", Message.raw(buildPriceLabel(offer)));
            ui.set(itemPath + " #ShopEntryType.TextSpans", Message.raw(buildTypeLabel(offer)).color(offer.displayColor()));
            ui.set(itemPath + " #ShopEntryDetails #ShopDetailText.TextSpans", Message.raw(buildDetailText(offer, playerCoins)));

            String iconPath = ItemIconResolver.resolveIconPath(offer.outputItemId());
            if (iconPath != null) {
                ui.set(itemPath + " #ShopEntryDetails #ShopDetailIcon.AssetPath", iconPath);
                ui.set(itemPath + " #ShopEntryDetails #ShopDetailIcon.Visible", true);
            } else {
                ui.set(itemPath + " #ShopEntryDetails #ShopDetailIcon.Visible", false);
            }

            boolean canPurchase = offer.canPurchase();
            ui.set(itemPath + " #ShopEntryBuyBtn.Visible", canPurchase);
            if (canPurchase) {
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        itemPath + " #ShopEntryBuyBtn",
                        new EventData()
                                .put(ShopEventData.KEY_ACTION, "BUY")
                                .put(ShopEventData.KEY_INDEX, String.valueOf(displayIndex)),
                        false
                );
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull ShopEventData data) {
        String action = data.action;
        if (action == null) {
            return;
        }

        switch (action) {
            case "CLOSE" -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().setPage(ref, store, Page.None);
                }
                return;
            }
            case "BACK" -> {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.getPageManager().openCustomPage(
                            ref,
                            store,
                            new RiftMerchantPage(playerRef, store, playerEntityRef, shopId)
                    );
                }
                return;
            }
            case "BUY" -> {
                int index = parseIntSafe(data.index, -1);
                if (index >= 0) {
                    attemptPurchase(ref, store, index);
                }
            }
            default -> {
                return;
            }
        }

        refreshShopPage(ref, store);
    }

    private void attemptPurchase(@Nonnull Ref<EntityStore> ref,
                                 @Nonnull Store<EntityStore> store,
                                 int index) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        List<MerchantOffer> offers = getOffers(store);
        if (index < 0 || index >= offers.size()) {
            return;
        }

        MerchantOffer offer = offers.get(index);
        if (!offer.canPurchase()) {
            notifyPlayer("That item is not available.", "rift_merchant_unavailable");
            return;
        }

        CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, playerEntityRef, InventoryComponent.HOTBAR_FIRST);
        if (combinedInventory == null) {
            notifyPlayer("Inventory unavailable.", "rift_merchant_inventory_fail");
            return;
        }

        if (!canAfford(combinedInventory, offer.trade())) {
            notifyPlayer("Not enough Rift Coins.", "rift_merchant_no_coins");
            return;
        }

        removeTradeInputs(combinedInventory, offer.trade());
        giveTradeOutput(player, store, offer.trade());

        UnstableRifts plugin = UnstableRifts.getInstance();
        if (plugin != null) {
            plugin.getMissionService().addProgress(playerRef, MissionType.SHOP_PURCHASES, 1);
        }

        notifyPlayer("Purchased " + offer.displayName() + "!", "rift_merchant_buy");
    }

    private void giveTradeOutput(@Nonnull Player player,
                                 @Nonnull Store<EntityStore> store,
                                 @Nonnull BarterTrade trade) {
        BarterItemStack output = trade.getOutput();
        ItemStack outputStack = new ItemStack(output.getItemId(), output.getQuantity());
        CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, playerEntityRef, InventoryComponent.HOTBAR_FIRST);
        if (combinedInventory == null) {
            ItemUtils.dropItem(playerEntityRef, outputStack, store);
            return;
        }

        ItemStackTransaction transaction = combinedInventory.addItemStack(outputStack);
        ItemStack remainder = transaction.getRemainder();
        if (remainder != null && !remainder.isEmpty()) {
            int addedQty = outputStack.getQuantity() - remainder.getQuantity();
            if (addedQty > 0) {
                player.notifyPickupItem(playerEntityRef, outputStack.withQuantity(addedQty), null, store);
            }
            ItemUtils.dropItem(playerEntityRef, remainder, store);
        } else {
            player.notifyPickupItem(playerEntityRef, outputStack, null, store);
        }
    }

    private boolean canAfford(@Nonnull CombinedItemContainer combinedInventory,
                              @Nonnull BarterTrade trade) {
        for (BarterItemStack input : trade.getInput()) {
            int currentCount = combinedInventory.countItemStacks(stack -> input.getItemId().equals(stack.getItemId()));
            if (currentCount < input.getQuantity()) {
                return false;
            }
        }
        return true;
    }

    private void removeTradeInputs(@Nonnull CombinedItemContainer combinedInventory,
                                   @Nonnull BarterTrade trade) {
        for (BarterItemStack input : trade.getInput()) {
            combinedInventory.removeItemStack(new ItemStack(input.getItemId(), input.getQuantity()));
        }
    }

    @Nonnull
    private List<MerchantOffer> getOffers(@Nonnull Store<EntityStore> store) {
        List<MerchantOffer> offers = new ArrayList<>();

        if (baseShopAsset != null) {
            BarterTrade[] trades = resolveBaseTrades();
            for (int i = 0; i < trades.length; i++) {
                offers.add(MerchantOffer.base(i, trades[i]));
            }
        }

        MissionDataComponent data = store.ensureAndGetComponent(playerEntityRef, MissionDataComponent.getComponentType());
        for (RiftMerchantTrophies.TrophyDefinition definition : RiftMerchantTrophies.definitions()) {
            int completionCount = data != null ? data.getBossCompletionCount(definition.boss().bossKey()) : 0;
            BarterTrade trade = new BarterTrade(
                    new BarterItemStack(definition.itemId(), 1),
                    new BarterItemStack[]{new BarterItemStack(RiftMerchantTrophies.COIN_ITEM_ID, definition.price())},
                    1
            );
            offers.add(MerchantOffer.trophy(definition, trade, completionCount));
        }

        return offers;
    }

    @Nonnull
    private BarterTrade[] resolveBaseTrades() {
        if (baseShopAsset == null) {
            return new BarterTrade[0];
        }
        if (baseShopAsset.getTrades() != null && baseShopAsset.getTrades().length > 0) {
            return baseShopAsset.getTrades();
        }
        if (baseShopAsset.getTradeSlots() == null || baseShopAsset.getTradeSlots().length == 0) {
            return new BarterTrade[0];
        }

        Random random = new Random(shopId.hashCode());
        List<BarterTrade> trades = new ArrayList<>(baseShopAsset.getTradeSlots().length);
        for (var slot : baseShopAsset.getTradeSlots()) {
            if (slot != null) {
                trades.addAll(slot.resolve(random));
            }
        }
        return trades.toArray(BarterTrade[]::new);
    }

    private int countPlayerCoins(@Nonnull Store<EntityStore> store) {
        CombinedItemContainer combinedInventory = InventoryComponent.getCombined(store, playerEntityRef, InventoryComponent.HOTBAR_FIRST);
        if (combinedInventory == null) {
            return 0;
        }
        return combinedInventory.countItemStacks(stack -> RiftMerchantTrophies.COIN_ITEM_ID.equals(stack.getItemId()));
    }

    @Nonnull
    private String buildPriceLabel(@Nonnull MerchantOffer offer) {
        if (!offer.tradeValid()) {
            return "INVALID";
        }
        if (offer.isLocked()) {
            return "LOCKED";
        }
        return offer.price() + " coins";
    }

    @Nonnull
    private String buildTypeLabel(@Nonnull MerchantOffer offer) {
        return switch (offer.kind()) {
            case BASE -> "MERCHANT STOCK";
            case TROPHY -> offer.trophyDefinition().boss().displayName().toUpperCase() + " TROPHY";
        };
    }

    @Nonnull
    private String buildDetailText(@Nonnull MerchantOffer offer, int playerCoins) {
        if (!offer.tradeValid()) {
            return "This offer references a missing item asset.";
        }

        StringBuilder sb = new StringBuilder();
        if (offer.kind() == OfferKind.BASE) {
            sb.append(getBaseOfferDescription(offer.outputItemId()));
        } else if (offer.trophyDefinition() != null) {
            RiftMerchantTrophies.TrophyDefinition trophy = offer.trophyDefinition();
            sb.append("Placeable boss trophy decoration.");
            sb.append("\nRarity: ").append(trophy.rarity().itemSuffix());
            sb.append("\nUnlocks at ").append(trophy.requiredCompletions()).append(" clears");
            sb.append("\nProgress: ").append(offer.completionCount()).append(" / ").append(trophy.requiredCompletions());
            if (offer.isLocked()) {
                sb.append("\nStatus: Locked");
            } else {
                sb.append("\nStatus: Available");
            }
        }

        sb.append("\nOwned coins: ").append(playerCoins).append(" / ").append(offer.price());
        return sb.toString();
    }

    @Nonnull
    private String getBaseOfferDescription(@Nonnull String itemId) {
        return switch (itemId) {
            case "UnstableRifts_Ammo_Item" -> "Refills gun ammo for future runs.";
            case "UnstableRifts_Shop_Crate_T1" -> "Contains a random weapon from the common to rare pool.";
            case "UnstableRifts_Shop_Crate_T2" -> "Contains a random weapon from the rare to legendary pool.";
            case "UnstableRifts_Shop_Crate_T3" -> "Contains a random weapon from the legendary to unique pool.";
            default -> "Merchant stock item.";
        };
    }

    private void refreshShopPage(@Nonnull Ref<EntityStore> ref,
                                 @Nonnull Store<EntityStore> store) {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        build(ref, ui, events, store);
        sendUpdate(ui, events, true);
    }

    private void notifyPlayer(@Nonnull String message, @Nonnull String notificationId) {
        try {
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw(message),
                    null,
                    notificationId
            );
        } catch (Exception ignored) {
        }
    }

    private static int parseIntSafe(@Nullable String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private enum OfferKind {
        BASE,
        TROPHY
    }

    private record MerchantOffer(@Nonnull OfferKind kind,
                                 int baseTradeIndex,
                                 @Nonnull BarterTrade trade,
                                 @Nullable RiftMerchantTrophies.TrophyDefinition trophyDefinition,
                                 int completionCount,
                                 boolean tradeValid,
                                 @Nonnull String displayName,
                                 @Nonnull Color displayColor) {

        @Nonnull
        static MerchantOffer base(int baseTradeIndex, @Nonnull BarterTrade trade) {
            String itemId = trade.getOutput().getItemId();
            return new MerchantOffer(
                    OfferKind.BASE,
                    baseTradeIndex,
                    trade,
                    null,
                    0,
                    isTradeValid(trade),
                    resolveBaseDisplayName(itemId),
                    resolveDisplayColor(itemId)
            );
        }

        @Nonnull
        static MerchantOffer trophy(@Nonnull RiftMerchantTrophies.TrophyDefinition trophyDefinition,
                                    @Nonnull BarterTrade trade,
                                    int completionCount) {
            return new MerchantOffer(
                    OfferKind.TROPHY,
                    -1,
                    trade,
                    trophyDefinition,
                    completionCount,
                    isTradeValid(trade),
                    trophyDefinition.displayName(),
                    resolveDisplayColor(trophyDefinition.itemId())
            );
        }

        boolean isLocked() {
            return trophyDefinition != null && completionCount < trophyDefinition.requiredCompletions();
        }

        boolean canPurchase() {
            return tradeValid && !isLocked();
        }

        int price() {
            return trade.getInput().length > 0 ? trade.getInput()[0].getQuantity() : 0;
        }

        @Nonnull
        String outputItemId() {
            return trade.getOutput().getItemId();
        }

        @Nonnull
        private static Color resolveDisplayColor(@Nonnull String itemId) {
            Item item = Item.getAssetMap().getAsset(itemId);
            if (item == null) {
                return java.awt.Color.WHITE;
            }

            ItemQuality quality = ItemQuality.getAssetMap().getAsset(item.getQualityIndex());
            if (quality == null || quality.getTextColor() == null) {
                return java.awt.Color.WHITE;
            }

            com.hypixel.hytale.protocol.Color textColor = quality.getTextColor();
            return new java.awt.Color(textColor.red & 0xFF, textColor.green & 0xFF, textColor.blue & 0xFF);
        }

        private static boolean isTradeValid(@Nonnull BarterTrade trade) {
            if (!ItemModule.exists(trade.getOutput().getItemId())) {
                return false;
            }

            for (BarterItemStack input : trade.getInput()) {
                if (!ItemModule.exists(input.getItemId())) {
                    return false;
                }
            }
            return true;
        }

        @Nonnull
        private static String resolveBaseDisplayName(@Nonnull String itemId) {
            return switch (itemId) {
                case "UnstableRifts_Ammo_Item" -> "Gun Ammo";
                case "UnstableRifts_Shop_Crate_T1" -> "Standard Weapon Crate";
                case "UnstableRifts_Shop_Crate_T2" -> "Advanced Weapon Crate";
                case "UnstableRifts_Shop_Crate_T3" -> "Elite Weapon Crate";
                default -> {
                    Item item = Item.getAssetMap().getAsset(itemId);
                    if (item != null) {
                        yield itemId.replace("UnstableRifts_", "").replace('_', ' ');
                    }
                    yield itemId;
                }
            };
        }
    }

    public static final class ShopEventData {
        static final String KEY_ACTION = "Action";
        static final String KEY_INDEX = "Index";

        static final BuilderCodec<ShopEventData> CODEC = BuilderCodec.builder(
                        ShopEventData.class, ShopEventData::new)
                .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING),
                        (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>(KEY_INDEX, Codec.STRING),
                        (d, v) -> d.index = v, d -> d.index).add()
                .build();

        @Nullable
        private String action;
        @Nullable
        private String index;
    }
}