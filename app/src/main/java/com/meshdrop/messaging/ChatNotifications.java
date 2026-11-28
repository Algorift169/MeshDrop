package com.meshdrop.messaging;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.meshdrop.R;
import com.meshdrop.ui.activity.ChatActivity;

public final class ChatNotifications {
    private static final String CHANNEL_ID = "direct_messages";

    private ChatNotifications() {}

    public static void createChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.chat_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.chat_notification_channel_description));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    public static void show(@NonNull Context context,
                            @NonNull String peerUserId,
                            @NonNull String peerUsername,
                            @NonNull String body) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_PEER_USER_ID, peerUserId)
                .putExtra(ChatActivity.EXTRA_PEER_USERNAME, peerUsername)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, peerUserId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mesh)
                .setContentTitle(peerUsername)
                .setContentText(body)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
                NotificationManagerCompat.from(context).notify(peerUserId, 0, notification.build());
        }

        public static void cancel(@NonNull Context context, @NonNull String peerUserId) {
                NotificationManagerCompat.from(context).cancel(peerUserId, 0);
    }
}