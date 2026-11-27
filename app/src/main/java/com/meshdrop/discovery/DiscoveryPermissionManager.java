package com.meshdrop.discovery;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class DiscoveryPermissionManager {
    private final Context appContext;

    public DiscoveryPermissionManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    public String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        return permissions.toArray(new String[0]);
    }

    @NonNull
    public String[] getMissingPermissions() {
        List<String> missing = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(appContext, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }

    public boolean hasAllRequiredPermissions() {
        return getMissingPermissions().length == 0;
    }

    public void requestMissingPermissions(@NonNull ActivityResultLauncher<String[]> launcher) {
        String[] missing = getMissingPermissions();
        if (missing.length > 0) {
            launcher.launch(missing);
        }
    }

    public boolean shouldExplainPermission(@NonNull Activity activity, @NonNull String permission) {
        return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    @NonNull
    public String getPermissionExplanation() {
        return "MeshDrop needs nearby-device permissions for local discovery and notification permission to keep background scanning visible.";
    }
}
