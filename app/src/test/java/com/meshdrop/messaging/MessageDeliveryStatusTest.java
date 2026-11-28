package com.meshdrop.messaging;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MessageDeliveryStatusTest {
    @Test
    public void exposesOnlySupportedDeliveryStates() {
        assertEquals(5, MessageDeliveryStatus.values().length);
        assertEquals(MessageDeliveryStatus.PENDING, MessageDeliveryStatus.valueOf("PENDING"));
        assertEquals(MessageDeliveryStatus.SENDING, MessageDeliveryStatus.valueOf("SENDING"));
        assertEquals(MessageDeliveryStatus.SENT, MessageDeliveryStatus.valueOf("SENT"));
        assertEquals(MessageDeliveryStatus.DELIVERED, MessageDeliveryStatus.valueOf("DELIVERED"));
        assertEquals(MessageDeliveryStatus.FAILED, MessageDeliveryStatus.valueOf("FAILED"));
    }
}
