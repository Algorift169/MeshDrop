package com.meshdrop.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.meshdrop.R;
import com.meshdrop.ui.activity.ThemePreferences;
import com.meshdrop.ui.activity.MainActivity;
import com.meshdrop.ui.RefreshRadar;
import com.meshdrop.discovery.DiscoveryCallback;
import com.meshdrop.discovery.DiscoveryError;
import com.meshdrop.discovery.DiscoveryState;
import com.meshdrop.discovery.NearbyDiscoveryManager;
import com.meshdrop.data.model.DiscoveredDevice;
import com.meshdrop.data.model.PeerTreeNode;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import androidx.core.view.ViewCompat;

public class MeshFragment extends Fragment {
    private TextView discoveryStatus;
    private TextView devicesCount;
    private TextView devicesLabel;
    private RefreshRadar refreshRadar;
    private View statusContainer;
    private NearbyDiscoveryManager discoveryManager;
    private List<DiscoveredDevice> currentDevices = new ArrayList<>();
    private List<PeerTreeNode> currentPeers = new ArrayList<>();

    private final DiscoveryCallback discoveryCallback = new DiscoveryCallback() {
        @Override
        public void onRequirementChanged(@NonNull DiscoveryState state) {
            updateStatus(state, null);
        }

        @Override
        public void onDiscoveryError(@NonNull DiscoveryError error) {
            updateStatus(DiscoveryState.ERROR, error);
        }

        @Override
        public void onDeviceRegistryChanged(@NonNull List<DiscoveredDevice> devices) {
            updateDevices(devices);
        }

        @Override
        public void onPeerTopologyChanged(@NonNull List<PeerTreeNode> peers) {
            currentPeers = new ArrayList<>(peers);
            if (refreshRadar != null) {
                refreshRadar.setPeerTreeNodes(peers);
            }
            updatePeerCount();
        }
    };
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mesh, container, false);
        discoveryStatus = view.findViewById(R.id.discovery_status);
        devicesCount = view.findViewById(R.id.discovered_devices_count);
        devicesLabel = view.findViewById(R.id.discovered_devices_label);
        refreshRadar = view.findViewById(R.id.refresh_radar);
        statusContainer = view.findViewById(R.id.discovery_status_container);
        statusContainer.setOnClickListener(button -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshDiscovery();
            }
        });
        view.findViewById(R.id.refresh_discovery).setOnClickListener(button -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshDiscovery();
            }
        });

        ImageButton themeToggle = view.findViewById(R.id.theme_toggle);
        updateThemeToggle(themeToggle);
        themeToggle.setOnClickListener(button -> ThemePreferences.toggle(requireContext()));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            discoveryManager = ((MainActivity) getActivity()).getDiscoveryManager();
            if (discoveryManager != null) {
                refreshRadar.setLocalDeviceName(discoveryManager.getAdvertisingName());
                refreshRadar.setLocalPeerId(discoveryManager.getLocalPeerId());
                discoveryManager.registerCallback(discoveryCallback);
            }
        }
    }

    @Override
    public void onPause() {
        if (discoveryManager != null) {
            discoveryManager.unregisterCallback(discoveryCallback);
        }
        super.onPause();
    }

    private void updateStatus(@NonNull DiscoveryState state,
                              @Nullable DiscoveryError error) {
        if (!isAdded() || discoveryStatus == null) return;
        int textResource;
        switch (state) {
            case REQUESTING_PERMISSIONS:
                textResource = R.string.discovery_requesting_permissions;
                break;
            case ENABLING_BLUETOOTH:
                textResource = R.string.discovery_bluetooth_disabled;
                break;
            case STARTING:
            case CHECKING_REQUIREMENTS:
                textResource = R.string.discovery_initializing;
                break;
            case STOPPING:
                textResource = R.string.discovery_stopped;
                break;
            case DISCOVERING:
                textResource = R.string.discovery_active;
                break;
            case ERROR:
                textResource = R.string.discovery_error;
                break;
            default:
                textResource = R.string.discovery_unavailable;
        }
        discoveryStatus.setText(error != null
                ? error.getDescription()
                : getString(textResource));
    }

    private void updateDevices(@NonNull List<DiscoveredDevice> devices) {
        if (!isAdded() || refreshRadar == null) return;
        currentDevices = new ArrayList<>(devices);
        refreshRadar.setDiscoveredDevices(currentDevices);
        updatePeerCount();
    }

    private void updatePeerCount() {
        if (!isAdded() || devicesCount == null || devicesLabel == null) return;
        Set<String> peerIds = new HashSet<>();
        Set<String> representedEndpoints = new HashSet<>();
        int directCount = 0;
        for (PeerTreeNode peer : currentPeers) {
            peerIds.add(peer.getPeerId());
            if (peer.getHopCount() == 1) {
                directCount++;
                if (peer.getEndpointId() != null) {
                    representedEndpoints.add(peer.getEndpointId());
                }
            }
        }
        for (DiscoveredDevice device : currentDevices) {
            if (!representedEndpoints.contains(device.getEndpointId())) {
                peerIds.add("endpoint:" + device.getEndpointId());
                directCount++;
            }
        }

        int total = peerIds.size();
        int relayedCount = Math.max(0, total - directCount);
        if (total == 0) {
            devicesCount.setText(getString(R.string.peers_visible_many, 0));
            devicesLabel.setText(R.string.no_peers_profile);
        } else if (total == 1) {
            devicesCount.setText(R.string.peers_visible_one);
            devicesLabel.setText(getString(R.string.peer_count_summary, directCount, relayedCount));
        } else {
            devicesCount.setText(getString(R.string.peers_visible_many, total));
            devicesLabel.setText(getString(R.string.peer_count_summary, directCount, relayedCount));
        }
    }

    private void updateThemeToggle(@NonNull ImageButton themeToggle) {
        boolean darkMode = ThemePreferences.isDarkMode(requireContext());
        int descriptionResource = darkMode
                ? R.string.switch_to_light_mode
                : R.string.switch_to_dark_mode;
        themeToggle.setImageResource(darkMode ? R.drawable.ic_light_mode : R.drawable.ic_dark_mode);
        themeToggle.setContentDescription(getString(descriptionResource));
        ViewCompat.setTooltipText(themeToggle, getString(descriptionResource));
    }
}
