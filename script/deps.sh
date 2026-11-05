#!/usr/bin/env bash
set -Eeuo pipefail

# MeshDrop Android development environment for Debian/Ubuntu-based Linux.
# Installs a Java 17+ JDK, Android SDK command-line tools, SDK platform 36,
# Android build-tools, platform-tools (adb), and basic Linux utilities.
# Android Studio and Android Emulator are intentionally NOT installed.

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_VERSION="15859902"
SDK_PLATFORM="android-36"
BUILD_TOOLS_VERSION="36.0.0"

if ! command -v apt-get >/dev/null 2>&1; then
    echo "Error: this script requires apt-get (Debian/Ubuntu-based Linux)." >&2
    exit 1
fi

if [[ "$EUID" -eq 0 ]]; then
    SUDO=()
    TARGET_USER="${SUDO_USER:-root}"
else
    if ! command -v sudo >/dev/null 2>&1; then
        echo "Error: sudo is required to install system packages." >&2
        exit 1
    fi
    SUDO=(sudo)
    TARGET_USER="$USER"
fi

if ! command -v apt-cache >/dev/null 2>&1; then
    echo "Error: apt-cache is required to select an available Java package." >&2
    exit 1
fi

"${SUDO[@]}" apt-get update

if apt-cache show openjdk-17-jdk >/dev/null 2>&1; then
    JAVA_PACKAGE="openjdk-17-jdk"
elif apt-cache show openjdk-21-jdk >/dev/null 2>&1; then
    JAVA_PACKAGE="openjdk-21-jdk"
elif apt-cache show default-jdk >/dev/null 2>&1; then
    JAVA_PACKAGE="default-jdk"
else
    echo "Error: no Java 17+ JDK package is available from the configured apt repositories." >&2
    exit 1
fi

echo "========== MeshDrop Android Dependencies =========="
echo "SDK location: $SDK_ROOT"
echo "Java package: $JAVA_PACKAGE"
echo

"${SUDO[@]}" apt-get install -y \
    "$JAVA_PACKAGE" \
    git curl wget ca-certificates \
    unzip zip xz-utils file \
    adb fastboot usbutils \
    libc6-i386 lib32stdc++6 lib32z1

JAVA_COMPILER="$(command -v javac || true)"
if [[ -z "$JAVA_COMPILER" ]]; then
    echo "Error: Java compiler was not installed." >&2
    exit 1
fi

JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_COMPILER")")")"
export PATH="$JAVA_HOME/bin:$PATH"

JAVA_VERSION="$(javac -version 2>&1 | awk '{print $2}')"
JAVA_MAJOR="${JAVA_VERSION%%.*}"
if [[ ! "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || (( JAVA_MAJOR < 17 )); then
    echo "Error: Android SDK requires Java 17 or newer; found ${JAVA_VERSION:-unknown}." >&2
    exit 1
fi

echo
echo "Java:"
java -version

mkdir -p "$SDK_ROOT/cmdline-tools"

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [[ ! -x "$SDKMANAGER" ]]; then
    echo
    echo "Downloading Android SDK command-line tools..."
    TMP_DIR="$(mktemp -d)"
    trap 'rm -rf "$TMP_DIR"' EXIT

    TOOLS_ZIP="$TMP_DIR/commandlinetools.zip"
    TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

    curl -fL "$TOOLS_URL" -o "$TOOLS_ZIP"
    mkdir -p "$TMP_DIR/extracted"
    unzip -q "$TOOLS_ZIP" -d "$TMP_DIR/extracted"

    # Google ZIP contains a "cmdline-tools" directory.
    rm -rf "$SDK_ROOT/cmdline-tools/latest"
    mkdir -p "$SDK_ROOT/cmdline-tools/latest"
    cp -a "$TMP_DIR/extracted/cmdline-tools/." "$SDK_ROOT/cmdline-tools/latest/"
fi

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [[ ! -x "$SDKMANAGER" ]]; then
    echo "Error: sdkmanager was not found after extraction." >&2
    exit 1
fi

echo
echo "Accepting Android SDK licenses..."
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses
set -o pipefail

echo
echo "Installing Android SDK packages..."
"$SDKMANAGER" --sdk_root="$SDK_ROOT" \
    "platform-tools" \
    "platforms;${SDK_PLATFORM}" \
    "build-tools;${BUILD_TOOLS_VERSION}"

# Add SDK environment variables to bash and zsh config files idempotently.
ENV_BLOCK=$(cat <<EOF
# MeshDrop Android SDK
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export JAVA_HOME="$JAVA_HOME"
export PATH="\$PATH:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin"
EOF
)

for rc_file in "$HOME/.bashrc" "$HOME/.zshrc"; do
    if [[ -f "$rc_file" ]] && ! grep -q "MeshDrop Android SDK" "$rc_file"; then
        {
            echo
            echo "$ENV_BLOCK"
        } >> "$rc_file"
        echo "Added Android SDK environment variables to $rc_file"
    fi
done

# Add user to plugdev group for USB debugging where the distro uses it.
if getent group plugdev >/dev/null 2>&1 && [[ "$TARGET_USER" != "root" ]]; then
    "${SUDO[@]}" usermod -aG plugdev "$TARGET_USER" || true
fi

echo
echo "========== Installation Complete =========="
echo "Android SDK: $SDK_ROOT"
echo "Java:        $JAVA_HOME"
echo
echo "Next:"
echo "  1. Log out and log back in (USB group permissions may need this)."
echo "  2. Run: source ~/.bashrc   # or source ~/.zshrc"
echo "  3. Verify: adb version"
echo "  4. Verify: sdkmanager --list_installed"
echo "  5. Connect your Android phone, enable Developer options + USB debugging."
echo
echo "Android Studio is not installed by this script."
echo "The MeshDrop APK will be built later with Gradle."
