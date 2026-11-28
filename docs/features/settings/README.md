# Settings

## Architecture

Java settings classes are contained in `com.meshdrop.settings`. `SettingsRepository` stores typed values in the private `meshdrop_settings` SharedPreferences file; it stores no profile contact information. `SettingsManager` supplies defaults and notifies listeners. `SettingsFragment` is opened from the Profile screen directly below Mesh Overview and returns to Profile through its toolbar or system Back action.

`MainActivity` coordinates Bluetooth/permission prerequisites. `NearbyDiscoveryManager` applies settings to its existing advertising and discovery operations; changing settings does not construct a second Nearby client or end established peer connections unless discovery is explicitly stopped by a capability failure or the foreground service is stopped.

## Defaults

| Setting | Default | Effect |
| --- | --- | --- |
| Force Scan | On | Runs discovery automatically, subject to the selected duty cycle. Off stops automatic scans and waits for Refresh. |
| Force Bluetooth | On | If Bluetooth is disabled, asks Android to enable it through the system confirmation flow. Off suppresses automatic prompts. |
| Auto Advertise | On | Allows this device to advertise when Device Visibility is on. |
| Scan Interval | Continuous | Continuous, Balanced (30 seconds scanning / 15 seconds idle), or Battery Efficient (10 seconds scanning / 60 seconds idle). |
| Device Visibility | On | Combined with Auto Advertise to determine whether this device advertises. It does not stop scanning. |
| Anonymous Discovery | Off | Uses the profile display name. On advertises as `MeshDrop User`; it does not change the saved profile. |
| Battery Saver | Off | Selects the Battery Efficient scan cycle and disables the local indicator pulse. Advertising and connections are preserved. |
| Radar Animation | On | Pulses the local-device indicator. Off stops only that animation; peer updates continue. |
| Peer Notifications | Off | Emits one notification for each newly discovered or finally lost endpoint, when Android notifications are allowed. |

## Discovery integration

Advertising is allowed only when both `AutoAdvertise` and `DeviceVisibility` are on. Discovery is controlled independently by `ForceScan` and `ScanInterval`. With Force Scan off, Refresh starts a bounded manual discovery session; current peers remain in the list during the scan and are removed after the two-minute stale timeout if not rediscovered.

Non-continuous modes use Nearby's supported stop/start lifecycle operations. Their times are application duty-cycle windows, not guarantees of Bluetooth scan frequency or physical range. Nearby chooses available radios; MeshDrop cannot force maximum physical range.

Anonymous mode changes only the public Nearby endpoint name. Peer topology exchange still uses a random per-install identifier and public peer names, not profile phone, email, bio, or database IDs.

## Bluetooth and permissions

Force Bluetooth controls whether the app requests activation. Android restricts ordinary applications from silently enabling or disabling Bluetooth, especially on Android 13 and later. When enabled, MeshDrop uses `BluetoothAdapter.ACTION_REQUEST_ENABLE`; disabling Bluetooth itself remains an Android system action.

Nearby permissions are requested by the existing discovery permission flow. Android 13+ notification permission is also needed for peer-event notifications. If notification delivery is disabled, enabling Peer Notifications explains how to open the app's Android notification settings.

## Lifecycle

The existing discovery foreground service keeps Nearby operations alive while the app UI is backgrounded. Moving between Mesh, Chat, Files, Profile, and Settings does not restart Nearby sessions. Force Scan, advertising visibility, duty cycle, and display animation can change independently. A permission or Bluetooth capability failure stops the service operations until prerequisites are restored.

## Limitations

- Automatic Nearby connections are used for bounded peer-name/topology exchange; they do not carry chat messages or files.
- Notification delivery depends on Android notification permission and channel settings.
- Device manufacturers may throttle background work despite a foreground service.
- Scan duty cycles do not represent measured distances and cannot guarantee discovery latency.
- Physical-device tests are required to validate radio behavior; emulator tests cannot certify it.
