package com.meshdrop.ui.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.badge.BadgeDrawable;
import com.meshdrop.R;
import com.meshdrop.data.model.Conversation;
import com.meshdrop.data.model.Message;
import com.meshdrop.data.model.PeerIdentity;
import com.meshdrop.database.repository.ChatRepository;
import com.meshdrop.database.repository.ChatRepositoryImpl;
import com.meshdrop.ui.fragment.ChatFragment;
import com.meshdrop.ui.fragment.FilesFragment;
import com.meshdrop.ui.fragment.MeshFragment;
import com.meshdrop.ui.fragment.ProfileFragment;
import com.meshdrop.discovery.DiscoveryError;
import com.meshdrop.discovery.DiscoveryForegroundService;
import com.meshdrop.discovery.DiscoveryPermissionManager;
import com.meshdrop.discovery.DiscoveryState;
import com.meshdrop.discovery.NearbyDiscoveryManager;
import com.meshdrop.settings.ForceBluetooth;
import com.meshdrop.settings.SettingsManager;
import com.meshdrop.settings.SettingsFragment;
import com.meshdrop.messaging.MessagingCallback;
import com.meshdrop.messaging.NearbyChatManager;


public class MainActivity extends AppCompatActivity implements MessagingCallback {
    private static final String STATE_SELECTED_DESTINATION = "selected_destination";
    private int selectedDestination = R.id.nav_mesh;
    private BottomNavigationView bottomNavigation;
    private FrameLayout contentContainer;
    private NearbyDiscoveryManager discoveryManager;
    private NearbyChatManager chatManager;
    private DiscoveryPermissionManager permissionManager;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> bluetoothLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private boolean bluetoothRequestPending;
    private boolean bluetoothPromptDeclined;
    private boolean permissionRequestDenied;
    private boolean bluetoothReceiverRegistered;
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) {
                if (state == BluetoothAdapter.STATE_ON) {
                    bluetoothPromptDeclined = false;
                }
                checkPrerequisites();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setStatusBarColor(getColor(R.color.background));
        getWindow().setNavigationBarColor(getColor(R.color.background));

        discoveryManager = NearbyDiscoveryManager.getInstance(getApplicationContext());
        chatManager = NearbyChatManager.getInstance(getApplicationContext());
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
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {});

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
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_USER_ID, peerId);
        intent.putExtra(ChatActivity.EXTRA_PEER_USERNAME, displayName);
        startActivity(intent);
    }

    public void showChatList() {
        showDestination(R.id.nav_chat);
    }

    public void openSettings() {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        for (Fragment existing : manager.getFragments()) {
            transaction.hide(existing);
        }
        Fragment settings = manager.findFragmentByTag("profile:settings");
        if (settings == null) {
            settings = new SettingsFragment();
            transaction.add(R.id.fragment_container, settings, "profile:settings");
        } else {
            transaction.show(settings);
        }
        transaction.commit();
    }

    public void showProfile() {
        showDestination(R.id.nav_profile);
    }

    public void onSettingsChanged() {
        checkPrerequisites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPrerequisites();
        refreshUnreadBadge();
    }

    @Override
    protected void onStart() {
        super.onStart();
        chatManager.addCallback(this);
        if (bluetoothReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, bluetoothStateReceiver, filter,
                    ContextCompat.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bluetoothStateReceiver, filter);
        }
        bluetoothReceiverRegistered = true;
    }

    @Override
    protected void onStop() {
        chatManager.removeCallback(this);
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver);
            bluetoothReceiverRegistered = false;
        }
        super.onStop();
    }

    public NearbyDiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    public void onLocalProfileNameChanged() {
        discoveryManager.onLocalProfileChanged();
        chatManager.onLocalProfileChanged();
    }

    public void connectToDiscoveredEndpoint(@NonNull String endpointId) {
        Toast.makeText(this, R.string.chat_connecting, Toast.LENGTH_SHORT).show();
        chatManager.connectToEndpoint(endpointId);
    }

    @Override
    public void onConnectionRequestReceived(@NonNull String endpointId,
                                           @NonNull String displayName,
                                           @NonNull String authenticationToken,
                                           boolean outgoingRequest) {
        if (isFinishing() || isDestroyed()) return;
        chatManager.acceptConnection(endpointId);
    }

    @Override
    public void onConnectionRejected(@NonNull String endpointId) {
        Toast.makeText(this, R.string.connection_rejected, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionEstablished(@NonNull PeerIdentity peer) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_PEER_USER_ID, peer.userId);
        intent.putExtra(ChatActivity.EXTRA_PEER_USERNAME, peer.username);
        startActivity(intent);
    }

    @Override
    public void onConnectionDisconnected(@NonNull String peerUserId) {
        Toast.makeText(this, R.string.connection_disconnected, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMessagingError(@NonNull String message, @Nullable Throwable cause) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onMessageReceived(@NonNull Message message) {
        refreshUnreadBadge();
    }

    private void refreshUnreadBadge() {
        if (bottomNavigation == null) return;
        ChatRepository repository = ChatRepositoryImpl.getInstance(getApplicationContext());
        repository.getConversations(new ChatRepository.Callback<java.util.List<Conversation>>() {
            @Override
            public void onSuccess(java.util.List<Conversation> conversations) {
                if (isFinishing() || isDestroyed() || bottomNavigation == null) return;
                int unreadCount = 0;
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.unreadCount;
                }
                if (unreadCount == 0) {
                    bottomNavigation.removeBadge(R.id.nav_chat);
                    return;
                }
                BadgeDrawable badge = bottomNavigation.getOrCreateBadge(R.id.nav_chat);
                badge.setBackgroundColor(Color.RED);
                badge.setBadgeTextColor(Color.WHITE);
                badge.setNumber(unreadCount);
                badge.setVisible(true);
            }

            @Override
            public void onError(@NonNull Exception error) {
                Toast.makeText(MainActivity.this, R.string.chat_storage_error,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        android.content.SharedPreferences preferences = getSharedPreferences(
                "notification_permissions", MODE_PRIVATE);
        if (preferences.getBoolean("requested", false)) return;
        preferences.edit().putBoolean("requested", true).apply();
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
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
            if (!SettingsManager.getInstance(getApplicationContext()).isForceBluetoothEnabled()) {
                reportPrerequisiteError(
                        DiscoveryError.Reason.BLUETOOTH_DISABLED,
                        "Bluetooth is disabled. Enable it in Android settings or turn on Force Bluetooth.");
                return;
            }
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

        requestNotificationPermissionIfNeeded();

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
