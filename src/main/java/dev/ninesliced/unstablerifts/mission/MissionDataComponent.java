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

    @Nonnull
    public List<ActiveQuest> getActiveQuests() { return activeQuests; }

    // ── Mutators ──

    public void addEnemiesKilled(int count) { this.totalEnemiesKilled += count; }
    public void addBossesKilled(int count) { this.totalBossesKilled += count; }
    public void addDungeonsCompleted(int count) { this.totalDungeonsCompleted += count; }
    public void addDungeonDeaths(int count) { this.totalDungeonDeaths += count; }

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
        this.activeQuests.clear();
    }

    public int getProgress(@Nonnull MissionType type) {
        return switch (type) {
            case KILL_ENEMIES -> totalEnemiesKilled;
            case KILL_BOSSES -> totalBossesKilled;
            case COMPLETE_DUNGEONS -> totalDungeonsCompleted;
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
