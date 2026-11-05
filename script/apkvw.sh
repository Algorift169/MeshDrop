
#!/usr/bin/env bash
set -Eeuo pipefail

# ==========================================
# APKVW - Android Emulator Installer
# Target: LordOS / Debian-based Linux x86_64
# ==========================================

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
TOOLS_BUILD="15859902"
TOOLS_ZIP="commandlinetools-linux-${TOOLS_BUILD}_latest.zip"
TOOLS_SHA="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
TOOLS_URL="https://dl.google.com/android/repository/${TOOLS_ZIP}"

API=36
IMAGE="system-images;android-${API};google_apis;x86_64"
AVD="MeshDrop_Test"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
RESET='\033[0m'

log()  { echo -e "${GREEN}[+]${RESET} $*"; }
info() { echo -e "${CYAN}[*]${RESET} $*"; }
warn() { echo -e "${YELLOW}[!]${RESET} $*"; }
die()  { echo -e "${RED}[X]${RESET} $*" >&2; exit 1; }

[[ "$(uname -m)" == "x86_64" ]] ||
    die "This installer requires an x86_64 Linux machine."

command -v sudo >/dev/null || die "sudo is required."
command -v apt-cache >/dev/null || die "apt-cache is required to select an available Java package."

echo
echo "======================================"
echo "       APKVW ANDROID INSTALLER"
echo "======================================"
echo "SDK location: $SDK"
echo "Android API:  $API"
echo "Virtual device: $AVD"
echo

read -rp "Continue with installation? [y/N] " answer
[[ "$answer" =~ ^[Yy]$ ]] || exit 0

# 1. System dependencies
log "Installing system dependencies..."

sudo apt-get update

if apt-cache show openjdk-17-jdk >/dev/null 2>&1; then
    JAVA_PACKAGE="openjdk-17-jdk"
elif apt-cache show openjdk-21-jdk >/dev/null 2>&1; then
    JAVA_PACKAGE="openjdk-21-jdk"
elif apt-cache show default-jdk >/dev/null 2>&1; then
    JAVA_PACKAGE="default-jdk"
else
    die "No Java 17+ JDK package is available from the configured apt repositories."
fi

log "Using $JAVA_PACKAGE..."

sudo apt-get install -y \
    curl unzip zip wget ca-certificates \
    "$JAVA_PACKAGE" adb \
    libgl1 libpulse0 libnss3 \
    libx11-xcb1 libxcb1 libxkbcommon0 \
    libxcb-cursor0 libxcb-xinerama0 \
    libxcb-xfixes0 libxcb-keysyms1 \
    libxcb-randr0 libxcb-shape0 \
    libxcb-xkb1 libxrender1 libxi6 \
    libxtst6 libatk1.0-0 libatk-bridge2.0-0 \
    libdrm2 libgbm1

# 2. KVM check
echo
info "Checking hardware virtualization..."

if grep -qE '(vmx|svm)' /proc/cpuinfo &&
   [[ -e /dev/kvm ]]; then
    log "KVM detected."

    if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
        warn "Adding current user to the kvm group..."
        sudo usermod -aG kvm "$USER"
        warn "A logout/login will be required for group changes."
    fi
else
    warn "KVM unavailable. Emulator may run slowly."
fi

# 3. SDK setup
log "Preparing Android SDK..."

mkdir -p "$SDK"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if [[ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
    log "Downloading official Android command-line tools..."

    curl -fL --retry 3 "$TOOLS_URL" -o "$TMP/$TOOLS_ZIP"

    echo "$TOOLS_SHA  $TMP/$TOOLS_ZIP" | sha256sum -c - ||
        die "Android tools checksum verification failed."

    unzip -q "$TMP/$TOOLS_ZIP" -d "$TMP"

    mkdir -p "$SDK/cmdline-tools/latest"

    cp -a "$TMP/cmdline-tools/." \
        "$SDK/cmdline-tools/latest/"

    log "Command-line tools installed."
else
    log "Existing command-line tools detected."
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$SDK/emulator:$PATH"

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"

# 4. Android SDK packages
log "Installing Android SDK packages..."

"$SDKMANAGER" --sdk_root="$SDK" --install \
    "platform-tools" \
    "platforms;android-${API}" \
    "build-tools;36.0.0" \
    "emulator" \
    "$IMAGE"

# 5. Licenses
log "Accepting Android SDK licenses..."
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses || true

# 6. Create virtual device
if [[ ! -d "$HOME/.android/avd/${AVD}.avd" ]]; then
    log "Creating Android virtual device..."

    printf 'no\n' | "$AVDMANAGER" create avd \
        -n "$AVD" \
        -k "$IMAGE" \
        -d pixel_2
else
    log "Virtual device already exists."
fi

# 7. Configure emulator memory
AVD_CONFIG="$HOME/.android/avd/${AVD}.avd/config.ini"

if [[ -f "$AVD_CONFIG" ]]; then
    sed -i '/^hw.ramSize=/d; /^vm.heapSize=/d' "$AVD_CONFIG"

    cat >> "$AVD_CONFIG" <<EOF

hw.ramSize=2048
vm.heapSize=256
hw.gpu.enabled=yes
hw.gpu.mode=auto
EOF
fi

# 8. Persistent environment variables
BASHRC="$HOME/.bashrc"

touch "$BASHRC"

if ! grep -qF '# APKVW Android SDK' "$BASHRC"; then
    cat >> "$BASHRC" <<EOF

# APKVW Android SDK
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator:\$PATH"
EOF
fi

# 9. Completion
echo
echo "======================================"
log "ANDROID EMULATOR INSTALLATION COMPLETE"
echo "======================================"

echo
echo "SDK: $SDK"
echo "AVD: $AVD"
echo
echo "To launch your virtual phone:"
echo
echo "  source ~/.bashrc"
echo "  emulator -avd $AVD -memory 2048 -cores 2 -gpu auto"
echo
echo "To install an APK:"
echo
echo "  adb install -r /path/to/app.apk"
echo
echo "To check connected devices:"
echo
echo "  adb devices"
echo
warn "A system logout/login may be needed if KVM permissions changed."
