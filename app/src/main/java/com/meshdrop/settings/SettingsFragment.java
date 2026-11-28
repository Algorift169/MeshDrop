package com.meshdrop.settings;

import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import androidx.core.app.NotificationManagerCompat;
import com.meshdrop.R;
import com.meshdrop.ui.activity.MainActivity;

public final class SettingsFragment extends Fragment implements SettingsManager.Listener {
    private SettingsManager settingsManager;
    private LinearLayout settingsContent;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && settingsManager != null) {
                    settingsManager.setBoolean(SettingsKeys.PEER_NOTIFICATIONS, true);
                } else if (isAdded()) {
                    showNotificationPermissionDialog();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        settingsManager = SettingsManager.getInstance(requireContext());
        settingsContent = view.findViewById(R.id.settings_content);
        view.findViewById(R.id.settings_back).setOnClickListener(button -> showProfile());
        buildSettings();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        settingsManager.addListener(this);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        showProfile();
                    }
                });
    }

    @Override
    public void onPause() {
        settingsManager.removeListener(this);
        super.onPause();
    }

    @Override
    public void onSettingChanged(@NonNull String key) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onSettingsChanged();
        }
    }

    private void buildSettings() {
        settingsContent.removeAllViews();
        addCategory(SettingsCategory.DISCOVERY);
        addToggle(R.string.setting_force_scan, R.string.setting_force_scan_summary,
                SettingsKeys.FORCE_SCAN, ForceScan.DEFAULT_ENABLED);
        addToggle(R.string.setting_auto_advertise, R.string.setting_auto_advertise_summary,
                SettingsKeys.AUTO_ADVERTISE, AutoAdvertise.DEFAULT_ENABLED);
        addScanInterval();

        addCategory(SettingsCategory.CONNECTIVITY);
        addToggle(R.string.setting_force_bluetooth, R.string.setting_force_bluetooth_summary,
                SettingsKeys.FORCE_BLUETOOTH, ForceBluetooth.DEFAULT_ENABLED);

        addCategory(SettingsCategory.PRIVACY);
        addToggle(R.string.setting_device_visibility, R.string.setting_device_visibility_summary,
                SettingsKeys.DEVICE_VISIBILITY, DeviceVisibility.DEFAULT_ENABLED);
        addToggle(R.string.setting_anonymous_discovery,
                R.string.setting_anonymous_discovery_summary,
                SettingsKeys.ANONYMOUS_DISCOVERY, AnonymousDiscovery.DEFAULT_ENABLED);

        addCategory(SettingsCategory.PERFORMANCE);
        addToggle(R.string.setting_battery_saver, R.string.setting_battery_saver_summary,
                SettingsKeys.BATTERY_SAVER, BatterySaver.DEFAULT_ENABLED);
        addToggle(R.string.setting_radar_animation, R.string.setting_radar_animation_summary,
                SettingsKeys.RADAR_ANIMATION, RadarAnimation.DEFAULT_ENABLED);

        addCategory(SettingsCategory.NOTIFICATIONS);
        addToggle(R.string.setting_peer_notifications,
                R.string.setting_peer_notifications_summary,
                SettingsKeys.PEER_NOTIFICATIONS, PeerNotifications.DEFAULT_ENABLED);
    }

    private void addCategory(@NonNull SettingsCategory category) {
        TextView heading = new TextView(requireContext());
        heading.setText(category.getTitleResource());
        heading.setTextColor(requireContext().getColor(R.color.accent));
        heading.setTextSize(11);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(settingsContent.getChildCount() == 0 ? 8 : 20);
        params.bottomMargin = dp(6);
        params.leftMargin = dp(4);
        settingsContent.addView(heading, params);
    }

    private void addToggle(int titleResource, int summaryResource,
                           @NonNull String key, boolean defaultValue) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));

        LinearLayout textContainer = new LinearLayout(requireContext());
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        TextView title = new TextView(requireContext());
        title.setText(titleResource);
        title.setTextColor(requireContext().getColor(R.color.primary_text));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView summary = new TextView(requireContext());
        summary.setText(summaryResource);
        summary.setTextColor(requireContext().getColor(R.color.secondary_text));
        summary.setTextSize(12);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(3);
        textContainer.addView(title);
        textContainer.addView(summary, summaryParams);

        SwitchMaterial toggle = new SwitchMaterial(requireContext());
        toggle.setChecked(settingsManager.getRepository().getBoolean(key, defaultValue));
        toggle.setContentDescription(getString(titleResource));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (checked && SettingsKeys.PEER_NOTIFICATIONS.equals(key)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                button.setChecked(false);
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
            if (checked && SettingsKeys.PEER_NOTIFICATIONS.equals(key)
                    && !NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) {
                button.setChecked(false);
                showNotificationPermissionDialog();
                return;
            }
            settingsManager.setBoolean(key, checked);
        });

        row.addView(textContainer, textParams);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.leftMargin = dp(10);
        row.addView(toggle, toggleParams);
        settingsContent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addDivider();
    }

    private void addScanInterval() {
        ScanInterval.Mode mode = settingsManager.getScanInterval();
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);

        LinearLayout textContainer = new LinearLayout(requireContext());
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        TextView title = new TextView(requireContext());
        title.setText(R.string.setting_scan_interval);
        title.setTextColor(requireContext().getColor(R.color.primary_text));
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView summary = new TextView(requireContext());
        summary.setText(getScanSummary(mode));
        summary.setTextColor(requireContext().getColor(R.color.secondary_text));
        summary.setTextSize(12);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(3);
        textContainer.addView(title);
        textContainer.addView(summary, summaryParams);
        TextView value = new TextView(requireContext());
        value.setText(getScanTitle(mode));
        value.setTextColor(requireContext().getColor(R.color.accent));
        value.setTextSize(12);
        row.addView(textContainer, textParams);
        row.addView(value);
        row.setOnClickListener(button -> showScanIntervalDialog());
        settingsContent.addView(row);
        addDivider();
    }

    private void showScanIntervalDialog() {
        String[] options = {
                getString(R.string.scan_interval_continuous),
                getString(R.string.scan_interval_balanced),
                getString(R.string.scan_interval_battery_efficient)
        };
        ScanInterval.Mode[] modes = ScanInterval.Mode.values();
        int selected = settingsManager.getScanInterval().ordinal();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_scan_interval)
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    settingsManager.setScanInterval(modes[which]);
                    dialog.dismiss();
                    buildSettings();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int getScanTitle(@NonNull ScanInterval.Mode mode) {
        switch (mode) {
            case BALANCED: return R.string.scan_interval_balanced;
            case BATTERY_EFFICIENT: return R.string.scan_interval_battery_efficient;
            default: return R.string.scan_interval_continuous;
        }
    }

    private int getScanSummary(@NonNull ScanInterval.Mode mode) {
        switch (mode) {
            case BALANCED: return R.string.scan_interval_balanced_summary;
            case BATTERY_EFFICIENT: return R.string.scan_interval_battery_summary;
            default: return R.string.scan_interval_continuous_summary;
        }
    }

    private void addDivider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(requireContext().getColor(R.color.divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(12);
        params.rightMargin = dp(12);
        settingsContent.addView(divider, params);
    }

    private void showProfile() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showProfile();
        }
    }

    private void showNotificationPermissionDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_peer_notifications)
                .setMessage(R.string.settings_notifications_permission)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.open_notification_settings, (dialog, which) -> {
                    Intent intent;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                    } else {
                        intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + requireContext().getPackageName()));
                    }
                    startActivity(intent);
                })
                .show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
