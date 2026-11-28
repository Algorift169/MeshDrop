package com.meshdrop.messaging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MessageProtocol {
    public static final int VERSION = 1;
    public static final int MAX_PACKET_BYTES = 16 * 1024;
    public static final int MAX_TEXT_CHARS = 4_000;

    private MessageProtocol() {}

    public enum PacketType {
        HANDSHAKE,
        TEXT_MESSAGE,
        MESSAGE_ACK,
        CONNECTION_INFO
    }

    @NonNull
    public static byte[] encode(@NonNull MessageEnvelope envelope) {
        validate(envelope);
        JSONObject json = new JSONObject();
        try {
            json.put("v", envelope.protocolVersion);
            json.put("type", envelope.packetType.name());
            json.put("id", envelope.messageId);
            json.put("sender", envelope.senderUserId);
            json.put("recipient", envelope.recipientUserId);
            json.put("timestamp", envelope.timestamp);
            json.put("payload", envelope.payload);
            if (envelope.acknowledgementFor != null) {
                json.put("ack", envelope.acknowledgementFor);
            }
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Could not encode message packet", exception);
        }
        byte[] encoded = json.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Message packet exceeds the size limit");
        }
        return encoded;
    }

    @NonNull
    public static MessageEnvelope decode(@NonNull byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Invalid message packet size");
        }
        try {
            JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            int version = json.getInt("v");
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported message protocol version");
            }
            PacketType type;
            try {
                type = PacketType.valueOf(json.getString("type"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unsupported message packet type", exception);
            }
            MessageEnvelope envelope = new MessageEnvelope(
                    version,
                    type,
                    json.getString("id"),
                    json.getString("sender"),
                    json.getString("recipient"),
                    json.getLong("timestamp"),
                    json.getString("payload"),
                    json.has("ack") ? json.getString("ack") : null);
            validate(envelope);
            return envelope;
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Malformed message packet", exception);
        }
    }

    public static void validateText(@NonNull String text) {
        if (text.trim().isEmpty()) {
            throw new IllegalArgumentException("Message text cannot be empty");
        }
        if (text.length() > MAX_TEXT_CHARS
                || text.getBytes(StandardCharsets.UTF_8).length > MAX_PACKET_BYTES / 2) {
            throw new IllegalArgumentException("Message text exceeds the size limit");
        }
    }

    private static void validate(@NonNull MessageEnvelope envelope) {
        if (envelope.protocolVersion != VERSION) {
            throw new IllegalArgumentException("Unsupported message protocol version");
        }
        validateIdentifier(envelope.messageId, "message ID");
        validateIdentifier(envelope.senderUserId, "sender ID");
        validateIdentifier(envelope.recipientUserId, "recipient ID");
        if (envelope.timestamp <= 0) {
            throw new IllegalArgumentException("Invalid message timestamp");
        }
        if (envelope.packetType == PacketType.TEXT_MESSAGE) {
            validateText(envelope.payload);
        } else if (envelope.payload.getBytes(StandardCharsets.UTF_8).length > 8 * 1024) {
            throw new IllegalArgumentException("Control packet payload exceeds the size limit");
        }
        if (envelope.packetType == PacketType.MESSAGE_ACK) {
            if (envelope.acknowledgementFor == null) {
                throw new IllegalArgumentException("Acknowledgement reference is required");
            }
            validateIdentifier(envelope.acknowledgementFor, "acknowledgement reference");
        }
    }

    private static void validateIdentifier(@NonNull String value, @NonNull String field) {
        if (value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(Character.isLetterOrDigit(character)
                    || character == '-' || character == '_' || character == '.')) {
                throw new IllegalArgumentException("Invalid " + field);
            }
        }
    }
}
