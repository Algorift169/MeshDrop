package com.meshdrop.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.meshdrop.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yalantis.ucrop.UCrop;

import java.io.File;

public class ProfileFragment extends Fragment {
    private static final String PREFERENCES_NAME = "profile";
    private static final String KEY_PROFILE_PICTURE = "profile_picture";
    private static final String KEY_NAME = "name";
    private static final String KEY_BIO = "bio";
    private static final String KEY_PHONE = "phone";
    private final ActivityResultLauncher<String[]> profilePicturePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onPictureSelected);
    private final ActivityResultLauncher<Intent> profilePictureCropper =
            registerForActivityResult(new StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                    Uri croppedUri = UCrop.getOutput(data);
                    if (croppedUri != null && isAdded()) {
                        saveProfilePicture(croppedUri);
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR && data != null && isAdded()) {
                    Toast.makeText(requireContext(), R.string.profile_photo_crop_failed,
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        SharedPreferences preferences = requireContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);

        bindEditableField(view, preferences, R.id.profile_name_edit, R.id.profile_name_value,
                R.string.name_label, R.string.name_hint, KEY_NAME, "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, false);
        bindEditableField(view, preferences, R.id.profile_bio_edit, R.id.profile_bio_value,
                R.string.bio_label, R.string.bio_hint, KEY_BIO, "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE, true);
        TextView deviceName = view.findViewById(R.id.profile_device_name_value);
        deviceName.setText(Build.MODEL);
        bindEditableField(view, preferences, R.id.profile_phone_edit, R.id.profile_phone_value,
                R.string.phone_label, R.string.phone_hint, KEY_PHONE, "",
                InputType.TYPE_CLASS_PHONE, false);
        String pictureUri = preferences.getString(KEY_PROFILE_PICTURE, null);
        if (pictureUri != null) {
            displayPicture(view, Uri.parse(pictureUri));
        }
        View.OnClickListener choosePicture = button ->
                profilePicturePicker.launch(new String[] {"image/*"});
        view.findViewById(R.id.profile_picture).setOnClickListener(choosePicture);
        view.findViewById(R.id.profile_picture_action).setOnClickListener(choosePicture);
        return view;
    }

    private void onPictureSelected(@Nullable Uri uri) {
        if (uri == null || !isAdded()) {
            return;
        }

        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }

        File destination = new File(requireContext().getFilesDir(), "profile-picture.jpg");
        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropFrame(false);
        options.setShowCropGrid(false);
        options.setToolbarTitle(getString(R.string.adjust_profile_photo));
        Intent cropIntent = UCrop.of(uri, Uri.fromFile(destination))
            .withAspectRatio(1, 1)
            .withMaxResultSize(512, 512)
            .withOptions(options)
            .getIntent(requireContext());
        profilePictureCropper.launch(cropIntent);
        }

        private void saveProfilePicture(@NonNull Uri uri) {
        SharedPreferences preferences = requireContext().getSharedPreferences(
            PREFERENCES_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_PROFILE_PICTURE, uri.toString()).apply();
        View view = getView();
        if (view != null) {
            displayPicture(view, uri);
        }
    }

    private void displayPicture(@NonNull View root, @NonNull Uri uri) {
        ImageView picture = root.findViewById(R.id.profile_picture);
        picture.setImageURI(uri);
        TextView action = root.findViewById(R.id.profile_picture_action);
        action.setText(R.string.change_photo);
    }

    private void bindEditableField(
            @NonNull View root,
            @NonNull SharedPreferences preferences,
            int editButtonId,
            int valueViewId,
            int titleResource,
            int hintResource,
            @NonNull String preferenceKey,
            @NonNull String fallbackValue,
            int inputType,
            boolean multiline) {
        TextView valueView = root.findViewById(valueViewId);
        String currentValue = preferences.getString(preferenceKey, fallbackValue);
        valueView.setText(displayValue(currentValue));

        root.findViewById(editButtonId).setOnClickListener(button -> {
            EditText input = new EditText(requireContext());
            input.setInputType(inputType);
            input.setHint(hintResource);
            input.setText(preferences.getString(preferenceKey, fallbackValue));
            if (multiline) {
                input.setMinLines(3);
                input.setGravity(Gravity.TOP);
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(titleResource)
                    .setView(input)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        String updatedValue = input.getText().toString().trim();
                        preferences.edit().putString(preferenceKey, updatedValue).apply();
                        valueView.setText(displayValue(updatedValue));
                    })
                    .show();
        });
    }

    @NonNull
    private String displayValue(@Nullable String value) {
        return value == null || value.trim().isEmpty()
                ? getString(R.string.profile_not_set)
                : value;
    }
}
