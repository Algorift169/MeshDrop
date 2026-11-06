# System Overview

## Application shell

`MainActivity` hosts the bottom navigation and the active feature fragment. The four destinations are Mesh, Chat, Files, and Profile. Destination fragments are added once and hidden or shown when navigation changes. A peer-specific chat can also be opened through `MainActivity.openConversation(peerId, displayName)`.

## Feature boundaries

- Mesh renders a local radar illustration and nearby-device status. It does not scan for peers.
- Chats use `ChatDatabase`, an Android `SQLiteOpenHelper`, for on-device conversation and message storage. There is no network transport or incoming-message path connected to the UI.
- Profile field values and the selected avatar URI are stored in the `profile` SharedPreferences file. The cropped image itself is in the app's private files directory.
- Appearance is stored in the `appearance` SharedPreferences file and applied with `AppCompatDelegate`.
- Files currently presents an empty state; no transfer service or transfer database is connected.

## Persistence and privacy

SQLite, SharedPreferences, and the app-private files directory are local to the app installation. Chat content and profile data are not synced to another device or account. Clearing app data removes these local records and the avatar file.

## Main implementation locations

- Activity and navigation: `app/src/main/java/com/meshdrop/ui/activity/MainActivity.java`
- Feature screens: `app/src/main/java/com/meshdrop/ui/fragment/`
- Chat database: `app/src/main/java/com/meshdrop/data/ChatDatabase.java`
- Theme preference: `app/src/main/java/com/meshdrop/ui/activity/ThemePreferences.java`
- Layouts and palettes: `app/src/main/res/layout/`, `app/src/main/res/values/`, and `app/src/main/res/values-night/`
