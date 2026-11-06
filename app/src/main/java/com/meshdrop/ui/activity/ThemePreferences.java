package com.meshdrop.ui.activity;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePreferences {
    private static final String PREFERENCES_NAME = "appearance";
    private static final String KEY_DARK_MODE = "dark_mode";

    private ThemePreferences() {
    }

    public static boolean isDarkMode(@NonNull Context context) {
        return preferences(context).getBoolean(KEY_DARK_MODE, true);
    }

    public static void apply(@NonNull Context context) {
        AppCompatDelegate.setDefaultNightMode(isDarkMode(context)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static void toggle(@NonNull Context context) {
        boolean darkMode = !isDarkMode(context);
        preferences(context).edit().putBoolean(KEY_DARK_MODE, darkMode).apply();
        AppCompatDelegate.setDefaultNightMode(darkMode
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}