package com.meshdrop.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

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
public class MessageDaoTest {
    private Context context;
    private String databaseName;
    private MeshDropDatabase database;
    private ConversationDao conversationDao;
    private MessageDao messageDao;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        databaseName = "message-test-" + UUID.randomUUID() + ".db";
        database = new MeshDropDatabase(context, databaseName);
        conversationDao = new ConversationDao(database);
        messageDao = new MessageDao(database);
        conversationDao.upsert(new ConversationEntity(
                "peer-a", "peer-a", "Alex", "", "TEXT", 0, 100, 100));
    }

    @After
    public void tearDown() {
        database.close();
        context.deleteDatabase(databaseName);
    }

    @Test
    public void insertsDeduplicatesAndUpdatesDeliveryState() {
        MessageEntity message = outgoing("message-1", "peer-a", "Hello", 200);
        assertTrue(messageDao.insertIfMissing(message) >= 0);
        assertEquals(-1, messageDao.insertIfMissing(message));

        List<MessageEntity> history = messageDao.getForPeer("peer-a");
        assertEquals(1, history.size());
        assertEquals("message-1", history.get(0).messageId);
        assertEquals(1, messageDao.updateDeliveryStatus("message-1", "SENT"));
        assertEquals("SENT", messageDao.getByMessageId("message-1").deliveryStatus);
    }

    @Test
    public void acknowledgementCanOnlyUpdateMessageForItsRecipient() {
        messageDao.insert(outgoing("message-1", "peer-a", "Hello", 200));

        assertEquals(0, messageDao.updateOutgoingDeliveryStatusForPeer(
                "message-1", "peer-b", "DELIVERED"));
        assertEquals(1, messageDao.updateOutgoingDeliveryStatusForPeer(
                "message-1", "peer-a", "DELIVERED"));
    }

    @Test
    public void unreadIncomingMessagesPersistUntilConversationIsOpened() {
        MessageEntity incoming = new MessageEntity(-1, "incoming-1", "peer-a", "peer-a",
                "local-user", "New message", false, 250, "DELIVERED", "TEXT", false);
        messageDao.insertIfMissing(incoming);

        assertEquals(1, conversationDao.getByPeerId("peer-a").unreadCount);
        assertEquals(1, messageDao.markConversationRead("peer-a"));
        assertEquals(0, conversationDao.getByPeerId("peer-a").unreadCount);
        assertTrue(messageDao.getForPeer("peer-a").get(0).isRead);
    }

    @Test
    public void historySurvivesDatabaseCloseAndReopen() {
        messageDao.insert(outgoing("persisted-message", "peer-a", "Saved text", 500));
        database.close();

        database = new MeshDropDatabase(context, databaseName);
        messageDao = new MessageDao(database);

        List<MessageEntity> history = messageDao.getForPeer("peer-a");
        assertEquals(1, history.size());
        assertEquals("Saved text", history.get(0).messageContent);
        assertEquals("persisted-message", history.get(0).messageId);
    }

    @Test
    public void migratesVersionOneRowsWithoutDroppingHistory() {
        database.close();
        context.deleteDatabase(databaseName);
        databaseName = "message-migration-test-" + UUID.randomUUID() + ".db";
        context.deleteDatabase(databaseName);
        SQLiteDatabase legacy = context.openOrCreateDatabase(databaseName, 0, null);
        legacy.execSQL("CREATE TABLE conversations (peer_id TEXT PRIMARY KEY NOT NULL, "
                + "peer_name TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        legacy.execSQL("CREATE TABLE messages (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "peer_id TEXT NOT NULL, body TEXT NOT NULL, outgoing INTEGER NOT NULL, "
                + "created_at INTEGER NOT NULL, FOREIGN KEY(peer_id) REFERENCES conversations(peer_id) ON DELETE CASCADE)");
        ContentValues conversation = new ContentValues();
        conversation.put("peer_id", "legacy-peer");
        conversation.put("peer_name", "Old Alex");
        conversation.put("updated_at", 300L);
        legacy.insertOrThrow("conversations", null, conversation);
        ContentValues oldMessage = new ContentValues();
        oldMessage.put("peer_id", "legacy-peer");
        oldMessage.put("body", "Saved before upgrade");
        oldMessage.put("outgoing", 1);
        oldMessage.put("created_at", 250L);
        legacy.insertOrThrow("messages", null, oldMessage);
        legacy.setVersion(1);
        legacy.close();

        database = new MeshDropDatabase(context, databaseName);
        conversationDao = new ConversationDao(database);
        messageDao = new MessageDao(database);
        assertEquals(1, conversationDao.getAll().size());
        ConversationEntity migrated = conversationDao.getByPeerId("legacy-peer");
        assertEquals("Old Alex", migrated.peerUsername);
        MessageEntity migratedMessage = messageDao.getForPeer("legacy-peer").get(0);
        assertEquals("Saved before upgrade", migratedMessage.messageContent);
        assertTrue(!migratedMessage.messageId.isEmpty());
        assertEquals("FAILED", migratedMessage.deliveryStatus);
        assertNotNull(migratedMessage.senderUserId);
        assertTrue(migratedMessage.isRead);
    }

    private MessageEntity outgoing(String messageId, String peerId, String body, long timestamp) {
        return new MessageEntity(-1, messageId, peerId, "local-user", peerId,
                body, true, timestamp, "PENDING", "TEXT");
    }
}
