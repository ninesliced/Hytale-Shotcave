package com.hypixel.hytale.math.vector;

import javax.annotation.Nonnull;

public class Rotation3f extends Vector3f {
    public static final Rotation3f ZERO = new Rotation3f(0.0F, 0.0F, 0.0F);
    public static final Rotation3f NaN = new Rotation3f(Float.NaN, Float.NaN, Float.NaN);

    public Rotation3f() {
        super();
    }

    public Rotation3f(float pitch, float yaw, float roll) {
        super(pitch, yaw, roll);
    }

    public Rotation3f(@Nonnull Vector3f other) {
        super(other);
    }

    public float pitch() {
        return this.getPitch();
    }

    public float yaw() {
        return this.getYaw();
    }

    public float roll() {
        return this.getRoll();
    }

    @Override
    @Nonnull
    public Rotation3f clone() {
        return new Rotation3f(this);
    }
}