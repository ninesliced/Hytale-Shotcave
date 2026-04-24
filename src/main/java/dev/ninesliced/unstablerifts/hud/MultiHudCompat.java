package dev.ninesliced.unstablerifts.hud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MultiHudCompat {

    private static final Logger LOGGER = Logger.getLogger(MultiHudCompat.class.getName());
    private static final String MHUD_CLASS = "com.buuz135.mhud.MultipleHUD";
    private static final Object LOCK = new Object();

    private static volatile boolean checked;
    private static volatile boolean available;
    private static volatile Method getInstanceMethod;
    private static volatile Method setCustomHudMethod;
    private static volatile Method hideCustomHudMethod;

    private MultiHudCompat() {
    }

    public static boolean isAvailable() {
        if (!checked) {
            synchronized (LOCK) {
                if (!checked) {
                    init();
                    checked = true;
                }
            }
        }
        return available;
    }

    public static boolean setCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                       @Nonnull String hudId, @Nonnull CustomUIHud hud) {
        if (!isAvailable() || setCustomHudMethod == null) {
            return false;
        }
        try {
            Object instance = getInstanceMethod.invoke(null);
            if (instance == null) {
                return false;
            }
            setCustomHudMethod.invoke(instance, player, playerRef, hudId, hud);
            return true;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to set custom HUD via MultipleHUD", e);
            return false;
        }
    }

    public static boolean hideCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef,
                                        @Nonnull String hudId) {
        if (!isAvailable() || hideCustomHudMethod == null) {
            return false;
        }
        try {
            Object instance = getInstanceMethod.invoke(null);
            if (instance == null) {
                return false;
            }
            hideCustomHudMethod.invoke(instance, player, playerRef, hudId);
            return true;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to hide custom HUD via MultipleHUD", e);
            return false;
        }
    }

    private static void init() {
        try {
            Class<?> clazz = loadMultiHudClass();
            if (clazz == null) {
                throw new ClassNotFoundException(MHUD_CLASS);
            }

            getInstanceMethod = clazz.getMethod("getInstance");
            setCustomHudMethod = clazz.getMethod("setCustomHud",
                    Player.class, PlayerRef.class, String.class, CustomUIHud.class);
            hideCustomHudMethod = clazz.getMethod("hideCustomHud",
                    Player.class, PlayerRef.class, String.class);

            available = true;
            LOGGER.info("MultipleHUD compatibility initialized");
        } catch (Throwable e) {
            available = false;
            getInstanceMethod = null;
            setCustomHudMethod = null;
            hideCustomHudMethod = null;
            LOGGER.info("MultipleHUD not available, using UnstableRifts fallback HUD host");
        }
    }

    private static Class<?> loadMultiHudClass() {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader != null) {
                return Class.forName(MHUD_CLASS, true, classLoader);
            }
        } catch (Throwable ignored) {
        }

        try {
            PluginManager pluginManager = PluginManager.get();
            if (pluginManager != null && pluginManager.getBridgeClassLoader() != null) {
                return pluginManager.getBridgeClassLoader().loadClass(MHUD_CLASS);
            }
        } catch (Throwable ignored) {
        }

        try {
            return Class.forName(MHUD_CLASS);
        } catch (Throwable ignored) {
            return null;
        }
    }
}