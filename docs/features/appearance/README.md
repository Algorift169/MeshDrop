# Appearance

## Current behavior

The app supports dark and light appearance modes. Dark mode is the default. The icon in the top-right of the Mesh screen toggles modes; the choice is stored locally and applied at startup, across the app's screens, system bars, and Material surfaces.

The palette uses semantic color resources. The light palette is in `values/colors.xml`; the black-default dark palette is in `values-night/colors.xml`. Shared surfaces, status pills, text, dividers, and radar colors resolve from those resources.

## Implementation

- Preference and AppCompat mode: `app/src/main/java/com/meshdrop/ui/activity/ThemePreferences.java`
- Startup application: `app/src/main/java/com/meshdrop/ui/activity/MainActivity.java`
- Toggle: `app/src/main/java/com/meshdrop/ui/fragment/MeshFragment.java`
- Theme resources: `app/src/main/res/values/styles.xml`, `app/src/main/res/values/colors.xml`, and `app/src/main/res/values-night/`
