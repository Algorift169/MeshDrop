package com.meshdrop.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.meshdrop.data.model.PeerIdentity;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PeerIdentityTest {
    @Test
    public void sameUsernameDoesNotMergeDifferentStableUsers() {
        PeerIdentity first = new PeerIdentity("user-1", "Alex", "Alex", false);
        PeerIdentity second = new PeerIdentity("user-2", "Alex", "Alex", false);

        assertEquals(first.username, second.username);
        assertNotEquals(first.userId, second.userId);
    }

    @Test
    public void anonymousPublicNameDoesNotReplaceRealUsername() {
        PeerIdentity identity = new PeerIdentity("user-1", "Alex", "MeshDrop User", true);

        assertEquals("Alex", identity.username);
        assertEquals("MeshDrop User", identity.publicDisplayName);
        assertTrue(identity.anonymousMode);
    }

    @Test
    public void localUserIdPersistsAcrossManagerRecreation() {
        Context context = RuntimeEnvironment.getApplication();
        PeerIdentity first = new PeerIdentityManager(context).getLocalIdentity();
        PeerIdentity afterRestart = new PeerIdentityManager(context).getLocalIdentity();

        assertEquals(first.userId, afterRestart.userId);
    }
}
