package com.meshdrop.settings;

public final class AutoAdvertise {
    public static final boolean DEFAULT_ENABLED = true;
    public static final String KEY = SettingsKeys.AUTO_ADVERTISE;

    private AutoAdvertise() {}

    public static boolean isEnabled(SettingsRepository repository) {
        return repository.getBoolean(KEY, DEFAULT_ENABLED);
    }

    public static void setEnabled(SettingsRepository repository, boolean enabled) {
        repository.putBoolean(KEY, enabled);
    }
}
