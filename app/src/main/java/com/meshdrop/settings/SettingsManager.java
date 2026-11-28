package com.meshdrop.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import com.meshdrop.discovery.NearbyDiscoveryManager;

import java.util.concurrent.CopyOnWriteArraySet;

public final class SettingsManager {
    public interface Listener {
        void onSettingChanged(@NonNull String key);
    }

    private static volatile SettingsManager instance;
    private final Context appContext;
    private final SettingsRepository repository;
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    public SettingsManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        repository = new SettingsRepository(appContext);
    }

    @NonNull
    public static SettingsManager getInstance(@NonNull Context context) {
        SettingsManager current = instance;
        if (current == null) {
            synchronized (SettingsManager.class) {
                current = instance;
                if (current == null) {
                    current = new SettingsManager(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    @NonNull
    public SettingsRepository getRepository() {
        return repository;
    }

    public boolean isForceScanEnabled() {
        return ForceScan.isEnabled(repository);
    }

    public boolean isForceBluetoothEnabled() {
        return ForceBluetooth.isEnabled(repository);
    }

    public boolean isAutoAdvertiseEnabled() {
        return AutoAdvertise.isEnabled(repository);
    }

    public boolean isDeviceVisible() {
        return DeviceVisibility.isEnabled(repository);
    }

    public boolean isAnonymousDiscoveryEnabled() {
        return AnonymousDiscovery.isEnabled(repository);
    }

    public boolean isBatterySaverEnabled() {
        return BatterySaver.isEnabled(repository);
    }

    public boolean isRadarAnimationEnabled() {
        return RadarAnimation.isEnabled(repository);
    }

    public boolean arePeerNotificationsEnabled() {
        return PeerNotifications.isEnabled(repository);
    }

    @NonNull
    public ScanInterval.Mode getScanInterval() {
        return ScanInterval.getMode(repository);
    }

    public void setBoolean(@NonNull String key, boolean value) {
        boolean previous = repository.getBoolean(key, defaultFor(key));
        if (previous == value) return;
        repository.putBoolean(key, value);
        notifyChanged(key);
    }

    public void setScanInterval(@NonNull ScanInterval.Mode mode) {
        if (getScanInterval() == mode) return;
        ScanInterval.setMode(repository, mode);
        notifyChanged(SettingsKeys.SCAN_INTERVAL);
    }

    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    private boolean defaultFor(@NonNull String key) {
        if (SettingsKeys.FORCE_SCAN.equals(key)) return ForceScan.DEFAULT_ENABLED;
        if (SettingsKeys.FORCE_BLUETOOTH.equals(key)) return ForceBluetooth.DEFAULT_ENABLED;
        if (SettingsKeys.AUTO_ADVERTISE.equals(key)) return AutoAdvertise.DEFAULT_ENABLED;
        if (SettingsKeys.DEVICE_VISIBILITY.equals(key)) return DeviceVisibility.DEFAULT_ENABLED;
        if (SettingsKeys.ANONYMOUS_DISCOVERY.equals(key)) return AnonymousDiscovery.DEFAULT_ENABLED;
        if (SettingsKeys.BATTERY_SAVER.equals(key)) return BatterySaver.DEFAULT_ENABLED;
        if (SettingsKeys.RADAR_ANIMATION.equals(key)) return RadarAnimation.DEFAULT_ENABLED;
        if (SettingsKeys.PEER_NOTIFICATIONS.equals(key)) return PeerNotifications.DEFAULT_ENABLED;
        throw new IllegalArgumentException("Unknown boolean setting: " + key);
    }

    private void notifyChanged(@NonNull String key) {
        NearbyDiscoveryManager.getInstance(appContext).applySettings(repository);
        for (Listener listener : listeners) {
            listener.onSettingChanged(key);
        }
    }
}
