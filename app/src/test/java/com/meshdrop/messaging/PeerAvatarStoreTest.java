package com.meshdrop.messaging;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PeerAvatarStoreTest {
    @Test
    public void compressesAndPersistsPeerAvatarWithinPacketLimit() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String peerId = UUID.randomUUID().toString();
        File source = new File(context.getCacheDir(), "source-" + peerId + ".jpg");
        Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(38, 112, 84));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(320, 240, 120, paint);
        try (FileOutputStream output = new FileOutputStream(source)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output);
        }
        bitmap.recycle();
        context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
                .putString("profile_picture", Uri.fromFile(source).toString()).commit();

        String payload = PeerAvatarStore.getLocalAvatarPayload(context);

        assertNotNull(payload);
        assertTrue(payload.length() <= 6_400);
        assertTrue(PeerAvatarStore.savePeerAvatar(context, peerId, payload));
        Uri savedUri = PeerAvatarStore.getPeerAvatarUri(context, peerId);
        assertNotNull(savedUri);
        assertTrue(new File(savedUri.getPath()).isFile());

        assertTrue(PeerAvatarStore.savePeerAvatar(context, peerId, ""));
        assertNull(PeerAvatarStore.getPeerAvatarUri(context, peerId));
        source.delete();
        deletePeerAvatarFile(context, peerId);
    }

    @Test
    public void rejectsMalformedOrOversizedAvatarPayloads() {
        Context context = RuntimeEnvironment.getApplication();
        String peerId = UUID.randomUUID().toString();

        assertFalse("invalid image must be rejected",
            PeerAvatarStore.savePeerAvatar(context, peerId, "not-an-image"));
        assertFalse("oversized data must be rejected",
            PeerAvatarStore.savePeerAvatar(context, peerId, "a".repeat(6_401)));
        assertNull(PeerAvatarStore.getPeerAvatarUri(context, peerId));
    }

    private void deletePeerAvatarFile(@NonNull Context context, @NonNull String peerId) {
        Uri uri = PeerAvatarStore.getPeerAvatarUri(context, peerId);
        if (uri != null && uri.getPath() != null) {
            new File(uri.getPath()).delete();
        }
    }
}