package com.meshdrop.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.meshdrop.R;
import com.meshdrop.data.model.DiscoveredDevice;
import com.meshdrop.data.model.PeerTreeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RefreshRadar extends FrameLayout {
    private final TextView treeText;
    private final ImageButton refreshButton;
    private final List<DiscoveredDevice> devices = new ArrayList<>();
    private final List<PeerTreeNode> peerTreeNodes = new ArrayList<>();
    private String localDeviceName = "This device";
    private String localPeerId = "local";
    private String emptyMessage = "No directly discovered peers.";

    public RefreshRadar(@NonNull Context context) {
        this(context, null);
    }

    public RefreshRadar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(context.getColor(R.color.card));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        LayoutParams scrollParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        scrollParams.setMargins(dp(4), dp(44), dp(4), dp(4));
        addView(scrollView, scrollParams);

        treeText = new TextView(context);
        treeText.setTextColor(context.getColor(R.color.primary_text));
        treeText.setTextSize(14);
        treeText.setTypeface(Typeface.MONOSPACE);
        treeText.setGravity(Gravity.TOP | Gravity.START);
        treeText.setPadding(dp(8), dp(8), dp(8), dp(8));
        scrollView.addView(treeText, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        refreshButton = new ImageButton(context);
        refreshButton.setImageResource(android.R.drawable.ic_popup_sync);
        refreshButton.setBackgroundResource(android.R.drawable.btn_default);
        refreshButton.setColorFilter(context.getColor(R.color.accent));
        refreshButton.setContentDescription("Refresh discovery using available radios");
        ViewCompat.setTooltipText(refreshButton, "Refresh discovery using available radios");
        LayoutParams refreshParams = new LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.END);
        addView(refreshButton, refreshParams);
        setContentDescription("Nearby device tree");
        renderTree();
    }

    public void setLocalDeviceName(@NonNull String name) {
        localDeviceName = name.trim().isEmpty() ? "This device" : name.trim();
        renderTree();
    }

    public void setLocalPeerId(@NonNull String peerId) {
        localPeerId = peerId;
        renderTree();
    }

    public void setEmptyMessage(@NonNull String message) {
        emptyMessage = message;
        renderTree();
    }

    public void setDiscoveredDevices(@Nullable List<DiscoveredDevice> discoveredDevices) {
        devices.clear();
        if (discoveredDevices != null) {
            devices.addAll(discoveredDevices);
        }
        renderTree();
    }

    public void setPeerTreeNodes(@Nullable List<PeerTreeNode> nodes) {
        peerTreeNodes.clear();
        if (nodes != null) {
            peerTreeNodes.addAll(nodes);
        }
        renderTree();
    }

    public void setOnRefreshClickListener(@Nullable OnClickListener listener) {
        refreshButton.setOnClickListener(listener);
    }

    private void renderTree() {
        StringBuilder tree = new StringBuilder(localDeviceName);
        List<PeerTreeNode> visibleNodes = new ArrayList<>(peerTreeNodes);
        Set<String> representedEndpoints = new HashSet<>();
        for (PeerTreeNode node : peerTreeNodes) {
            if (node.getEndpointId() != null) {
                representedEndpoints.add(node.getEndpointId());
            }
        }
        for (DiscoveredDevice device : devices) {
            if (!representedEndpoints.contains(device.getEndpointId())) {
                visibleNodes.add(new PeerTreeNode("endpoint:" + device.getEndpointId(),
                        device.getDeviceName(), localPeerId, device.getEndpointId(), 1));
            }
        }

        if (visibleNodes.isEmpty()) {
            tree.append('\n').append(emptyMessage);
        } else {
            Map<String, List<PeerTreeNode>> children = new HashMap<>();
            for (PeerTreeNode node : visibleNodes) {
                String parentId = node.getParentId();
                if (parentId != null) {
                    List<PeerTreeNode> siblings = children.get(parentId);
                    if (siblings == null) {
                        siblings = new ArrayList<>();
                        children.put(parentId, siblings);
                    }
                    siblings.add(node);
                }
            }
            for (List<PeerTreeNode> siblings : children.values()) {
                Collections.sort(siblings, (left, right) -> {
                    int nameComparison = left.getDeviceName().compareToIgnoreCase(right.getDeviceName());
                    return nameComparison != 0
                            ? nameComparison
                            : left.getPeerId().compareTo(right.getPeerId());
                });
            }
            tree.append('\n');
            appendChildren(tree, children, localPeerId, "", new HashSet<>());
        }
        treeText.setText(tree.toString());
    }

    private void appendChildren(StringBuilder tree,
                                Map<String, List<PeerTreeNode>> children,
                                String parentId,
                                String prefix,
                                Set<String> visited) {
        List<PeerTreeNode> siblings = children.get(parentId);
        if (siblings == null) return;
        for (int index = 0; index < siblings.size(); index++) {
            PeerTreeNode node = siblings.get(index);
            if (!visited.add(node.getPeerId())) continue;
            boolean last = index == siblings.size() - 1;
            tree.append(prefix).append(last ? "└── " : "├── ")
                    .append(node.getDeviceName()).append('\n');
            appendChildren(tree, children, node.getPeerId(),
                    prefix + (last ? "    " : "│   "), visited);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
