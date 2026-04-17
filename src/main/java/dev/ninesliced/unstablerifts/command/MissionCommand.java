package dev.ninesliced.unstablerifts.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ninesliced.unstablerifts.UnstableRifts;
import dev.ninesliced.unstablerifts.mission.MissionDataComponent;
import dev.ninesliced.unstablerifts.mission.MissionService;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.List;

/**
 * Admin/debug commands for the quest system.
 */
public class MissionCommand extends AbstractCommandCollection {

    public MissionCommand() {
        super("missions", "View or modify quest progress (debug)");
        this.addAliases("mission", "quests", "quest");
        this.addSubCommand(new Status());
        this.addSubCommand(new ResetCmd());
        this.addSubCommand(new SetProgress());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected String generatePermissionNode() {
        return "";
    }

    static class Status extends AbstractPlayerCommand {

        Status() {
            super("status", "Show your current active quests");
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

            UnstableRifts plugin = UnstableRifts.getInstance();
            if (plugin == null) return;

            List<MissionService.QuestStatus> statuses =
                    plugin.getMissionService().getActiveQuestStatuses(store, ref);

            if (statuses.isEmpty()) {
                context.sendMessage(Message.raw("No active quests.").color(Color.YELLOW));
                return;
            }

            context.sendMessage(Message.raw("=== Active Quests ===").color(Color.YELLOW));
            for (MissionService.QuestStatus qs : statuses) {
                String status;
                Color color;
                int clamped = Math.min(qs.currentProgress(), qs.definition().target());
                if (qs.isComplete()) {
                    status = "READY TO CLAIM";
                    color = Color.GREEN;
                } else {
                    status = clamped + "/" + qs.definition().target();
                    color = Color.WHITE;
                }
                context.sendMessage(Message.raw(
                        "[" + qs.slotIndex() + "] " + qs.definition().displayName() + " — " + status
                                + " [" + qs.definition().rewardCoins() + " coins]"
                ).color(color));
            }
        }
    }

    static class ResetCmd extends AbstractPlayerCommand {

        ResetCmd() {
            super("reset", "Reset all quest progress for yourself");
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

            MissionDataComponent data = store.ensureAndGetComponent(ref, MissionDataComponent.getComponentType());
            if (data != null) {
                data.reset();
                context.sendMessage(Message.raw("All quest progress reset.").color(Color.GREEN));
            } else {
                context.sendMessage(Message.raw("No quest data found.").color(Color.RED));
            }
        }
    }

    static class SetProgress extends AbstractPlayerCommand {

        @Nonnull
        private final RequiredArg<String> typeArg = this.withRequiredArg(
                "type", "KILL_ENEMIES, KILL_BOSSES, or COMPLETE_DUNGEONS", ArgTypes.STRING);

        @Nonnull
        private final RequiredArg<Integer> valueArg = this.withRequiredArg(
                "value", "Value to set as the progress counter", ArgTypes.INTEGER);

        SetProgress() {
            super("setprogress", "Set a specific progress counter");
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

            String typeStr = this.typeArg.get(context);
            int value = this.valueArg.get(context);

            MissionDataComponent data = store.ensureAndGetComponent(ref, MissionDataComponent.getComponentType());
            if (data == null) {
                context.sendMessage(Message.raw("No quest data found.").color(Color.RED));
                return;
            }

            switch (typeStr.toUpperCase()) {
                case "KILL_ENEMIES" -> {
                    data.reset();
                    data.addEnemiesKilled(value);
                    context.sendMessage(Message.raw("Set total enemies killed to " + value).color(Color.GREEN));
                }
                case "KILL_BOSSES" -> {
                    data.reset();
                    data.addBossesKilled(value);
                    context.sendMessage(Message.raw("Set total bosses killed to " + value).color(Color.GREEN));
                }
                case "COMPLETE_DUNGEONS" -> {
                    data.reset();
                    data.addDungeonsCompleted(value);
                    context.sendMessage(Message.raw("Set total dungeons completed to " + value).color(Color.GREEN));
                }
                default -> context.sendMessage(Message.raw("Unknown type: " + typeStr
                        + ". Use KILL_ENEMIES, KILL_BOSSES, or COMPLETE_DUNGEONS.").color(Color.RED));
            }
        }
    }
}
