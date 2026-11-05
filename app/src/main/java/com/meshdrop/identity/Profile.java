package com.meshdrop.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Profile {
    private final String name;
    private final String bio;
    private final String deviceName;
    @Nullable
    private final String phoneNumber;

    public Profile(
            @NonNull String name,
            @NonNull String bio,
            @NonNull String deviceName,
            @Nullable String phoneNumber) {
        this.name = name;
        this.bio = bio;
        this.deviceName = deviceName;
        this.phoneNumber = phoneNumber;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getBio() {
        return bio;
    }

    @NonNull
    public String getDeviceName() {
        return deviceName;
    }

    @Nullable
    public String getPhoneNumber() {
        return phoneNumber;
    }
}
