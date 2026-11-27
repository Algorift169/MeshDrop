package com.meshdrop.ui.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.meshdrop.R;
import com.meshdrop.ui.fragment.ChatFragment;
import com.meshdrop.ui.fragment.FilesFragment;
import com.meshdrop.ui.fragment.MeshFragment;
import com.meshdrop.ui.fragment.ProfileFragment;
import com.meshdrop.discovery.DiscoveryError;
import com.meshdrop.discovery.DiscoveryForegroundService;
import com.meshdrop.discovery.DiscoveryPermissionManager;
import com.meshdrop.discovery.DiscoveryState;
import com.meshdrop.discovery.NearbyDiscoveryManager;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_SELECTED_DESTINATION = "selected_destination";
    private int selectedDestination = R.id.nav_mesh;
    private BottomNavigationView bottomNavigation;
    private FrameLayout contentContainer;
    private NearbyDiscoveryManager discoveryManager;
    private DiscoveryPermissionManager permissionManager;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> bluetoothLauncher;
    private boolean bluetoothRequestPending;
    private boolean bluetoothPromptDeclined;
    private boolean permissionRequestDenied;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setStatusBarColor(getColor(R.color.background));
        getWindow().setNavigationBarColor(getColor(R.color.background));

        discoveryManager = NearbyDiscoveryManager.getInstance(getApplicationContext());
        permissionManager = new DiscoveryPermissionManager(getApplicationContext());
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    permissionRequestDenied = result.containsValue(false);
                    checkPrerequisites();
                });
        bluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    bluetoothRequestPending = false;
                    bluetoothPromptDeclined = result.getResultCode() != RESULT_OK;
                    checkPrerequisites();
                });

        bottomNavigation = findViewById(R.id.bottom_navigation);
        contentContainer = findViewById(R.id.fragment_container);
        if (savedInstanceState != null) {
            selectedDestination = savedInstanceState.getInt(
                    STATE_SELECTED_DESTINATION, R.id.nav_mesh);
        }
        bottomNavigation.setSelectedItemId(selectedDestination);
        bottomNavigation.setOnItemSelectedListener(this::onDestinationSelected);
        showDestination(selectedDestination);
    }

    private boolean onDestinationSelected(@NonNull MenuItem item) {
        showDestination(item.getItemId());
        return true;
    }

    private void showDestination(int destinationId) {
        Fragment fragment = fragmentFor(destinationId);
        if (fragment == null || contentContainer == null) {
            return;
        }

        String tag = tagFor(destinationId);
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        for (Fragment existing : manager.getFragments()) {
            transaction.hide(existing);
        }

        Fragment existing = manager.findFragmentByTag(tag);
        if (existing == null) {
            transaction.add(R.id.fragment_container, fragment, tag);
        } else {
            transaction.show(existing);
        }
        transaction.commit();
        selectedDestination = destinationId;
    }

    private Fragment fragmentFor(int destinationId) {
        if (destinationId == R.id.nav_mesh) {
            return new MeshFragment();
        } else if (destinationId == R.id.nav_chat) {
            return new ChatFragment();
        } else if (destinationId == R.id.nav_files) {
            return new FilesFragment();
        } else if (destinationId == R.id.nav_profile) {
            return new ProfileFragment();
        }
        return null;
    }

    private String tagFor(int destinationId) {
        return "destination:" + destinationId;
    }

    public void openConversation(@NonNull String peerId, @NonNull String displayName) {
        Bundle arguments = new Bundle();
        arguments.putString(ChatFragment.ARG_PEER_ID, peerId);
        arguments.putString(ChatFragment.ARG_PEER_NAME, displayName);

        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        for (Fragment existing : manager.getFragments()) {
            transaction.hide(existing);
        }
        String conversationTag = "conversation:" + peerId;
        Fragment conversation = manager.findFragmentByTag(conversationTag);
        if (conversation == null) {
            conversation = new ChatFragment();
            conversation.setArguments(arguments);
            transaction.add(R.id.fragment_container, conversation, conversationTag);
        } else {
            transaction.show(conversation);
        }
        transaction.commit();

        selectedDestination = R.id.nav_chat;
        bottomNavigation.setOnItemSelectedListener(null);
        bottomNavigation.setSelectedItemId(R.id.nav_chat);
        bottomNavigation.setOnItemSelectedListener(this::onDestinationSelected);
    }

    public void showChatList() {
        showDestination(R.id.nav_chat);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPrerequisites();
    }

    public NearbyDiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    public void checkPrerequisites() {
        checkPrerequisites(false);
    }

    public void refreshDiscovery() {
        bluetoothPromptDeclined = false;
        permissionRequestDenied = false;
        checkPrerequisites(true);
    }

    private void checkPrerequisites(boolean forceRefresh) {
        if (isFinishing() || discoveryManager == null) return;

        discoveryManager.setState(DiscoveryState.CHECKING_REQUIREMENTS);
        int playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (playServices != ConnectionResult.SUCCESS) {
            reportPrerequisiteError(
                    DiscoveryError.Reason.NEARBY_SERVICE_UNAVAILABLE,
                    "Google Play Services is unavailable or needs an update.");
            return;
        }

        String[] missingPermissions = permissionManager.getMissingPermissions();
        if (missingPermissions.length > 0) {
            discoveryManager.setState(DiscoveryState.REQUESTING_PERMISSIONS);
            if (permissionRequestDenied || shouldExplainMissingPermission(missingPermissions)) {
                reportPrerequisiteError(
                        DiscoveryError.Reason.MISSING_PERMISSION,
                        permissionManager.getPermissionExplanation());
            } else {
                permissionManager.requestMissingPermissions(permissionLauncher);
            }
            return;
        }
        permissionRequestDenied = false;

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager != null
                ? bluetoothManager.getAdapter()
                : BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            reportPrerequisiteError(
                    DiscoveryError.Reason.BLUETOOTH_UNAVAILABLE,
                    "Bluetooth hardware is unavailable on this device.");
            return;
        }

        boolean enabled;
        try {
            enabled = bluetoothAdapter.isEnabled();
        } catch (SecurityException exception) {
            enabled = false;
        }
        if (!enabled) {
            discoveryManager.setState(DiscoveryState.ENABLING_BLUETOOTH);
            if (bluetoothPromptDeclined) {
                reportPrerequisiteError(
                        DiscoveryError.Reason.BLUETOOTH_DISABLED,
                        "Bluetooth is disabled. Tap refresh to try again.");
            } else if (!bluetoothRequestPending) {
                bluetoothRequestPending = true;
                bluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
            return;
        }
        bluetoothPromptDeclined = false;

        Intent serviceIntent = new Intent(this, DiscoveryForegroundService.class);
        serviceIntent.setAction(forceRefresh
                ? DiscoveryForegroundService.ACTION_REFRESH
                : DiscoveryForegroundService.ACTION_START);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void reportPrerequisiteError(
            @NonNull DiscoveryError.Reason reason,
            @NonNull String description) {
        discoveryManager.reportError(reason, description);
        stopService(new Intent(this, DiscoveryForegroundService.class));
    }

    private boolean shouldExplainMissingPermission(@NonNull String[] permissions) {
        for (String permission : permissions) {
            if (androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_DESTINATION, selectedDestination);
        super.onSaveInstanceState(outState);
    }
}
