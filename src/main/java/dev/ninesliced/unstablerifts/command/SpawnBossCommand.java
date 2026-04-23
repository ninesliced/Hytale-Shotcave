package dev.ninesliced.unstablerifts.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.Map;

/**
 * /ur spawnboss <name> — Spawns a boss NPC at the player's position without the Frozen component,
 * so its AI ticks immediately (unlike the EntitySpawnPage which freezes spawned NPCs).
 */
public final class SpawnBossCommand extends AbstractPlayerCommand {

    private static final Map<String, String> BOSS_ALIASES = Map.of(
            "excavator", "Boss_Excavator",
            "forklift", "Boss_Forklift",
            "zombie_commander", "Boss_Zombie_Commander",
            "ceotank", "Boss_CEO_Tank",
            "ceo_tank", "Boss_CEO_Tank"
    );

    private static final Map<String, String> BOSS_DISPLAY_NAMES = Map.of(
            "Boss_Excavator", "Excavator",
            "Boss_Forklift", "Forklift",
            "Boss_Zombie_Commander", "Zombie Commander",
            "Boss_CEO_Tank", "CEO Tank"
    );

    @Nonnull
    private final RequiredArg<String> bossArg = this.withRequiredArg(
            "boss", "Boss name (excavator, forklift, zombie_commander, ceotank) or full NPC role id",
            ArgTypes.STRING);

    public SpawnBossCommand() {
        super("spawnboss", "Spawn a boss NPC at your position (AI active)");
        this.addAliases("sb");
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
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        String input = this.bossArg.get(context);
        if (input == null || input.isBlank()) {
            context.sendMessage(Message.raw("Usage: /ur spawnboss <excavator|forklift|zombie_commander|ceotank>").color(Color.RED));
            return;
        }

        // Resolve alias to full role id, or use the input directly
        String roleId = BOSS_ALIASES.getOrDefault(input.toLowerCase(), input);

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not resolve player position.").color(Color.RED));
            return;
        }

        Vector3d position = new Vector3d(transform.getPosition());
        // Offset slightly in front of the player so the boss doesn't spawn inside them
        position.add(0, 0, 2);

        try {
            var result = NPCPlugin.get().spawnNPC(store, roleId, null, position, Rotation3f.ZERO);
            if (result == null || result.first() == null) {
                context.sendMessage(Message.raw("Failed to spawn '" + roleId + "'. Check that the NPC role exists.").color(Color.RED));
                return;
            }

            Ref<EntityStore> mobRef = result.first();

            // Apply display name if it's a known boss
            String displayName = BOSS_DISPLAY_NAMES.get(roleId);
            if (displayName != null) {
                store.putComponent(mobRef,
                        DisplayNameComponent.getComponentType(),
                        new DisplayNameComponent(Message.raw(displayName)));
            }

            context.sendMessage(Message.raw("Spawned " + roleId + " at your position.").color(Color.GREEN));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error spawning '" + roleId + "': " + e.getMessage()).color(Color.RED));
        }
    }
}
