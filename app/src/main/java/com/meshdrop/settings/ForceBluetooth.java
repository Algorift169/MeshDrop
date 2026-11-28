package com.meshdrop.settings;

public final class ForceBluetooth {
    public static final boolean DEFAULT_ENABLED = true;
    public static final String KEY = SettingsKeys.FORCE_BLUETOOTH;

    private ForceBluetooth() {}

    public static boolean isEnabled(SettingsRepository repository) {
        return repository.getBoolean(KEY, DEFAULT_ENABLED);
    }

    public static void setEnabled(SettingsRepository repository, boolean enabled) {
        repository.putBoolean(KEY, enabled);
    }
}
