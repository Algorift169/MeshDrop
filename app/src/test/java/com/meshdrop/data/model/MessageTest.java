package com.meshdrop.data.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MessageTest {
    @Test
    public void messageKeepsStableParticipantsAndDeliveryState() {
        Message message = new Message("message-1", "conversation-1", "sender-1", "receiver-1",
                "Hello", true, 1234L, "PENDING", "TEXT");

        assertEquals("message-1", message.messageId);
        assertEquals("sender-1", message.senderUserId);
        assertEquals("receiver-1", message.receiverUserId);
        assertEquals("PENDING", message.deliveryStatus);
        assertEquals("Hello", message.body);
    }
}
