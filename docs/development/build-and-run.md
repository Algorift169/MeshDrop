# Build and Run

## Requirements

- Java 17 or newer
- Android SDK with platform 36 installed
- `ANDROID_HOME` pointing to the Android SDK
- For emulator runs, an installed AVD named `MeshDrop_Test` by default

## Make targets

| Command | Behavior |
| --- | --- |
| `make app` | Assemble the debug APK and copy it to `build/MeshDrop.apk`. |
| `make test` | Run Bash smoke checks for app structure/features and build the debug APK. |
| `make install` | Build and install the APK to the ADB-selected device. |
| `make run` | Build, install, and launch MeshDrop on the ADB-selected device. |
| `make erun` | Build, start the configured emulator if no emulator is connected, wait for boot, install, and launch the app. |
| `make emulator` | Start the configured AVD in the foreground. |
| `make logs` | Stream MeshDrop, AndroidRuntime, and ActivityTaskManager log lines. |
| `make clean` | Remove root `build/` and legacy `app/build/` outputs. |

Set `AVD=My_AVD` to select another emulator name, for example `make erun AVD=Pixel_API_35`.

## Output locations

Gradle module outputs are redirected to the root `build/` directory. The distributable debug APK is `build/MeshDrop.apk`. The root `build/` directory is ignored by Git. `gradle/wrapper/gradle-wrapper.jar` is Gradle bootstrap tooling required by `gradlew`; it is not an output artifact.

## Nearby discovery dependency

Discovery uses `com.google.android.gms:play-services-nearby:19.3.0`. Devices must have compatible Google Play Services and run Android 6.0 or newer (the app's `minSdk` is 23).

Relevant permissions include legacy Bluetooth/location permissions on older API levels, Bluetooth scan/advertise/connect permissions on Android 12+, fine/coarse location where required, and `NEARBY_WIFI_DEVICES` on Android 13+. Notification permission is optional for scanning and is requested when Peer Notifications is enabled. Permissions are requested at runtime and Bluetooth is enabled only through the system confirmation flow.

Discovery runs in a `connectedDevice` foreground service with an ongoing notification. Direct chat connections are requested from Mesh Radar and require both users to compare the Nearby authentication token and accept. Messages use the existing Nearby session and persist in SQLite. They are direct only; no file or multi-hop message routing is implemented.

## Physical-device testing

Emulators may not provide reliable real-radio discovery. Build and install on at least two physical devices:

```sh
make app
make install
```

Enable Bluetooth on both devices, grant Nearby permissions, open Mesh, and disable internet. Verify bidirectional discovery, tap each other's username, compare the authentication token out of band, and accept on both devices. Exchange messages in both directions, verify delivery ACKs, leave and reopen Chats, then restart the app and confirm history persists. Move devices out of range, reconnect, and confirm pending messages remain. Physical devices are required to validate radio behavior; no physical-device tests are claimed as passed by the automated build.

## Notes

The project currently uses Android Gradle Plugin 8.7.3 with `compileSdk = 36`. Gradle may print a compatibility warning because that plugin version was tested through SDK 35; the warning does not by itself indicate a failed build.

On Linux, the emulator target sets `QT_QPA_PLATFORM=xcb` because some desktop environments export a Wayland Qt platform that is not bundled with the Android emulator.