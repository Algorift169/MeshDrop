package com.meshdrop.messaging;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public final class PeerAvatarStore {
    private static final String TAG = "PeerAvatarStore";
    private static final String PROFILE_PREFERENCES = "profile";
    private static final String AVATAR_PREFERENCES = "peer_avatars";
    private static final String PROFILE_PICTURE_KEY = "profile_picture";
    private static final int MAX_AVATAR_BYTES = 4_800;
    private static final int MAX_AVATAR_BASE64_CHARS = 6_400;
    private static final int MAX_DIMENSION = 128;

    private PeerAvatarStore() {}

    @Nullable
    public static String getLocalAvatarPayload(@NonNull Context context) {
        SharedPreferences profile = context.getSharedPreferences(
                PROFILE_PREFERENCES, Context.MODE_PRIVATE);
        String uriText = profile.getString(PROFILE_PICTURE_KEY, null);
        if (uriText == null) return "";

        Bitmap decoded = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uriText))) {
            if (input == null) return "";
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || bounds.outWidth > 16_384 || bounds.outHeight > 16_384) {
                return "";
            }
            options.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight);
        } catch (Exception error) {
            Log.w(TAG, "Could not inspect local profile avatar", error);
            return "";
        }

        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uriText))) {
            if (input == null) return "";
            decoded = BitmapFactory.decodeStream(input, null, options);
            if (decoded == null) return "";
            Bitmap current = scaleToMaximum(decoded, MAX_DIMENSION);
            if (current != decoded) decoded.recycle();
            int quality = 72;
            while (current.getWidth() >= 48 && current.getHeight() >= 48) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                current.compress(Bitmap.CompressFormat.JPEG, quality, output);
                byte[] bytes = output.toByteArray();
                if (bytes.length <= MAX_AVATAR_BYTES) {
                    current.recycle();
                    return Base64.encodeToString(bytes, Base64.NO_WRAP);
                }
                if (quality > 48) {
                    quality -= 12;
                } else {
                    Bitmap smaller = Bitmap.createScaledBitmap(current,
                            Math.max(48, current.getWidth() * 3 / 4),
                            Math.max(48, current.getHeight() * 3 / 4), true);
                    if (smaller != current) current.recycle();
                    current = smaller;
                    quality = 60;
                }
            }
            current.recycle();
        } catch (Exception error) {
            Log.w(TAG, "Could not encode local profile avatar", error);
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
        }
        return "";
    }

    @Nullable
    public static Uri getPeerAvatarUri(@NonNull Context context, @NonNull String peerUserId) {
        String uri = context.getSharedPreferences(AVATAR_PREFERENCES, Context.MODE_PRIVATE)
                .getString(preferenceKey(peerUserId), null);
        return uri == null ? null : Uri.parse(uri);
    }

    public static boolean savePeerAvatar(@NonNull Context context,
                                         @NonNull String peerUserId,
                                         @NonNull String payload) {
        if (payload.length() > MAX_AVATAR_BASE64_CHARS) return false;
        File destination = avatarFile(context, peerUserId);
        if (payload.isEmpty()) {
            context.getSharedPreferences(AVATAR_PREFERENCES, Context.MODE_PRIVATE)
                    .edit().remove(preferenceKey(peerUserId)).apply();
            if (destination.exists()) destination.delete();
            return true;
        }

        final byte[] bytes;
        try {
            bytes = Base64.decode(payload, Base64.NO_WRAP);
        } catch (IllegalArgumentException error) {
            return false;
        }
        if (bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES
                || !Base64.encodeToString(bytes, Base64.NO_WRAP).equals(payload)
                || bytes.length < 3 || (bytes[0] & 0xFF) != 0xFF
                || (bytes[1] & 0xFF) != 0xD8 || (bytes[2] & 0xFF) != 0xFF) {
            return false;
        }

        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || bounds.outWidth > 4_096 || bounds.outHeight > 4_096) {
                return false;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight);
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) return false;
            Bitmap normalized = scaleToMaximum(bitmap, MAX_DIMENSION);
            if (normalized != bitmap) bitmap.recycle();
            File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!normalized.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                    normalized.recycle();
                    temporary.delete();
                    return false;
                }
                output.getFD().sync();
            }
            normalized.recycle();
            if (destination.exists() && !destination.delete()) {
                temporary.delete();
                return false;
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                return false;
            }
            Uri uri = Uri.fromFile(destination);
            context.getSharedPreferences(AVATAR_PREFERENCES, Context.MODE_PRIVATE)
                    .edit().putString(preferenceKey(peerUserId), uri.toString()).apply();
            return true;
        } catch (Exception error) {
            Log.w(TAG, "Could not save peer profile avatar", error);
            if (destination.exists()) destination.delete();
            return false;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static int sampleSizeFor(int width, int height) {
        int sampleSize = 1;
        while (width > MAX_DIMENSION * sampleSize * 2
                || height > MAX_DIMENSION * sampleSize * 2) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    @NonNull
    private static Bitmap scaleToMaximum(@NonNull Bitmap bitmap, int maximumDimension) {
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest <= maximumDimension) return bitmap;
        float scale = (float) maximumDimension / largest;
        return Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
    }

    @NonNull
    private static File avatarFile(@NonNull Context context, @NonNull String peerUserId) {
        return new File(context.getFilesDir(), "peer-avatar-" + safeHash(peerUserId) + ".jpg");
    }

    @NonNull
    private static String preferenceKey(@NonNull String peerUserId) {
        return "avatar:" + safeHash(peerUserId);
    }

    @NonNull
    private static String safeHash(@NonNull String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", item));
            return result.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}