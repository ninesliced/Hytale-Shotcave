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
    private static final String GROUP_TEMPLATE = "Pages/UnstableRifts/RiftMerchantShopGroup.ui";
    private static final String OPTION_TEMPLATE = "Pages/UnstableRifts/RiftMerchantShopOption.ui";
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

        List<MerchantOffer> offers = getOffers(store);
        List<MerchantGroupView> groups = buildGroupViews(offers);

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            MerchantGroupView group = groups.get(groupIndex);
            String groupPath = ITEM_LIST_PATH + "[" + groupIndex + "]";
            String optionListPath = groupPath + " #MerchantOptionList";

            ui.append(ITEM_LIST_PATH, GROUP_TEMPLATE);
            ui.set(groupPath + " #MerchantGroupTitle.TextSpans", Message.raw(group.title()).color(group.accentColor()));
            ui.set(groupPath + " #MerchantGroupDesc.TextSpans", Message.raw(group.description()));

            boolean hasMeta = !group.meta().isBlank();
            ui.set(groupPath + " #MerchantGroupMeta.Visible", hasMeta);
            if (hasMeta) {
                ui.set(groupPath + " #MerchantGroupMeta.TextSpans", Message.raw(group.meta()).color(group.accentColor()));
            }

            String iconPath = ItemIconResolver.resolveIconPath(group.iconItemId());
            if (iconPath != null) {
                ui.set(groupPath + " #MerchantGroupIcon.AssetPath", iconPath);
                ui.set(groupPath + " #MerchantGroupIcon.Visible", true);
            } else {
                ui.set(groupPath + " #MerchantGroupIcon.Visible", false);
            }

            for (int optionIndex = 0; optionIndex < group.options().size(); optionIndex++) {
                MerchantOptionView option = group.options().get(optionIndex);
                String optionPath = optionListPath + "[" + optionIndex + "]";

                ui.append(optionListPath, OPTION_TEMPLATE);
                ui.set(optionPath + " #MerchantOptionName.TextSpans", Message.raw(option.title()).color(option.titleColor()));
                ui.set(optionPath + " #MerchantOptionPrice.TextSpans", Message.raw(option.priceLabel()));
                ui.set(optionPath + " #MerchantOptionDesc.TextSpans", Message.raw(option.description()));
                ui.set(optionPath + " #MerchantOptionBuyBtn.Visible", option.buyVisible());
                ui.set(optionPath + " #MerchantOptionLockedBtn.Visible", option.lockedVisible());
                ui.set(optionPath + " #MerchantOptionLockedBtn.Text", option.lockedLabel());

                if (option.buyVisible()) {
                    events.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            optionPath + " #MerchantOptionBuyBtn",
                            new EventData()
                                    .put(ShopEventData.KEY_ACTION, "BUY")
                                    .put(ShopEventData.KEY_INDEX, String.valueOf(option.purchaseIndex())),
                            false
                    );
                }
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
    private List<MerchantGroupView> buildGroupViews(@Nonnull List<MerchantOffer> offers) {
        List<IndexedOffer> supplies = new ArrayList<>();
        List<IndexedOffer> crates = new ArrayList<>();
        List<IndexedOffer> baseSingles = new ArrayList<>();
        List<IndexedOffer> forkliftTrophies = new ArrayList<>();
        List<IndexedOffer> excavatorTrophies = new ArrayList<>();
        List<IndexedOffer> ceoTrophies = new ArrayList<>();

        for (int offerIndex = 0; offerIndex < offers.size(); offerIndex++) {
            MerchantOffer offer = offers.get(offerIndex);
            IndexedOffer indexedOffer = new IndexedOffer(offerIndex, offer);

            if (offer.kind() == OfferKind.BASE) {
                switch (offer.outputItemId()) {
                    case "UnstableRifts_Ammo_Item" -> supplies.add(indexedOffer);
                    case "UnstableRifts_Shop_Crate_T1", "UnstableRifts_Shop_Crate_T2", "UnstableRifts_Shop_Crate_T3" -> crates.add(indexedOffer);
                    default -> baseSingles.add(indexedOffer);
                }
                continue;
            }

            if (offer.trophyDefinition() == null) {
                continue;
            }

            switch (offer.trophyDefinition().boss()) {
                case FORKLIFT -> forkliftTrophies.add(indexedOffer);
                case EXCAVATOR -> excavatorTrophies.add(indexedOffer);
                case CEO -> ceoTrophies.add(indexedOffer);
            }
        }

        List<MerchantGroupView> groups = new ArrayList<>();
        if (!supplies.isEmpty()) {
            groups.add(buildBaseGroup(
                    "FIELD SUPPLIES",
                    "Quick utility purchases for the next run.",
                    "Always available",
                    supplies,
                    false
            ));
        }

        if (!crates.isEmpty()) {
            groups.add(buildBaseGroup(
                    "WEAPON CRATES",
                    "Choose the exact crate tier you want instead of waiting for rotation.",
                    "All tiers stay available",
                    crates,
                    true
            ));
        }

        for (IndexedOffer offer : baseSingles) {
            groups.add(buildBaseSingleGroup(offer));
        }

        if (!forkliftTrophies.isEmpty()) {
            groups.add(buildTrophyGroup("FORKLIFT TROPHY", forkliftTrophies));
        }
        if (!excavatorTrophies.isEmpty()) {
            groups.add(buildTrophyGroup("EXCAVATOR TROPHY", excavatorTrophies));
        }
        if (!ceoTrophies.isEmpty()) {
            groups.add(buildTrophyGroup("CEO TROPHY", ceoTrophies));
        }
        return groups;
    }

    @Nonnull
    private MerchantGroupView buildBaseGroup(@Nonnull String title,
                                             @Nonnull String description,
                                             @Nonnull String meta,
                                             @Nonnull List<IndexedOffer> offers,
                                             boolean sortByPrice) {
        List<IndexedOffer> sortedOffers = new ArrayList<>(offers);
        if (sortByPrice) {
            sortedOffers.sort((left, right) -> Integer.compare(left.offer().price(), right.offer().price()));
        }

        List<MerchantOptionView> options = new ArrayList<>(sortedOffers.size());
        for (IndexedOffer offer : sortedOffers) {
            options.add(buildBaseOption(offer, sortByPrice));
        }

        return new MerchantGroupView(
                title,
                description,
                meta,
                resolveGroupIconItem(sortedOffers),
                resolveGroupAccent(sortedOffers),
                options
        );
    }

    @Nonnull
    private MerchantGroupView buildBaseSingleGroup(@Nonnull IndexedOffer indexedOffer) {
        MerchantOffer offer = indexedOffer.offer();
        List<IndexedOffer> offers = List.of(indexedOffer);
        List<MerchantOptionView> options = List.of(buildBaseOption(indexedOffer, false));
        return new MerchantGroupView(
                offer.displayName().toUpperCase(),
                getBaseOfferDescription(offer.outputItemId()),
                "Merchant stock",
                offer.outputItemId(),
                offer.displayColor(),
                options
        );
    }

    @Nonnull
    private MerchantGroupView buildTrophyGroup(@Nonnull String title,
                                               @Nonnull List<IndexedOffer> offers) {
        List<IndexedOffer> sortedOffers = new ArrayList<>(offers);
        sortedOffers.sort((left, right) -> Integer.compare(
                left.offer().trophyDefinition().sortOrder(),
                right.offer().trophyDefinition().sortOrder()
        ));

        List<MerchantOptionView> options = new ArrayList<>(sortedOffers.size());
        for (IndexedOffer offer : sortedOffers) {
            options.add(buildTrophyOption(offer));
        }

        int clears = sortedOffers.get(0).offer().completionCount();
        return new MerchantGroupView(
                title,
                "Choose the rarity you want to place in your base.",
                "Boss clears: " + clears,
                resolveGroupIconItem(sortedOffers),
                resolveGroupAccent(sortedOffers),
                options
        );
    }

    @Nonnull
    private MerchantOptionView buildBaseOption(@Nonnull IndexedOffer indexedOffer,
                                               boolean compactCrateTitles) {
        MerchantOffer offer = indexedOffer.offer();
        String description = offer.tradeValid()
                ? getBaseOfferDescription(offer.outputItemId())
                : "This offer references a missing item asset.";

        return new MerchantOptionView(
                indexedOffer.purchaseIndex(),
                compactCrateTitles ? getBaseVariantTitle(offer.outputItemId()) : offer.displayName(),
                buildOptionPriceLabel(offer),
                description,
                offer.displayColor(),
                offer.canPurchase(),
                !offer.canPurchase(),
                offer.tradeValid() ? "LOCKED" : "INVALID"
        );
    }

    @Nonnull
    private MerchantOptionView buildTrophyOption(@Nonnull IndexedOffer indexedOffer) {
        MerchantOffer offer = indexedOffer.offer();
        RiftMerchantTrophies.TrophyDefinition trophy = offer.trophyDefinition();
        String description;
        if (!offer.tradeValid()) {
            description = "This trophy references a missing item asset.";
        } else if (offer.isLocked()) {
            description = "Unlocks at " + trophy.requiredCompletions() + " clears. Progress: "
                    + offer.completionCount() + " / " + trophy.requiredCompletions() + ".";
        } else {
            description = "Unlocked at " + trophy.requiredCompletions() + " clears. Placeable boss trophy decoration.";
        }

        return new MerchantOptionView(
                indexedOffer.purchaseIndex(),
                trophy.rarity().itemSuffix(),
                buildOptionPriceLabel(offer),
                description,
                offer.displayColor(),
                offer.canPurchase(),
                !offer.canPurchase(),
                offer.tradeValid() ? "LOCKED" : "INVALID"
        );
    }

    @Nonnull
    private String buildOptionPriceLabel(@Nonnull MerchantOffer offer) {
        return offer.tradeValid() ? offer.price() + " coins" : "INVALID";
    }

    @Nonnull
    private String getBaseVariantTitle(@Nonnull String itemId) {
        return switch (itemId) {
            case "UnstableRifts_Ammo_Item" -> "Ammo Refill";
            case "UnstableRifts_Shop_Crate_T1" -> "Standard";
            case "UnstableRifts_Shop_Crate_T2" -> "Advanced";
            case "UnstableRifts_Shop_Crate_T3" -> "Elite";
            default -> MerchantOffer.resolveBaseDisplayName(itemId);
        };
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

    @Nonnull
    private String resolveGroupIconItem(@Nonnull List<IndexedOffer> offers) {
        for (int index = offers.size() - 1; index >= 0; index--) {
            MerchantOffer offer = offers.get(index).offer();
            if (offer.tradeValid() && !offer.isLocked()) {
                return offer.outputItemId();
            }
        }
        for (IndexedOffer offer : offers) {
            if (offer.offer().tradeValid()) {
                return offer.offer().outputItemId();
            }
        }
        return offers.get(0).offer().outputItemId();
    }

    @Nonnull
    private Color resolveGroupAccent(@Nonnull List<IndexedOffer> offers) {
        for (int index = offers.size() - 1; index >= 0; index--) {
            MerchantOffer offer = offers.get(index).offer();
            if (offer.tradeValid() && !offer.isLocked()) {
                return offer.displayColor();
            }
        }
        for (IndexedOffer offer : offers) {
            if (offer.offer().tradeValid()) {
                return offer.offer().displayColor();
            }
        }
        return java.awt.Color.WHITE;
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

    private record IndexedOffer(int purchaseIndex,
                                @Nonnull MerchantOffer offer) {
    }

    private record MerchantGroupView(@Nonnull String title,
                                     @Nonnull String description,
                                     @Nonnull String meta,
                                     @Nonnull String iconItemId,
                                     @Nonnull Color accentColor,
                                     @Nonnull List<MerchantOptionView> options) {
    }

    private record MerchantOptionView(int purchaseIndex,
                                      @Nonnull String title,
                                      @Nonnull String priceLabel,
                                      @Nonnull String description,
                                      @Nonnull Color titleColor,
                                      boolean buyVisible,
                                      boolean lockedVisible,
                                      @Nonnull String lockedLabel) {
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