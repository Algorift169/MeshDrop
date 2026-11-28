package com.meshdrop.settings;

public final class BatterySaver {
    public static final boolean DEFAULT_ENABLED = false;
    public static final String KEY = SettingsKeys.BATTERY_SAVER;

    private BatterySaver() {}

    public static boolean isEnabled(SettingsRepository repository) {
        return repository.getBoolean(KEY, DEFAULT_ENABLED);
    }

    public static void setEnabled(SettingsRepository repository, boolean enabled) {
        repository.putBoolean(KEY, enabled);
    }
}
