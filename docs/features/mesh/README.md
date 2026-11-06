# Mesh Screen

## Current behavior

The Mesh destination contains the `RadarView`, a status pill, and a nearby-distance panel alongside the radar. The panel currently displays “No nearby devices to show yet.” The radar draws the local device marker, rings, and a sweep graphic.

## Limitations

The radar is a visual placeholder. It does not run Bluetooth, Wi-Fi, or other peer discovery, and the distance panel has no live measurements. The status pill indicates discovery is unavailable.

## Implementation

- Screen: `app/src/main/java/com/meshdrop/ui/fragment/MeshFragment.java`
- Radar drawing: `app/src/main/java/com/meshdrop/ui/RadarView.java`
- Layout: `app/src/main/res/layout/fragment_mesh.xml`
