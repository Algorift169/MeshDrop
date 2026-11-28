package com.meshdrop.ui;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private final TextView localDeviceText;
    private final View localIndicator;
    private final List<DiscoveredDevice> devices = new ArrayList<>();
    private final List<PeerTreeNode> peerTreeNodes = new ArrayList<>();
    private String localDeviceName = "This device";
    private String localPeerId = "local";
    private String emptyMessage = "No directly discovered peers.";
    private boolean animationEnabled = true;
    private ValueAnimator indicatorAnimator;

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
        scrollParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        addView(scrollView, scrollParams);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout localRow = new LinearLayout(context);
        localRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        localRow.setOrientation(LinearLayout.HORIZONTAL);
        localRow.setPadding(dp(8), dp(8), dp(8), dp(8));
        content.addView(localRow, new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        localIndicator = new View(context);
        GradientDrawable indicatorBackground = new GradientDrawable();
        indicatorBackground.setShape(GradientDrawable.OVAL);
        indicatorBackground.setColor(context.getColor(R.color.accent));
        localIndicator.setBackground(indicatorBackground);
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        indicatorParams.rightMargin = dp(8);
        localRow.addView(localIndicator, indicatorParams);

        localDeviceText = new TextView(context);
        localDeviceText.setTextColor(context.getColor(R.color.primary_text));
        localDeviceText.setTextSize(15);
        localDeviceText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        localRow.addView(localDeviceText, new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        treeText = new TextView(context);
        treeText.setTextColor(context.getColor(R.color.primary_text));
        treeText.setTextSize(14);
        treeText.setTypeface(Typeface.MONOSPACE);
        treeText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        treeText.setPadding(dp(8), dp(8), dp(8), dp(8));
        content.addView(treeText, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        setContentDescription("Nearby device tree");
        renderTree();
    }

    public void setLocalDeviceName(@NonNull String name) {
        localDeviceName = name.trim().isEmpty() ? "This device" : name.trim();
        localDeviceText.setText(localDeviceName);
        renderTree();
    }

    public void setRadarAnimationEnabled(boolean enabled) {
        if (animationEnabled == enabled) return;
        animationEnabled = enabled;
        updateIndicatorAnimation();
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

    private void renderTree() {
        localDeviceText.setText(localDeviceName);
        StringBuilder tree = new StringBuilder();
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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateIndicatorAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (indicatorAnimator != null) {
            indicatorAnimator.cancel();
            indicatorAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    private void updateIndicatorAnimation() {
        if (indicatorAnimator != null) {
            indicatorAnimator.cancel();
            indicatorAnimator = null;
        }
        if (!animationEnabled || !isAttachedToWindow()) {
            localIndicator.setAlpha(1f);
            localIndicator.setScaleX(1f);
            localIndicator.setScaleY(1f);
            return;
        }
        indicatorAnimator = ValueAnimator.ofFloat(0.35f, 1f);
        indicatorAnimator.setDuration(1_200L);
        indicatorAnimator.setRepeatMode(ValueAnimator.REVERSE);
        indicatorAnimator.setRepeatCount(ValueAnimator.INFINITE);
        indicatorAnimator.addUpdateListener(animator -> {
            float value = (float) animator.getAnimatedValue();
            localIndicator.setAlpha(value);
            localIndicator.setScaleX(0.85f + value * 0.3f);
            localIndicator.setScaleY(0.85f + value * 0.3f);
        });
        indicatorAnimator.start();
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
