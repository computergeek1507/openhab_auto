# openHAB Auto

An Android Auto app for controlling an [openHAB](https://www.openhab.org/) smart home from your car's display. Devices in a chosen openHAB group appear as a tappable grid; tap to toggle them on/off (or open/close), with the screen auto-refreshing.

## Features

- **Android Auto grid** of your openHAB items with at-a-glance ON/OFF state
- **Tap to toggle** switches and dimmers; **UP/DOWN** for rollershutters (shown as OPEN/CLOSED)
- **Read-only String items** display their value
- **Three connection modes**:
  - **Local** — your openHAB server URL + API token
  - **myopenHAB** — the cloud service at `home.myopenhab.org` (email + password)
  - **Demo** — offline sample devices, no server needed (handy for trying the app)
- Auto-refreshes every 30 seconds

## Building

Requires JDK 17+ and the Android SDK (compileSdk 35, minSdk 26).

```sh
./gradlew assembleRelease   # installable APK
./gradlew bundleRelease      # Play Store App Bundle (.aab)
```

Release builds are signed via a `keystore.properties` file at the project root
(ignored by git):

```properties
storeFile=/absolute/path/to/upload-keystore.jks
keyAlias=your-alias
storePassword=...
keyPassword=...
```

## Configuration

Launch the phone app, pick a connection mode, enter your server/credentials and
the openHAB **group** whose members you want in the car, then connect to Android
Auto.

## License

This is an independent companion app for openHAB and is not an official openHAB
product.
