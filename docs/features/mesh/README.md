# Mesh Screen

## Discovery behavior

The Mesh destination uses Nearby Connections to advertise and continuously discover direct peers. Failed starts retry with bounded backoff. A foreground service keeps scanning in the background and shows an ongoing notification. `RefreshRadar` shows usernames for directly discovered endpoints in a tree. Its refresh button restarts discovery.

Settings control scanning and advertising independently. Force Scan controls automatic discovery; with it off, scanning starts only from Refresh. Auto Advertise and Device Visibility must both be on for this device to advertise. These controls do not disable the system Bluetooth adapter.

Discovered peers remain unconnected until a user taps a username and both users accept the Nearby authentication token. Peer names in the tree are not physical distance or GPS measurements.

## Peer categories

- Discovered peers: endpoints found directly through Nearby.
- Connected peers: direct peers accepted by both users for chat.
- Relayed peers: topology may be displayed when learned from an approved direct peer; text messages are never forwarded through relays.

## Implementation

- Screen: `app/src/main/java/com/meshdrop/ui/fragment/MeshFragment.java`
- Peer tree and refresh control: `app/src/main/java/com/meshdrop/ui/RefreshRadar.java`
- Lifecycle and prerequisites: `app/src/main/java/com/meshdrop/ui/activity/MainActivity.java`
- Nearby, connection, and endpoint registry: `app/src/main/java/com/meshdrop/discovery/NearbyDiscoveryManager.java`
- Background lifecycle: `app/src/main/java/com/meshdrop/discovery/DiscoveryForegroundService.java`
- Layout: `app/src/main/res/layout/fragment_mesh.xml`
- Detailed guide: [discovery.md](discovery.md)
- Settings behavior: [../settings/README.md](../settings/README.md)
