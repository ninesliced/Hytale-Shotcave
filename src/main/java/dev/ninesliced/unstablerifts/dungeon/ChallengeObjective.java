package dev.ninesliced.unstablerifts.dungeon;

import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * A single objective within a challenge room.
 */
public final class ChallengeObjective {

    private final Type type;
    private final Vector3i position;
    private boolean completed;

    public ChallengeObjective(@Nonnull Type type, @Nonnull Vector3i position) {
        this.type = type;
        this.position = position;
    }

    @Nonnull
    public Type getType() {
        return type;
    }

    @Nonnull
    public Vector3i getPosition() {
        return position;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void complete() {
        this.completed = true;
    }

    /**
     * Human-readable description for the challenge HUD.
     */
    @Nonnull
    public String getDisplayName() {
        return switch (type) {
            case ACTIVATION_ZONE -> "Reach the activation zone";
            case MOB_CLEAR -> "Clear all enemies";
        };
    }

    public enum Type {
        /**
         * Player must enter an activation zone.
         */
        ACTIVATION_ZONE,
        /**
         * All mobs in the room must be cleared.
         */
        MOB_CLEAR
    }
}
