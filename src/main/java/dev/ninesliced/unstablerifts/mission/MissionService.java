package dev.ninesliced.unstablerifts.mission;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the quest pool, per-player random quest assignment, claim, and skip.
 * <p>
 * Instead of a static list of missions, each player has 3 active quests randomly
 * picked from a pool. Progress is tracked relative to the player's counters at
 * the time the quest was assigned (baseline). When a quest is claimed or skipped,
 * it is replaced with a new random quest.
 * </p>
 */
public final class MissionService {

    private static final Logger LOGGER = Logger.getLogger(MissionService.class.getName());
    private static final String CONFIG_FILE = "missions.json";
    private static final String COIN_ITEM_ID = "UnstableRifts_Rift_Coin";
    private static final Gson GSON = new Gson();

    private final List<MissionDefinition> questPool;
    private final int activeQuestCount;
    private final int skipCostCoins;

    public MissionService() {
        QuestConfig cfg = loadConfig();
        this.questPool = cfg.definitions;
        this.activeQuestCount = cfg.activeQuestCount;
        this.skipCostCoins = cfg.skipCostCoins;
        LOGGER.info("Loaded " + questPool.size() + " quest pool entries (active=" + activeQuestCount
                + ", skipCost=" + skipCostCoins + ")");
    }

    @Nonnull
    public List<MissionDefinition> getQuestPool() {
        return questPool;
    }

    public int getSkipCostCoins() {
        return skipCostCoins;
    }

    // ── Quest initialization ──────────────────────────────────────

    /**
     * Ensures a player has the correct number of active quests. Called when
     * the merchant page is opened or after a dungeon run. If the player has
     * fewer active quests than required, random ones are assigned.
     */
    public void ensureActiveQuests(@Nonnull Store<EntityStore> store,
                                   @Nonnull Ref<EntityStore> ref) {
        MissionDataComponent data = store.ensureAndGetComponent(ref, MissionDataComponent.getComponentType());
        if (data == null || questPool.isEmpty()) return;

        List<MissionDataComponent.ActiveQuest> current = data.getActiveQuests();
        if (current.size() >= activeQuestCount) return;

        List<MissionDataComponent.ActiveQuest> quests = new ArrayList<>(current);
        while (quests.size() < activeQuestCount) {
            MissionDefinition def = pickRandomQuest();
            int baseline = data.getProgress(def.type());
            quests.add(new MissionDataComponent.ActiveQuest(def.id(), baseline));
        }
        data.setActiveQuests(quests);
    }

    // ── Progress update ──────────────────────────────────────────

    /**
     * Increments a single progress counter for an online player.
     * <p>
     * Intended for mid-run events (room cleared, shop purchase, coin pickup,
     * armor ability activated). Silently no-ops if the player's entity /
     * mission component is unavailable (e.g. during login/logout or before
     * the component has been ensured for the first time).
     * </p>
     */
    public void addProgress(@Nonnull PlayerRef playerRef, @Nonnull MissionType type, int amount) {
        if (amount <= 0) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;
        MissionDataComponent data = store.getComponent(ref, MissionDataComponent.getComponentType());
        if (data == null) return;
        data.addProgress(type, amount);
    }

