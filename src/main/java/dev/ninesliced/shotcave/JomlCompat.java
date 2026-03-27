package dev.ninesliced.shotcave;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nonnull;

public final class JomlCompat {

    private JomlCompat() {
    }

    @Nonnull
    public static Vector3d lookDirection(float yaw, float pitch) {
        double horizontal = Math.cos(pitch);
        return new Vector3d(
                horizontal * -Math.sin(yaw),
                Math.sin(pitch),
                horizontal * -Math.cos(yaw)
        );
    }

    @Nonnull
    public static Vector3d addScaled(@Nonnull Vector3d base, @Nonnull Vector3dc delta, double scale) {
        return base.fma(scale, delta);
    }

    public static double distance(@Nonnull Vector3dc a, @Nonnull Vector3dc b) {
        return a.distance(b);
    }

    public static double distanceSquared(@Nonnull Vector3dc a, @Nonnull Vector3dc b) {
        return a.distanceSquared(b);
    }

    public static double distanceSquared(@Nonnull Vector3dc a, double x, double y, double z) {
        return a.distanceSquared(x, y, z);
    }

    public static double lengthSquared(@Nonnull Vector3dc v) {
        return v.lengthSquared();
    }
}
