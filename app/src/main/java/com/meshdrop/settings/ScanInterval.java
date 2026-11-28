package com.meshdrop.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ScanInterval {
    public static final String KEY = SettingsKeys.SCAN_INTERVAL;
    public static final Mode DEFAULT_MODE = Mode.CONTINUOUS;

    private ScanInterval() {}

    @NonNull
    public static Mode getMode(SettingsRepository repository) {
        String stored = repository.getString(KEY, DEFAULT_MODE.value);
        return Mode.fromValue(stored);
    }

    public static void setMode(SettingsRepository repository, @NonNull Mode mode) {
        repository.putString(KEY, mode.value);
    }

    public enum Mode {
        CONTINUOUS("continuous", 0, 0),
        BALANCED("balanced", 30_000, 15_000),
        BATTERY_EFFICIENT("battery_efficient", 10_000, 60_000);

        private final String value;
        private final long activeDurationMillis;
        private final long idleDurationMillis;

        Mode(String value, long activeDurationMillis, long idleDurationMillis) {
            this.value = value;
            this.activeDurationMillis = activeDurationMillis;
            this.idleDurationMillis = idleDurationMillis;
        }

        @NonNull
        public String getValue() {
            return value;
        }

        public long getActiveDurationMillis() {
            return activeDurationMillis;
        }

        public long getIdleDurationMillis() {
            return idleDurationMillis;
        }

        @NonNull
        static Mode fromValue(@Nullable String value) {
            for (Mode mode : values()) {
                if (mode.value.equals(value)) return mode;
            }
            return DEFAULT_MODE;
        }
    }

}
