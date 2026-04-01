package dev.ninesliced.unstablerifts.dungeon;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;
import dev.ninesliced.unstablerifts.party.PartyManager;
import dev.ninesliced.unstablerifts.party.PartyUiPage;
import dev.ninesliced.unstablerifts.player.OnlinePlayers;
import dev.ninesliced.unstablerifts.player.PlayerEventNotifier;
import dev.ninesliced.unstablerifts.systems.DeathComponent;
import dev.ninesliced.unstablerifts.systems.DeathStateController;
import dev.ninesliced.unstablerifts.tooltip.ArmorVirtualItems;
import dev.ninesliced.unstablerifts.tooltip.WeaponVirtualItems;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static dev.ninesliced.unstablerifts.dungeon.RotationUtil.rotateLocalX;
import static dev.ninesliced.unstablerifts.dungeon.RotationUtil.rotateLocalZ;

/**
 * Orchestrates the dungeon game lifecycle: generation, start, boss, end, cleanup.
 */
public final class GameManager {

    private static final Logger LOGGER = Logger.getLogger(GameManager.class.getName());

    private final UnstableRifts plugin;
    private final PlayerInventoryService inventoryService;
    private final MobSpawningService mobSpawningService;
    private final ReviveMarkerService reviveMarkerService;
    private final PlayerStateService playerStateService;
    /**
     * Active games indexed by party ID.
     */
    private final Map<UUID, Game> activeGames = new ConcurrentHashMap<>();
    /**
     * Reverse lookup: player UUID → party ID for fast game resolution.
     */
    private final Map<UUID, UUID> playerToParty = new ConcurrentHashMap<>();
    /**
     * Players who have already been normalized after landing outside a dungeon world.
     * Prevents repeated inventory restores if multiple world/ready/tick hooks fire.
     */
    private final Set<UUID> outsideDungeonNormalizedPlayers = ConcurrentHashMap.newKeySet();
    /**
     * Players reconnecting with a saved inventory whose recovery must wait
     * until PlayerReady so the engine does not overwrite restored containers.
     */
    private final Set<UUID> pendingReadyRecoveryPlayers = ConcurrentHashMap.newKeySet();
    /**
     * Players currently in the process of being teleported back to a dungeon
     * after accepting the rejoin prompt. Prevents normalizeOutsideDungeonState from interfering.
     */
    private final Set<UUID> pendingReconnectPlayers = ConcurrentHashMap.newKeySet();
    /**
     * Players who need a full inventory resync after PlayerReadyEvent fires
     * in the dungeon world. This ensures virtual item definitions reach the
     * client after it has finished loading (post-ClientReady).
     */
    private final Set<UUID> pendingPostReadyResync = ConcurrentHashMap.newKeySet();
    /**
     * Players who have been shown the rejoin-dungeon popup and have not yet
     * responded. While in this set, normalization is deferred.
     */
    private final Set<UUID> pendingRejoinDecision = ConcurrentHashMap.newKeySet();

    public GameManager(@Nonnull UnstableRifts plugin) {
        this.plugin = plugin;
        this.inventoryService = new PlayerInventoryService(plugin);
        this.mobSpawningService = new MobSpawningService();
        this.reviveMarkerService = new ReviveMarkerService();
        this.playerStateService = new PlayerStateService();
    }

    // ────────────────────────────────────────────────
    //  Game lifecycle
    // ────────────────────────────────────────────────

