package com.meshdrop.discovery;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.meshdrop.R;
import com.meshdrop.ui.activity.MainActivity;

public final class DiscoveryForegroundService extends Service {
    public static final String ACTION_START = "com.meshdrop.discovery.START";
    public static final String ACTION_REFRESH = "com.meshdrop.discovery.REFRESH";
    private static final String CHANNEL_ID = "meshdrop_discovery";
    private static final int NOTIFICATION_ID = 4107;

    private NearbyDiscoveryManager discoveryManager;

    @Override
    public void onCreate() {
        super.onCreate();
        discoveryManager = NearbyDiscoveryManager.getInstance(getApplicationContext());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Notification notification = buildNotification();
        int foregroundType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            discoveryManager.refreshDiscovery();
        } else {
            discoveryManager.startPrerequisitesReadySession();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (discoveryManager != null) {
            discoveryManager.stop();
        }
        super.onDestroy();
    }

    private Notification buildNotification() {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(getString(R.string.discovery_notification_title))
                .setContentText(getString(R.string.discovery_notification_text))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.discovery_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.discovery_notification_text));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
