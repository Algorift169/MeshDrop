package com.meshdrop.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
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
import androidx.annotation.VisibleForTesting;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.meshdrop.R;
import com.meshdrop.ui.activity.MainActivity;
import com.meshdrop.messaging.NearbyChatManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";
    private static final String PREFERENCES_NAME = "profile";
    private static final String KEY_PROFILE_PICTURE = "profile_picture";
    private static final String KEY_NAME = "name";
    private static final String KEY_BIO = "bio";
    private static final String KEY_PHONE = "phone";
    private static final String STATE_CROP_SOURCE = "state_crop_source";
    private static final String STATE_CROP_OUTPUT = "state_crop_output";
    private static final int MAX_STAGED_DIMENSION = 2048;
    private static final int MAX_AVATAR_DIMENSION = 512;
    @Nullable private File pendingCropSource;
    @Nullable private File pendingCropOutput;
    private final ActivityResultLauncher<String[]> profilePicturePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onPictureSelected);
    private final ActivityResultLauncher<Intent> profilePictureCropper =
            registerForActivityResult(new StartActivityForResult(), this::onCropResult);

    @VisibleForTesting
    void onCropResult(@NonNull ActivityResult result) {
        Intent data = result.getData();
        try {
            if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                Uri croppedUri = UCrop.getOutput(data);
                if (croppedUri != null && isAdded()) {
                    if (!saveProfilePicture(croppedUri) && !saveCenterCroppedFallback()) {
                        showCropFailure(null);
                    }
                } else if (isAdded() && !saveCenterCroppedFallback()) {
                    showCropFailure(null);
                }
            } else if (result.getResultCode() == UCrop.RESULT_ERROR && isAdded()) {
                Throwable cropError = data != null ? UCrop.getError(data) : null;
                if (cropError != null) {
                    Log.w(TAG, "UCrop returned error, attempting center-crop fallback", cropError);
                }
                if (!saveCenterCroppedFallback()) {
                    showCropFailure(cropError);
                }
            }
        } finally {
            cleanupCropFiles();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            String sourcePath = savedInstanceState.getString(STATE_CROP_SOURCE);
            String outputPath = savedInstanceState.getString(STATE_CROP_OUTPUT);
            pendingCropSource = sourcePath != null ? new File(sourcePath) : null;
            pendingCropOutput = outputPath != null ? new File(outputPath) : null;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingCropSource != null) {
            outState.putString(STATE_CROP_SOURCE, pendingCropSource.getAbsolutePath());
        }
        if (pendingCropOutput != null) {
            outState.putString(STATE_CROP_OUTPUT, pendingCropOutput.getAbsolutePath());
        }
    }

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
        view.findViewById(R.id.profile_settings_entry).setOnClickListener(button -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSettings();
            }
        });
        return view;
    }

    private void onPictureSelected(@Nullable Uri uri) {
        if (uri == null || !isAdded()) {
            return;
        }

        final File stagedSource;
        try {
            stagedSource = stageSelectedPhoto(uri);
        } catch (IOException | RuntimeException error) {
            showCropFailure(error);
            return;
        }

        File destination = new File(requireContext().getCacheDir(),
            "profile-crop-" + UUID.randomUUID() + ".png");
        try {
            if (!destination.exists()) {
                destination.createNewFile();
            }
        } catch (IOException ignored) {
        }
        pendingCropSource = stagedSource;
        pendingCropOutput = destination;
        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropFrame(false);
        options.setShowCropGrid(false);
        options.setCompressionFormat(Bitmap.CompressFormat.PNG);
        options.setToolbarTitle(getString(R.string.adjust_profile_photo));
        Intent cropIntent = UCrop.of(Uri.fromFile(stagedSource), Uri.fromFile(destination))
            .withAspectRatio(1, 1)
            .withMaxResultSize(MAX_AVATAR_DIMENSION, MAX_AVATAR_DIMENSION)
            .withOptions(options)
            .getIntent(requireContext());
        profilePictureCropper.launch(cropIntent);
        }

    @NonNull
    private File stageSelectedPhoto(@NonNull Uri uri) throws IOException {
        File stagedSource = new File(requireContext().getCacheDir(),
                "profile-source-" + UUID.randomUUID() + ".jpg");
        Bitmap decoded = decodeScaledBitmapFromUri(uri, MAX_STAGED_DIMENSION);
        if (decoded == null) {
            throw new IOException("Selected photo could not be decoded.");
        }
        Bitmap oriented = applyExifOrientation(uri, decoded);
        try (OutputStream output = new FileOutputStream(stagedSource)) {
            if (!oriented.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                throw new IOException("Selected photo could not be staged.");
            }
            output.flush();
            if (stagedSource.length() == 0) {
                throw new IOException("Selected photo was empty.");
            }
            return stagedSource;
        } catch (IOException | RuntimeException error) {
            stagedSource.delete();
            throw error;
        } finally {
            if (oriented != decoded && !oriented.isRecycled()) {
                oriented.recycle();
            }
            if (!decoded.isRecycled()) {
                decoded.recycle();
            }
        }
    }

    @Nullable
    private Bitmap decodeScaledBitmapFromUri(@NonNull Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream boundsStream = requireContext().getContentResolver().openInputStream(uri)) {
            if (boundsStream == null) {
                throw new IOException("Photo stream could not be opened.");
            }
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
        try (InputStream decodeStream = requireContext().getContentResolver().openInputStream(uri)) {
            if (decodeStream == null) {
                throw new IOException("Photo stream could not be opened.");
            }
            return BitmapFactory.decodeStream(decodeStream, null, decodeOptions);
        }
    }

    @NonNull
    private Bitmap applyExifOrientation(@NonNull Uri uri, @NonNull Bitmap bitmap) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return bitmap;
        }
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream exifStream = requireContext().getContentResolver().openInputStream(uri)) {
            if (exifStream != null) {
                ExifInterface exif = new ExifInterface(exifStream);
                orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (IOException | RuntimeException ignored) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270f);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.preScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.preScale(1f, -1f);
                break;
            default:
                return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return rotated;
    }

    private static int calculateInSampleSize(int width, int height, int maxDimension) {
        int inSampleSize = 1;
        while ((width / inSampleSize) > maxDimension || (height / inSampleSize) > maxDimension) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private void cleanupCropFiles() {
        if (pendingCropSource != null) {
            pendingCropSource.delete();
            pendingCropSource = null;
        }
        if (pendingCropOutput != null) {
            pendingCropOutput.delete();
            pendingCropOutput = null;
        }
    }

    private boolean saveProfilePicture(@NonNull Uri uri) {
        File savedImage = new File(requireContext().getFilesDir(),
                "profile-picture-" + UUID.randomUUID() + ".jpg");
        Bitmap decoded = null;
        Bitmap scaled = null;
        try {
            decoded = decodeScaledBitmapFromUri(uri, MAX_AVATAR_DIMENSION);
            if (decoded == null) {
                throw new IOException("Crop output could not be decoded.");
            }
            scaled = scaleDownIfNeeded(decoded, MAX_AVATAR_DIMENSION);
            try (OutputStream output = new FileOutputStream(savedImage)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                    throw new IOException("Crop output could not be compressed.");
                }
                output.flush();
            }
            if (savedImage.length() == 0) {
                throw new IOException("Crop output was empty.");
            }
        } catch (IOException | RuntimeException error) {
            if (savedImage.exists()) savedImage.delete();
            Log.e(TAG, "Could not persist cropped profile photo", error);
            return false;
        } finally {
            if (scaled != null && scaled != decoded && !scaled.isRecycled()) {
                scaled.recycle();
            }
            if (decoded != null && !decoded.isRecycled()) {
                decoded.recycle();
            }
        }

        persistSavedProfilePicture(savedImage);
        return true;
    }

    private boolean saveCenterCroppedFallback() {
        if (pendingCropSource == null || !pendingCropSource.exists() || pendingCropSource.length() == 0) {
            return false;
        }
        File savedImage = new File(requireContext().getFilesDir(),
                "profile-picture-" + UUID.randomUUID() + ".jpg");
        Bitmap decoded = null;
        Bitmap cropped = null;
        Bitmap scaled = null;
        try {
            decoded = decodeScaledBitmapFromUri(Uri.fromFile(pendingCropSource), MAX_STAGED_DIMENSION);
            if (decoded == null) {
                return false;
            }
            int size = Math.min(decoded.getWidth(), decoded.getHeight());
            int left = (decoded.getWidth() - size) / 2;
            int top = (decoded.getHeight() - size) / 2;
            cropped = Bitmap.createBitmap(decoded, left, top, size, size);
            scaled = scaleDownIfNeeded(cropped, MAX_AVATAR_DIMENSION);
            try (OutputStream output = new FileOutputStream(savedImage)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                    throw new IOException("Fallback crop could not be compressed.");
                }
                output.flush();
            }
            if (savedImage.length() == 0) {
                throw new IOException("Fallback crop output was empty.");
            }
        } catch (IOException | RuntimeException error) {
            if (savedImage.exists()) savedImage.delete();
            Log.e(TAG, "Fallback profile photo crop failed", error);
            return false;
        } finally {
            if (scaled != null && scaled != cropped && !scaled.isRecycled()) {
                scaled.recycle();
            }
            if (cropped != null && cropped != decoded && !cropped.isRecycled()) {
                cropped.recycle();
            }
            if (decoded != null && !decoded.isRecycled()) {
                decoded.recycle();
            }
        }

        persistSavedProfilePicture(savedImage);
        return true;
    }

    @NonNull
    private static Bitmap scaleDownIfNeeded(@NonNull Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap;
        }
        float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }

    private void persistSavedProfilePicture(@NonNull File savedImage) {
        SharedPreferences preferences = requireContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
        String oldUri = preferences.getString(KEY_PROFILE_PICTURE, null);
        preferences.edit().putString(KEY_PROFILE_PICTURE, Uri.fromFile(savedImage).toString()).apply();
        NearbyChatManager.getInstance(requireContext()).onLocalProfileChanged();
        View view = getView();
        if (view != null) {
            displayPicture(view, Uri.fromFile(savedImage));
        }
        if (oldUri != null) {
            Uri previousUri = Uri.parse(oldUri);
            if ("file".equals(previousUri.getScheme()) && previousUri.getPath() != null) {
                File previousImage = new File(previousUri.getPath());
                if (!previousImage.equals(savedImage)
                        && requireContext().getFilesDir().equals(previousImage.getParentFile())) {
                    previousImage.delete();
                }
            }
        }
    }

    private void showCropFailure(@Nullable Throwable error) {
        if (error != null) {
            Log.e(TAG, "Profile photo crop failed", error);
        }
        Toast.makeText(requireContext(), R.string.profile_photo_crop_failed,
                Toast.LENGTH_SHORT).show();
    }

    private void displayPicture(@NonNull View root, @NonNull Uri uri) {
        ImageView picture = root.findViewById(R.id.profile_picture);
        picture.setImageURI(null);
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
                        if (KEY_NAME.equals(preferenceKey)
                                && getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).onLocalProfileNameChanged();
                        }
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
