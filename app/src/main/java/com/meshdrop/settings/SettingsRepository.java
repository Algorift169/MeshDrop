package com.meshdrop.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class SettingsRepository {
    private static final String PREFERENCES_NAME = "meshdrop_settings";
    private final SharedPreferences preferences;

    public SettingsRepository(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public boolean getBoolean(@NonNull String key, boolean defaultValue) {
        try {
            return preferences.getBoolean(key, defaultValue);
        } catch (ClassCastException exception) {
            return defaultValue;
        }
    }

    public int getInt(@NonNull String key, int defaultValue, int minimum, int maximum) {
        try {
            int value = preferences.getInt(key, defaultValue);
            return value < minimum || value > maximum ? defaultValue : value;
        } catch (ClassCastException exception) {
            return defaultValue;
        }
    }

    @Nullable
    public String getString(@NonNull String key, @Nullable String defaultValue) {
        try {
            return preferences.getString(key, defaultValue);
        } catch (ClassCastException exception) {
            return defaultValue;
        }
    }

    public void putBoolean(@NonNull String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public void putInt(@NonNull String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    public void putString(@NonNull String key, @Nullable String value) {
        preferences.edit().putString(key, value).apply();
    }
}
