GRADLE := ./gradlew
ANDROID_HOME ?= $(HOME)/Android/Sdk
export ANDROID_HOME
ADB ?= $(ANDROID_HOME)/platform-tools/adb
EMULATOR := $(ANDROID_HOME)/emulator/emulator
PACKAGE := com.meshdrop
ACTIVITY := .ui.activity.MainActivity
APK := build/MeshDrop.apk
GRADLE_APK := build/app/outputs/apk/debug/app-debug.apk
AVD ?= MeshDrop_Test

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
	@if ! $(ADB) devices | grep -q '^emulator-[0-9][0-9]*[[:space:]]'; then \
		nohup env QT_QPA_PLATFORM=xcb "$(EMULATOR)" -avd "$(AVD)" -memory 2048 -cores 2 -gpu auto >build/emulator.log 2>&1 </dev/null & \
	fi
	$(ADB) -e wait-for-device
	@attempt=0; \
	until [ "$$($(ADB) -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do \
		attempt=$$((attempt + 1)); \
		if [ "$$attempt" -ge 120 ]; then echo "Timed out waiting for emulator boot"; exit 1; fi; \
		sleep 1; \
	done
	$(ADB) -e install -r $(APK)
	$(ADB) -e shell am start -n $(PACKAGE)/$(ACTIVITY)

clean:
	rm -rf build app/build

emulator:
	QT_QPA_PLATFORM=xcb "$(EMULATOR)" -avd $(AVD) -memory 2048 -cores 2 -gpu auto

logs:
	$(ADB) logcat -v time | grep -E 'MeshDrop|AndroidRuntime|ActivityTaskManager'