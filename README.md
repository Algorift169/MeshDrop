# MeshDrop

MeshDrop is a native Android prototype for offline-first peer-to-peer
communication, built with Java, XML layouts, AndroidX fragments, and Material
Components. Chat messages and profile data currently persist locally; peer
discovery, network delivery, and file transfers remain unimplemented.

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

## Documentation

See [docs/README.md](docs/README.md) for the architecture overview, build guide,
and separate documentation for each feature.