package com.meshdrop.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MessageEnvelopeTest {
    @Test
    public void storesTransportMetadataSeparatelyFromMessageBody() {
        MessageEnvelope envelope = new MessageEnvelope(1,
                MessageProtocol.PacketType.MESSAGE_ACK,
                "ack-1", "user-a", "user-b", 1234L, "", "message-1");

        assertEquals("user-a", envelope.senderUserId);
        assertEquals("user-b", envelope.recipientUserId);
        assertEquals("message-1", envelope.acknowledgementFor);
        assertNull(new MessageEnvelope(1, MessageProtocol.PacketType.HANDSHAKE,
                "handshake-1", "user-a", "user-b", 1234L, "{}", null)
                .acknowledgementFor);
    }
}
