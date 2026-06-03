# Publishing openHAB Auto to Google Play

Package name: `com.sandbdesigns.openhabauto`

## Listing assets (ready to upload)

| Play Console field | File |
|---|---|
| App icon (512x512 PNG) | `play-store-icon-512.png` |
| Feature graphic (1024x500 PNG) | `play-store-feature-graphic.png` |
| Short / full description | `play-store-listing.md` |
| Phone screenshots (min 2) | capture — see below |

## Build artifacts

| Purpose | Command | Output |
|---|---|---|
| Upload to Play (App Bundle) | `./gradlew bundleRelease` | `app/build/outputs/bundle/release/app-release.aab` |
| Install locally for testing | `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` |

Both are signed with the upload key in `keystore.properties` (never commit that file
or `upload-keystore.jks`; both are covered by `.gitignore`).

## Capturing screenshots

### Phone UI (the settings screen)

1. Start an emulator (or plug in a phone with USB debugging on).
2. Install the app: `adb install -r app\build\outputs\apk\release\app-release.apk`
3. Open the app, then run: `.\scripts\take-screenshot.ps1 -Name settings`
   - Saves to `play-store-assets\screenshots\settings-01.png`.

### Android Auto car UI (the device grid) — via Desktop Head Unit

The app's main interface only appears when projected through Android Auto, which the
DHU renders on the desktop. One-time device setup (needs a phone with the Android Auto
app from the Play Store; stock emulator images do not include it):

1. On the phone: install **Android Auto**, open it, and enable **Developer mode**
   (tap the version number repeatedly in Android Auto settings).
2. In Android Auto's developer settings, enable **"Start head unit server"**.
3. Connect the phone via USB (debugging enabled).
4. Run: `.\scripts\launch-head-unit.ps1`
   - This forwards `tcp:5277` and launches the DHU window.
5. In the DHU, open the openHAB Auto app and screenshot the **DHU window**
   (Alt+PrintScreen / Snipping Tool) — `adb screencap` will not capture it, because
   the car display is drawn on the desktop, not the phone.

## Play Console checklist

- [ ] Create app (free/paid, default language)
- [ ] Upload `app-release.aab` to a track (start with Internal testing)
- [ ] Store listing: name, short + full description, icon, feature graphic, screenshots
- [ ] Content rating questionnaire
- [ ] Data safety form (this app sends data only to the user's own openHAB server)
- [ ] Target audience
- [ ] **Android Auto / car-app driver-distraction review** (required for car apps)
- [ ] Keep Play App Signing enabled; back up `upload-keystore.jks` + its password

## Known follow-ups

- `androidx.car.app:app:1.7.0-rc01` is a release candidate — consider moving to a
  stable release before a production launch (Play may flag the non-stable dependency).
- `versionCode` must increase on every upload (currently `1`).
