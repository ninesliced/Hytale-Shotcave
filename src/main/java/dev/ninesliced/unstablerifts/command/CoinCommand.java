package dev.ninesliced.unstablerifts.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;
import dev.ninesliced.unstablerifts.coin.CoinScoreService;
import dev.ninesliced.unstablerifts.dungeon.Game;
import dev.ninesliced.unstablerifts.dungeon.GameState;
import dev.ninesliced.unstablerifts.logging.UnstableRiftsLog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Debug commands for viewing and modifying coin scores.
 */
public class CoinCommand extends AbstractCommandCollection {

    public CoinCommand() {
        super("coins", "View or modify coins and dungeon team money (debug)");
        this.addAliases("coin", "score");
        this.addSubCommand(new Get());
        this.addSubCommand(new Set());
        this.addSubCommand(new Add());
        this.addSubCommand(new Reset());
        this.addSubCommand(new ListAll());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }

    @Nullable
    private static Game resolveDungeonGame(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        UnstableRifts plugin = UnstableRifts.getInstance();
        if (plugin == null) {
            return null;
        }

        Game game = plugin.getGameManager().findGameForPlayer(playerRef.getUuid());
        if (game == null || game.getState() == GameState.COMPLETE) {
            return null;
        }

        World instanceWorld = game.getInstanceWorld();
        if (instanceWorld == null || instanceWorld != world) {
            return null;
        }

        return game;
    }

    static class Get extends AbstractPlayerCommand {

        Get() {
            super("get", "Show your current coins or dungeon team money");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected String generatePermissionNode() {
            return "";
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            Game game = resolveDungeonGame(playerRef, world);
            if (game != null) {
                context.sendMessage(Message.raw("Your team money: " + game.getMoney()).color(Color.YELLOW));
                return;
            }

            int score = CoinScoreService.getScore(playerRef.getUuid());
            context.sendMessage(Message.raw("Your coin score: " + score).color(Color.YELLOW));
        }
    }

    static class Set extends AbstractPlayerCommand {

        private static final HytaleLogger LOGGER = UnstableRiftsLog.forModule("Command");

        @Nonnull
        private final RequiredArg<Integer> amountArg = this.withRequiredArg(
                "amount", "The coin or team money value to set", ArgTypes.INTEGER);

        Set() {
            super("set", "Set your coins or active dungeon team money to a specific value");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected String generatePermissionNode() {
            return "";
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            int amount = this.amountArg.get(context);
            Game game = resolveDungeonGame(playerRef, world);
            if (game != null) {
                long newAmount = Math.max(0L, amount);
                synchronized (game) {
                    game.setMoney(newAmount);
                }
                context.sendMessage(Message.raw("Team money set to " + newAmount + ".").color(Color.GREEN));
                LOGGER.at(Level.INFO).log("[CoinCommand] %s set team money to %d",
                        playerRef.getUsername(), newAmount);
                return;
            }

            int newScore = CoinScoreService.setScore(playerRef.getUuid(), amount);
            context.sendMessage(Message.raw("Coin score set to " + newScore + ".").color(Color.GREEN));
            LOGGER.at(Level.INFO).log("[CoinCommand] %s set their coin score to %d",
                    playerRef.getUsername(), newScore);
        }
    }

    static class Add extends AbstractPlayerCommand {

        private static final HytaleLogger LOGGER = UnstableRiftsLog.forModule("Command");

        @Nonnull
        private final RequiredArg<Integer> amountArg = this.withRequiredArg(
                "amount", "The number of coins to add to your score or team money", ArgTypes.INTEGER);

        Add() {
            super("add", "Add coins to your score or active dungeon team money");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected String generatePermissionNode() {
            return "";
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            int amount = this.amountArg.get(context);
            if (amount <= 0) {
                context.sendMessage(Message.raw("Amount must be a positive integer.").color(Color.RED));
                return;
            }

            Game game = resolveDungeonGame(playerRef, world);
            if (game != null) {
                long newTotal;
                synchronized (game) {
                    newTotal = game.addMoney(amount);
                }
                context.sendMessage(Message.raw("Added " + amount + " coins to team money. New total: " + newTotal + ".")
                        .color(Color.GREEN));
                LOGGER.at(Level.INFO).log("[CoinCommand] %s added %d coins to team money (total: %d)",
                        playerRef.getUsername(), amount, newTotal);
                return;
            }

            int newTotal = CoinScoreService.addCoins(playerRef.getUuid(), amount);
            context.sendMessage(Message.raw("Added " + amount + " coins. New total: " + newTotal + ".")
                    .color(Color.GREEN));
            LOGGER.at(Level.INFO).log("[CoinCommand] %s added %d coins (total: %d)",
                    playerRef.getUsername(), amount, newTotal);
        }
    }

    static class Reset extends AbstractPlayerCommand {

        private static final HytaleLogger LOGGER = UnstableRiftsLog.forModule("Command");

        Reset() {
            super("reset", "Reset your coins or active dungeon team money to 0");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected String generatePermissionNode() {
            return "";
        }

        @Override
        protected void execute(
                @Nonnull CommandContext context,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef playerRef,
                @Nonnull World world) {

            Game game = resolveDungeonGame(playerRef, world);
            if (game != null) {
                synchronized (game) {
                    game.setMoney(0L);
                }
                context.sendMessage(Message.raw("Team money reset to 0.").color(Color.YELLOW));
                LOGGER.at(Level.INFO).log("[CoinCommand] %s reset team money", playerRef.getUsername());
                return;
            }

            CoinScoreService.reset(playerRef.getUuid());
            context.sendMessage(Message.raw("Coin score reset to 0.").color(Color.YELLOW));
            LOGGER.at(Level.INFO).log("[CoinCommand] %s reset their coin score", playerRef.getUsername());
        }
    }

    static class ListAll extends CommandBase {

        ListAll() {
            super("all", "List all players' coin scores");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected String generatePermissionNode() {
            return "";
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            UnstableRifts plugin = UnstableRifts.getInstance();
            boolean sentAny = false;

            if (plugin != null && !plugin.getGameManager().getActiveGames().isEmpty()) {
                context.sendMessage(Message.raw("=== Active Dungeon Team Money ===").color(Color.ORANGE));
                plugin.getGameManager().getActiveGames().forEach((partyId, game) -> context.sendMessage(
                        Message.raw("  Party " + partyId + ": " + game.getMoney()).color(Color.WHITE)));
                sentAny = true;
            }

            Map<UUID, Integer> allScores = CoinScoreService.getAllScores();
            if (!allScores.isEmpty()) {
                context.sendMessage(Message.raw("=== All Coin Scores ===").color(Color.ORANGE));
                allScores.forEach((playerUuid, score) -> context
                        .sendMessage(Message.raw("  " + playerUuid + ": " + score).color(Color.WHITE)));
                context.sendMessage(Message.raw("Total players: " + allScores.size()).color(Color.GRAY));
                sentAny = true;
            }

            if (!sentAny) {
                context.sendMessage(Message.raw("No coin data recorded.").color(Color.GRAY));
            }
        }
    }
}
