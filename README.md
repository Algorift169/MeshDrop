# MeshDrop

Native Android UI prototype for an offline-first peer-to-peer communication app.
The current frontend uses Java, XML layouts, AndroidX fragments, and Material
Components. Discovery, messaging, transfers, and profile persistence are not
implemented; screens use empty states instead of sample data.

## Build and run

Install Android SDK platform 36 and a Java 17+ JDK, then set `ANDROID_HOME`.

```sh
make app
make erun
make logs
make clean
```

`make app` creates `build/MeshDrop.apk`. `make erun` starts the `MeshDrop_Test`
AVD if needed, waits for boot, installs the APK, and launches the app. Build
outputs are kept under the ignored `build/` directory.