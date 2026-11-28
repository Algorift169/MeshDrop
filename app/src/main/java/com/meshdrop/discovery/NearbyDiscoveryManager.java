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
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
        private final Set<String> pendingEndpoints = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
        private final Set<String> acceptedEndpoints = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
        private final Set<String> connectedEndpoints = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
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
        if (!advertising && !discovering && devices.isEmpty() && connectedEndpoints.isEmpty()) {
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
        pendingEndpoints.clear();
        acceptedEndpoints.clear();
        connectedEndpoints.clear();
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
            if (!discovering && !manualScanActive && devices.remove(endpointId) != null) {
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
                    }
                    requestEndpointConnection(endpointId);
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
                    if (!acceptedEndpoints.add(endpointId)) return;
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                            .addOnFailureListener(error -> {
                                acceptedEndpoints.remove(endpointId);
                                Log.w(TAG, "Could not accept peer connection", error);
                            });
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId,
                                               @NonNull ConnectionResolution result) {
                    pendingEndpoints.remove(endpointId);
                    if (result.getStatus().isSuccess()) {
                        connectedEndpoints.add(endpointId);
                        sendTopology(endpointId);
                    } else {
                        acceptedEndpoints.remove(endpointId);
                        if (peerTopology.removeNeighbor(endpointId)) {
                            notifyPeerTopologyChanged();
                        }
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    connectedEndpoints.remove(endpointId);
                    pendingEndpoints.remove(endpointId);
                    acceptedEndpoints.remove(endpointId);
                    if (peerTopology.removeNeighbor(endpointId)) {
                        notifyPeerTopologyChanged();
                        broadcastTopology(null);
                    }
                    if (devices.containsKey(endpointId)) {
                        mainHandler.postDelayed(() -> requestEndpointConnection(endpointId), 5_000L);
                    }
                }
            };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) {
                return;
            }
            try {
                JSONObject message = new JSONObject(new String(
                        payload.asBytes(), java.nio.charset.StandardCharsets.UTF_8));
                String peerId = message.getString("id");
                String peerName = sanitizePeerName(message.getString("name"));
                if (!isValidPeerId(peerId)) {
                    Log.w(TAG, "Ignoring peer topology with an invalid peer ID");
                    return;
                }
                if (peerId.equals(peerTopology.getLocalPeerId())) return;

                JSONArray routesArray = message.optJSONArray("routes");
                List<PeerTopology.Route> routes = new ArrayList<>();
                if (routesArray != null) {
                    int routeLimit = Math.min(routesArray.length(), 64);
                    for (int index = 0; index < routeLimit; index++) {
                        JSONObject route = routesArray.optJSONObject(index);
                        if (route == null) continue;
                        JSONArray idsArray = route.optJSONArray("ids");
                        JSONArray namesArray = route.optJSONArray("names");
                        if (idsArray == null || namesArray == null) continue;
                        List<String> ids = jsonStrings(idsArray);
                        List<String> receivedNames = jsonStrings(namesArray);
                        List<String> names = new ArrayList<>();
                        for (String receivedName : receivedNames) {
                            names.add(sanitizePeerName(receivedName));
                        }
                        boolean validIds = !ids.isEmpty();
                        for (String routePeerId : ids) {
                            if (!isValidPeerId(routePeerId)) {
                                validIds = false;
                                break;
                            }
                        }
                        String targetId = ids.isEmpty() ? "" : ids.get(ids.size() - 1);
                        String targetName = names.isEmpty() ? "" : names.get(names.size() - 1);
                        if (validIds && ids.size() == names.size()
                                && !targetId.isEmpty() && !targetName.isEmpty()) {
                            routes.add(new PeerTopology.Route(targetId, targetName,
                                    ids, names, null));
                        }
                    }
                }

                boolean changed = peerTopology.updateFromNeighbor(
                        endpointId, peerId, peerName, routes);
                notifyPeerTopologyChanged();
                if (changed) broadcastTopology(endpointId);
            } catch (JSONException | RuntimeException error) {
                Log.w(TAG, "Ignoring invalid peer topology payload", error);
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                           @NonNull PayloadTransferUpdate update) {
        }
    };

    private void requestEndpointConnection(@NonNull String endpointId) {
        if (!sessionRequested || !devices.containsKey(endpointId)
            || connectedEndpoints.contains(endpointId)
                || !pendingEndpoints.add(endpointId)) {
            return;
        }
        connectionsClient.requestConnection(
                getAdvertisingName(), endpointId, connectionLifecycleCallback)
                .addOnFailureListener(error -> {
                    pendingEndpoints.remove(endpointId);
                    Log.w(TAG, "Could not request peer connection", error);
                    if (devices.containsKey(endpointId) && sessionRequested) {
                        mainHandler.postDelayed(() -> requestEndpointConnection(endpointId), 5_000L);
                    }
                });
    }

    private void sendTopology(@NonNull String endpointId) {
        if (!connectedEndpoints.contains(endpointId)) return;
        try {
            JSONObject message = new JSONObject();
            message.put("id", peerTopology.getLocalPeerId());
            message.put("name", getAdvertisingName());
            JSONArray routes = new JSONArray();
            for (PeerTopology.Route route : peerTopology.getAdvertisedRoutes()) {
                JSONObject routeJson = new JSONObject();
                routeJson.put("ids", new JSONArray(route.getPathIds()));
                routeJson.put("names", new JSONArray(route.getPathNames()));
                routes.put(routeJson);
            }
            message.put("routes", routes);
            byte[] data = message.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(data))
                    .addOnFailureListener(error -> Log.w(TAG, "Could not send peer list", error));
        } catch (JSONException error) {
            Log.e(TAG, "Could not encode peer topology", error);
        }
    }

    private void broadcastTopology(@Nullable String excludedEndpointId) {
        for (String endpointId : connectedEndpoints) {
            if (!endpointId.equals(excludedEndpointId)) {
                sendTopology(endpointId);
            }
        }
    }

    private void notifyPeerTopologyChanged() {
        List<PeerTreeNode> peers = getPeerTree();
        mainHandler.post(() -> {
            for (DiscoveryCallback callback : callbacks) {
                callback.onPeerTopologyChanged(peers);
            }
        });
    }

    @NonNull
    private List<String> jsonStrings(@NonNull JSONArray array) {
        List<String> values = new ArrayList<>();
        int limit = Math.min(array.length(), PeerTopology.MAX_HOPS + 1);
        for (int index = 0; index < limit; index++) {
            String value = array.optString(index, "");
            if (value.isEmpty() || value.length() > 64) return Collections.emptyList();
            values.add(value);
        }
        return values;
    }

    @NonNull
    private String sanitizePeerName(@NonNull String name) {
        StringBuilder safeName = new StringBuilder();
        for (int index = 0; index < name.length() && safeName.length() < 48; index++) {
            char character = name.charAt(index);
            if (!Character.isISOControl(character)) {
                safeName.append(character);
            }
        }
        String result = safeName.toString().trim();
        return result.isEmpty() ? "MeshDrop Device" : result;
    }

    private boolean isValidPeerId(@NonNull String peerId) {
        if (peerId.length() > 64 || peerId.isEmpty()) return false;
        for (int index = 0; index < peerId.length(); index++) {
            char character = peerId.charAt(index);
            if (!(Character.isLetterOrDigit(character) || character == '-')) {
                return false;
            }
        }
        return true;
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
        pendingEndpoints.clear();
        acceptedEndpoints.clear();
        connectedEndpoints.clear();
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
