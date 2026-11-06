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
| `make install` | Build and install the APK to the ADB-selected device. |
| `make run` | Build, install, and launch MeshDrop on the ADB-selected device. |
| `make erun` | Build, start the configured emulator if no emulator is connected, wait for boot, install, and launch the app. |
| `make emulator` | Start the configured AVD in the foreground. |
| `make logs` | Stream MeshDrop, AndroidRuntime, and ActivityTaskManager log lines. |
| `make clean` | Remove root `build/` and legacy `app/build/` outputs. |

Set `AVD=My_AVD` to select another emulator name, for example `make erun AVD=Pixel_API_35`.

## Output locations

Gradle module outputs are redirected to the root `build/` directory. The distributable debug APK is `build/MeshDrop.apk`. The root `build/` directory is ignored by Git. `gradle/wrapper/gradle-wrapper.jar` is Gradle bootstrap tooling required by `gradlew`; it is not an output artifact.

## Notes

The project currently uses Android Gradle Plugin 8.7.3 with `compileSdk = 36`. Gradle may print a compatibility warning because that plugin version was tested through SDK 35; the warning does not by itself indicate a failed build.

On Linux, the emulator target sets `QT_QPA_PLATFORM=xcb` because some desktop environments export a Wayland Qt platform that is not bundled with the Android emulator.