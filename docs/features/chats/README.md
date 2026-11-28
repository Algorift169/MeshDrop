# Direct Offline Chats

## Overview

Mesh Radar lists discovered usernames. Tapping a direct peer starts a Nearby Connections request. Both devices automatically accept the Nearby connection handshake so messaging does not require a separate approval prompt. The connection is not treated as an authenticated real-world identity.

After the automatic handshake, devices exchange a stable per-install user ID and public username. Only then is a conversation created. Chat uses direct connections only; multi-hop messaging, group chats, file transfer, and read receipts are not implemented.

## Architecture

- `NearbyDiscoveryManager` continues discovery and advertising without initiating connections.
- `NearbyChatManager` owns outgoing requests, automatic connection acceptance, connected endpoint-to-user mappings, message payloads, and acknowledgement processing.
- `PeerIdentityManager` reuses a persistent per-install ID and reads the profile username. Nearby endpoint IDs are temporary transport addresses, never conversation keys.
- `MessageProtocol` encodes versioned UTF-8 JSON envelopes, validates packet types and IDs, and limits packets and text size.
- `ChatRepositoryImpl` performs database operations off the main thread; `ChatFragment` and `ChatActivity` use repository callbacks and transport events.

## Sending and delivery

The composer stores each outgoing message before sending. If the peer is disconnected, it stays `PENDING`. Nearby payload success sets `SENT`, not `DELIVERED`. The receiver validates the envelope, checks the sender against the connected peer, inserts by unique message ID, and acknowledges only after persistence. Duplicate packets are not inserted again but are acknowledged again. The sender marks a message `DELIVERED` only after a matching ACK from the intended peer. Retries are bounded; exhausted messages become `FAILED`.

## Persistence and migration

`meshdrop_chats.db` is schema version 3. Migrations add identity, preview, UUID, delivery, and read-state columns without dropping conversations or messages. Existing history is treated as read during migration. New incoming messages are stored unread unless their conversation is open. New conversations use the peer's stable user ID; repeated connections to that ID reuse one conversation.

## Identity and trust

The peer's stable random installation ID is the conversation key. Username is display metadata and may be duplicated or changed. Anonymous Discovery advertises a generic name and sends that generic public username during handshake; it does not overwrite the saved profile. Connections are automatically accepted, so Nearby's authentication token is not manually verified. There is no public-key identity infrastructure, account verification, key recovery, or application-level end-to-end encryption; do not treat a connected username as proof of identity. Payloads should be treated as untrusted and message contents are not logged.

## Lifecycle and limitations

Connections survive navigation between Mesh, Chats, and ChatActivity while the discovery service remains active. They are lost when Nearby disconnects or the process/service stops. Pending messages are persisted and retried when the user establishes a direct connection again; delivery is not guaranteed while the recipient is offline or the app is stopped.

Only text messages are supported. There are no group chats, file or media payloads, read receipts, seen indicators, message relaying, or guaranteed background delivery. Physical-device testing with two phones is required; emulator tests do not verify radio behavior.

## Notifications and unread messages

The device that initiated the radar tap opens the conversation after identity exchange. The receiving device stays on its current screen. A persisted incoming message increments that conversation's unread count and posts a notification when Android notification permission is granted. Chats with unread messages sort above read chats; the Chats navigation item shows the total unread count, and each unread conversation has a red dot. Opening a conversation marks its incoming messages read and clears its notification. On Android 13 and later, notification permission is requested after Nearby prerequisites are satisfied.

## Testing

1. Install MeshDrop on two physical Android devices, enable Bluetooth, grant Nearby permissions, and turn internet off.
2. Keep Mesh open on both and tap the other device's username.
3. Confirm that only the initiating device opens ChatActivity; the receiver stays on its current screen.
4. Send a message and verify the receiver gets a notification, unread navigation badge, and conversation dot.
5. Open that conversation on the receiver and verify the unread indicators clear and the notification disappears.
6. Exchange messages in both directions and confirm delivery status changes only after the receiver stores and acknowledges the message.
7. Force-stop/reopen MeshDrop and verify message history and unread state remain stored.
8. Test a disconnect and reconnect; verify pending messages remain and duplicate delivery does not create duplicate rows.

## Implementation

- Messaging: `app/src/main/java/com/meshdrop/messaging/`
- Chat list: `app/src/main/java/com/meshdrop/ui/fragment/ChatFragment.java`
- Conversation Activity: `app/src/main/java/com/meshdrop/ui/activity/ChatActivity.java`
- Persistence: `app/src/main/java/com/meshdrop/database/`
- Layouts: `app/src/main/res/layout/fragment_chat.xml`, `activity_chat.xml`, and `item_*.xml`
