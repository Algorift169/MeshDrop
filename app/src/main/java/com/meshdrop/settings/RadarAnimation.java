package com.meshdrop.settings;

public final class RadarAnimation {
    public static final boolean DEFAULT_ENABLED = true;
    public static final String KEY = SettingsKeys.RADAR_ANIMATION;

    private RadarAnimation() {}

    public static boolean isEnabled(SettingsRepository repository) {
        return repository.getBoolean(KEY, DEFAULT_ENABLED);
    }

    public static void setEnabled(SettingsRepository repository, boolean enabled) {
        repository.putBoolean(KEY, enabled);
    }
}
