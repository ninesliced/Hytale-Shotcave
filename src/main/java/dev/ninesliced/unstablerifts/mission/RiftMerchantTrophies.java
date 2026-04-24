package dev.ninesliced.unstablerifts.mission;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared catalog and progression rules for Rift Merchant trophy unlocks.
 */
public final class RiftMerchantTrophies {

    public static final String COIN_ITEM_ID = "UnstableRifts_Rift_Coin";
    public static final int TROPHY_RESTOCK_HOUR = 7;

    @Nonnull
    private static final List<TrophyDefinition> DEFINITIONS;

    static {
        List<TrophyDefinition> definitions = new ArrayList<>();
        for (BossTrophySet boss : BossTrophySet.values()) {
            for (TrophyRarity rarity : TrophyRarity.values()) {
                definitions.add(new TrophyDefinition(
                        boss,
                        rarity,
                        "UnstableRifts_" + boss.itemPrefix() + "_Trophy_" + rarity.itemSuffix()
                ));
            }
        }
        DEFINITIONS = Collections.unmodifiableList(definitions);
    }

    private RiftMerchantTrophies() {
    }

    @Nonnull
    public static List<TrophyDefinition> definitions() {
        return DEFINITIONS;
    }

    @Nullable
    public static BossTrophySet bossForLevelIndex(int levelIndex) {
        for (BossTrophySet boss : BossTrophySet.values()) {
            if (boss.levelIndex() == levelIndex) {
                return boss;
            }
        }
        return null;
    }

    public static long resolveDailyCycle(@Nonnull Instant gameTime) {
        long daysSinceEpoch = Duration.between(WorldTimeResource.ZERO_YEAR, gameTime).toDays();
        if (gameTime.atZone(ZoneOffset.UTC).getHour() < TROPHY_RESTOCK_HOUR) {
            daysSinceEpoch -= 1L;
        }
        return Math.max(daysSinceEpoch, 0L);
    }

    public enum TrophyRarity {
        COPPER("Copper", "Common", 1, 20, 0),
        SILVER("Silver", "Uncommon", 3, 40, 1),
        GOLD("Gold", "Rare", 7, 80, 2),
        MYTHRIL("Mythril", "Legendary", 15, 160, 3);

        private final String itemSuffix;
        private final String quality;
        private final int requiredCompletions;
        private final int price;
        private final int sortOrder;

        TrophyRarity(@Nonnull String itemSuffix,
                     @Nonnull String quality,
                     int requiredCompletions,
                     int price,
                     int sortOrder) {
            this.itemSuffix = itemSuffix;
            this.quality = quality;
            this.requiredCompletions = requiredCompletions;
            this.price = price;
            this.sortOrder = sortOrder;
        }

        @Nonnull
        public String itemSuffix() {
            return itemSuffix;
        }

        @Nonnull
        public String quality() {
            return quality;
        }

        public int requiredCompletions() {
            return requiredCompletions;
        }

        public int price() {
            return price;
        }

        public int sortOrder() {
            return sortOrder;
        }
    }

    public enum BossTrophySet {
        FORKLIFT(0, "Boss_Forklift", "Forklift", "Forklift"),
        EXCAVATOR(1, "Boss_Excavator", "Excavator", "Excavator"),
        CEO(2, "Boss_CEO_Tank", "CEO", "CEO");

        private final int levelIndex;
        private final String bossKey;
        private final String displayName;
        private final String itemPrefix;

        BossTrophySet(int levelIndex,
                      @Nonnull String bossKey,
                      @Nonnull String displayName,
                      @Nonnull String itemPrefix) {
            this.levelIndex = levelIndex;
            this.bossKey = bossKey;
            this.displayName = displayName;
            this.itemPrefix = itemPrefix;
        }

        public int levelIndex() {
            return levelIndex;
        }

        @Nonnull
        public String bossKey() {
            return bossKey;
        }

        @Nonnull
        public String displayName() {
            return displayName;
        }

        @Nonnull
        public String itemPrefix() {
            return itemPrefix;
        }
    }

    public record TrophyDefinition(@Nonnull BossTrophySet boss,
                                   @Nonnull TrophyRarity rarity,
                                   @Nonnull String itemId) {

        @Nonnull
        public String displayName() {
            return rarity.itemSuffix() + " " + boss.displayName() + " Trophy";
        }

        @Nonnull
        public String dropListId() {
            return "Drop_" + itemId;
        }

        public int requiredCompletions() {
            return rarity.requiredCompletions();
        }

        public int price() {
            return rarity.price();
        }

        public int sortOrder() {
            return boss.levelIndex() * 10 + rarity.sortOrder();
        }
    }
}