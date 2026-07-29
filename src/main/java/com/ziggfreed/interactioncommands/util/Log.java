package com.ziggfreed.interactioncommands.util;

import com.ziggfreed.interactioncommands.InteractionCommandsPlugin;

/**
 * A small logging facade over {@link InteractionCommandsPlugin#LOGGER}, mirroring the
 * MMO Skill Tree mod's {@code SafeLog}: every call is guarded by {@code try/catch
 * (Throwable)} so a plain unit-test JVM with no Hytale log manager never crashes on a
 * logging call (a raw {@code HytaleLogger} throws an {@link Error} there, which escapes
 * an ordinary {@code catch (Exception)}). Zero dependencies, ~40 lines by design.
 */
public final class Log {

    private Log() {
    }

    public static void info(String msg) {
        try {
            InteractionCommandsPlugin.LOGGER.atInfo().log(msg);
        } catch (Throwable ignored) {
            // no Hytale log manager (unit JVM)
        }
    }

    public static void warn(String msg) {
        try {
            InteractionCommandsPlugin.LOGGER.atWarning().log(msg);
        } catch (Throwable ignored) {
            // no Hytale log manager (unit JVM)
        }
    }

    public static void warn(String msg, Throwable cause) {
        try {
            InteractionCommandsPlugin.LOGGER.atWarning().withCause(cause).log(msg);
        } catch (Throwable ignored) {
            // no Hytale log manager (unit JVM)
        }
    }

    public static void severe(String msg) {
        try {
            InteractionCommandsPlugin.LOGGER.atSevere().log(msg);
        } catch (Throwable ignored) {
            // no Hytale log manager (unit JVM)
        }
    }

    public static void severe(String msg, Throwable cause) {
        try {
            InteractionCommandsPlugin.LOGGER.atSevere().withCause(cause).log(msg);
        } catch (Throwable ignored) {
            // no Hytale log manager (unit JVM)
        }
    }

    public static void fine(String msg) {
        try {
            InteractionCommandsPlugin.LOGGER.atFine().log(msg);
        } catch (Throwable ignored) {
            // no Hytale log manager (unit JVM)
        }
    }
}
