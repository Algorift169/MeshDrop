package com.meshdrop.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.meshdrop.R;
import com.meshdrop.ui.activity.ThemePreferences;
import androidx.core.view.ViewCompat;

public class MeshFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mesh, container, false);
        ImageButton themeToggle = view.findViewById(R.id.theme_toggle);
        updateThemeToggle(themeToggle);
        themeToggle.setOnClickListener(button -> ThemePreferences.toggle(requireContext()));
        return view;
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
