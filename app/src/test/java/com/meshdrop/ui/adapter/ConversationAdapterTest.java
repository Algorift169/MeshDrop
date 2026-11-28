package com.meshdrop.ui.adapter;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;

import com.meshdrop.R;
import com.meshdrop.data.model.Conversation;
import com.meshdrop.messaging.PeerAvatarStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collections;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class ConversationAdapterTest {
    @Test
    public void bindsPeerProfilePhotoInChatHeadAndFallsBackWhenMissing() {
        Context context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.Theme_MeshDrop);
        String peerId = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(
                peerId, "Alice", "Hello", System.currentTimeMillis());

        ConversationAdapter adapter = new ConversationAdapter(clicked -> {});
        FrameLayout parent = new FrameLayout(context);
        ConversationAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.submitList(Collections.singletonList(conversation));
        adapter.onBindViewHolder(holder, 0);

        assertEquals(View.VISIBLE, holder.avatar.getVisibility());
        assertEquals(View.GONE, holder.avatarPhoto.getVisibility());
        assertEquals("A", holder.avatar.getText().toString());

        String base64 = createValidJpegBase64();
        PeerAvatarStore.savePeerAvatar(context, peerId, base64);
        adapter.onBindViewHolder(holder, 0);

        assertEquals(View.GONE, holder.avatar.getVisibility());
        assertEquals(View.VISIBLE, holder.avatarPhoto.getVisibility());

        Uri savedUri = PeerAvatarStore.getPeerAvatarUri(context, peerId);
        if (savedUri != null && savedUri.getPath() != null) {
            new File(savedUri.getPath()).delete();
        }
        adapter.onBindViewHolder(holder, 0);

        assertEquals(View.VISIBLE, holder.avatar.getVisibility());
        assertEquals(View.GONE, holder.avatarPhoto.getVisibility());
    }

    private String createValidJpegBase64() {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(40, 100, 160));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        bitmap.recycle();
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
    }
}