    /**
     * Updates a player's lifetime progress counters after a dungeon run.
     */
    public void updateProgress(@Nonnull PlayerRef playerRef,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               int enemyKills,
                               int bossKills,
                               boolean completed) {
        MissionDataComponent data = store.ensureAndGetComponent(ref, MissionDataComponent.getComponentType());
        if (data == null) return;

        if (enemyKills > 0) data.addEnemiesKilled(enemyKills);
        if (bossKills > 0) data.addBossesKilled(bossKills);
        if (completed) data.addDungeonsCompleted(1);
        if (!completed) data.addDungeonDeaths(1);

        ensureActiveQuests(store, ref);

        // Notify about completable quests
        List<String> completable = new ArrayList<>();
        for (MissionDataComponent.ActiveQuest aq : data.getActiveQuests()) {
            MissionDefinition def = findQuest(aq.questId);
            if (def != null) {
                int questProgress = data.getProgress(def.type()) - aq.baseline;
                if (questProgress >= def.target()) {
                    completable.add(def.displayName());
                }
            }
        }

        if (!completable.isEmpty()) {
            try {
                String msg = "Quest" + (completable.size() > 1 ? "s" : "")
                        + " ready to claim: " + String.join(", ", completable);
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        Message.raw(msg),
                        null,
                        "quest_complete");
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to send quest notification", e);
            }
        }
    }

    // ── Claim quest ──────────────────────────────────────────────

    /**
     * Claims the reward for an active quest at the given slot index.
     *
     * @return true if claimed, false if not complete or invalid slot
     */
    public boolean claimQuest(int slotIndex,
                              @Nonnull PlayerRef playerRef,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref) {
        MissionDataComponent data = store.ensureAndGetComponent(ref, MissionDataComponent.getComponentType());
        if (data == null) return false;

        List<MissionDataComponent.ActiveQuest> quests = data.getActiveQuests();
        if (slotIndex < 0 || slotIndex >= quests.size()) return false;

        MissionDataComponent.ActiveQuest aq = quests.get(slotIndex);
        MissionDefinition def = findQuest(aq.questId);
        if (def == null) return false;

        int questProgress = data.getProgress(def.type()) - aq.baseline;
        if (questProgress < def.target()) return false;

        // Award coins
        if (def.rewardCoins() > 0) {
            CombinedItemContainer combined = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
            if (combined != null) {
                ItemStack coinStack = new ItemStack(COIN_ITEM_ID, def.rewardCoins());
                SimpleItemContainer.addOrDropItemStacks(store, ref, combined, List.of(coinStack));
            }

            try {
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        Message.raw("Quest Complete: " + def.displayName()
                                + " — +" + def.rewardCoins() + " Rift Coin"
                                + (def.rewardCoins() != 1 ? "s" : "")),
                        null,
                        "quest_claimed");
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to send claim notification", e);
            }
        }

        // Replace with a new random quest
        MissionDefinition newDef = pickRandomQuest();
        int newBaseline = data.getProgress(newDef.type());
        data.replaceQuest(slotIndex, newDef.id(), newBaseline);

        return true;
    }

    // ── Skip quest ───────────────────────────────────────────────

    /**
     * Skips an active quest at the given slot index, consuming coins from the
     * player's inventory.
     *
     * @return true if skipped, false if insufficient coins or invalid
     */
    public boolean skipQuest(int slotIndex,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull Ref<EntityStore> ref) {
        MissionDataComponent data = store.ensureAndGetComponent(ref, MissionDataComponent.getComponentType());
        if (data == null) return false;

        List<MissionDataComponent.ActiveQuest> quests = data.getActiveQuests();
        if (slotIndex < 0 || slotIndex >= quests.size()) return false;

        // Check and consume coins
        CombinedItemContainer combined = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        if (combined == null) return false;

        int coinCount = combined.countItemStacks(stack -> COIN_ITEM_ID.equals(stack.getItemId()));
        if (coinCount < skipCostCoins) {
            try {
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        Message.raw("Not enough Rift Coins! Need " + skipCostCoins + " to skip."),
                        null,
                        "quest_skip_fail");
            } catch (Exception ignored) {
            }
            return false;
        }

        combined.removeItemStack(new ItemStack(COIN_ITEM_ID, skipCostCoins));

        // Replace with a new random quest
        MissionDefinition newDef = pickRandomQuest();
        int newBaseline = data.getProgress(newDef.type());
        data.replaceQuest(slotIndex, newDef.id(), newBaseline);

        try {
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(),
                    Message.raw("Quest skipped for " + skipCostCoins + " Rift Coins."),
                    null,
                    "quest_skipped");
        } catch (Exception ignored) {
        }

        return true;
    }

    // ── Quest status for UI ──────────────────────────────────────

    /**
     * Returns a snapshot of the player's active quests with computed progress.
     */
    @Nonnull
    public List<QuestStatus> getActiveQuestStatuses(@Nonnull Store<EntityStore> store,
                                                    @Nonnull Ref<EntityStore> ref) {
        MissionDataComponent data = store.ensureAndGetComponent(ref, MissionDataComponent.getComponentType());
        if (data == null) return List.of();

        ensureActiveQuests(store, ref);

        List<QuestStatus> statuses = new ArrayList<>();
        List<MissionDataComponent.ActiveQuest> quests = data.getActiveQuests();
        for (int i = 0; i < quests.size(); i++) {
            MissionDataComponent.ActiveQuest aq = quests.get(i);
            MissionDefinition def = findQuest(aq.questId);
            if (def == null) continue;
            int questProgress = Math.max(0, data.getProgress(def.type()) - aq.baseline);
            statuses.add(new QuestStatus(i, def, questProgress));
        }
        return statuses;
    }

    // ── Internal ─────────────────────────────────────────────────

    @Nonnull
    private MissionDefinition pickRandomQuest() {
        return questPool.get(ThreadLocalRandom.current().nextInt(questPool.size()));
    }

    @Nullable
    private MissionDefinition findQuest(@Nonnull String id) {
        for (MissionDefinition m : questPool) {
            if (m.id().equals(id)) return m;
        }
        return null;
    }

    @Nonnull
    private static QuestConfig loadConfig() {
        try (var is = MissionService.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                LOGGER.warning("Missing resource: " + CONFIG_FILE);
                return new QuestConfig();
            }
            try (var reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                QuestConfigJson root = GSON.fromJson(reader, QuestConfigJson.class);
                if (root == null) return new QuestConfig();

                List<MissionDefinition> defs = new ArrayList<>();
                if (root.questPool != null) {
                    for (MissionJson mj : root.questPool) {
                        MissionType type;
                        try {
                            type = MissionType.valueOf(mj.type);
                        } catch (IllegalArgumentException e) {
                            LOGGER.warning("Unknown mission type: " + mj.type + " in quest " + mj.id);
                            continue;
                        }
                        defs.add(new MissionDefinition(mj.id, type, mj.target, mj.rewardCoins,
                                mj.displayName, mj.description));
                    }
                }

                QuestConfig cfg = new QuestConfig();
                cfg.definitions = Collections.unmodifiableList(defs);
                cfg.activeQuestCount = root.activeQuestCount > 0 ? root.activeQuestCount : 3;
                cfg.skipCostCoins = root.skipCostCoins > 0 ? root.skipCostCoins : 10;
                return cfg;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load " + CONFIG_FILE, e);
            return new QuestConfig();
        }
    }

    // ── JSON data classes ──

    private static final class QuestConfig {
        List<MissionDefinition> definitions = List.of();
        int activeQuestCount = 3;
        int skipCostCoins = 10;
    }

    private static final class QuestConfigJson {
        List<MissionJson> questPool;
        int activeQuestCount;
        int skipCostCoins;
    }

    private static final class MissionJson {
        String id;
        String type;
        int target;
        int rewardCoins;
        String displayName;
        String description;
    }

    /**
     * Snapshot of an active quest's status for UI rendering.
     */
    public record QuestStatus(
            int slotIndex,
            @Nonnull MissionDefinition definition,
            int currentProgress) {

        public boolean isComplete() {
            return currentProgress >= definition.target();
        }
    }
}
