#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$script_dir"
while [[ ! -f "$project_root/gradlew" && "$project_root" != "/" ]]; do
    project_root="$(dirname "$project_root")"
done

if [[ ! -f "$project_root/gradlew" ]]; then
    printf 'FAIL: Could not locate the project root from %s\n' "$script_dir" >&2
    exit 1
fi

cd "$project_root"

pass() {
    printf 'PASS: %s\n' "$1"
}

fail() {
    printf 'FAIL: %s\n' "$1" >&2
    exit 1
}

assert_contains() {
    local file="$1"
    local expected="$2"
    local description="$3"

    if grep -Fq -- "$expected" "$file"; then
        pass "$description"
    else
        fail "$description (missing '$expected' in $file)"
    fi
}

manifest="app/src/main/AndroidManifest.xml"
mesh_layout="app/src/main/res/layout/fragment_mesh.xml"
chat_layout="app/src/main/res/layout/fragment_chat.xml"
files_layout="app/src/main/res/layout/fragment_files.xml"
strings="app/src/main/res/values/strings.xml"
meshdrop_database="app/src/main/java/com/meshdrop/database/MeshDropDatabase.java"
conversation_dao="app/src/main/java/com/meshdrop/database/dao/ConversationDao.java"
message_dao="app/src/main/java/com/meshdrop/database/dao/MessageDao.java"
chat_repository="app/src/main/java/com/meshdrop/database/repository/ChatRepository.java"
chat_repository_impl="app/src/main/java/com/meshdrop/database/repository/ChatRepositoryImpl.java"
chat_fragment="app/src/main/java/com/meshdrop/ui/fragment/ChatFragment.java"
profile_fragment="app/src/main/java/com/meshdrop/ui/fragment/ProfileFragment.java"
profile_layout="app/src/main/res/layout/fragment_profile.xml"
theme_preferences="app/src/main/java/com/meshdrop/ui/activity/ThemePreferences.java"
nearby_manager="app/src/main/java/com/meshdrop/discovery/NearbyDiscoveryManager.java"
permission_manager="app/src/main/java/com/meshdrop/discovery/DiscoveryPermissionManager.java"
refresh_radar="app/src/main/java/com/meshdrop/ui/RefreshRadar.java"
discovery_service="app/src/main/java/com/meshdrop/discovery/DiscoveryForegroundService.java"

assert_contains "$manifest" 'android:name=".ui.activity.MainActivity"' 'launcher activity is registered'
assert_contains "$manifest" 'android:name="android.intent.action.MAIN"' 'launcher intent is registered'
assert_contains "$mesh_layout" 'com.meshdrop.ui.RefreshRadar' 'mesh peer tree is present'
assert_contains "$mesh_layout" '@+id/theme_toggle' 'mesh theme toggle is present'
assert_contains "$strings" '<string name="no_conversations">No chats yet</string>' 'chat empty state is explicit'
assert_contains "$strings" '<string name="no_transfers">No files shared yet</string>' 'files empty state is explicit'
if grep -Fq 'MaterialCardView' "$chat_layout" || grep -Fq 'MaterialCardView' "$chat_fragment"; then
    fail 'chat list must not use boxed card containers'
fi
if grep -Fq 'MaterialCardView' "$files_layout"; then
    fail 'files screen must not use a boxed card container'
