package com.meshdrop.messaging;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.meshdrop.data.model.PeerIdentity;
import com.meshdrop.settings.AnonymousDiscovery;
import com.meshdrop.settings.SettingsManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PeerIdentityManager {
    private static final String PROFILE_PREFERENCES = "profile";
    private static final String KEY_USERNAME = "name";
    private static final String KEY_USER_ID = "nearby_local_peer_id";

    private final Context appContext;
    private final SharedPreferences profilePreferences;
    private final Map<String, PeerIdentity> identitiesByEndpoint = new ConcurrentHashMap<>();
    private final Map<String, String> endpointsByUserId = new ConcurrentHashMap<>();

    public PeerIdentityManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        profilePreferences = appContext.getSharedPreferences(
                PROFILE_PREFERENCES, Context.MODE_PRIVATE);
    }

    @NonNull
    public PeerIdentity getLocalIdentity() {
        String userId = profilePreferences.getString(KEY_USER_ID, null);
        if (userId == null || userId.trim().isEmpty()) {
            userId = UUID.randomUUID().toString();
            profilePreferences.edit().putString(KEY_USER_ID, userId).apply();
        }
        String username = profilePreferences.getString(KEY_USERNAME, "");
        if (username != null) username = username.trim();
        if (username == null || username.isEmpty()) username = Build.MODEL;
        if (username == null || username.trim().isEmpty()) username = "MeshDrop User";
        username = username.trim();
        boolean anonymous = SettingsManager.getInstance(appContext)
                .getRepository().getBoolean(AnonymousDiscovery.KEY,
                        AnonymousDiscovery.DEFAULT_ENABLED);
        String publicName = anonymous ? "MeshDrop User" : username;
        return new PeerIdentity(userId, username, publicName, anonymous);
    }

    public void rememberPeer(@NonNull String endpointId, @NonNull PeerIdentity identity) {
        String existingEndpoint = endpointsByUserId.put(identity.userId, endpointId);
        if (existingEndpoint != null && !existingEndpoint.equals(endpointId)) {
            identitiesByEndpoint.remove(existingEndpoint);
        }
        identitiesByEndpoint.put(endpointId, identity);
    }

    @Nullable
    public PeerIdentity getPeerForEndpoint(@NonNull String endpointId) {
        return identitiesByEndpoint.get(endpointId);
    }

    @Nullable
    public String getEndpointForPeer(@NonNull String userId) {
        return endpointsByUserId.get(userId);
    }

    public void removeEndpoint(@NonNull String endpointId) {
        PeerIdentity identity = identitiesByEndpoint.remove(endpointId);
        if (identity != null && endpointId.equals(endpointsByUserId.get(identity.userId))) {
            endpointsByUserId.remove(identity.userId);
        }
    }

    public void clear() {
        identitiesByEndpoint.clear();
        endpointsByUserId.clear();
    }
}
