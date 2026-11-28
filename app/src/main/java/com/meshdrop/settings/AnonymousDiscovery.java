package com.meshdrop.settings;

public final class AnonymousDiscovery {
    public static final boolean DEFAULT_ENABLED = false;
    public static final String KEY = SettingsKeys.ANONYMOUS_DISCOVERY;

    private AnonymousDiscovery() {}

    public static boolean isEnabled(SettingsRepository repository) {
        return repository.getBoolean(KEY, DEFAULT_ENABLED);
    }

    public static void setEnabled(SettingsRepository repository, boolean enabled) {
        repository.putBoolean(KEY, enabled);
    }
}