fi
pass 'chat and files lists are unboxed'
assert_contains "$meshdrop_database" 'DATABASE_NAME = "meshdrop_chats.db"' 'chat database filename is preserved'
assert_contains "$meshdrop_database" 'DATABASE_VERSION = 1' 'chat database version is preserved'
assert_contains "$meshdrop_database" 'CREATE INDEX messages_peer_created_at' 'chat message index is defined'
assert_contains "$meshdrop_database" 'ON DELETE CASCADE' 'chat message foreign key cascades'
assert_contains "$conversation_dao" 'getAll()' 'conversation DAO retrieves all conversations'
assert_contains "$message_dao" 'getForPeer(' 'message DAO retrieves peer history'
assert_contains "$message_dao" 'COLUMN_MESSAGE_ID + " ASC"' 'message ordering has a stable ID tie-breaker'
assert_contains "$chat_repository" 'void saveMessage(' 'chat repository exposes message writes'
assert_contains "$chat_repository_impl" 'sqliteDatabase.beginTransaction();' 'chat writes use a transaction'
assert_contains "$chat_repository_impl" 'Executors.newSingleThreadExecutor()' 'database work uses a worker thread'
assert_contains "$chat_fragment" 'repository.saveMessage(' 'chat composer saves through repository'
if grep -Eq 'ChatDatabase|SQLiteDatabase|android\.database' "$chat_fragment"; then
    fail 'chat UI must not access the database directly'
fi
pass 'chat UI has no direct database access'
assert_contains "$profile_fragment" 'UCrop.of(uri' 'profile photos use the crop flow'
assert_contains "$profile_fragment" 'deviceName.setText(Build.MODEL)' 'device name comes from the device model'

if grep -Fq -- '@+id/profile_device_name_edit' "$profile_layout"; then
    fail 'device name must not have an edit control'
fi
pass 'device name has no edit control'

assert_contains "$theme_preferences" 'getBoolean(KEY_DARK_MODE, true)' 'dark mode is the default'
assert_contains "$nearby_manager" 'Strategy.P2P_CLUSTER' 'Nearby discovery uses P2P_CLUSTER'
assert_contains "$nearby_manager" 'startAdvertising(' 'Nearby advertising is implemented'
assert_contains "$nearby_manager" 'startDiscovery(' 'Nearby discovery is implemented'
assert_contains "$nearby_manager" 'onEndpointFound(' 'endpoint discovery callbacks are implemented'
assert_contains "$nearby_manager" 'onEndpointLost(' 'endpoint loss callbacks are implemented'
assert_contains "$nearby_manager" 'scheduleRetry()' 'Nearby session failures schedule retries'
assert_contains "$nearby_manager" 'refreshDiscovery()' 'Nearby session can be refreshed'
assert_contains "$nearby_manager" 'requestConnection(' 'direct peers are connected for topology exchange'
assert_contains "$nearby_manager" 'acceptConnection(' 'incoming topology connections are accepted'
assert_contains "$nearby_manager" 'sendPayload(' 'peer topology is exchanged over Nearby'
assert_contains "$nearby_manager" 'PeerTopology' 'peer routes are tracked separately from discovery endpoints'
assert_contains "$refresh_radar" '└── ' 'peer tree uses branch markers'
assert_contains "$mesh_layout" '@+id/refresh_discovery' 'refresh button sits beside discovery status'
assert_contains "$discovery_service" 'startForeground(' 'discovery uses a foreground service'
assert_contains "$discovery_service" 'START_STICKY' 'background discovery service is sticky'
assert_contains "$manifest" 'FOREGROUND_SERVICE_CONNECTED_DEVICE' 'connected-device foreground service permission is declared'
assert_contains "$manifest" 'POST_NOTIFICATIONS' 'discovery notification permission is declared'
assert_contains "$permission_manager" 'NEARBY_WIFI_DEVICES' 'Nearby Wi-Fi runtime permission is handled'
assert_contains "$manifest" 'BLUETOOTH_SCAN' 'Bluetooth scan permission is declared'
assert_contains "$manifest" 'BLUETOOTH_ADVERTISE' 'Bluetooth advertise permission is declared'
assert_contains "$manifest" 'BLUETOOTH_CONNECT' 'Bluetooth connect permission is declared'

if grep -R -Fq -- 'ChatDatabase' app/src/main/java; then
    fail 'obsolete ChatDatabase references remain in app source'
fi
pass 'obsolete ChatDatabase references are removed'

make app

if [[ -s build/MeshDrop.apk ]]; then
    pass 'debug APK exists at build/MeshDrop.apk'
else
    fail 'debug APK was not created at build/MeshDrop.apk'
fi

printf 'All MeshDrop smoke tests passed.\n'