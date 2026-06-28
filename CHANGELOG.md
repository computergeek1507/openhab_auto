# Changelog

## v1.9

### Fixes

- **Passwords with punctuation (e.g. `!`) rejected with `HTTP 401`** — The password and API-token fields used a normal text keyboard, so autocorrect/suggestions could silently alter the entered value (typing a `!` after a word commonly triggers an autocorrect substitution). The corrupted credential was then sent, causing a 401 even though the password was correct in a browser. These fields now use a password keyboard, which disables autocorrect, suggestions, and auto-capitalization so the value is entered literally.

### Internal

- Added a regression test confirming a password containing `!` round-trips through Basic auth encoding unchanged.

## v1.8

### New features

- **Dark mode** — The app now follows the system light/dark theme. The brand orange is lightened slightly in dark mode so it stays legible against the dark background.
- **Local username & password login** — Local mode adds an auth-method toggle: keep using an **API token** (sent as a `Bearer` token) or switch to **Username & password** (HTTP Basic auth). Basic auth requires "Allow Basic Authentication" to be enabled on the server (openHAB → Settings → API Security).

### Improvements

- **Show/hide password** — Password and API-token fields now have an eye button to temporarily reveal the entered value.
- **Scrollable settings** — The settings screen scrolls as a single unit, so the full item list is usable on small screens instead of being squeezed into the leftover space.
- **Clearer myopenHAB auth error** — A rejected myopenHAB login now reports `email or password rejected by myopenHAB` instead of a bare `HTTP 401`.
- **Credentials are trimmed** — Surrounding spaces and newlines are stripped from the email/username/password/token before use, avoiding hard-to-spot failures from a stray trailing space.

### Fixes

- **Non-ASCII passwords** — HTTP Basic auth is now retried with ISO-8859-1 encoding when a UTF-8 attempt returns `HTTP 401`. Some servers decode Basic credentials as ISO-8859-1 (the legacy default), which previously rejected otherwise-correct passwords containing non-ASCII characters. Pure-ASCII passwords are unaffected (no extra request).

### Internal

- Added JUnit and a unit test covering the UTF-8 vs ISO-8859-1 credential encoding.

## v1.7

### Fixes

- **Self-signed certificate support** — Connecting to a local openHAB server with a self-signed (or otherwise untrusted) TLS certificate previously failed with `Trust anchor for certification path not found`. A new **Allow self-signed certificates** checkbox in the Local settings lets the app trust the server's certificate. It is off by default and applies only to the local connection — the myopenHAB cloud connection always uses normal certificate validation. Use it only on a network you trust.
- **API token authentication** — Local API tokens are now sent as a `Bearer` token instead of HTTP Basic auth. openHAB disables Basic authentication by default, which caused valid tokens to be rejected with `HTTP 401`; Bearer tokens are accepted without changing any server setting. The myopenHAB cloud login (email + password) continues to use Basic auth.

### Other

- Renamed the app to **OH Auto** (formerly "openHAB Auto") to avoid use of the openHAB trademark. It remains an independent companion app for openHAB and is not an official openHAB product.

## v1.6

### New features

- **Custom item order** — Reorder the item list on the phone with up/down arrows. The order is saved and applied everywhere, including the Android Auto grid. Newly added items appear at the end.

## v1.5

### New features

- **Dimmer controls** — Dimmer items now show a 0–100% slider on the phone. In the car (where sliders aren't allowed), tapping a dimmer opens a list of preset levels: Off, 25%, 50%, 75%, 100%.
- **Selectable command items** — Items that advertise command options via openHAB's `commandDescription` (e.g. an HVAC mode with Off/Heat/Cool/Auto) are now interactive. The phone shows a dropdown menu; the car opens a dedicated selection list (the current value is marked "Current"). Picking an option sends that command immediately.
- **Sensor / Number items** — `Number` items (including dimensioned types like `Number:Temperature`) are now recognized as read-only and displayed as values with no toggle action, alongside the existing `String` display items.

### Improvements

- Added a generic `sendCommand` path to the openHAB service so arbitrary commands (dimmer levels, selected options) can be sent, separate from the existing on/off toggle.
- Demo source expanded with an outdoor-temperature reading and an HVAC-mode selector so the new behaviors are visible without a live server.

### Internal

- New `ItemCommandScreen` for the Android Auto command-list flow.
- New `CommandOption` model and `commandDescription` parsing on `OpenHabItem`, with `isDimmer` / `isNumber` / `isReadOnly` / `isSelectable` helpers.

## v1.4

- Add Demo Mode.

## v1.3

- Add myopenhab.org support.

## v1.2

- Support Strings, Covers, Dimmers.

## v1.1

- Support Switches in Null state.

## v1.0

- First version for testing.
