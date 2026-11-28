package com.meshdrop.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import com.meshdrop.database.dao.ConversationDao;
import com.meshdrop.database.dao.MessageDao;
import com.meshdrop.database.entity.ConversationEntity;
import com.meshdrop.database.entity.MessageEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.UUID;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ConversationDaoTest {
    private Context context;
    private String databaseName;
    private MeshDropDatabase database;
    private ConversationDao dao;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        databaseName = "conversation-test-" + UUID.randomUUID() + ".db";
        database = new MeshDropDatabase(context, databaseName);
        dao = new ConversationDao(database);
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase(databaseName);
    }

    @Test
    public void createsAndSortsConversationByLatestActivity() {
        dao.upsert(conversation("peer-a", "Alex", 100));
        dao.upsert(conversation("peer-b", "Sam", 300));
        dao.updateLastMessage("peer-a", "Latest from Alex", "TEXT", 500);

        List<ConversationEntity> conversations = dao.getAll();

        assertEquals(2, conversations.size());
        assertEquals("peer-a", conversations.get(0).peerUserId);
        assertEquals("Latest from Alex", conversations.get(0).lastMessage);
        assertEquals(500, conversations.get(0).updatedAt);
        assertNotNull(dao.getByConversationId("peer-b"));
    }

    @Test
    public void upsertUpdatesPeerNameWithoutDeletingMessages() {
        dao.upsert(conversation("peer-a", "Alex", 100));
        MessageDao messageDao = new MessageDao(database);
        messageDao.insert(new MessageEntity(-1, "message-1", "peer-a", "local-user",
                "peer-a", "Stored text", true, 150, "PENDING", "TEXT"));
        dao.upsert(new ConversationEntity("peer-a", "peer-a", "Alexandra",
                "Preview", "TEXT", 200, 100, 200));

        ConversationEntity stored = dao.getByPeerId("peer-a");
        assertNotNull(stored);
        assertEquals("Alexandra", stored.peerUsername);
        assertEquals("Preview", stored.lastMessage);
        assertEquals(1, dao.getAll().size());
        assertEquals(1, messageDao.getForPeer("peer-a").size());
    }

    @Test
    public void unreadConversationsSortAheadOfMoreRecentReadConversations() {
        dao.upsert(conversation("peer-read", "Recent", 900));
        dao.upsert(conversation("peer-unread", "Unread", 100));
        MessageDao messageDao = new MessageDao(database);
        messageDao.insert(new MessageEntity(-1, "unread-message", "peer-unread",
                "peer-unread", "local-user", "Unread content", false, 150,
                "DELIVERED", "TEXT", false));

        List<ConversationEntity> conversations = dao.getAll();

        assertEquals("peer-unread", conversations.get(0).peerUserId);
        assertEquals(1, conversations.get(0).unreadCount);
        assertEquals("peer-read", conversations.get(1).peerUserId);
    }

    private ConversationEntity conversation(String peerId, String username, long timestamp) {
        return new ConversationEntity(peerId, peerId, username, "", "TEXT", 0,
                timestamp, timestamp);
    }
}
