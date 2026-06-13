# Changelog

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