    /**
     * Starts a new game for the given party.
     * Creates the instance and begins background generation.
     */
    @Nonnull
    public CompletableFuture<Game> startGame(@Nonnull UUID partyId,
                                             @Nonnull List<UUID> memberIds,
                                             @Nonnull Map<UUID, PlayerRef> memberRefs,
                                             @Nonnull Map<UUID, Ref<EntityStore>> memberEntities,
                                             @Nonnull Map<UUID, Store<EntityStore>> memberStores,
                                             @Nonnull World leaderWorld,
                                             @Nonnull Transform leaderReturnPoint) {
        if (activeGames.containsKey(partyId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A game is already active for this party."));
        }

        DungeonConfig config = plugin.loadDungeonConfig();
        if (config.getLevels().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No dungeon levels configured."));
        }

        Game game = new Game(partyId);
        activeGames.put(partyId, game);

        for (UUID memberId : memberIds) {
            playerToParty.put(memberId, partyId);
        }

        DungeonConfig.LevelConfig firstLevelConfig = config.getLevels().get(0);
        Level firstLevel = new Level(firstLevelConfig.getName(), 0);
        game.addLevel(firstLevel);
        game.setCurrentLevelSelector(firstLevelConfig.getSelector());

        CompletableFuture<World> worldFuture = plugin.getDungeonInstanceService().spawnGeneratedInstance(
                leaderWorld,
                leaderReturnPoint,
                firstLevelConfig,
                status -> broadcastToParty(partyId, status,
                        status.startsWith("Dungeon ready") ? DungeonConstants.COLOR_SUCCESS : DungeonConstants.COLOR_WARNING)
        );

        game.setGenerationFuture(worldFuture);

        return worldFuture.thenApply(world -> {
            game.setInstanceWorld(world);

            Level generatedLevel = plugin.getDungeonInstanceService().getLastGeneratedLevel();
            if (generatedLevel != null) {
                game.setLevel(0, generatedLevel);
                LOGGER.info("Applied generated level graph '" + generatedLevel.getName()
                        + "' with " + generatedLevel.getRooms().size() + " rooms for party " + partyId);
            } else {
                LOGGER.warning("No generated level graph was available for party " + partyId + "; using placeholder level state.");
            }

            game.setGenerationProgress(1.0f);
            game.setState(GameState.READY);
            plugin.getDungeonMapService().buildMap(game);
            PartyUiPage.refreshOpenPages();
            LOGGER.info("Game ready for party " + partyId + " in world " + world.getName());
            return game;
        });
    }

    /**
     * Called when all party members have loaded into the dungeon world.
     * Saves inventories, resets status, gives equipment, and spawns mobs.
     */
    public void onGameStart(@Nonnull Game game) {
        if (game.getState() != GameState.READY) {
            LOGGER.warning("Attempted to start game in state: " + game.getState());
            return;
        }

        game.setState(GameState.ACTIVE);
        game.setStartTime(System.currentTimeMillis());
        game.setLevelStartTime(System.currentTimeMillis());
        PartyUiPage.refreshOpenPages();

        World world = game.getInstanceWorld();
        if (world == null) {
            LOGGER.severe("Game instance world is null on start!");
            return;
        }

        DungeonConfig config = plugin.loadDungeonConfig();

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();

            for (Map.Entry<UUID, UUID> entry : playerToParty.entrySet()) {
                if (!entry.getValue().equals(game.getPartyId())) continue;

                UUID playerId = entry.getKey();
                PlayerRef playerRef = Universe.get().getPlayer(playerId);
                if (playerRef == null) continue;

                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) continue;

                Store<EntityStore> playerStore = ref.getStore();
                Player player = playerStore.getComponent(ref, Player.getComponentType());
                if (player == null) continue;

                preparePlayerForDungeon(game, playerId, playerRef, player, ref, playerStore, config);
            }

            Level currentLevel = game.getCurrentLevel();
            if (currentLevel != null) {
                mobSpawningService.spawnLevelMobs(currentLevel, store);
                // Spawn portals in unlocked rooms at level start (cosmetic only).
                for (RoomData room : currentLevel.getRooms()) {
                    if (room.getType() != RoomType.BOSS
                            && !room.isLocked()
                            && !room.getPortalPositions().isEmpty()) {
                        plugin.getPortalService().spawnPortal(room, world);
                    }
                }
            }

            broadcastToParty(game.getPartyId(), "The dungeon awaits! Fight your way through!", DungeonConstants.COLOR_SUCCESS);
        });
    }

    /**
     * Enters boss phase for the current level.
     */
    public void enterBossPhase(@Nonnull Game game) {
        if (game.getState() != GameState.ACTIVE) return;

        game.setState(GameState.BOSS);
        Level level = game.getCurrentLevel();
        if (level == null) return;

        RoomData bossRoom = level.getBossRoom();
        if (bossRoom == null) return;

        World world = game.getInstanceWorld();
        if (world == null) return;

        DungeonConfig config = plugin.loadDungeonConfig();
        DungeonConfig.LevelConfig levelConfig = resolveCurrentLevelConfig(config, game);

        world.execute(() -> {
            // Seal boss room — prefer door positions from prefab, fall back to hardcoded.
            if (!bossRoom.getDoorPositions().isEmpty()) {
                String doorBlock = levelConfig != null ? levelConfig.getDoorBlock() : DungeonConstants.DEFAULT_DOOR_BLOCK;
                plugin.getDoorService().sealRoom(bossRoom, world, doorBlock);
                game.setBossRoomSealed(true);
            } else {
                sealBossRoom(game, bossRoom, world);
            }

            broadcastToParty(game.getPartyId(), "BOSS ROOM! Clear the room to advance!", DungeonConstants.COLOR_DANGER);
        });
    }

    /**
     * Called when the boss room is cleared. Sets TRANSITIONING state and spawns
     * a portal in the boss room for players to walk into.
     */
    public void onBossDefeated(@Nonnull Game game) {
        if (game.getState() != GameState.BOSS && game.getState() != GameState.ACTIVE) return;

        Level level = game.getCurrentLevel();
        RoomData bossRoom = level != null ? level.getBossRoom() : null;
        LOGGER.info("onBossDefeated start: party=" + game.getPartyId()
                + " state=" + game.getState()
                + " levelIndex=" + game.getCurrentLevelIndex()
                + " level=" + (level != null ? level.getName() : "null")
                + " playersInInstance=" + game.getPlayersInInstance().size()
                + " deadPlayers=" + game.getDeadPlayers().size()
                + " bossRoomAnchor=" + (bossRoom != null ? bossRoom.getAnchor() : "null"));
        game.setState(GameState.TRANSITIONING);

        if (level == null) return;

        World world = game.getInstanceWorld();
        if (world == null) return;

        world.execute(() -> {
            // Unseal boss room — use onRoomCleared to handle both door markers and lock door prefab blocks.
            if (bossRoom != null) {
                plugin.getDoorService().onRoomCleared(bossRoom, world);
                game.setBossRoomSealed(false);
            }

            // Revive all dead/ghost players on level completion
            reviveAllDeadPlayers(game);

            // Spawn portal in boss room
            if (bossRoom != null) {
                plugin.getPortalService().spawnPortal(bossRoom, world);
            }
            game.setPortalsActive(true);
            game.setPortalsActivatedAt(System.currentTimeMillis());
            LOGGER.info("Boss portal activated for party " + game.getPartyId()
                    + " level=" + level.getName()
                    + " nextLevel=" + hasNextLevelConfig(game)
                    + " portalPositions=" + (bossRoom != null ? bossRoom.getPortalPositions().size() : 0)
                    + " activatedAt=" + game.getPortalsActivatedAt()
                    + " victoryTimestamp=" + game.getVictoryTimestamp());

            if (hasNextLevelConfig(game)) {
                broadcastToParty(game.getPartyId(), "Boss defeated! Step into the portal to advance!", DungeonConstants.COLOR_SUCCESS);
            } else {
                broadcastToParty(game.getPartyId(), "Dungeon Complete! Step into the portal to return home!", DungeonConstants.COLOR_VICTORY);
                game.setVictoryTimestamp(System.currentTimeMillis());
            }
        });
    }

    /**
     * Checks if there is a next level configured (not yet generated).
     */
    public boolean hasNextLevelConfig(@Nonnull Game game) {
        DungeonConfig config = plugin.loadDungeonConfig();
        return resolveNextLevelConfig(config, game) != null;
    }

    /**
     * Called when a player walks into the boss room portal.
     * Advances to the next level or ends the game.
     */
    public void onPortalEntered(@Nonnull Game game, @Nonnull UUID playerId) {
        if (game.getState() != GameState.TRANSITIONING) return;
        boolean hasNextLevel = hasNextLevelConfig(game);
        LOGGER.info("onPortalEntered: party=" + game.getPartyId()
                + " player=" + playerId
                + " state=" + game.getState()
                + " hasNextLevel=" + hasNextLevel
                + " playersInInstanceBefore=" + game.getPlayersInInstance().size()
                + " victoryTimestamp=" + game.getVictoryTimestamp());

        if (hasNextLevel) {
            game.setPortalsActive(false);
            game.setPortalsActivatedAt(0L);
            advanceToNextLevel(game);
        } else {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef != null) {
                PlayerEventNotifier.showEventTitle(playerRef, "Returning to the surface...", true);
            }
            onPlayerLeftParty(game.getPartyId(), playerId);
        }
    }

    /**
     * Teleports a player from a Closest Exit portal to the nearest ancestor room
     * that contains one or more portal exit markers.
     */
    public void onClosestExitPortalEntered(@Nonnull Game game,
                                           @Nonnull UUID playerId,
                                           @Nonnull RoomData sourceRoom,
                                           @Nullable RoomData fallbackRoom) {
        Vector3i exitPos = plugin.getPortalService().resolveClosestExitDestination(sourceRoom);
        RoomData resolvedSourceRoom = sourceRoom;
        if (exitPos == null && fallbackRoom != null && fallbackRoom != sourceRoom) {
            exitPos = plugin.getPortalService().resolveClosestExitDestination(fallbackRoom);
            if (exitPos != null) {
                resolvedSourceRoom = fallbackRoom;
            }
        }

        if (exitPos == null) {
            LOGGER.warning("Closest Exit portal used in room " + sourceRoom.getAnchor()
                    + (fallbackRoom != null && fallbackRoom != sourceRoom
                    ? " (fallback owner " + fallbackRoom.getAnchor() + ")"
                    : "")
                    + " but no ancestor room contains portal exit markers.");
            return;
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null) {
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> playerStore = ref.getStore();
        Vector3d destination = new Vector3d(exitPos.x + 0.5, exitPos.y + 1.0, exitPos.z + 0.5);
        Teleport tp = Teleport.createForPlayer(destination, new Rotation3f());
        playerStore.putComponent(ref, Teleport.getComponentType(), tp);

        LOGGER.info("Closest Exit portal teleported player " + playerId
                + " from room " + resolvedSourceRoom.getAnchor()
                + " to exit " + exitPos);
    }

    /**
     * Generates the next level, teleports all players to its entrance, and starts it.
     */
    private void advanceToNextLevel(@Nonnull Game game) {
        World world = game.getInstanceWorld();
        if (world == null) return;

        DungeonConfig config = plugin.loadDungeonConfig();
        DungeonConfig.LevelConfig nextLevelConfig = resolveNextLevelConfig(config, game);
        if (nextLevelConfig == null) {
            LOGGER.warning("advanceToNextLevel called for party " + game.getPartyId()
                    + " but no nextLevel is configured for current selector " + game.getCurrentLevelSelector());
            return;
        }

        int nextIndex = game.getCurrentLevelIndex() + 1;
        Level nextLevel = new Level(nextLevelConfig.getName(), nextIndex);
        game.addLevel(nextLevel);
        game.setCurrentLevelIndex(nextIndex);
        game.setCurrentLevelSelector(nextLevelConfig.getSelector());

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            mobSpawningService.spawnLevelMobs(nextLevel, store);

            // Spawn portals for unlocked rooms in the new level.
            for (RoomData room : nextLevel.getRooms()) {
                if (room.getType() != RoomType.BOSS
                        && !room.isLocked()
                        && !room.getPortalPositions().isEmpty()) {
                    plugin.getPortalService().spawnPortal(room, world);
                }
            }

            // Teleport all players to the entrance of the new level.
            RoomData entrance = nextLevel.getEntranceRoom();
            if (entrance != null) {
                Vector3i anchor = entrance.getAnchor();
                Vector3d spawnPos = new Vector3d(anchor.x + 0.5, anchor.y + 1.0, anchor.z + 0.5);
                teleportAllPlayersToPosition(game, world, store, spawnPos);
            }

            game.setState(GameState.ACTIVE);
            broadcastToParty(game.getPartyId(), "Welcome to " + nextLevelConfig.getName() + "!", DungeonConstants.COLOR_SUCCESS);
        });
    }

    /**
     * Teleports all players currently in the instance to the given position (same world).
     */
    private void teleportAllPlayersToPosition(@Nonnull Game game, @Nonnull World world,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Vector3d position) {
        for (UUID playerId : game.getPlayersInInstance()) {
            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null) continue;

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;

            Store<EntityStore> playerStore = ref.getStore();
            Teleport tp = Teleport.createForPlayer(position, new Rotation3f());
            playerStore.putComponent(ref, Teleport.getComponentType(), tp);
        }
    }

    /**
     * Ends the game, restores inventories, and cleans up.
     *
     * @param forced true if the game was aborted (e.g. party disband)
     */
    public void endGame(@Nonnull Game game, boolean forced) {
        if (game.getState() == GameState.COMPLETE) {
            return;
        }

        // Cancel ongoing generation if the game is still generating
        if (game.getState() == GameState.GENERATING) {
            CompletableFuture<World> genFuture = game.getGenerationFuture();
            if (genFuture != null && !genFuture.isDone()) {
                genFuture.cancel(true);
                LOGGER.info("Cancelled generation future for party " + game.getPartyId());
            }
        }

        game.setState(GameState.COMPLETE);
        game.clearDeadPlayers();
        game.clearDisconnectedDungeonInventories();
        showAllPartyMembers(game.getPartyId());

        // Clean up pending reconnect state for any disconnected players
        for (UUID disconnectedId : game.getDisconnectedPlayers()) {
            pendingReconnectPlayers.remove(disconnectedId);
            pendingReadyRecoveryPlayers.remove(disconnectedId);
            pendingRejoinDecision.remove(disconnectedId);
            RejoinDungeonPage.closeForPlayer(disconnectedId);
        }
        game.clearDisconnectedPlayers();
        game.clearDisconnectedPositions();

        List<UUID> partyMembers = playerToParty.entrySet().stream()
                .filter(entry -> entry.getValue().equals(game.getPartyId()))
                .map(Map.Entry::getKey)
                .toList();

        for (UUID playerId : partyMembers) {
            reviveMarkerService.despawnReviveMarker(playerId);
            inventoryService.removeDeathSnapshot(playerId);
            pendingRejoinDecision.remove(playerId);
            pendingReconnectPlayers.remove(playerId);
            pendingPostReadyResync.remove(playerId);
            RejoinDungeonPage.closeForPlayer(playerId);

            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null) continue;

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;

            Store<EntityStore> store = ref.getStore();
            Transform returnPoint = game.getReturnPoints().get(playerId);
            World trackedReturnWorld = game.getReturnWorlds().get(playerId);
            // wasInInstance covers players actively in the dungeon.
            // wasReconnected covers players who reconnected and are in the
            // home world (popup shown or pending) — they still need to be
            // teleported to their return point and have inventory restored.
            boolean wasInInstance = game.isPlayerInInstance(playerId);
            boolean wasReconnected = !wasInInstance && (game.isDisconnectedPlayer(playerId)
                    || outsideDungeonNormalizedPlayers.contains(playerId));

            plugin.getCameraService().restoreDefault(playerRef);

            store.getExternalData().getWorld().execute(() -> {
                if (!ref.isValid()) {
                    return;
                }

                // Reset death state inside world.execute() so we're on the
                // correct thread for store access. ReviveTickSystem also runs
                // on this thread, so the reset still happens before the next tick.
                DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
                if (deathComponent != null) {
                    deathComponent.reset();
                }
                DeathStateController.clear(store, ref);

                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    plugin.getInventoryLockService().remove(playerId);
                    game.setPlayerInInstance(playerId, false);
                    return;
                }

                playerStateService.hideDungeonHuds(player, playerRef);

                plugin.getInventoryLockService().unlock(player, playerId);
                if (wasInInstance) {
                    inventoryService.restorePlayerInventory(playerId, player, true);
                } else if (wasReconnected) {
                    if (inventoryService.hasSavedInventoryFile(playerId)) {
                        inventoryService.deleteSavedInventoryFiles(playerId);
                    }
                } else if (inventoryService.shouldPreserveSavedInventoryDuringCleanup(playerId, player, game)) {
                    LOGGER.info("Preserving saved inventory for disconnected player " + playerId
                            + " during endGame cleanup.");
                } else {
                    LOGGER.info("Deleting saved inventory for player " + playerId
                            + " during endGame cleanup.");
                    inventoryService.deleteSavedInventoryFiles(playerId);
                }

                playerStateService.resetPlayerStatus(player, ref, store);
                game.setPlayerInInstance(playerId, false);

                if ((wasInInstance || wasReconnected) && returnPoint != null) {
                    try {
                        World returnWorld = trackedReturnWorld;
                        if (returnWorld == null) {
                            returnWorld = Universe.get().getDefaultWorld();
                        }
                        if (returnWorld != null) {
                            teleportPlayerToReturnPoint(ref, store, returnWorld, returnPoint);
                        }
                    } catch (Exception e) {
                        LOGGER.log(java.util.logging.Level.WARNING, "Failed to teleport player " + playerId + " back", e);
                    }
                }
            });
        }

        plugin.getDungeonMapService().cleanup(game.getPartyId());
        activeGames.remove(game.getPartyId());

        if (forced) {
            broadcastToParty(game.getPartyId(), "The dungeon run was cancelled.", DungeonConstants.COLOR_SOFT_DANGER);
        }

        World instanceWorld = game.getInstanceWorld();
        if (instanceWorld != null) {
            instanceWorld.execute(() -> InstancesPlugin.safeRemoveInstance(instanceWorld));
        }

        playerToParty.entrySet().removeIf(e -> e.getValue().equals(game.getPartyId()));
        plugin.getPartyManager().closePartyForSystem(game.getPartyId(), "The dungeon run ended, so the party was closed.");
        PartyUiPage.refreshOpenPages();

        // Remove all mob registry entries belonging to this game's rooms.
        java.util.Set<RoomData> gameRooms = new java.util.HashSet<>();
        for (Level level : game.getLevels()) {
            gameRooms.addAll(level.getRooms());
        }
        mobSpawningService.removeRegistryEntriesForRooms(gameRooms);

        LOGGER.info("Game ended for party " + game.getPartyId() + " (forced=" + forced + ")");
    }

    /**
     * Handle party disband — end the active game immediately.
     */
    public void onPartyDisband(@Nonnull UUID partyId) {
        Game game = activeGames.get(partyId);
        if (game == null) return;

        if (game.getState() != GameState.COMPLETE) {
            endGame(game, true);
        }
    }

    public void onPlayerLeftParty(@Nonnull UUID partyId, @Nonnull UUID playerId) {
        Game game = activeGames.get(partyId);
        playerToParty.remove(playerId, partyId);
        reviveMarkerService.despawnReviveMarker(playerId);

        if (game == null) {
            return;
        }

        game.removeDeadPlayer(playerId);
        boolean wasInInstance = game.isPlayerInInstance(playerId);
        boolean wasDisconnected = game.isDisconnectedPlayer(playerId);
        game.removeDisconnectedPlayer(playerId);
        game.setPlayerInInstance(playerId, false);
        LOGGER.info("onPlayerLeftParty: party=" + partyId
                + " player=" + playerId
                + " state=" + game.getState()
                + " wasInInstance=" + wasInInstance
                + " wasDisconnected=" + wasDisconnected
                + " remainingPlayersInInstance=" + game.getPlayersInInstance().size()
                + " victoryTimestamp=" + game.getVictoryTimestamp());

        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef != null) {
            plugin.getCameraService().restoreDefault(playerRef);

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    Transform returnPoint = game.getReturnPoints().get(playerId);
                    World trackedReturnWorld = game.getReturnWorlds().get(playerId);
                    LOGGER.info("Preparing leave-party cleanup for player " + playerId
                            + " currentWorld=" + (player.getWorld() != null ? player.getWorld().getName() : "null")
                            + " returnWorld=" + (trackedReturnWorld != null ? trackedReturnWorld.getName() : "null")
                            + " hasReturnPoint=" + (returnPoint != null));

                    playerStateService.hideDungeonHuds(player, playerRef);

                    store.getExternalData().getWorld().execute(() -> {
                        if (!ref.isValid()) {
                            return;
                        }

                        DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
                        if (deathComponent != null) {
                            deathComponent.reset();
                        }

                        DeathStateController.clear(store, ref);
                        plugin.getInventoryLockService().unlock(player, playerId);
                        if (wasInInstance) {
                            inventoryService.restorePlayerInventory(playerId, player, true);
                        } else if (wasDisconnected && inventoryService.hasSavedInventoryFile(playerId)) {
                            // Reconnected player in home world — home inventory is already
                            // restored, just delete the saved file since they're leaving.
                            inventoryService.deleteSavedInventoryFiles(playerId);
                        } else if (inventoryService.shouldPreserveSavedInventoryDuringCleanup(playerId, player, game)) {
                            LOGGER.info("Preserving saved inventory for disconnected player " + playerId
                                    + " during party-leave cleanup.");
                        } else {
                            LOGGER.info("Deleting saved inventory for player " + playerId
                                    + " during party-leave cleanup.");
                            inventoryService.deleteSavedInventoryFiles(playerId);
                        }
                        playerStateService.resetPlayerStatus(player, ref, store);

                        if ((wasInInstance || wasDisconnected) && returnPoint != null) {
                            try {
                                World returnWorld = trackedReturnWorld;
                                if (returnWorld == null) {
                                    returnWorld = Universe.get().getDefaultWorld();
                                }
                                if (returnWorld != null && ref.isValid()) {
                                    teleportPlayerToReturnPoint(ref, store, returnWorld, returnPoint);
                                }
                            } catch (Exception e) {
                                LOGGER.log(java.util.logging.Level.WARNING, "Failed to teleport player " + playerId + " out of dungeon after leaving party", e);
                            }
                        }
                    });
                }
            }
        }

        game.getReturnPoints().remove(playerId);
        game.getReturnWorlds().remove(playerId);
        game.getSavedInventoryPaths().remove(playerId);
        closeGameIfInstanceEmpty(game);
    }

    /**
     * Handle player disconnect during an active game.
     * If the player is in an active dungeon run, their dungeon inventory is
     * snapshotted in-memory and they are marked as disconnected so they can
     * automatically rejoin on reconnect.
     */
    public void onPlayerDisconnect(@Nonnull PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        LOGGER.info("[RECONNECT-DEBUG] onPlayerDisconnect ENTER: player=" + playerRef.getUsername()
                + " id=" + playerId
                + " pendingRejoinDecision.contains=" + pendingRejoinDecision.contains(playerId));
        pendingReadyRecoveryPlayers.remove(playerId);
        pendingReconnectPlayers.remove(playerId);
        pendingPostReadyResync.remove(playerId);
        pendingRejoinDecision.remove(playerId);
        RejoinDungeonPage.closeForPlayer(playerId);
        inventoryService.removeDeathSnapshot(playerId);

        UUID partyId = playerToParty.get(playerId);
        Game game = partyId != null ? activeGames.get(partyId) : null;
        boolean wasInActiveDungeon = game != null && game.getState() != GameState.COMPLETE
                && (game.isPlayerInInstance(playerId) || game.isPlayerDead(playerId));

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref != null && ref.isValid()) {
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                if (!ref.isValid()) {
                    return;
                }

                // Camera and revive marker cleanup must run on the world thread
                // because they access store components (which assert thread safety).
                plugin.getCameraService().restoreDefault(playerRef);
                reviveMarkerService.despawnReviveMarker(playerId);

                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null && !player.wasRemoved()) {
                    playerStateService.hideDungeonHuds(player, playerRef);
                }
            });
        } else {
            // Ref not valid — do non-store cleanup directly
            plugin.getCameraService().cancelDeferredEnable(playerRef);
            reviveMarkerService.despawnReviveMarker(playerId);
        }

        plugin.getInventoryLockService().remove(playerId);

        if (game == null) return;

        if (wasInActiveDungeon) {
            showPlayerToParty(playerId, partyId);
            game.setPlayerInInstance(playerId, false);
            game.removeDeadPlayer(playerId);
            game.addDisconnectedPlayer(playerId);
            LOGGER.info("[RECONNECT-DEBUG] onPlayerDisconnect: marked " + playerId + " as disconnected in game for party " + partyId);
            // Do NOT remove playerToParty — needed for reconnect lookup.
            if (!closeGameIfInstanceEmpty(game)) {
                PartyUiPage.refreshOpenPages();
            }
        } else {
            game.addDisconnectedPlayer(playerId);
            PartyUiPage.refreshOpenPages();
            LOGGER.info("[RECONNECT-DEBUG] onPlayerDisconnect: marked " + playerId + " as disconnected (not in active dungeon). gameState=" + game.getState());
        }
    }

    @Nonnull
    public PartyManager.ActionResult teleportPlayerToDungeon(@Nonnull PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        Game game = findGameForPlayer(playerId);
        if (game == null || !isDungeonJoinable(game)) {
            return PartyManager.ActionResult.error("Your party does not have an active dungeon instance to return to.");
        }

        World instanceWorld = game.getInstanceWorld();
        if (instanceWorld == null) {
            return PartyManager.ActionResult.error("The dungeon instance is no longer available.");
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return PartyManager.ActionResult.error("You must be fully loaded before teleporting to the dungeon.");
        }

        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return PartyManager.ActionResult.error("You must be fully loaded before teleporting to the dungeon.");
        }

        if (player.getWorld() == instanceWorld) {
            game.setPlayerInInstance(playerId, true);
            return PartyManager.ActionResult.error("You are already inside the dungeon.");
        }

        if (!inventoryService.hasSavedInventoryFile(playerId)) {
            return PartyManager.ActionResult.error("You cannot return to this dungeon because your saved inventory could not be found.");
        }

        plugin.getCameraService().scheduleEnableOnNextReady(playerRef);
        sendPlayerToDungeon(
                game,
                playerRef,
                ref,
                store,
                CompletableFuture.completedFuture(instanceWorld),
                status -> {
                    plugin.getCameraService().cancelDeferredEnable(playerRef);
                    PlayerEventNotifier.showEventTitle(playerRef, status, true);
                    PartyUiPage.refreshOpenPages();
                }
        );
        PartyUiPage.refreshOpenPages();
        return PartyManager.ActionResult.success("Teleporting you back into the dungeon.");
    }

    /**
     * On player connect, check if they have a saved inventory from a crash/disconnect,
     * or if they are reconnecting to an active dungeon run.
     */
    public void onPlayerConnect(@Nonnull PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();

        // Check if this player was disconnected from an active dungeon
        UUID partyId = playerToParty.get(playerId);
        Game game = partyId != null ? activeGames.get(partyId) : null;
        LOGGER.info("[RECONNECT-DEBUG] onPlayerConnect: player=" + playerRef.getUsername()
                + " id=" + playerId
                + " partyId=" + partyId
                + " game=" + (game != null)
                + " isDisconnected=" + (game != null && game.isDisconnectedPlayer(playerId))
                + " gameState=" + (game != null ? game.getState() : "null"));
        if (game != null && game.isDisconnectedPlayer(playerId)
                && game.getState() != GameState.COMPLETE) {
            // They'll be shown the rejoin popup once ready. Mark as pending
            // so normalization is deferred until after the popup decision.
            pendingRejoinDecision.add(playerId);
            LOGGER.info("[RECONNECT-DEBUG] onPlayerConnect: added " + playerId
                    + " to pendingRejoinDecision. Set size=" + pendingRejoinDecision.size());
            return;
        }

        boolean hasFileSnapshot = inventoryService.hasSavedInventoryFile(playerId);

        if (hasFileSnapshot) {
            pendingReadyRecoveryPlayers.add(playerId);
            LOGGER.info("Found saved inventory for " + playerRef.getUsername()
                    + "; recovery will run once the player is fully loaded."
                    + " fileSnapshot=" + hasFileSnapshot);
        } else {
            pendingReadyRecoveryPlayers.remove(playerId);
        }
    }

    public void releasePendingRecovery(@Nonnull UUID playerId) {
        pendingReadyRecoveryPlayers.remove(playerId);
    }

    /**
     * Called after PlayerReadyEvent fires in the dungeon world to re-send
     * inventory with fresh virtual item definitions. During world transition
     * the client may not process UpdateItems packets, so we clear the
     * per-player sent tracking and resync once the client is fully loaded.
     */
    public void handlePostReadyResync(@Nonnull PlayerRef playerRef, @Nonnull Player player) {
        UUID playerId = playerRef.getUuid();
        if (!pendingPostReadyResync.remove(playerId)) return;

        LOGGER.info("[POST-READY-RESYNC] Running for " + playerRef.getUsername() + " (" + playerId + ")");

        // Clear sent tracking so definitions are re-sent fresh now that
        // the client is fully loaded (post-ClientReady).
        WeaponVirtualItems.onPlayerDisconnect(playerId);
        ArmorVirtualItems.onPlayerDisconnect(playerId);

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            LOGGER.warning("[POST-READY-RESYNC] Invalid ref for " + playerId);
            return;
        }
        Store<EntityStore> store = ref.getStore();

        // Reset death state and player status (health, stamina, etc.).
        // Skip movement reset since dungeon movement will be applied below.
        playerStateService.resetPlayerStatus(player, ref, store, null, true);

        // Always clear and give fresh dungeon start equipment.
        // The player still has home-world items at this point because
        // onPlayerAddedToWorld cannot touch inventory (entity ref is null)
        // and the home-world inventory was restored before the rejoin popup.
        plugin.getInventoryLockService().unlock(player, playerId);
        inventoryService.clearPlayerInventory(player);
        Game game = findGameForPlayer(playerId);
        if (game != null) {
            game.removeDisconnectedDungeonInventory(playerId); // discard snapshot
        }
        DungeonConfig config = plugin.loadDungeonConfig();
        inventoryService.giveStartEquipment(playerRef, player, config);
        LOGGER.info("[POST-READY-RESYNC] Gave fresh start equipment to " + playerRef.getUsername());

        // Re-apply the inventory lock (3-slot restriction).
        plugin.getInventoryLockService().lock(player, playerId);

        // Re-apply camera with force since packets sent before JoinWorld
        // were likely discarded by the client.
        plugin.getCameraService().forceReapply(playerRef);

        // Re-apply dungeon movement settings AFTER forceReapply so the dungeon
        // movement packet is the LAST movement update the client receives.
        // forceReapply → applyTopCamera → applyEqualizedMovement sends a movement
        // packet with default multipliers; we must override with dungeon values after.
        playerStateService.applyDungeonMovementSettings(ref, store, playerRef);

        // Re-enable map + send dungeon map data
        playerStateService.enableMap(playerRef);
        if (game != null) {
            plugin.getDungeonMapService().sendMapToPlayer(playerRef, game);
        }

        inventoryService.syncInventoryAndSelectedSlots(playerRef, ref, store);

        // Teleport the reconnecting player to their saved position (where they
        // were when they disconnected), or fall back to the level entrance.
        if (game != null) {
            Vector3d savedPosition = game.removeDisconnectedPosition(playerId);
            if (savedPosition != null) {
                Teleport tp = Teleport.createForPlayer(savedPosition, new Rotation3f());
                store.putComponent(ref, Teleport.getComponentType(), tp);
                LOGGER.info("[POST-READY-RESYNC] Teleporting " + playerRef.getUsername()
                        + " to saved position " + savedPosition);
            } else {
                Level currentLevel = game.getCurrentLevel();
                if (currentLevel != null) {
                    RoomData entrance = currentLevel.getEntranceRoom();
                    if (entrance != null) {
                        Vector3i anchor = entrance.getAnchor();
                        Vector3d spawnPos = new Vector3d(anchor.x + 0.5, anchor.y + 1.0, anchor.z + 0.5);
                        Teleport tp = Teleport.createForPlayer(spawnPos, new Rotation3f());
                        store.putComponent(ref, Teleport.getComponentType(), tp);
                        LOGGER.info("[POST-READY-RESYNC] Teleporting " + playerRef.getUsername()
                                + " to level entrance " + spawnPos);
                    }
                }
            }
            PartyUiPage.refreshOpenPages();
        }

        // Safety net: re-apply dungeon movement one tick later in case any engine
        // system (PostAssignmentSystem, resetManagers, etc.) overwrites our settings
        // after this handler returns.
        World world = player.getWorld();
        if (world != null) {
            world.execute(() -> {
                Ref<EntityStore> delayedRef = playerRef.getReference();
                if (delayedRef != null && delayedRef.isValid()) {
                    Store<EntityStore> delayedStore = delayedRef.getStore();
                    playerStateService.applyDungeonMovementSettings(delayedRef, delayedStore, playerRef);
                    LOGGER.info("[POST-READY-RESYNC] Delayed movement re-application for " + playerRef.getUsername());
                }
            });
        }

        LOGGER.info("[POST-READY-RESYNC] Complete for " + playerRef.getUsername());
    }

    /**
     * Called when a previously-disconnected player reconnects and is ready.
     * Ensures the player is in their home world with normal inventory, then
     * shows a popup asking if they want to rejoin the dungeon.
     */
    public void handlePlayerReconnect(@Nonnull PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        boolean wasInSet = pendingRejoinDecision.remove(playerId);
        LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect ENTER: player=" + playerRef.getUsername()
                + " id=" + playerId
                + " wasInPendingRejoinDecision=" + wasInSet
                + " pendingRejoinDecisionSize=" + pendingRejoinDecision.size());
        if (!wasInSet) return;

        UUID partyId = playerToParty.get(playerId);
        Game game = partyId != null ? activeGames.get(partyId) : null;

        LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: partyId=" + partyId
                + " game=" + (game != null)
                + " isDisconnected=" + (game != null && game.isDisconnectedPlayer(playerId))
                + " gameState=" + (game != null ? game.getState() : "null"));

        if (game == null || !game.isDisconnectedPlayer(playerId)
                || game.getState() == GameState.COMPLETE) {
            LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: early return — game null, not disconnected, or complete");
            return;
        }

        World instanceWorld = game.getInstanceWorld();
        if (instanceWorld == null) {
            LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: instanceWorld is null, removing disconnected player");
            game.removeDisconnectedPlayer(playerId);
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: ref=" + ref
                + " refValid=" + (ref != null && ref.isValid()));
        if (ref == null || !ref.isValid()) {
            LOGGER.warning("[RECONNECT-DEBUG] handlePlayerReconnect: ref not ready for " + playerId);
            return;
        }

        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: player=" + (player != null)
                + " playerWorld=" + (player != null && player.getWorld() != null ? player.getWorld().getName() : "null")
                + " instanceWorld=" + instanceWorld.getName());
        if (player == null) return;

        // If the engine put the player back in the dungeon instance,
        // exit the instance now. PlayerReadyEvent fires after the player is
        // fully loaded in the world, so store operations and Teleport
        // component addition work reliably here (unlike AddPlayerToWorldEvent
        // where cross-world teleports are silently dropped).
        World currentWorld = player.getWorld();
        boolean inDungeonWorld = currentWorld != null && currentWorld == instanceWorld;
        LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: currentWorld=" + (currentWorld != null ? currentWorld.getName() : "null")
                + " inDungeonWorld=" + inDungeonWorld
                + " sameRef=" + (currentWorld == instanceWorld));
        if (inDungeonWorld) {
            try {
                LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: calling InstancesPlugin.exitInstance for " + playerId);
                InstancesPlugin.exitInstance(ref, store);
                LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: exitInstance SUCCEEDED for " + playerId);
            } catch (Exception e) {
                LOGGER.warning("[RECONNECT-DEBUG] handlePlayerReconnect: exitInstance FAILED for " + playerId
                        + ": " + e.getClass().getName() + ": " + e.getMessage());
                Transform returnPoint = game.getReturnPoints().get(playerId);
                World returnWorld = game.getReturnWorlds().get(playerId);
                LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: fallback returnPoint=" + (returnPoint != null)
                        + " returnWorld=" + (returnWorld != null ? returnWorld.getName() : "null"));
                if (returnWorld == null) returnWorld = Universe.get().getDefaultWorld();
                if (returnWorld != null && returnPoint != null) {
                    LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: calling teleportPlayerToReturnPoint as fallback");
                    teleportPlayerToReturnPoint(ref, store, returnWorld, returnPoint);
                } else {
                    LOGGER.warning("[RECONNECT-DEBUG] handlePlayerReconnect: NO fallback available — no return world/point!");
                }
            }
            // Re-add so the next PlayerReadyEvent (in home world) shows the popup.
            pendingRejoinDecision.add(playerId);
            LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: re-added " + playerId + " to pendingRejoinDecision");
            return;
        }

        // Player is in their home world. Restore home state and show rejoin popup.
        LOGGER.info("[RECONNECT-DEBUG] handlePlayerReconnect: player is in home world, calling restoreHomeStateAndShowPopup");
        restoreHomeStateAndShowPopup(game, playerId, playerRef, player, ref, store);
    }

    /**
     * Restores the player's home (pre-dungeon) inventory and opens the rejoin
     * popup. Called after the player is confirmed to be in the home world.
     */
    private void restoreHomeStateAndShowPopup(@Nonnull Game game,
                                              @Nonnull UUID playerId,
                                              @Nonnull PlayerRef playerRef,
                                              @Nonnull Player player,
                                              @Nonnull Ref<EntityStore> ref,
                                              @Nonnull Store<EntityStore> store) {
        // Mark as already-normalized so that normalizeOutsideDungeonState (called
        // later in onPlayerReady) doesn't run again and undo our state/popup.
        outsideDungeonNormalizedPlayers.add(playerId);

        // Reset death state if present
        DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
        if (deathComponent != null) deathComponent.reset();
        DeathStateController.clear(store, ref);
        playerStateService.resetPlayerStatus(player, ref, store);

        // Restore camera, hide dungeon HUDs, unlock inventory
        plugin.getCameraService().restoreDefault(playerRef);
        playerStateService.hideDungeonHuds(player, playerRef);
        plugin.getInventoryLockService().unlock(player, playerId);

        // Restore home/pre-dungeon inventory from file
        if (inventoryService.hasSavedInventoryFile(playerId)) {
            inventoryService.restorePlayerInventory(playerId, player, false);
        }

        // Show the rejoin popup
        player.getPageManager().openCustomPage(ref, store, new RejoinDungeonPage(plugin, playerRef));
        LOGGER.info("Showing rejoin popup to player " + playerRef.getUsername()
                + " for party " + game.getPartyId());
        PartyUiPage.refreshOpenPages();
    }

    /**
     * Called when a reconnecting player accepts the rejoin popup.
     * Saves home inventory, teleports to dungeon, restores dungeon inventory.
     */
    public void handleRejoinAccepted(@Nonnull PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        UUID partyId = playerToParty.get(playerId);
        Game game = partyId != null ? activeGames.get(partyId) : null;

        if (game == null || game.getState() == GameState.COMPLETE) {
            PlayerEventNotifier.showEventTitle(playerRef, "The dungeon run has ended.", true);
            return;
        }

        World instanceWorld = game.getInstanceWorld();
        if (instanceWorld == null) {
            PlayerEventNotifier.showEventTitle(playerRef, "The dungeon instance is no longer available.", true);
            game.removeDisconnectedPlayer(playerId);
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        // Save home inventory to file (overwrite with current state)
        if (!inventoryService.hasSavedInventoryFile(playerId)) {
            inventoryService.savePlayerInventory(playerId, player, game);
        }

        pendingReconnectPlayers.add(playerId);
        pendingReadyRecoveryPlayers.add(playerId);

        plugin.getCameraService().scheduleEnableOnNextReady(playerRef);
        sendPlayerToDungeon(
                game,
                playerRef,
                ref,
                store,
                CompletableFuture.completedFuture(instanceWorld),
                status -> {
                    plugin.getCameraService().cancelDeferredEnable(playerRef);
                    pendingReconnectPlayers.remove(playerId);
                    pendingReadyRecoveryPlayers.remove(playerId);
                    PlayerEventNotifier.showEventTitle(playerRef, status, true);
                    LOGGER.warning("handleRejoinAccepted: failed to teleport " + playerId + " to dungeon: " + status);
                }
        );

        LOGGER.info("handleRejoinAccepted: teleporting " + playerRef.getUsername()
                + " back to dungeon for party " + partyId);
    }

    /**
     * Called when a reconnecting player declines the rejoin popup (or closes it).
     * Removes them from the party and cleans up disconnected state.
     */
    public void handleRejoinDeclined(@Nonnull PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        UUID partyId = playerToParty.get(playerId);
        Game game = partyId != null ? activeGames.get(partyId) : null;

        if (game != null) {
            game.removeDisconnectedPlayer(playerId);
            game.removeDisconnectedDungeonInventory(playerId);
            game.removeDisconnectedPosition(playerId);
        }

        // Delete the saved home inventory file since they're not going back
        if (inventoryService.hasSavedInventoryFile(playerId)) {
            inventoryService.deleteSavedInventoryFiles(playerId);
        }

        // Teleport the player to their original pre-dungeon location.
        // InstancesPlugin.exitInstance may have placed them at the instance
        // return point which could differ from their saved home position.
        if (game != null) {
            Transform returnPoint = game.getReturnPoints().get(playerId);
            World returnWorld = game.getReturnWorlds().get(playerId);
            if (returnPoint != null) {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    if (returnWorld == null) returnWorld = Universe.get().getDefaultWorld();
                    if (returnWorld != null) {
                        teleportPlayerToReturnPoint(ref, store, returnWorld, returnPoint);
                    }
                }
            }
        }

        // Leave the party (this also cleans up returnPoints/returnWorlds)
        plugin.getPartyManager().leave(playerRef);
        LOGGER.info("handleRejoinDeclined: player " + playerRef.getUsername()
                + " declined rejoin. Left party " + partyId);
    }

    /**
     * Restores a reconnecting player's dungeon state (inventory, camera, movement, map)
     * once they arrive in the dungeon world after accepting the rejoin popup.
     */
    private void restorePlayerToDungeon(@Nonnull Game game,
                                        @Nonnull UUID playerId,
                                        @Nonnull PlayerRef playerRef,
                                        @Nonnull Player player,
                                        @Nonnull Ref<EntityStore> ref,
                                        @Nonnull Store<EntityStore> store) {
        LOGGER.info("[RESTORE-DUNGEON] BEGIN for " + playerRef.getUsername() + " (" + playerId + ")");
        outsideDungeonNormalizedPlayers.remove(playerId);
        pendingReconnectPlayers.remove(playerId);
        pendingReadyRecoveryPlayers.remove(playerId);

        // Reset any residual death state
        DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
        if (deathComponent != null) {
            deathComponent.reset();
        }
        DeathStateController.clear(store, ref);
        // Skip movement reset — dungeon movement will be applied below.
        playerStateService.resetPlayerStatus(player, ref, store, null, true);

        // Always give fresh base start equipment on reconnect (ignore any dungeon snapshot).
        // The snapshot is discarded so that all reconnecting players start with the same loadout.
        plugin.getInventoryLockService().unlock(player, playerId);
        inventoryService.clearPlayerInventory(player);
        game.removeDisconnectedDungeonInventory(playerId); // discard snapshot
        DungeonConfig config = plugin.loadDungeonConfig();
        inventoryService.giveStartEquipment(playerRef, player, config);
        LOGGER.info("[RESTORE-DUNGEON] Gave fresh start equipment to " + playerId);

        plugin.getInventoryLockService().lock(player, playerId);

        // Verify items were actually placed
        InventoryComponent.Hotbar hotbarCheck = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbarCheck != null) {
            ItemContainer hb = hotbarCheck.getInventory();
            int itemCount = 0;
            for (short i = 0; i < hb.getCapacity(); i++) {
                ItemStack stack = hb.getItemStack(i);
                if (stack != null && !stack.isEmpty()) itemCount++;
            }
            LOGGER.info("[RESTORE-DUNGEON] Hotbar has " + itemCount + " items after setup for " + playerId);
        } else {
            LOGGER.warning("[RESTORE-DUNGEON] No hotbar component for " + playerId);
        }

        // Clear SENT_PER_PLAYER so that virtual item definitions are re-sent
        WeaponVirtualItems.onPlayerDisconnect(playerId);
        ArmorVirtualItems.onPlayerDisconnect(playerId);

        plugin.getCameraService().scheduleEnableOnNextReady(playerRef);
        playerStateService.applyDungeonMovementSettings(ref, store, playerRef);
        playerStateService.enableMap(playerRef);
        plugin.getDungeonMapService().sendMapToPlayer(playerRef, game);
        game.removeDisconnectedPlayer(playerId);
        game.setPlayerInInstance(playerId, true);
        pendingPostReadyResync.add(playerId);

        // Teleport to saved position, or fall back to level entrance
        Vector3d savedPosition = game.removeDisconnectedPosition(playerId);
        if (savedPosition != null) {
            Teleport tp = Teleport.createForPlayer(savedPosition, new Rotation3f());
            store.putComponent(ref, Teleport.getComponentType(), tp);
        } else {
            Level currentLevel = game.getCurrentLevel();
            if (currentLevel != null) {
                RoomData entrance = currentLevel.getEntranceRoom();
                if (entrance != null) {
                    Vector3i anchor = entrance.getAnchor();
                    Vector3d spawnPos = new Vector3d(anchor.x + 0.5, anchor.y + 1.0, anchor.z + 0.5);
                    Teleport tp = Teleport.createForPlayer(spawnPos, new Rotation3f());
                    store.putComponent(ref, Teleport.getComponentType(), tp);
                }
            }
        }

        LOGGER.info("restorePlayerToDungeon: restored " + playerRef.getUsername()
                + " into dungeon for party " + game.getPartyId());
        PartyUiPage.refreshOpenPages();
    }

    public void onPlayerAddedToWorld(@Nonnull PlayerRef playerRef, @Nonnull Player player, @Nonnull World world) {
        UUID playerId = playerRef.getUuid();
        Game game = findGameForPlayer(playerId);
        boolean isReconnecting = game != null && game.isDisconnectedPlayer(playerId);
        boolean hasSavedInventoryFile = inventoryService.hasSavedInventoryFile(playerId);
        boolean hasSavedInventory = hasSavedInventoryFile;
        boolean inActiveDungeonWorld = game != null
                && game.getState() != GameState.COMPLETE
                && world == game.getInstanceWorld();
        LOGGER.info("onPlayerAddedToWorld: player=" + playerId
                + " world=" + world.getName()
                + " gameState=" + (game != null ? game.getState() : null)
                + " inActiveDungeonWorld=" + inActiveDungeonWorld
                + " isPlayerInInstance=" + (game != null && game.isPlayerInInstance(playerId))
                + " isReconnecting=" + isReconnecting
                + " hasSavedInventory=" + hasSavedInventory
                + " fileSnapshot=" + hasSavedInventoryFile);

        // Player pending a rejoin decision (reconnecting after disconnect during dungeon).
        // Skip all handling here — cross-world teleports cannot be initiated during
        // AddPlayerToWorldEvent (the entity is mid-add). The exit will be performed
        // by handlePlayerReconnect once PlayerReadyEvent fires.
        if (pendingRejoinDecision.contains(playerId)) {
            LOGGER.info("[RECONNECT-DEBUG] onPlayerAddedToWorld: SKIPPING for player " + playerId
                    + " pending rejoin decision, world=" + world.getName()
                    + " inActiveDungeonWorld=" + inActiveDungeonWorld);
            return;
        }

        if (!inActiveDungeonWorld) {
            if (isReconnecting && pendingReconnectPlayers.contains(playerId)) {
                // Reconnecting player landed in a non-dungeon world temporarily (e.g. default world).
                // Skip normalization — they'll be teleported into the dungeon shortly.
                LOGGER.info("Skipping normalization for reconnecting player " + playerId + " in non-dungeon world.");
                return;
            }
            normalizeOutsideDungeonState(playerRef, player, game);
            return;
        }

        outsideDungeonNormalizedPlayers.remove(playerId);

        // Reconnecting player arriving in the dungeon world (after accepting rejoin popup).
        // Entity ref is NOT available during AddPlayerToWorldEvent — the entity
        // hasn't been added to the store yet (addToStore runs asynchronously in
        // onSetupPlayerJoining). Queue all ref-requiring setup to run during
        // handlePostReadyResync (PlayerReadyEvent) when the entity is fully valid.
        if (isReconnecting) {
            pendingReconnectPlayers.remove(playerId);
            pendingReadyRecoveryPlayers.remove(playerId);
            game.removeDisconnectedPlayer(playerId);
            game.setPlayerInInstance(playerId, true);
            pendingPostReadyResync.add(playerId);
            LOGGER.info("[RECONNECT-DEBUG] onPlayerAddedToWorld: deferred dungeon restore to PlayerReady for " + playerId
                    + " (entity ref not available during AddPlayerToWorldEvent)");
            return;
        }

        if (game.isPlayerInInstance(playerId) || !hasSavedInventory) {
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        DungeonConfig config = plugin.loadDungeonConfig();
        preparePlayerForDungeon(game, playerId, playerRef, player, ref, store, config);
        PartyUiPage.refreshOpenPages();
    }

    public void onPlayerRemovedFromWorld(@Nonnull PlayerRef playerRef, @Nonnull Player player, @Nonnull World world) {
        Game game = findGameForPlayer(playerRef.getUuid());
        LOGGER.info("onPlayerRemovedFromWorld: player=" + playerRef.getUuid()
                + " world=" + world.getName()
                + " gameState=" + (game != null ? game.getState() : null)
                + " isPlayerInInstance=" + (game != null && game.isPlayerInInstance(playerRef.getUuid()))
                + " playerWorld=" + (player.getWorld() != null ? player.getWorld().getName() : "null"));
        if (game == null || game.getState() == GameState.COMPLETE || world != game.getInstanceWorld()) {
            return;
        }
        if (!game.isPlayerInInstance(playerRef.getUuid())) {
            return;
        }

        // Player is still tracked as in-instance. Defer full cleanup to
        // onPlayerDisconnect (if disconnecting) or normalizeOutsideDungeonState
        // (if teleported elsewhere by admin). For alive players, snapshot
        // their dungeon inventory now while the entity is still valid so it
        // can be restored on reconnect.
        boolean alive = !game.isPlayerDead(playerRef.getUuid());
        if (alive) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
                if (deathComponent != null && deathComponent.isDead()) {
                    alive = false;
                }
            }
        }
        if (alive) {
            PlayerInventoryService.InventorySaveData snapshot =
                    inventoryService.snapshotCurrentInventory(player);
            if (snapshot != null) {
                game.putDisconnectedDungeonInventory(playerRef.getUuid(), snapshot);
                LOGGER.info("Snapshotted dungeon inventory for player " + playerRef.getUuid()
                        + " being removed from dungeon world.");
            }
            // Save the player's current position so we can teleport them back on rejoin
            Ref<EntityStore> refPos = playerRef.getReference();
            if (refPos != null && refPos.isValid()) {
                Store<EntityStore> storePos = refPos.getStore();
                TransformComponent transform = storePos.getComponent(refPos, TransformComponent.getComponentType());
                if (transform != null) {
                    game.putDisconnectedPosition(playerRef.getUuid(),
                            new Vector3d(transform.getPosition()));
                }
            }
        }
        LOGGER.info("Deferring dungeon exit cleanup for player " + playerRef.getUuid()
                + " to the appropriate exit handler.");
    }

    public boolean normalizeOutsideDungeonState(@Nonnull PlayerRef playerRef,
                                                @Nonnull Player player,
                                                @Nullable Game game) {
        return normalizeOutsideDungeonState(playerRef, player, game, null);
    }

    public boolean normalizeOutsideDungeonState(@Nonnull PlayerRef playerRef,
                                                @Nonnull Player player,
                                                @Nullable Game game,
                                                @Nullable CommandBuffer<EntityStore> commandBuffer) {
        UUID playerId = playerRef.getUuid();

        // Reconnecting players should not be normalized — they're about to be teleported to the dungeon.
        if (pendingReconnectPlayers.contains(playerId)) {
            LOGGER.info("Skipping normalizeOutsideDungeonState for reconnecting player " + playerId);
            return false;
        }

        // Player pending rejoin decision — defer normalization until they accept or decline.
        if (pendingRejoinDecision.contains(playerId)) {
            LOGGER.info("Skipping normalizeOutsideDungeonState for player pending rejoin decision " + playerId);
            return false;
        }

        // Player already inside the dungeon instance — skip normalization to avoid
        // undoing restorePlayerToDungeon state (e.g. if PlayerReadyEvent world
        // reference is stale during a cross-world teleport).
        if (game != null && game.isPlayerInInstance(playerId)) {
            LOGGER.info("Skipping normalizeOutsideDungeonState for in-instance player " + playerId);
            return false;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        Store<EntityStore> store = ref != null && ref.isValid() ? ref.getStore() : null;
        boolean inventoryLocked = plugin.getInventoryLockService().isLocked(playerId);
        boolean hasSavedInventory = inventoryService.hasSavedInventoryFile(playerId);
        boolean shouldNormalize = inventoryLocked || hasSavedInventory;
        boolean deadComponentDead = false;
        boolean gamePlayerInInstance = game != null && game.isPlayerInInstance(playerId);
        boolean gamePlayerDead = game != null && game.isPlayerDead(playerId);

        if (store != null && ref != null) {
            DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
            if (deathComponent != null && deathComponent.isDead()) {
                deadComponentDead = true;
                shouldNormalize = true;
            }
        }

        if (game != null) {
            shouldNormalize |= gamePlayerInInstance || gamePlayerDead;
        }

        if (!shouldNormalize) {
            outsideDungeonNormalizedPlayers.remove(playerId);
            return false;
        }

        if (pendingReadyRecoveryPlayers.contains(playerId)) {
            LOGGER.info("Deferring outside-dungeon normalization until PlayerReady for " + playerId);
            return false;
        }

        if (!outsideDungeonNormalizedPlayers.add(playerId)) {
            if (game != null) {
                game.removeDeadPlayer(playerId);
                game.setPlayerInInstance(playerId, false);
            }
            return false;
        }

        LOGGER.info("normalizeOutsideDungeonState: player=" + playerId
                + " username=" + playerRef.getUsername()
                + " currentWorld=" + (player.getWorld() != null ? player.getWorld().getName() : "null")
                + " gameState=" + (game != null ? game.getState() : null)
                + " instanceWorld=" + (game != null && game.getInstanceWorld() != null ? game.getInstanceWorld().getName() : "null")
                + " inventoryLocked=" + inventoryLocked
                + " hasSavedInventory=" + hasSavedInventory
                + " deathComponentDead=" + deadComponentDead
                + " gamePlayerInInstance=" + gamePlayerInInstance
                + " gamePlayerDead=" + gamePlayerDead
                + " deleteAfterRestore=" + (game == null || game.getState() == GameState.COMPLETE));

        plugin.getCameraService().restoreDefault(playerRef);
        playerStateService.hideDungeonHuds(player, playerRef);
        plugin.getInventoryLockService().unlock(player, playerId);

        if (hasSavedInventory) {
            boolean deleteAfterRestore = game == null || game.getState() == GameState.COMPLETE;
            inventoryService.restorePlayerInventory(playerId, player, deleteAfterRestore);
        }

        if (ref != null && ref.isValid() && store != null) {
            playerStateService.resetPlayerStatus(player, ref, store, commandBuffer);
        }

        if (game != null) {
            game.removeDeadPlayer(playerId);
            game.setPlayerInInstance(playerId, false);
        }

        LOGGER.info("Normalized player state for " + playerRef.getUsername()
                + " upon arriving in non-dungeon world.");
        return true;
    }

    public void sendPlayerToDungeon(@Nonnull Game game,
                                    @Nonnull PlayerRef playerRef,
                                    @Nonnull Ref<EntityStore> ref,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull CompletableFuture<World> readyFuture,
                                    @Nullable Consumer<String> failureConsumer) {
        UUID playerId = playerRef.getUuid();
        World currentWorld = store.getExternalData().getWorld();

        readyFuture.whenComplete((targetWorld, throwable) -> {
            if (throwable != null) {
                LOGGER.log(java.util.logging.Level.WARNING, "Failed to prepare dungeon instance", throwable);
                if (failureConsumer != null) {
                    failureConsumer.accept("Dungeon creation failed.");
                }
                return;
            }

            currentWorld.execute(() -> {
                if (!ref.isValid()) {
                    return;
                }

                Player currentPlayer = store.getComponent(ref, Player.getComponentType());
                if (currentPlayer == null) {
                    return;
                }

                // Only capture return point if not already set (reconnecting players keep their original home)
                if (!game.getReturnPoints().containsKey(playerId)) {
                    Transform returnPoint = DungeonInstanceService.captureReturnPoint(store, ref);
                    game.getReturnPoints().put(playerId, returnPoint);
                    game.getReturnWorlds().put(playerId, currentWorld);
                }
                Transform returnPoint = game.getReturnPoints().get(playerId);

                try {
                    InstancesPlugin.teleportPlayerToInstance(ref, store, targetWorld, returnPoint);
                } catch (Exception exception) {
                    LOGGER.log(java.util.logging.Level.WARNING, "Failed to send player to ready dungeon instance", exception);
                    if (failureConsumer != null) {
                        failureConsumer.accept("Teleport to dungeon failed.");
                    }
                }
            });
        });
    }

    private void teleportPlayerToReturnPoint(@Nonnull Ref<EntityStore> ref,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull World returnWorld,
                                             @Nonnull Transform returnPoint) {
        Teleport teleport = Teleport.createForPlayer(returnWorld, returnPoint);
        store.putComponent(ref, Teleport.getComponentType(), teleport);
    }

    public void onInstanceWorldRemoved(@Nonnull World world) {
        List<Game> removedGames = activeGames.values().stream()
                .filter(game -> game.getInstanceWorld() == world)
                .toList();

        for (Game game : removedGames) {
            LOGGER.warning("Dungeon instance world was removed for party " + game.getPartyId() + ". Ending the run and closing the party.");
            game.setInstanceWorld(null);
            // endGame accesses player stores that may live on a different world
            // thread. Defer cleanup so it runs without cross-thread store access.
            try {
                endGame(game, true);
            } catch (Exception e) {
                LOGGER.severe("Failed to end game for party " + game.getPartyId() + " during world removal: " + e.getMessage());
                // Still clean up the game mapping so it doesn't leak.
                activeGames.remove(game.getPartyId());
            }
        }
    }

    @Nonnull
    public List<Ref<EntityStore>> getDeadPlayerRefsInStore(@Nonnull Store<EntityStore> store) {
        List<Ref<EntityStore>> deadPlayerRefs = new ArrayList<>();

        for (Game game : activeGames.values()) {
            if (game.getState() != GameState.ACTIVE && game.getState() != GameState.BOSS) {
                continue;
            }

            for (UUID deadPlayerId : new ArrayList<>(game.getDeadPlayers())) {
                PlayerRef playerRef = Universe.get().getPlayer(deadPlayerId);
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }

                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid() || ref.getStore() != store) {
                    continue;
                }

                DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
                if (deathComponent == null || !deathComponent.isDead()) {
                    continue;
                }

                deadPlayerRefs.add(ref);
            }
        }

        return deadPlayerRefs;
    }

    // ────────────────────────────────────────────────
    //  Inventory save / restore
    // ────────────────────────────────────────────────

    private void preparePlayerForDungeon(@Nonnull Game game,
                                         @Nonnull UUID playerId,
                                         @Nonnull PlayerRef playerRef,
                                         @Nonnull Player player,
                                         @Nonnull Ref<EntityStore> ref,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull DungeonConfig config) {
        outsideDungeonNormalizedPlayers.remove(playerId);
        if (!inventoryService.hasSavedInventoryFile(playerId)) {
            inventoryService.savePlayerInventory(playerId, player, game);
        }
        plugin.getInventoryLockService().unlock(player, playerId);
        inventoryService.clearPlayerInventory(player);
        playerStateService.resetPlayerStatus(player, ref, store);
        inventoryService.giveStartEquipment(playerRef, player, config);
        plugin.getInventoryLockService().lock(player, playerId);
        plugin.getCameraService().setEnabled(playerRef, true);
        playerStateService.applyDungeonMovementSettings(ref, store, playerRef);
        playerStateService.enableMap(playerRef);
        plugin.getDungeonMapService().sendMapToPlayer(playerRef, game);
        game.setPlayerInInstance(playerId, true);
    }

    // ────────────────────────────────────────────────
    //  Boss room sealing
    // ────────────────────────────────────────────────

    private void sealBossRoom(@Nonnull Game game, @Nonnull RoomData bossRoom, @Nonnull World world) {
        if (game.isBossRoomSealed()) return;

        Vector3i anchor = bossRoom.getAnchor();
        int rotation = bossRoom.getRotation();
        DungeonConfig config = plugin.loadDungeonConfig();
        String wallBlock = config.getBossWallBlock();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                try {
                    int wx = anchor.x + rotateLocalX(dx, -1, rotation);
                    int wz = anchor.z + rotateLocalZ(dx, -1, rotation);
                    world.setBlock(wx, anchor.y + dy, wz, wallBlock, 0);
                } catch (Exception e) {
                    LOGGER.log(java.util.logging.Level.FINE, "Failed to seal block at offset " + dx + "," + dy, e);
                }
            }
        }

        game.setBossRoomSealed(true);
        LOGGER.info("Boss room sealed at " + anchor);
    }

    private void unsealBossRoom(@Nonnull Game game, @Nullable RoomData bossRoom, @Nonnull World world) {
        if (!game.isBossRoomSealed() || bossRoom == null) return;

        Vector3i anchor = bossRoom.getAnchor();
        int rotation = bossRoom.getRotation();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                try {
                    int wx = anchor.x + rotateLocalX(dx, -1, rotation);
                    int wz = anchor.z + rotateLocalZ(dx, -1, rotation);
                    world.setBlock(wx, anchor.y + dy, wz, DungeonConstants.EMPTY_BLOCK, 0);
                } catch (Exception e) {
                    LOGGER.log(java.util.logging.Level.FINE, "Failed to unseal block at offset " + dx + "," + dy, e);
                }
            }
        }

        game.setBossRoomSealed(false);
        LOGGER.info("Boss room unsealed at " + anchor);
    }

    // ────────────────────────────────────────────────
    //  Lookups
    // ────────────────────────────────────────────────

    @Nullable
    public Game findGameForPlayer(@Nonnull UUID playerId) {
        UUID partyId = playerToParty.get(playerId);
        return partyId != null ? activeGames.get(partyId) : null;
    }

    @Nullable
    public Game findGameForParty(@Nonnull UUID partyId) {
        return activeGames.get(partyId);
    }

    @Nullable
    public Game findGameForWorld(@Nonnull World world) {
        for (Game game : activeGames.values()) {
            if (game.getInstanceWorld() == world && game.getState() != GameState.COMPLETE) {
                return game;
            }
        }
        return null;
    }

    public boolean hasActiveGame(@Nonnull UUID partyId) {
        return activeGames.containsKey(partyId);
    }

    @Nonnull
    public Map<UUID, Game> getActiveGames() {
        return activeGames;
    }

    // ────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────

    @Nullable
    private DungeonConfig.LevelConfig resolveLevelConfig(@Nonnull DungeonConfig config, int levelIndex) {
        List<DungeonConfig.LevelConfig> levels = config.getLevels();
        return levelIndex >= 0 && levelIndex < levels.size() ? levels.get(levelIndex) : null;
    }

    @Nullable
    private DungeonConfig.LevelConfig resolveCurrentLevelConfig(@Nonnull DungeonConfig config, @Nonnull Game game) {
        String selector = game.getCurrentLevelSelector();
        if (selector != null && !selector.isBlank()) {
            DungeonConfig.LevelConfig levelConfig = config.findLevel(selector);
            if (levelConfig != null) {
                return levelConfig;
            }
            LOGGER.warning("Could not resolve current level config for selector '" + selector
                    + "' in party " + game.getPartyId() + ". Falling back to index " + game.getCurrentLevelIndex());
        }
        return resolveLevelConfig(config, game.getCurrentLevelIndex());
    }

    @Nullable
    private DungeonConfig.LevelConfig resolveNextLevelConfig(@Nonnull DungeonConfig config, @Nonnull Game game) {
        DungeonConfig.LevelConfig currentLevelConfig = resolveCurrentLevelConfig(config, game);
        if (currentLevelConfig == null) {
            return null;
        }
        String nextLevelSelector = currentLevelConfig.getNextLevel();
        if (nextLevelSelector == null) {
            return null;
        }
        DungeonConfig.LevelConfig nextLevelConfig = config.findLevel(nextLevelSelector);
        if (nextLevelConfig == null) {
            LOGGER.warning("Level '" + currentLevelConfig.getSelector()
                    + "' points to unknown nextLevel '" + nextLevelSelector + "'.");
        }
        return nextLevelConfig;
    }

    private boolean isDungeonJoinable(@Nonnull Game game) {
        return switch (game.getState()) {
            case READY, ACTIVE, BOSS, TRANSITIONING -> game.getInstanceWorld() != null;
            default -> false;
        };
    }

    private boolean closeGameIfInstanceEmpty(@Nonnull Game game) {
        if (!isDungeonJoinable(game) || !game.getPlayersInInstance().isEmpty()) {
            return false;
        }

        // Don't close the game if disconnected players may still reconnect
        if (!game.getDisconnectedPlayers().isEmpty()) {
            return false;
        }

        if (game.getVictoryTimestamp() > 0 && !hasNextLevelConfig(game)) {
            LOGGER.info("All players exited completed dungeon instance for party " + game.getPartyId() + ". Cleaning up finished run.");
            cleanupCompletedRun(game);
            return true;
        }

        LOGGER.info("No players remain inside dungeon instance for party " + game.getPartyId() + ". Ending run.");
        endGame(game, true);
        return true;
    }

    private void cleanupCompletedRun(@Nonnull Game game) {
        if (game.getState() != GameState.COMPLETE) {
            game.setState(GameState.COMPLETE);
        }
        game.clearDeadPlayers();

        plugin.getDungeonMapService().cleanup(game.getPartyId());
        activeGames.remove(game.getPartyId());

        World instanceWorld = game.getInstanceWorld();
        if (instanceWorld != null) {
            instanceWorld.execute(() -> InstancesPlugin.safeRemoveInstance(instanceWorld));
        }

        playerToParty.entrySet().removeIf(e -> e.getValue().equals(game.getPartyId()));
        plugin.getPartyManager().closePartyForSystem(game.getPartyId(), "The dungeon run ended, so the party was closed.");
        PartyUiPage.refreshOpenPages();

        LOGGER.info("Completed game cleaned up for party " + game.getPartyId());
    }

    public void broadcastToParty(@Nonnull UUID partyId, @Nonnull String text, @Nonnull String color) {
        for (Map.Entry<UUID, UUID> entry : playerToParty.entrySet()) {
            if (entry.getValue().equals(partyId)) {
                PlayerRef playerRef = Universe.get().getPlayer(entry.getKey());
                if (playerRef != null) {
                    PlayerEventNotifier.showEventTitle(playerRef, text, true);
                }
            }
        }
    }

    /**
     * Shutdown: clean up all active games, restore inventories.
     */
    public void shutdown() {
        inventoryService.clearDeathSnapshots();
        for (Game game : activeGames.values()) {
            showAllPartyMembers(game.getPartyId());
        }

        for (PlayerRef playerRef : OnlinePlayers.snapshot()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }

            UUID playerId = playerRef.getUuid();
            Game game = findGameForPlayer(playerId);
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }

            Store<EntityStore> store = ref.getStore();
            store.getExternalData().getWorld().execute(() -> {
                if (!ref.isValid()) {
                    return;
                }

                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    return;
                }

                boolean hasSavedInventory = inventoryService.hasSavedInventoryFile(playerId);
                boolean inventoryLocked = plugin.getInventoryLockService().isLocked(playerId);
                DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
                boolean customDead = deathComponent != null && deathComponent.isDead();

                if (game == null && !hasSavedInventory && !inventoryLocked && !customDead) {
                    return;
                }

                reviveMarkerService.despawnReviveMarker(playerId);
                plugin.getCameraService().restoreDefault(playerRef);
                playerStateService.hideDungeonHuds(player, playerRef);
                plugin.getInventoryLockService().unlock(player, playerId);

                if (hasSavedInventory) {
                    inventoryService.restorePlayerInventory(playerId, player, false);
                }

                playerStateService.resetPlayerStatus(player, ref, store);

                if (game != null) {
                    game.removeDeadPlayer(playerId);
                    game.setPlayerInInstance(playerId, false);
                }
            });
        }

        for (Game game : new ArrayList<>(activeGames.values())) {
            if (game.getState() != GameState.COMPLETE) {
                LOGGER.info("Shutting down active game for party " + game.getPartyId());
                game.setState(GameState.COMPLETE);
            }
        }
        activeGames.clear();
        playerToParty.clear();
        reviveMarkerService.clear();
        outsideDungeonNormalizedPlayers.clear();
        pendingReadyRecoveryPlayers.clear();
    }

    // ────────────────────────────────────────────────
    //  Death / Revive
    // ────────────────────────────────────────────────

    /**
     * Called when all players in the dungeon instance are dead or ghosts.
     * Ends the run and closes the party.
     */
    public void onAllPlayersDead(@Nonnull Game game) {
        if (game.getState() == GameState.COMPLETE) return;

        broadcastToParty(game.getPartyId(),
                "All players have fallen! The dungeon run is over.", DungeonConstants.COLOR_DANGER);
        endGame(game, true);
    }

    /**
     * Revives all dead/ghost players in the given game, restoring health and equipment.
     */
    public void reviveAllDeadPlayers(@Nonnull Game game) {
        Set<UUID> dead = new java.util.HashSet<>(game.getDeadPlayers());
        if (dead.isEmpty()) return;

        DungeonConfig config = plugin.loadDungeonConfig();

        for (UUID playerId : dead) {
            reviveMarkerService.despawnReviveMarker(playerId);
            showPlayerToParty(playerId, game.getPartyId());

            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            if (playerRef == null || !playerRef.isValid()) continue;

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;

            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) continue;

            // Reset DeathComponent
            DeathComponent deathComp = store.getComponent(ref, DeathComponent.getComponentType());
            if (deathComp != null) {
                deathComp.revive();
            }
            DeathStateController.clear(store, ref);

            // Restore health/stamina
            playerStateService.resetPlayerStatus(player, ref, store);

            // Discard the death snapshot — level-change revive gives fresh start equipment
            inventoryService.discardDeathInventory(playerId);

            // Give back start equipment
            plugin.getInventoryLockService().unlock(player, playerId);
            inventoryService.clearPlayerInventory(player);
            inventoryService.giveStartEquipment(playerRef, player, config);
            plugin.getInventoryLockService().lock(player, playerId);

            // Re-apply dungeon movement and camera
            playerStateService.applyDungeonMovementSettings(ref, store, playerRef);
            plugin.getCameraService().setEnabled(playerRef, true);
        }

        game.clearDeadPlayers();
        broadcastToParty(game.getPartyId(),
                "All fallen players have been revived!", DungeonConstants.COLOR_SUCCESS);
    }

    /**
     * Hides a dead player's entity from all other party members so they cannot
     * see the ghost moving around.
     */
    public void hideDeadPlayerFromParty(@Nonnull UUID deadPlayerId, @Nonnull UUID partyId) {
        for (Map.Entry<UUID, UUID> entry : playerToParty.entrySet()) {
            if (!entry.getValue().equals(partyId)) continue;
            UUID otherId = entry.getKey();
            if (otherId.equals(deadPlayerId)) continue;

            PlayerRef otherRef = Universe.get().getPlayer(otherId);
            if (otherRef != null && otherRef.isValid()) {
                otherRef.getHiddenPlayersManager().hidePlayer(deadPlayerId);
            }
        }
    }

    /**
     * Shows a previously-hidden player to all other party members (e.g. after revive).
     */
    public void showPlayerToParty(@Nonnull UUID playerId, @Nonnull UUID partyId) {
        for (Map.Entry<UUID, UUID> entry : playerToParty.entrySet()) {
            if (!entry.getValue().equals(partyId)) continue;
            UUID otherId = entry.getKey();
            if (otherId.equals(playerId)) continue;

            PlayerRef otherRef = Universe.get().getPlayer(otherId);
            if (otherRef != null && otherRef.isValid()) {
                otherRef.getHiddenPlayersManager().showPlayer(playerId);
            }
        }
    }

    /**
     * Shows all party members to each other — used during game cleanup to
     * undo any death-related hiding.
     */
    private void showAllPartyMembers(@Nonnull UUID partyId) {
        List<UUID> members = playerToParty.entrySet().stream()
                .filter(e -> e.getValue().equals(partyId))
                .map(Map.Entry::getKey)
                .toList();
        for (UUID memberId : members) {
            for (UUID otherId : members) {
                if (memberId.equals(otherId)) continue;
                PlayerRef otherRef = Universe.get().getPlayer(otherId);
                if (otherRef != null && otherRef.isValid()) {
                    otherRef.getHiddenPlayersManager().showPlayer(memberId);
                }
            }
        }
    }

    @Nonnull
    public PlayerInventoryService getInventoryService() {
        return inventoryService;
    }

    @Nonnull
    public MobSpawningService getMobSpawningService() {
        return mobSpawningService;
    }

    @Nonnull
    public ReviveMarkerService getReviveMarkerService() {
        return reviveMarkerService;
    }

    @Nonnull
    public PlayerStateService getPlayerStateService() {
        return playerStateService;
    }
}
