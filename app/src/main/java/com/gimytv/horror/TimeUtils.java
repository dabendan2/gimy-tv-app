package com.gimytv.horror;

public class TimeUtils {
    public static String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        int hours = ms / (1000 * 60 * 60);

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public static String formatDelta(int ms) {
        int absMs = Math.abs(ms);
        int seconds = (absMs / 1000) % 60;
        int minutes = (absMs / (1000 * 60)) % 60;
        int hours = absMs / (1000 * 60 * 60);

        String sign = ms >= 0 ? "+" : "-";
        if (hours > 0) {
            return String.format("%s%02d:%02d:%02d", sign, hours, minutes, seconds);
        } else {
            return String.format("%s%02d:%02d", sign, minutes, seconds);
        }
    }

    public static boolean isProgressSaveAllowed(int position, int duration, int existingPos, boolean force) {
        if (position < 0 || duration <= 0) {
            return false;
        }
        if (force) {
            return true;
        }
        // Protect against backward rollbacks (this naturally blocks tiny-overwriting-large)
        return position >= existingPos;
    }

    public static boolean isNearEnd(int position, int duration) {
        return duration > 0 && position >= duration - 15000;
    }
}
