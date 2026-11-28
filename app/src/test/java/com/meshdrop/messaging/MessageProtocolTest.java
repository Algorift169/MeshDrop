package com.meshdrop.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MessageProtocolTest {
    @Test
    public void textEnvelopeRoundTrips() {
        MessageEnvelope original = new MessageEnvelope(
                MessageProtocol.VERSION,
                MessageProtocol.PacketType.TEXT_MESSAGE,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                1_700_000_000_000L,
                "Hello nearby",
                null);

        MessageEnvelope decoded = MessageProtocol.decode(MessageProtocol.encode(original));

        assertEquals(original.messageId, decoded.messageId);
        assertEquals(original.senderUserId, decoded.senderUserId);
        assertEquals(original.recipientUserId, decoded.recipientUserId);
        assertEquals(original.payload, decoded.payload);
        assertEquals(original.packetType, decoded.packetType);
    }

    @Test
    public void malformedAndUnsupportedPacketsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageProtocol.decode("not json".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> MessageProtocol.decode(("{\"v\":99,\"type\":\"TEXT_MESSAGE\","
                        + "\"id\":\"id\",\"sender\":\"sender\",\"recipient\":\"recipient\","
                        + "\"timestamp\":1,\"payload\":\"hi\"}")
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> MessageProtocol.decode(("{\"v\":1,\"type\":\"UNKNOWN\","
                        + "\"id\":\"id\",\"sender\":\"sender\",\"recipient\":\"recipient\","
                        + "\"timestamp\":1,\"payload\":\"hi\"}")
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void oversizedPacketsAndTextAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageProtocol.decode(new byte[MessageProtocol.MAX_PACKET_BYTES + 1]));
        assertThrows(IllegalArgumentException.class,
                () -> MessageProtocol.validateText("x".repeat(MessageProtocol.MAX_TEXT_CHARS + 1)));
    }
}
