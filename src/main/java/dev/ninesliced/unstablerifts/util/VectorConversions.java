package dev.ninesliced.unstablerifts.util;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Vector4d;

import javax.annotation.Nullable;

public final class VectorConversions {

    private VectorConversions() {
    }

    @Nullable
    public static org.joml.Vector3d toJoml(@Nullable Vector3d value) {
        return value == null ? null : new org.joml.Vector3d(value.x, value.y, value.z);
    }

    @Nullable
    public static org.joml.Vector3d toJoml(@Nullable org.joml.Vector3d value) {
        return value;
    }

    @Nullable
    public static org.joml.Vector3i toJoml(@Nullable Vector3i value) {
        return value == null ? null : new org.joml.Vector3i(value.x, value.y, value.z);
    }

    @Nullable
    public static org.joml.Vector3i toJoml(@Nullable org.joml.Vector3i value) {
        return value;
    }

    @Nullable
    public static Vector3d toHytale(@Nullable org.joml.Vector3d value) {
        return value == null ? null : new Vector3d(value.x, value.y, value.z);
    }

    @Nullable
    public static Vector3i toHytale(@Nullable org.joml.Vector3i value) {
        return value == null ? null : new Vector3i(value.x, value.y, value.z);
    }

    @Nullable
    public static Vector4d toHytale(@Nullable org.joml.Vector4d value) {
        return value == null ? null : new Vector4d(value.x, value.y, value.z, value.w);
    }

    @Nullable
    public static com.hypixel.hytale.protocol.Vector3f toProtocol(@Nullable org.joml.Vector3f value) {
        return value == null ? null : new com.hypixel.hytale.protocol.Vector3f(value.x, value.y, value.z);
    }
}