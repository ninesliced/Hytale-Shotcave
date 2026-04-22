package dev.ninesliced.unstablerifts.mission;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent ECS component storing a player's lifetime progress counters and
 * their 3 active random quests.
 * <p>
 * Each active quest is stored as "{questId}:{baselineProgress}" so we can
 * compute quest-specific progress as (currentTotal - baseline).
 * </p>
 */
public final class MissionDataComponent implements Component<EntityStore> {

    /** Max number of active quests shown at once. */
    public static final int ACTIVE_QUEST_COUNT = 3;

    @Nonnull
    public static final BuilderCodec<MissionDataComponent> CODEC;

    static {
        BuilderCodec.Builder<MissionDataComponent> b = BuilderCodec.builder(
                MissionDataComponent.class, MissionDataComponent::new);

        b.append(new KeyedCodec<>("TotalEnemiesKilled", Codec.INTEGER),
                (c, v) -> c.totalEnemiesKilled = v,
                c -> c.totalEnemiesKilled).add();

        b.append(new KeyedCodec<>("TotalBossesKilled", Codec.INTEGER),
                (c, v) -> c.totalBossesKilled = v,
                c -> c.totalBossesKilled).add();

        b.append(new KeyedCodec<>("TotalDungeonsCompleted", Codec.INTEGER),
                (c, v) -> c.totalDungeonsCompleted = v,
                c -> c.totalDungeonsCompleted).add();

        b.append(new KeyedCodec<>("TotalDungeonDeaths", Codec.INTEGER),
                (c, v) -> c.totalDungeonDeaths = v,
                c -> c.totalDungeonDeaths).add();

        b.append(new KeyedCodec<>("TotalChallengeRoomsCleared", Codec.INTEGER),
                (c, v) -> c.totalChallengeRoomsCleared = v,
                c -> c.totalChallengeRoomsCleared).add();

        b.append(new KeyedCodec<>("TotalKeyRoomsCleared", Codec.INTEGER),
                (c, v) -> c.totalKeyRoomsCleared = v,
                c -> c.totalKeyRoomsCleared).add();

        b.append(new KeyedCodec<>("TotalAltarRoomsCleared", Codec.INTEGER),
                (c, v) -> c.totalAltarRoomsCleared = v,
                c -> c.totalAltarRoomsCleared).add();

        b.append(new KeyedCodec<>("TotalShopPurchases", Codec.INTEGER),
                (c, v) -> c.totalShopPurchases = v,
                c -> c.totalShopPurchases).add();

        b.append(new KeyedCodec<>("TotalDungeonCoins", Codec.INTEGER),
                (c, v) -> c.totalDungeonCoins = v,
                c -> c.totalDungeonCoins).add();

        b.append(new KeyedCodec<>("TotalArmorAbilitiesUsed", Codec.INTEGER),
                (c, v) -> c.totalArmorAbilitiesUsed = v,
                c -> c.totalArmorAbilitiesUsed).add();

        // Active quests stored as comma-separated "questId:baseline" entries
        b.append(new KeyedCodec<>("ActiveQuests", Codec.STRING),
                (c, v) -> c.activeQuests = parseActiveQuests(v),
                c -> serializeActiveQuests(c.activeQuests)).add();

        CODEC = b.build();
    }

    private static ComponentType<EntityStore, MissionDataComponent> componentType;

    private int totalEnemiesKilled;
    private int totalBossesKilled;
    private int totalDungeonsCompleted;
    private int totalDungeonDeaths;
    private int totalChallengeRoomsCleared;
    private int totalKeyRoomsCleared;
    private int totalAltarRoomsCleared;
    private int totalShopPurchases;
    private int totalDungeonCoins;
    private int totalArmorAbilitiesUsed;

    /** Active quest slots. Each entry is a quest pool ID + baseline progress. */
    @Nonnull
    private List<ActiveQuest> activeQuests = new ArrayList<>();

    public MissionDataComponent() {
    }

    public MissionDataComponent(@Nonnull MissionDataComponent other) {
        this.totalEnemiesKilled = other.totalEnemiesKilled;
        this.totalBossesKilled = other.totalBossesKilled;
        this.totalDungeonsCompleted = other.totalDungeonsCompleted;
        this.totalDungeonDeaths = other.totalDungeonDeaths;
        this.totalChallengeRoomsCleared = other.totalChallengeRoomsCleared;
        this.totalKeyRoomsCleared = other.totalKeyRoomsCleared;
        this.totalAltarRoomsCleared = other.totalAltarRoomsCleared;
        this.totalShopPurchases = other.totalShopPurchases;
        this.totalDungeonCoins = other.totalDungeonCoins;
        this.totalArmorAbilitiesUsed = other.totalArmorAbilitiesUsed;
        this.activeQuests = new ArrayList<>();
        for (ActiveQuest aq : other.activeQuests) {
            this.activeQuests.add(new ActiveQuest(aq.questId, aq.baseline));
        }
    }

