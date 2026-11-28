package com.meshdrop.app;

import android.app.Application;

import com.meshdrop.messaging.ChatNotifications;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ChatNotifications.createChannel(this);
    }
}