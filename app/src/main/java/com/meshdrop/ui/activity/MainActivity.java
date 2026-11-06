package com.meshdrop.ui.activity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.meshdrop.R;
import com.meshdrop.ui.fragment.ChatFragment;
import com.meshdrop.ui.fragment.FilesFragment;
import com.meshdrop.ui.fragment.MeshFragment;
import com.meshdrop.ui.fragment.ProfileFragment;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_SELECTED_DESTINATION = "selected_destination";
    private int selectedDestination = R.id.nav_mesh;
    private BottomNavigationView bottomNavigation;
    private FrameLayout contentContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferences.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setStatusBarColor(getColor(R.color.background));
        getWindow().setNavigationBarColor(getColor(R.color.background));

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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_DESTINATION, selectedDestination);
        super.onSaveInstanceState(outState);
    }
}
