package com.meshdrop.discovery;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Strategy;
import com.meshdrop.messaging.NearbyChatManager;
import com.meshdrop.data.model.PeerTreeNode;
import com.meshdrop.data.model.DiscoveredDevice;
import com.meshdrop.settings.AnonymousDiscovery;
import com.meshdrop.settings.AutoAdvertise;
import com.meshdrop.settings.BatterySaver;
import com.meshdrop.settings.DeviceVisibility;
import com.meshdrop.settings.ForceScan;
import com.meshdrop.settings.ScanInterval;
import com.meshdrop.settings.SettingsRepository;
import com.meshdrop.settings.SettingsManager;
import com.meshdrop.settings.PeerNotifications;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public final class NearbyDiscoveryManager {
    private static final String TAG = "NearbyDiscovery";
    private static final String KEY_LOCAL_PEER_ID = "nearby_local_peer_id";
    private static volatile NearbyDiscoveryManager instance;

    private final Context appContext;
    private final Handler mainHandler;
    private final ConnectionsClient connectionsClient;
    private final String serviceId;
    private final Map<String, DiscoveredDevice> devices;
    private final Map<String, Runnable> staleDeviceRemovals = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<DiscoveryCallback> callbacks;
    private final PeerTopology peerTopology;

    private DiscoveryState state = DiscoveryState.IDLE;
    private boolean advertising;
    private boolean discovering;
    private boolean advertisingStarting;
    private boolean discoveryStarting;
    private boolean sessionRequested;
    private int sessionGeneration;
    private int retryAttempt;
    private Runnable retryRunnable;
    private Runnable scanCycleRunnable;
    private Runnable scanWindowFinishRunnable;
    private Runnable manualScanFinishRunnable;
    private SettingsRepository settingsRepository;
    private boolean advertisingAllowed = true;
    private boolean forceScanEnabled = true;
    private boolean manualScanActive;
    private ScanInterval.Mode scanIntervalMode = ScanInterval.Mode.CONTINUOUS;
    private String activeAdvertisingName;

    public NearbyDiscoveryManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        mainHandler = new Handler(Looper.getMainLooper());
        connectionsClient = Nearby.getConnectionsClient(appContext);
        serviceId = appContext.getPackageName();
        devices = new ConcurrentHashMap<>();
        callbacks = new CopyOnWriteArraySet<>();
        SharedPreferences preferences = appContext.getSharedPreferences("profile", Context.MODE_PRIVATE);
        String localPeerId = preferences.getString(KEY_LOCAL_PEER_ID, null);
        if (localPeerId == null || localPeerId.trim().isEmpty()) {
            localPeerId = UUID.randomUUID().toString();
            preferences.edit().putString(KEY_LOCAL_PEER_ID, localPeerId).apply();
        }
        peerTopology = new PeerTopology(localPeerId, getAdvertisingName());
    }

    @NonNull
    public static NearbyDiscoveryManager getInstance(@NonNull Context context) {
        NearbyDiscoveryManager current = instance;
        if (current == null) {
            synchronized (NearbyDiscoveryManager.class) {
                current = instance;
                if (current == null) {
                    current = new NearbyDiscoveryManager(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    public void registerCallback(@NonNull DiscoveryCallback callback) {
        callbacks.add(callback);
        mainHandler.post(() -> {
            callback.onRequirementChanged(state);
            callback.onAdvertisingStatusChanged(advertising);
            callback.onDeviceRegistryChanged(getDiscoveredDevices());
            callback.onPeerTopologyChanged(getPeerTree());
        });
    }

    public void unregisterCallback(@NonNull DiscoveryCallback callback) {
        callbacks.remove(callback);
    }

    @NonNull
    public String getAdvertisingName() {
        SharedPreferences preferences = appContext.getSharedPreferences("profile", Context.MODE_PRIVATE);
        String profileName = preferences.getString("name", "");
        boolean anonymous = settingsRepository != null
                && AnonymousDiscovery.isEnabled(settingsRepository);
        return resolvePublicName(profileName, Build.MODEL, anonymous);
    }

    @NonNull
    static String resolvePublicName(@Nullable String profileName,
                                    @Nullable String deviceName,
                                    boolean anonymous) {
        if (anonymous) return "MeshDrop User";
        String selectedName = profileName == null ? "" : profileName.trim();
        if (selectedName.isEmpty()) {
            selectedName = deviceName == null ? "" : deviceName.trim();
        }
        if (selectedName.isEmpty()) selectedName = "MeshDrop Device";

        StringBuilder safeName = new StringBuilder();
        int byteLength = 0;
        for (int offset = 0; offset < selectedName.length();) {
            int codePoint = selectedName.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (byteLength + characterBytes > 32) break;
            safeName.append(character);
            byteLength += characterBytes;
            offset += Character.charCount(codePoint);
        }
        String result = safeName.toString().trim();
        return result.isEmpty() ? "MeshDrop Device" : result;
    }

    public void setState(@NonNull DiscoveryState nextState) {
        if (!isValidTransition(state, nextState)) {
            return;
        }
        state = nextState;
        mainHandler.post(() -> {
            for (DiscoveryCallback callback : callbacks) {
                callback.onRequirementChanged(nextState);
            }
        });
    }

    private boolean isValidTransition(@NonNull DiscoveryState current, @NonNull DiscoveryState next) {
        if (current == next) return true;
        if (next == DiscoveryState.ERROR || next == DiscoveryState.IDLE) return true;
        if (current == DiscoveryState.STOPPING) return next == DiscoveryState.IDLE;
        switch (current) {
            case IDLE:
                return next == DiscoveryState.CHECKING_REQUIREMENTS;
            case CHECKING_REQUIREMENTS:
                return next == DiscoveryState.REQUESTING_PERMISSIONS
                        || next == DiscoveryState.ENABLING_BLUETOOTH
                        || next == DiscoveryState.STARTING
                        || next == DiscoveryState.ERROR;
            case REQUESTING_PERMISSIONS:
            case ENABLING_BLUETOOTH:
                return next == DiscoveryState.CHECKING_REQUIREMENTS
                        || next == DiscoveryState.STARTING
                        || next == DiscoveryState.ERROR;
            case STARTING:
                return next == DiscoveryState.DISCOVERING
                        || next == DiscoveryState.ADVERTISING
                        || next == DiscoveryState.STOPPING
                        || next == DiscoveryState.ERROR;
            case DISCOVERING:
                return next == DiscoveryState.STOPPING
                        || next == DiscoveryState.ADVERTISING
                        || next == DiscoveryState.STARTING
                        || next == DiscoveryState.ERROR;
            case ADVERTISING:
                return next == DiscoveryState.DISCOVERING
                        || next == DiscoveryState.STOPPING
                        || next == DiscoveryState.STARTING
                        || next == DiscoveryState.IDLE
                        || next == DiscoveryState.ERROR;
            case ERROR:
                return next == DiscoveryState.CHECKING_REQUIREMENTS
                        || next == DiscoveryState.STARTING
                        || next == DiscoveryState.IDLE;
            default:
                return false;
        }
    }

    public void startAdvertising() {
        if (!sessionRequested || !advertisingAllowed || advertising || advertisingStarting) return;
        advertisingStarting = true;
        int generation = sessionGeneration;
        setState(DiscoveryState.STARTING);
        AdvertisingOptions options = new AdvertisingOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .build();
        String requestedName = getAdvertisingName();

        try {
            connectionsClient.startAdvertising(
                    requestedName, serviceId, connectionLifecycleCallback, options)
                    .addOnSuccessListener(unused -> {
                        if (generation != sessionGeneration || !sessionRequested || !advertisingAllowed
                                || !requestedName.equals(getAdvertisingName())) {
                            connectionsClient.stopAdvertising();
                            advertisingStarting = false;
                            if (sessionRequested && advertisingAllowed) restartAdvertising();
                            return;
                        }
                        advertisingStarting = false;
                        advertising = true;
                        activeAdvertisingName = getAdvertisingName();
                        mainHandler.post(() -> {
                            for (DiscoveryCallback callback : callbacks) {
                                callback.onAdvertisingStatusChanged(true);
                            }
                            updateDiscoveryState();
                        });
                    })
                    .addOnFailureListener(e -> {
                        if (generation != sessionGeneration) return;
                        advertisingStarting = false;
                        reportSessionFailure(DiscoveryError.Reason.ADVERTISING_FAILURE,
                                "Advertising could not start: " + failureDescription(e));
                    });
        } catch (SecurityException error) {
            advertisingStarting = false;
            Log.w(TAG, "Nearby advertising permission was revoked", error);
            reportSessionFailure(DiscoveryError.Reason.MISSING_PERMISSION,
                    "Nearby advertising permission was revoked.");
        }
    }

    public void startDiscovery() {
        if (!sessionRequested || (!forceScanEnabled && !manualScanActive)
            || discovering || discoveryStarting) return;
        discoveryStarting = true;
        int generation = sessionGeneration;
        DiscoveryOptions options = new DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .build();

        try {
            connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback, options)
                    .addOnSuccessListener(unused -> {
                        if (generation != sessionGeneration || !sessionRequested
                                || (!forceScanEnabled && !manualScanActive)) {
                            connectionsClient.stopDiscovery();
                            discoveryStarting = false;
                            return;
                        }
                        discoveryStarting = false;
                        discovering = true;
                        mainHandler.post(this::updateDiscoveryState);
                    })
                    .addOnFailureListener(e -> {
                        if (generation != sessionGeneration) return;
                        discoveryStarting = false;
                        reportSessionFailure(DiscoveryError.Reason.DISCOVERY_FAILURE,
                                "Discovery could not start: " + failureDescription(e));
                    });
        } catch (SecurityException error) {
            discoveryStarting = false;
            Log.w(TAG, "Nearby discovery permission was revoked", error);
            reportSessionFailure(DiscoveryError.Reason.MISSING_PERMISSION,
                    "Nearby discovery permission was revoked.");
        }
    }

    public void startPrerequisitesReadySession() {
        sessionRequested = true;
        settingsRepository = SettingsManager.getInstance(appContext).getRepository();
        applySettings(settingsRepository);
    }

    public void applySettings(@NonNull SettingsRepository repository) {
        settingsRepository = repository;
        peerTopology.setLocalName(getAdvertisingName());
        String desiredName = getAdvertisingName();
        boolean shouldAdvertise = AutoAdvertise.isEnabled(repository)
                && DeviceVisibility.isEnabled(repository);
        advertisingAllowed = shouldAdvertise;
        forceScanEnabled = ForceScan.isEnabled(repository);
        scanIntervalMode = BatterySaver.isEnabled(repository)
                ? ScanInterval.Mode.BATTERY_EFFICIENT
                : ScanInterval.getMode(repository);

        if (!sessionRequested) return;
        if (advertisingAllowed) {
            if (advertising && !desiredName.equals(activeAdvertisingName)) {
                restartAdvertising();
            } else if (!advertising && !advertisingStarting) {
                startAdvertising();
            }
        } else {
            stopAdvertisingOnly();
        }

        cancelScanCycle();
        if (forceScanEnabled) {
            manualScanActive = false;
            cancelManualScanFinish();
            if (!discovering && !discoveryStarting) {
                startDiscovery();
            }
            scheduleScanCycle();
        } else if (!manualScanActive) {
            stopDiscoveryOnly();
        }
        updateDiscoveryState();
    }

    public void onLocalProfileChanged() {
        peerTopology.setLocalName(getAdvertisingName());
        if (sessionRequested && advertisingAllowed
                && !getAdvertisingName().equals(activeAdvertisingName)) {
            restartAdvertising();
        }
        notifyPeerTopologyChanged();
    }

    public void refreshDiscovery() {
        sessionRequested = true;
        if (settingsRepository == null) {
            settingsRepository = SettingsManager.getInstance(appContext).getRepository();
        }
        boolean manual = !ForceScan.isEnabled(settingsRepository);
        manualScanActive = manual;
        sessionGeneration++;
        int generation = sessionGeneration;
        cancelRetry();
        setState(DiscoveryState.STARTING);
        cancelScanCycle();
        cancelManualScanFinish();

        stopDiscoveryOnly();
        mainHandler.postDelayed(() -> {
            if (!sessionRequested || generation != sessionGeneration) return;
            startDiscovery();
            if (manual) {
                long duration = scanIntervalMode == ScanInterval.Mode.CONTINUOUS
                        ? 30_000L
                        : scanIntervalMode.getActiveDurationMillis();
                manualScanFinishRunnable = () -> {
                    manualScanActive = false;
                    manualScanFinishRunnable = null;
                    stopDiscoveryOnly();
                };
                mainHandler.postDelayed(manualScanFinishRunnable, duration);
            } else {
                scheduleScanCycle();
            }
        }, 250L);
    }

    public void stop() {
        sessionRequested = false;
        sessionGeneration++;
        cancelRetry();
        cancelScanCycle();
        cancelManualScanFinish();
        for (Runnable removal : staleDeviceRemovals.values()) {
            mainHandler.removeCallbacks(removal);
        }
        staleDeviceRemovals.clear();
        advertisingStarting = false;
        discoveryStarting = false;
        if (!advertising && !discovering && devices.isEmpty()) {
            if (state != DiscoveryState.ERROR) {
                setState(DiscoveryState.IDLE);
            }
            return;
        }
        setState(DiscoveryState.STOPPING);
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();
        advertising = false;
        discovering = false;
        clearDevices();
        peerTopology.clear();
        notifyPeerTopologyChanged();
        mainHandler.post(() -> {
            for (DiscoveryCallback callback : callbacks) {
                callback.onAdvertisingStatusChanged(false);
                callback.onDiscoveryStopped();
            }
            setState(DiscoveryState.IDLE);
        });
    }

    public void release() {
        stop();
        callbacks.clear();
        connectionsClient.stopAllEndpoints();
    }

    @NonNull
    public List<DiscoveredDevice> getDiscoveredDevices() {
        List<DiscoveredDevice> snapshot = new ArrayList<>(devices.values());
        Collections.sort(snapshot, (left, right) ->
                left.getEndpointId().compareToIgnoreCase(right.getEndpointId()));
        return snapshot;
    }

    @NonNull
    public DiscoveryState getState() {
        return state;
    }

    @NonNull
    public String getLocalDeviceName() {
        return getAdvertisingName();
    }

    @NonNull
    public String getLocalPeerId() {
        return peerTopology.getLocalPeerId();
    }

    @NonNull
    public List<PeerTreeNode> getPeerTree() {
        return peerTopology.getTreeNodes();
    }

    public boolean updateConnectedPeerTopology(@NonNull String endpointId,
                                               @NonNull String peerUserId,
                                               @NonNull String peerName,
                                               @NonNull List<PeerTreeNode> peers) {
        boolean changed = peerTopology.updateFromNeighborTree(
                endpointId, peerUserId, peerName, peers);
        if (changed) {
            notifyPeerTopologyChanged();
        }
        return changed;
    }

    public boolean removeConnectedPeerTopology(@NonNull String endpointId) {
        if (peerTopology.removeNeighbor(endpointId)) {
            notifyPeerTopologyChanged();
            return true;
        }
        return false;
    }

    private void updateDiscoveryState() {
        if (discovering) {
            boolean newlyDiscovering = state != DiscoveryState.DISCOVERING;
            cancelRetry();
            retryAttempt = 0;
            setState(DiscoveryState.DISCOVERING);
            scheduleScanCycle();
            if (newlyDiscovering) {
                mainHandler.post(() -> {
                    for (DiscoveryCallback callback : callbacks) {
                        callback.onDiscoveryStarted();
                    }
                });
            }
        } else if (advertising) {
            setState(DiscoveryState.ADVERTISING);
        }
    }

    private void restartAdvertising() {
        connectionsClient.stopAdvertising();
        advertising = false;
        advertisingStarting = false;
        activeAdvertisingName = null;
        mainHandler.postDelayed(() -> {
            if (sessionRequested && advertisingAllowed) startAdvertising();
        }, 250L);
    }

    private void stopAdvertisingOnly() {
        if (advertising || advertisingStarting) {
            connectionsClient.stopAdvertising();
        }
        advertising = false;
        advertisingStarting = false;
        activeAdvertisingName = null;
        mainHandler.post(() -> {
            for (DiscoveryCallback callback : callbacks) {
                callback.onAdvertisingStatusChanged(false);
            }
        });
    }

    private void stopDiscoveryOnly() {
        if (discovering || discoveryStarting) {
            connectionsClient.stopDiscovery();
        }
        discovering = false;
        discoveryStarting = false;
        if (advertising) {
            setState(DiscoveryState.ADVERTISING);
        } else if (state == DiscoveryState.DISCOVERING || state == DiscoveryState.STARTING) {
            setState(DiscoveryState.IDLE);
        }
        scheduleStaleDeviceRemoval();
    }

    private void scheduleScanCycle() {
        if (!forceScanEnabled || manualScanActive
                || scanIntervalMode == ScanInterval.Mode.CONTINUOUS
                || !discovering || scanWindowFinishRunnable != null) {
            return;
        }
        scanWindowFinishRunnable = () -> {
            scanWindowFinishRunnable = null;
            if (!sessionRequested || !forceScanEnabled || manualScanActive) return;
            stopDiscoveryOnly();
            scanCycleRunnable = () -> {
                scanCycleRunnable = null;
                if (!sessionRequested || !forceScanEnabled || manualScanActive) return;
                startDiscovery();
            };
            mainHandler.postDelayed(scanCycleRunnable, scanIntervalMode.getIdleDurationMillis());
        };
        mainHandler.postDelayed(scanWindowFinishRunnable,
                scanIntervalMode.getActiveDurationMillis());
    }

    private void cancelScanCycle() {
        if (scanCycleRunnable != null) {
            mainHandler.removeCallbacks(scanCycleRunnable);
            scanCycleRunnable = null;
        }
        if (scanWindowFinishRunnable != null) {
            mainHandler.removeCallbacks(scanWindowFinishRunnable);
            scanWindowFinishRunnable = null;
        }
    }

    private void cancelManualScanFinish() {
        if (manualScanFinishRunnable != null) {
            mainHandler.removeCallbacks(manualScanFinishRunnable);
            manualScanFinishRunnable = null;
        }
    }

    private void scheduleStaleDeviceRemoval() {
        for (String endpointId : devices.keySet()) {
            scheduleStaleDeviceRemoval(endpointId);
        }
    }

    private void scheduleStaleDeviceRemoval(@NonNull String endpointId) {
        Runnable oldRemoval = staleDeviceRemovals.remove(endpointId);
        if (oldRemoval != null) mainHandler.removeCallbacks(oldRemoval);
        Runnable removal = () -> {
            staleDeviceRemovals.remove(endpointId);
            if (devices.remove(endpointId) != null) {
                notifyDeviceRegistryChanged();
                notifyPeerLost(endpointId);
            }
        };
        staleDeviceRemovals.put(endpointId, removal);
        mainHandler.postDelayed(removal, 120_000L);
    }

    private void notifyDeviceRegistryChanged() {
        List<DiscoveredDevice> snapshot = getDiscoveredDevices();
        mainHandler.post(() -> {
            for (DiscoveryCallback callback : callbacks) {
                callback.onDeviceRegistryChanged(snapshot);
            }
        });
    }

    private void notifyPeerDiscovered(@NonNull String endpointId, @NonNull String deviceName) {
        SettingsRepository repository = settingsRepository != null
                ? settingsRepository
                : SettingsManager.getInstance(appContext).getRepository();
        PeerNotifications.notifyDiscovered(appContext, repository, endpointId, deviceName);
    }

    private void notifyPeerLost(@NonNull String endpointId) {
        SettingsRepository repository = settingsRepository != null
                ? settingsRepository
                : SettingsManager.getInstance(appContext).getRepository();
        PeerNotifications.notifyLost(appContext, repository, endpointId);
    }

    private void reportSessionFailure(@NonNull DiscoveryError.Reason reason,
                                      @NonNull String description) {
        if (!sessionRequested) return;
        setState(DiscoveryState.ERROR);
        mainHandler.post(() -> {
            DiscoveryError error = new DiscoveryError(reason, description);
            for (DiscoveryCallback callback : callbacks) {
                callback.onDiscoveryError(error);
                callback.onAdvertisingStatusChanged(advertising);
            }
        });
        scheduleRetry();
    }

    private void scheduleRetry() {
        if (!sessionRequested || retryRunnable != null) return;
        long delayMillis = Math.min(30_000L, 1_000L << Math.min(retryAttempt, 5));
        retryAttempt++;
        retryRunnable = () -> {
            retryRunnable = null;
            if (!sessionRequested) return;
            setState(DiscoveryState.STARTING);
            startAdvertising();
            startDiscovery();
        };
        mainHandler.postDelayed(retryRunnable, delayMillis);
    }

    private void cancelRetry() {
        if (retryRunnable != null) {
            mainHandler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId,
                                           @NonNull DiscoveredEndpointInfo endpointInfo) {
                    String name = endpointInfo.getEndpointName().trim();
                    if (name.isEmpty()) name = "MeshDrop Device";
                    DiscoveredDevice device = new DiscoveredDevice(endpointId, name);
                    Runnable pendingRemoval = staleDeviceRemovals.remove(endpointId);
                    if (pendingRemoval != null) mainHandler.removeCallbacks(pendingRemoval);
                    DiscoveredDevice previous = devices.put(endpointId, device);
                    if (previous == null) {
                        mainHandler.post(() -> {
                            for (DiscoveryCallback callback : callbacks) {
                                callback.onDeviceDiscovered(device);
                                callback.onDeviceRegistryChanged(getDiscoveredDevices());
                            }
                        });
                        notifyPeerDiscovered(endpointId, name);
                    } else if (!previous.getDeviceName().equals(name)) {
                        notifyDeviceRegistryChanged();
                    }
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    if (manualScanActive) {
                        scheduleStaleDeviceRemoval(endpointId);
                        return;
                    }
                    if (devices.remove(endpointId) != null) {
                        Runnable pendingRemoval = staleDeviceRemovals.remove(endpointId);
                        if (pendingRemoval != null) mainHandler.removeCallbacks(pendingRemoval);
                        mainHandler.post(() -> {
                            for (DiscoveryCallback callback : callbacks) {
                                callback.onDeviceLost(endpointId);
                                callback.onDeviceRegistryChanged(getDiscoveredDevices());
                            }
                        });
                        notifyPeerLost(endpointId);
                    }
                }
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId,
                                                 @NonNull ConnectionInfo connectionInfo) {
                    NearbyChatManager.getInstance(appContext)
                            .onNearbyConnectionInitiated(endpointId, connectionInfo);
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId,
                                               @NonNull ConnectionResolution result) {
                    NearbyChatManager.getInstance(appContext)
                            .onNearbyConnectionResult(endpointId, result);
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    NearbyChatManager.getInstance(appContext).onNearbyDisconnected(endpointId);
                }
            };

    private void notifyPeerTopologyChanged() {
        List<PeerTreeNode> peers = getPeerTree();
        mainHandler.post(() -> {
            for (DiscoveryCallback callback : callbacks) {
                callback.onPeerTopologyChanged(peers);
            }
        });
    }

    private void clearDevices() {
        devices.clear();
        mainHandler.post(() -> {
            for (DiscoveryCallback callback : callbacks) {
                callback.onDeviceRegistryChanged(Collections.emptyList());
            }
        });
    }

    public void reportError(@NonNull DiscoveryError.Reason reason,
                            @NonNull String description) {
        sessionRequested = false;
        sessionGeneration++;
        cancelRetry();
        advertisingStarting = false;
        discoveryStarting = false;
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();
        advertising = false;
        discovering = false;
        peerTopology.clear();
        clearDevices();
        notifyPeerTopologyChanged();
        setState(DiscoveryState.ERROR);
        mainHandler.post(() -> {
            DiscoveryError error = new DiscoveryError(reason, description);
            for (DiscoveryCallback callback : callbacks) {
                callback.onDiscoveryError(error);
                callback.onAdvertisingStatusChanged(false);
            }
        });
    }

    @NonNull
    private String failureDescription(@NonNull Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message;
    }
}
