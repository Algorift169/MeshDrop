GRADLE := ./gradlew
ANDROID_HOME ?= $(HOME)/Android/Sdk
export ANDROID_HOME
ADB ?= $(ANDROID_HOME)/platform-tools/adb
EMULATOR := $(ANDROID_HOME)/emulator/emulator
AVDMANAGER ?= $(ANDROID_HOME)/cmdline-tools/latest/bin/avdmanager
PACKAGE := com.meshdrop
ACTIVITY := .ui.activity.MainActivity
APK := build/MeshDrop.apk
GRADLE_APK := build/app/outputs/apk/debug/app-debug.apk
AVD ?= MeshDrop_Test

COMPATIBLE_JDK := $(firstword $(wildcard \
	/usr/lib/jvm/java-21-openjdk-amd64 \
	/usr/lib/jvm/java-17-openjdk-amd64 \
	/usr/lib/jvm/java-21-* \
	/usr/lib/jvm/java-17-*))
ifdef JAVA_HOME
JAVA_MAJOR := $(shell "$(JAVA_HOME)/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)
ifeq ($(shell [ -n "$(JAVA_MAJOR)" ] && [ "$(JAVA_MAJOR)" -ge 17 ] && [ "$(JAVA_MAJOR)" -le 23 ] && echo ok),ok)
EFFECTIVE_JAVA_HOME := $(JAVA_HOME)
endif
endif
ifndef EFFECTIVE_JAVA_HOME
ifneq ($(strip $(COMPATIBLE_JDK)),)
EFFECTIVE_JAVA_HOME := $(COMPATIBLE_JDK)
endif
endif
ifdef EFFECTIVE_JAVA_HOME
export JAVA_HOME := $(EFFECTIVE_JAVA_HOME)
export PATH := $(EFFECTIVE_JAVA_HOME)/bin:$(PATH)
endif

define ENSURE_AVD
if ! "$(EMULATOR)" -list-avds | grep -Fxq "$(AVD)"; then \
	img=$$(find "$(ANDROID_HOME)/system-images" -mindepth 4 -maxdepth 4 -name system.img 2>/dev/null | head -n 1); \
	if [ -n "$$img" ] && [ -x "$(AVDMANAGER)" ]; then \
		pkg=$$(dirname "$$img"); \
		rel=$${pkg#"$(ANDROID_HOME)/"}; \
		sysimg=$$(printf '%s' "$$rel" | tr '/' ';'); \
		echo "Creating AVD '$(AVD)' with $$sysimg..."; \
		echo no | "$(AVDMANAGER)" create avd -n "$(AVD)" -k "$$sysimg" --force || exit 1; \
	else \
		echo "AVD '$(AVD)' not found and no complete system image is installed under $(ANDROID_HOME)/system-images." >&2; \
		echo "Install one with: $(ANDROID_HOME)/cmdline-tools/latest/bin/sdkmanager --sdk_root=$(ANDROID_HOME) \"system-images;android-36;google_apis;x86_64\"" >&2; \
		exit 1; \
	fi; \
fi
endef

.PHONY: app build test install run erun clean emulator logs

app:
	$(GRADLE) --no-daemon assembleDebug
	mkdir -p build
	cp $(GRADLE_APK) $(APK)

build: app

test:
	bash app/src/test/java/com/meshdrop/smoke_test.sh

install: app
	$(ADB) install -r $(APK)

run: install
	$(ADB) shell am start -n $(PACKAGE)/$(ACTIVITY)

erun: app
	@emu_pid=""; \
	if ! "$(ADB)" devices | grep -q '^emulator-[0-9][0-9]*[[:space:]]'; then \
		$(ENSURE_AVD); \
		mkdir -p build; \
		nohup env QT_QPA_PLATFORM=xcb "$(EMULATOR)" -avd "$(AVD)" -memory 2048 -cores 2 -gpu auto >build/emulator.log 2>&1 </dev/null & \
		emu_pid=$$!; \
	fi; \
	attempt=0; \
	until [ "$$("$(ADB)" -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do \
		if [ -n "$$emu_pid" ] && ! kill -0 "$$emu_pid" 2>/dev/null; then \
			echo "Emulator exited unexpectedly. See build/emulator.log:" >&2; \
			tail -n 20 build/emulator.log >&2 || true; \
			exit 1; \
		fi; \
		attempt=$$((attempt + 1)); \
		if [ "$$attempt" -ge 120 ]; then echo "Timed out waiting for emulator boot" >&2; exit 1; fi; \
		sleep 1; \
	done
	"$(ADB)" -e install -r $(APK)
	"$(ADB)" -e shell am start -n $(PACKAGE)/$(ACTIVITY)

clean:
	rm -rf build app/build

emulator:
	@$(ENSURE_AVD)
	QT_QPA_PLATFORM=xcb "$(EMULATOR)" -avd "$(AVD)" -memory 2048 -cores 2 -gpu auto

logs:
	"$(ADB)" logcat -v time | grep -E 'MeshDrop|AndroidRuntime|ActivityTaskManager'