package com.meshdrop.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.fragment.app.FragmentActivity;

import com.yalantis.ucrop.UCrop;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class ProfileFragmentTest {
    private FragmentActivity activity;
    private ProfileFragment fragment;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
        fragment = new ProfileFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
    }

    @Test
    public void selectingPngPhotoStagesNormalizedJpegAndStartsUCrop() throws Exception {
        File inputPng = createTestImage("input.png", 800, 600, Bitmap.CompressFormat.PNG);

        Method onPictureSelected = ProfileFragment.class
                .getDeclaredMethod("onPictureSelected", Uri.class);
        onPictureSelected.setAccessible(true);
        onPictureSelected.invoke(fragment, Uri.fromFile(inputPng));

        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals("com.yalantis.ucrop.UCropActivity", started.getComponent().getClassName());
        Uri stagedUri = started.getParcelableExtra(UCrop.EXTRA_INPUT_URI);
        Uri outputUri = started.getParcelableExtra(UCrop.EXTRA_OUTPUT_URI);
        assertNotNull(stagedUri);
        assertNotNull(outputUri);
        assertTrue(stagedUri.getPath().endsWith(".jpg"));
        assertTrue(outputUri.getPath().endsWith(".png"));
        assertTrue(new File(stagedUri.getPath()).length() > 0);
        assertTrue(new File(outputUri.getPath()).exists());
    }

    @Test
    public void cropErrorFallsBackToCenterCroppedStagedPhoto() throws Exception {
        File stagedJpg = createTestImage("staged.jpg", 600, 400, Bitmap.CompressFormat.JPEG);
        File emptyOutput = new File(RuntimeEnvironment.getApplication().getCacheDir(), "empty-crop.png");
        emptyOutput.createNewFile();

        setField("pendingCropSource", stagedJpg);
        setField("pendingCropOutput", emptyOutput);

        Intent errorData = new Intent().putExtra(UCrop.EXTRA_ERROR,
                new IllegalArgumentException("Simulated UCrop rounding error"));
        fragment.onCropResult(new ActivityResult(UCrop.RESULT_ERROR, errorData));

        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("profile", Context.MODE_PRIVATE);
        String savedUriText = prefs.getString("profile_picture", null);
        assertNotNull(savedUriText);
        File savedFile = new File(Uri.parse(savedUriText).getPath());
        assertTrue(savedFile.isFile());
        Bitmap savedBitmap = BitmapFactory.decodeFile(savedFile.getAbsolutePath());
        assertNotNull(savedBitmap);
        assertEquals(400, savedBitmap.getWidth());
        assertEquals(400, savedBitmap.getHeight());
        assertFalse(stagedJpg.exists());
        assertFalse(emptyOutput.exists());
    }

    @Test
    public void successfulCropPersistsAvatarAndCleansTempFiles() throws Exception {
        File stagedJpg = createTestImage("staged2.jpg", 500, 500, Bitmap.CompressFormat.JPEG);
        File croppedPng = createTestImage("cropped.png", 320, 320, Bitmap.CompressFormat.PNG);

        setField("pendingCropSource", stagedJpg);
        setField("pendingCropOutput", croppedPng);

        Intent resultData = new Intent().putExtra(UCrop.EXTRA_OUTPUT_URI, Uri.fromFile(croppedPng));
        fragment.onCropResult(new ActivityResult(Activity.RESULT_OK, resultData));

        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("profile", Context.MODE_PRIVATE);
        String savedUriText = prefs.getString("profile_picture", null);
        assertNotNull(savedUriText);
        File savedFile = new File(Uri.parse(savedUriText).getPath());
        assertTrue(savedFile.isFile());
        Bitmap savedBitmap = BitmapFactory.decodeFile(savedFile.getAbsolutePath());
        assertNotNull(savedBitmap);
        assertEquals(320, savedBitmap.getWidth());
        assertEquals(320, savedBitmap.getHeight());
        assertFalse(stagedJpg.exists());
        assertFalse(croppedPng.exists());
    }

    @Test
    public void savedInstanceStatePreservesPendingCropPaths() throws Exception {
        File stagedJpg = createTestImage("state-src.jpg", 200, 200, Bitmap.CompressFormat.JPEG);
        File outputPng = new File(RuntimeEnvironment.getApplication().getCacheDir(), "state-out.png");
        setField("pendingCropSource", stagedJpg);
        setField("pendingCropOutput", outputPng);

        Bundle state = new Bundle();
        fragment.onSaveInstanceState(state);

        assertEquals(stagedJpg.getAbsolutePath(), state.getString("state_crop_source"));
        assertEquals(outputPng.getAbsolutePath(), state.getString("state_crop_output"));
    }

    private File createTestImage(String name, int width, int height, Bitmap.CompressFormat format)
            throws Exception {
        File file = new File(RuntimeEnvironment.getApplication().getCacheDir(), name);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(20, 120, 90));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(format, 95, out);
        }
        bitmap.recycle();
        return file;
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = ProfileFragment.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(fragment, value);
    }
}
