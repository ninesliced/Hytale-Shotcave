package dev.ninesliced.unstablerifts.dungeon;

/**
 * Shared local-space rotation helpers for quarter-turn prefab and room math.
 */
public final class RotationUtil {

    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private RotationUtil() {
    }

    public static int rotateLocalX(int x, int z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> z;
            case 2 -> -x;
            case 3 -> -z;
            default -> x;
        };
    }

    public static int rotateLocalZ(int x, int z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> -x;
            case 2 -> -z;
            case 3 -> x;
            default -> z;
        };
    }

    public static double rotateLocalX(double x, double z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> z;
            case 2 -> -x;
            case 3 -> -z;
            default -> x;
        };
    }

    public static double rotateLocalZ(double x, double z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> -x;
            case 2 -> -z;
            case 3 -> x;
            default -> z;
        };
    }

    public static float rotationIndexToYaw(int rotation) {
        return switch (rotation & 3) {
            case 1 -> HALF_PI;
            case 2 -> HALF_PI * 2.0f;
            case 3 -> -HALF_PI;
            default -> 0.0f;
        };
    }

    public static double rotationIndexToYawDegrees(int rotation) {
        return Math.toDegrees(rotationIndexToYaw(rotation));
    }
}
