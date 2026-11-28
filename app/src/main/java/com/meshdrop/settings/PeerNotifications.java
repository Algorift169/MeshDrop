package com.meshdrop.settings;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.meshdrop.R;

public final class PeerNotifications {
    public static final boolean DEFAULT_ENABLED = false;
    public static final String KEY = SettingsKeys.PEER_NOTIFICATIONS;
    private static final String CHANNEL_ID = "meshdrop_peer_events";

    private PeerNotifications() {}

    public static boolean isEnabled(SettingsRepository repository) {
        return repository.getBoolean(KEY, DEFAULT_ENABLED);
    }

    public static void setEnabled(SettingsRepository repository, boolean enabled) {
        repository.putBoolean(KEY, enabled);
    }

    public static void notifyDiscovered(@NonNull Context context,
                                        @NonNull SettingsRepository repository,
                                        @NonNull String endpointId,
                                        @NonNull String deviceName) {
        post(context, repository, endpointId, deviceName,
                context.getString(R.string.peer_discovered_notification));
    }

    public static void notifyLost(@NonNull Context context,
                                  @NonNull SettingsRepository repository,
                                  @NonNull String endpointId) {
        post(context, repository, endpointId, "",
                context.getString(R.string.peer_lost_notification));
    }

    private static void post(@NonNull Context context,
                             @NonNull SettingsRepository repository,
                             @NonNull String endpointId,
                             @NonNull String deviceName,
                             @NonNull String eventText) {
        if (!isEnabled(repository)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) return;
        createChannel(context);

        String title = deviceName.isEmpty()
                ? context.getString(R.string.peer_notification_title)
                : deviceName;
        int eventHash = eventText.hashCode();
        int notificationId = 31 * endpointId.hashCode() + eventHash;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(title)
                .setContentText(eventText)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        manager.notify(notificationId, notification.build());
    }

    private static void createChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.peer_notifications_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.peer_notifications_channel));
        manager.createNotificationChannel(channel);
    }
}
