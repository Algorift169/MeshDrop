package com.meshdrop.settings;

public final class DeviceVisibility {
    public static final boolean DEFAULT_ENABLED = true;
    public static final String KEY = SettingsKeys.DEVICE_VISIBILITY;

    private DeviceVisibility() {}

    public static boolean isEnabled(SettingsRepository repository) {
        return repository.getBoolean(KEY, DEFAULT_ENABLED);
    }

    public static void setEnabled(SettingsRepository repository, boolean enabled) {
        repository.putBoolean(KEY, enabled);
    }
}
