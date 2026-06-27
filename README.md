# OH Auto

An Android Auto app for controlling an [openHAB](https://www.openhab.org/) smart home from your car's display. Devices in a chosen openHAB group appear as a tappable grid; tap to toggle them on/off (or open/close), with the screen auto-refreshing.

## Features

- **Android Auto grid** of your openHAB items with at-a-glance ON/OFF state
- **Tap to toggle** switches and dimmers; **UP/DOWN** for rollershutters (shown as OPEN/CLOSED)
- **Read-only String items** display their value
- **Three connection modes**:
  - **Local** — your openHAB server URL + API token (optionally trusting a self-signed certificate)
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

For **Local** connections you can authenticate two ways, chosen with the toggle under
the server URL:

- **API token** (default) — the token is sent as a `Bearer` token, so it works with
  openHAB's default API security settings (Basic authentication does not need to be
  enabled).
- **Username & password** — sent as HTTP Basic auth. This requires **Allow Basic
  Authentication** to be enabled on the server (openHAB → **Settings → API Security**),
  which is off by default.

If your server uses a self-signed TLS certificate, enable **Allow self-signed
certificates** in the Local settings — otherwise the connection fails with `Trust
anchor for certification path not found`. This bypasses certificate validation for the
local server, so only use it on a network you trust.

### Troubleshooting

- **`Trust anchor for certification path not found`** — your server's TLS certificate
  isn't trusted by Android. Enable **Allow self-signed certificates**, switch the URL
  to plain `http://` on your LAN, or install a properly trusted certificate.
- **`HTTP 401`** (Local) — the credentials were rejected. With **API token** auth,
  re-paste a valid token (openHAB → **Profile → API tokens**) and confirm its user can
  access the REST API. With **Username & password** auth, check the credentials and make
  sure **Allow Basic Authentication** is enabled (openHAB → **Settings → API Security**).
- **`email or password rejected by myopenHAB`** — your myopenHAB account credentials were
  refused. Re-check the password, confirm the email matches the one registered at
  `myopenhab.org`, and if you sign in with Google/social login, set a native account
  password and use that. (Surrounding spaces and newlines are trimmed automatically, and
  passwords with non-ASCII characters are retried in both UTF-8 and ISO-8859-1 encodings.)

## License

This is an independent companion app for openHAB and is not an official openHAB
product.