    @Nonnull
    public static ComponentType<EntityStore, MissionDataComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("MissionDataComponent has not been registered yet");
        }
        return componentType;
    }

    public static void setComponentType(@Nonnull ComponentType<EntityStore, MissionDataComponent> type) {
        componentType = type;
    }

    // ── Getters ──

    public int getTotalEnemiesKilled() { return totalEnemiesKilled; }
    public int getTotalBossesKilled() { return totalBossesKilled; }
    public int getTotalDungeonsCompleted() { return totalDungeonsCompleted; }
    public int getTotalDungeonDeaths() { return totalDungeonDeaths; }
    public int getTotalChallengeRoomsCleared() { return totalChallengeRoomsCleared; }
    public int getTotalKeyRoomsCleared() { return totalKeyRoomsCleared; }
    public int getTotalAltarRoomsCleared() { return totalAltarRoomsCleared; }
    public int getTotalShopPurchases() { return totalShopPurchases; }
    public int getTotalDungeonCoins() { return totalDungeonCoins; }
    public int getTotalArmorAbilitiesUsed() { return totalArmorAbilitiesUsed; }

    @Nonnull
    public List<ActiveQuest> getActiveQuests() { return activeQuests; }

    // ── Mutators ──

    public void addEnemiesKilled(int count) { this.totalEnemiesKilled += count; }
    public void addBossesKilled(int count) { this.totalBossesKilled += count; }
    public void addDungeonsCompleted(int count) { this.totalDungeonsCompleted += count; }
    public void addDungeonDeaths(int count) { this.totalDungeonDeaths += count; }
    public void addChallengeRoomsCleared(int count) { this.totalChallengeRoomsCleared += count; }
    public void addKeyRoomsCleared(int count) { this.totalKeyRoomsCleared += count; }
    public void addAltarRoomsCleared(int count) { this.totalAltarRoomsCleared += count; }
    public void addShopPurchases(int count) { this.totalShopPurchases += count; }
    public void addDungeonCoins(int count) { this.totalDungeonCoins += count; }
    public void addArmorAbilitiesUsed(int count) { this.totalArmorAbilitiesUsed += count; }

    /**
     * Generic progress increment dispatched by mission type.
     */
    public void addProgress(@Nonnull MissionType type, int count) {
        if (count <= 0) return;
        switch (type) {
            case KILL_ENEMIES -> totalEnemiesKilled += count;
            case KILL_BOSSES -> totalBossesKilled += count;
            case COMPLETE_DUNGEONS -> totalDungeonsCompleted += count;
            case COMPLETE_CHALLENGE_ROOMS -> totalChallengeRoomsCleared += count;
            case COMPLETE_KEY_ROOMS -> totalKeyRoomsCleared += count;
            case COMPLETE_ALTAR_ROOMS -> totalAltarRoomsCleared += count;
            case SHOP_PURCHASES -> totalShopPurchases += count;
            case COLLECT_DUNGEON_COINS -> totalDungeonCoins += count;
            case ACTIVATE_ARMOR_ABILITIES -> totalArmorAbilitiesUsed += count;
        }
    }

    public void setActiveQuests(@Nonnull List<ActiveQuest> quests) {
        this.activeQuests = new ArrayList<>(quests);
    }

    /**
     * Replaces the quest at the given slot index with a new quest + baseline.
     */
    public void replaceQuest(int slotIndex, @Nonnull String questId, int baseline) {
        if (slotIndex >= 0 && slotIndex < activeQuests.size()) {
            activeQuests.set(slotIndex, new ActiveQuest(questId, baseline));
        }
    }

    public void reset() {
        this.totalEnemiesKilled = 0;
        this.totalBossesKilled = 0;
        this.totalDungeonsCompleted = 0;
        this.totalDungeonDeaths = 0;
        this.totalChallengeRoomsCleared = 0;
        this.totalKeyRoomsCleared = 0;
        this.totalAltarRoomsCleared = 0;
        this.totalShopPurchases = 0;
        this.totalDungeonCoins = 0;
        this.totalArmorAbilitiesUsed = 0;
        this.activeQuests.clear();
    }

    public int getProgress(@Nonnull MissionType type) {
        return switch (type) {
            case KILL_ENEMIES -> totalEnemiesKilled;
            case KILL_BOSSES -> totalBossesKilled;
            case COMPLETE_DUNGEONS -> totalDungeonsCompleted;
            case COMPLETE_CHALLENGE_ROOMS -> totalChallengeRoomsCleared;
            case COMPLETE_KEY_ROOMS -> totalKeyRoomsCleared;
            case COMPLETE_ALTAR_ROOMS -> totalAltarRoomsCleared;
            case SHOP_PURCHASES -> totalShopPurchases;
            case COLLECT_DUNGEON_COINS -> totalDungeonCoins;
            case ACTIVATE_ARMOR_ABILITIES -> totalArmorAbilitiesUsed;
        };
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new MissionDataComponent(this);
    }

    // ── Active Quest Record ──

    public static final class ActiveQuest {
        @Nonnull
        public final String questId;
        public final int baseline;

        public ActiveQuest(@Nonnull String questId, int baseline) {
            this.questId = questId;
            this.baseline = baseline;
        }
    }

    // ── Serialization helpers ──

    @Nonnull
    private static List<ActiveQuest> parseActiveQuests(@Nonnull String raw) {
        List<ActiveQuest> result = new ArrayList<>();
        if (raw.isEmpty()) return result;
        String[] parts = raw.split(",");
        for (String part : parts) {
            int sep = part.lastIndexOf(':');
            if (sep > 0 && sep < part.length() - 1) {
                String id = part.substring(0, sep);
                try {
                    int baseline = Integer.parseInt(part.substring(sep + 1));
                    result.add(new ActiveQuest(id, baseline));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    @Nonnull
    private static String serializeActiveQuests(@Nonnull List<ActiveQuest> quests) {
        if (quests.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < quests.size(); i++) {
            if (i > 0) sb.append(',');
            ActiveQuest aq = quests.get(i);
            sb.append(aq.questId).append(':').append(aq.baseline);
        }
        return sb.toString();
    }
}
