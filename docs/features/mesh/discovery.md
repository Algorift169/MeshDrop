# Nearby Device Discovery

## Overview

MeshDrop discovers nearby MeshDrop devices through Google Play Services Nearby Connections. It uses `Strategy.P2P_CLUSTER`, which may use Bluetooth, Bluetooth Low Energy, and local Wi-Fi transports without internet. The foreground service both advertises and discovers at the package service ID `com.meshdrop`.

Discovered endpoints are automatically connected through Nearby Connections so devices can exchange a bounded list of public display names and peer paths. Connection identity uses a random per-install ID; profile phone, email, bio, and database information are not exchanged. No chat or file payload is sent.

## Lifecycle

Application startup checks Google Play Services, runtime permissions, Bluetooth hardware, and Bluetooth state. Missing permissions are requested through Android activity results. Disabled Bluetooth is enabled only through `BluetoothAdapter.ACTION_REQUEST_ENABLE` with user confirmation.

After prerequisites pass, `NearbyDiscoveryManager` simultaneously starts advertising and discovery. `onEndpointFound` creates or updates a temporary `DiscoveredDevice` keyed by endpoint ID. `onEndpointLost` removes it. Devices are kept only in memory.

`DiscoveryForegroundService` owns an ongoing notification and keeps advertising and discovery active while the app is backgrounded. A failed advertising or discovery start is retried with bounded exponential backoff; the delay grows from one second to a maximum of 30 seconds. Fragment navigation does not create a second session. Notification permission is optional for scanning; Android may limit where the foreground notification is shown if notification permission is denied.

The Mesh peer tree includes the local device, direct peers, and advertised peer paths received over connections. Disconnecting a relay removes routes learned through that relay. Its refresh button rechecks prerequisites, stops the current Nearby operations, and restarts advertising and discovery with `P2P_CLUSTER`. Nearby does not expose a maximum-range setting; the operating system selects supported radios and range.

## Permissions

The manifest and permission manager account for API levels:

- Android 5-9: legacy Bluetooth permissions and coarse location as applicable.
- Android 10-12: fine location where required.
- Android 12+: `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, and `BLUETOOTH_CONNECT`.
- Android 13+: `NEARBY_WIFI_DEVICES`. Notification permission is requested separately only if peer alerts are enabled.
- Wi-Fi state and change permissions support Nearby's local radio management.

Nearby Connections is responsible for radio use. MeshDrop does not silently enable Bluetooth or directly scan for peers.

## UI integration

`MainActivity` checks prerequisites and starts the foreground service. `NearbyDiscoveryManager` owns Nearby callbacks, direct endpoints, accepted connections, and the in-memory topology exchange. `MeshFragment` registers a main-thread callback, updates the status pill and count, and passes peer paths to `RefreshRadar` for tree rendering.

## Failure scenarios

- Missing or denied permissions show discovery as unavailable and expose a retry action.
- Bluetooth hardware absence is reported without crashing.
- Disabled Bluetooth opens the system confirmation dialog; declining leaves discovery stopped.
- Missing or outdated Google Play Services prevents Nearby startup.
- Advertising or discovery API failures are reported with Nearby's error text when available.

## Testing with physical devices

1. Build and install the debug APK on at least two Android phones.
2. Disable mobile/Wi-Fi internet on both phones.
3. Grant every requested Nearby/Bluetooth/location permission.
4. Confirm the system Bluetooth prompt on both devices.
5. Open Mesh on each phone and verify the ongoing discovery notification appears.
6. Verify each name appears in the opposite device's peer tree.
7. Background one app and confirm it remains discoverable.
8. For topology propagation, connect A-B and B-C while A and C are outside direct range; verify C appears below B on A.
9. Stop MeshDrop on B and verify routes learned only through B disappear.
10. Force-stop a device and reopen it to verify rediscovery.

Emulators may not expose reliable Bluetooth/Wi-Fi radio behavior, so physical-device testing is required for final verification.

## Limitations

- Endpoints do not represent permanent user identities; the topology protocol uses a random per-install ID.
- Peer display names are exchanged automatically with directly connected devices; there is no peer authentication in this prototype.
- The tree can show peers beyond direct radio range through connected peers, but it does not route messages or files.
- No chat transport, file transfer, durable topology, message TTL, or multi-hop message routing is implemented.