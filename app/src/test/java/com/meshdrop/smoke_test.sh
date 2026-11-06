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
meshdrop_database="app/src/main/java/com/meshdrop/database/MeshDropDatabase.java"
conversation_dao="app/src/main/java/com/meshdrop/database/dao/ConversationDao.java"
message_dao="app/src/main/java/com/meshdrop/database/dao/MessageDao.java"
chat_repository="app/src/main/java/com/meshdrop/database/repository/ChatRepository.java"
chat_repository_impl="app/src/main/java/com/meshdrop/database/repository/ChatRepositoryImpl.java"
chat_fragment="app/src/main/java/com/meshdrop/ui/fragment/ChatFragment.java"
profile_fragment="app/src/main/java/com/meshdrop/ui/fragment/ProfileFragment.java"
profile_layout="app/src/main/res/layout/fragment_profile.xml"
theme_preferences="app/src/main/java/com/meshdrop/ui/activity/ThemePreferences.java"

assert_contains "$manifest" 'android:name=".ui.activity.MainActivity"' 'launcher activity is registered'
assert_contains "$manifest" 'android:name="android.intent.action.MAIN"' 'launcher intent is registered'
assert_contains "$mesh_layout" 'com.meshdrop.ui.RadarView' 'mesh radar is present'
assert_contains "$mesh_layout" '@+id/theme_toggle' 'mesh theme toggle is present'
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