<p align="center">
  <img src="docs/icon.png" width="96" alt="NNA Stamina icon">
</p>

<h1 align="center">NNA Stamina</h1>

<p align="center">
  Stamina tracker for gacha games on Android. Enter your current stamina once and get notified before it caps.
</p>

<p align="center">
  <a href="../../releases/latest">Download</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

---

## Overview

Most gacha games refill stamina on a timer and stop at a cap, so every minute spent at the cap is stamina lost. NNA Stamina keeps the clock for you. Tell it how much you have, and it shows when each game will be full, sorts your games by urgency, and sends a notification at the moment you choose.

It works fully offline. There is no account, no login, and no game data is read from your device.

## Supported games

| Game | Stamina | Default max | Recovery | Daily reset |
| --- | --- | --- | --- | --- |
| Genshin Impact | Original Resin | 200 | 1 per 8 min | 04:00 server time |
| Honkai: Star Rail | Trailblaze Power | 300 | 1 per 6 min | 04:00 server time |
| Zenless Zone Zero | Battery Charge | 240 | 1 per 6 min | 04:00 server time |
| Wuthering Waves | Waveplate | 240 | 1 per 6 min | 04:00 server time |
| Arknights | Sanity | 180 ¹ | 1 per 6 min | 04:00 UTC−7 |
| Arknights: Endfield | Sanity | 240 ¹ | 1 per 7.2 min | 04:00 server time |
| Uma Musume: Pretty Derby | Training Points | 100 | 1 per 10 min | 00:00 UTC+9 |
| Blue Archive | AP | 240 ¹ | 1 per 6 min | 04:00 UTC+9 |

¹ The cap grows with account level. Set your own in the game's settings.

Any other game can be added with its own name, cap, and recovery time.

## Features

- **At-a-glance list.** Current stamina, a progress bar, time until full, and time until the daily reset for each game. Games that are already full, or will be soonest, appear first.
- **Wasted stamina.** When a bar has been full for a while, the card shows how many points have been lost.
- **Quick update.** Tap a game, then type the new value or use the spend shortcuts. Spending keeps your progress toward the next point.
- **Reminders.** Notify when full, at a custom amount, or before the daily reset.
- **Server-aware resets.** Pick your server (Asia, America, Europe, and others) and see the reset in your local time.
- **Home screen widget.** Up to five games, most urgent first.
- **Custom games.** Add any game with its own cap and recovery time.
- Light and dark themes that follow the system setting.

## Requirements

- Android 8.0 (API 26) or later
- Notification permission for reminders
- *Optional:* "Alarms & reminders" permission on Android 12 and later, for reminders at the exact minute

## Installation

1. Download `NNA-Stamina.apk` from the [latest release](../../releases/latest) and install it.
2. Allow notifications when asked.
3. Tap **Games** to choose what you play. Genshin Impact, Honkai: Star Rail, Zenless Zone Zero, Wuthering Waves, and Arknights are on by default.

## Usage

1. Tap a game and enter your current stamina.
2. Tap **⋮** (or long-press a card) to set your max, server, and reminders.
3. After spending stamina in-game, tap the game and use a spend shortcut such as **−40**.

Game values are community-documented and can change with game updates. If a game changes its cap or recovery time, adjust it in the app, and please open an issue so the defaults can be updated.

## Building from source

The project uses Gradle 8.7 with Android Gradle Plugin 8.5 and Kotlin 1.9. It has no third-party dependencies.

```bash
gradle assembleDebug
```

Every push to `main` builds the APK with GitHub Actions. When `versionName` in `app/build.gradle.kts` changes, a new release is published with notes taken from [`CHANGELOG.md`](CHANGELOG.md).

## Disclaimer

NNA Stamina is an unofficial fan tool. It is not affiliated with or endorsed by HoYoverse, Kuro Games, Hypergryph, Gryphline, Yostar, Nexon, Cygames, or any other game publisher. All game names are trademarks of their respective owners and are used here only to identify the games.

## License

[MIT](LICENSE)

---

<p align="center">Made by <a href="https://github.com/NNazuna">NNazuna</a></p>
