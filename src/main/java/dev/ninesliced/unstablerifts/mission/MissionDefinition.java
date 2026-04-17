package dev.ninesliced.unstablerifts.mission;

import javax.annotation.Nonnull;

/**
 * Immutable definition of a single mission objective loaded from {@code missions.json}.
 *
 * @param id          Unique mission identifier (e.g. {@code "kill_100_enemies"}).
 * @param type        Category of the objective.
 * @param target      Number the player must reach to complete the mission.
 * @param rewardCoins Number of {@code UnstableRifts_Rift_Coin} items awarded on claim.
 * @param displayName Human-readable name shown in the UI.
 * @param description Short flavour/description text.
 */
public record MissionDefinition(
        @Nonnull String id,
        @Nonnull MissionType type,
        int target,
        int rewardCoins,
        @Nonnull String displayName,
        @Nonnull String description) {
}
