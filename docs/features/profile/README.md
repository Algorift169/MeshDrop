# Profile

## Current behavior

The Profile screen shows the user's name, bio, device model, phone number, and avatar. Name, bio, and phone are read-only in the screen and changed through their Edit dialogs. Device name is not editable; it displays `Build.MODEL`.

The avatar action opens Android's image document picker. The chosen image opens uCrop with a square crop area and circular dimmed overlay, allowing drag and zoom adjustment. Confirming the crop replaces the local avatar; cancelling leaves the current avatar unchanged.

## Local storage

Profile text and the selected cropped-image URI are kept in the app's `profile` SharedPreferences file. The cropped image is written to the app-private file `profile-picture.jpg`. The profile is not uploaded or synchronized to a server.

## Implementation

- Screen and interactions: `app/src/main/java/com/meshdrop/ui/fragment/ProfileFragment.java`
- Layout: `app/src/main/res/layout/fragment_profile.xml`
- Crop dependency: `com.github.yalantis:ucrop:2.2.11`
