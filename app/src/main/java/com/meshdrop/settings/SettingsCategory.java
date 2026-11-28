package com.meshdrop.settings;

import androidx.annotation.StringRes;

import com.meshdrop.R;

public enum SettingsCategory {
    DISCOVERY(R.string.settings_category_discovery),
    CONNECTIVITY(R.string.settings_category_connectivity),
    PRIVACY(R.string.settings_category_privacy),
    PERFORMANCE(R.string.settings_category_performance),
    NOTIFICATIONS(R.string.settings_category_notifications);

    @StringRes
    private final int titleResource;

    SettingsCategory(int titleResource) {
        this.titleResource = titleResource;
    }

    @StringRes
    public int getTitleResource() {
        return titleResource;
    }
}
