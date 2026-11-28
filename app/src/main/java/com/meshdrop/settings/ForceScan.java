package com.meshdrop.settings;

public final class ForceScan {
    public static final boolean DEFAULT_ENABLED = true;
    public static final String KEY = SettingsKeys.FORCE_SCAN;

    private ForceScan() {}

    public static boolean isEnabled(SettingsRepository repository) {
        return repository.getBoolean(KEY, DEFAULT_ENABLED);
    }

    public static void setEnabled(SettingsRepository repository, boolean enabled) {
        repository.putBoolean(KEY, enabled);
    }
}
