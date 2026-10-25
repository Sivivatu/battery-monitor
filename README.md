<img src="assets/icon.svg" width="64" height="64" alt="">

# Battery Monitor

Research-stage Android project for understanding battery drain over time.

## Intended capabilities

- Record a persistent history of battery level, charging state, and discharge trends.
- Identify applications associated with battery use.
- Distinguish foreground activity from background activity where Android permits it.
- Compare usage across hours, days, and application versions.
- Prefer a free experience with no advertising and local storage by default.

## Current status

This repository starts with product and API research, before application implementation.
Android's permission model and attribution limits must be established before promising
per-app battery measurements. App activity duration and device-wide drain are different
measurements and must be presented accordingly.

## Research

Start with the [research findings and recommendation](docs/research/README.md).
The reports cover existing applications, public Android APIs, privileged attribution,
and a source-level check of BatStats, tailored to a Pixel 9 Pro on Android 17.

The key feasibility question is access: device-wide drain and app activity history
are available through public APIs, but those observations alone cannot measure
other apps' foreground/background energy consumption. Deeper estimates need an
appropriate elevated-access route and verification on the intended phone.

Created in [T3 Code](https://t3.codes).
