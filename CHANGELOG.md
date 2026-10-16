# Changelog

All notable changes to NNA Stamina are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project uses [Semantic Versioning](https://semver.org/).

## [1.0.1] - 2026-10-17

### Changed
- **New signing key. Uninstall the previous version once before installing this one.** The old key was public in this repository, so anyone could have published an APK that installs over yours. Releases are now signed with a private key; later updates install over this version as usual. Your games and stamina values need to be entered again after reinstalling.

## [1.0.0] - 2026-10-13

Initial release.

### Games
- Built-in support for Genshin Impact, Honkai: Star Rail, Zenless Zone Zero, Wuthering Waves, Arknights, Arknights: Endfield, Uma Musume: Pretty Derby, and Blue Archive.
- Custom games with their own name, cap, and recovery time.

### Tracking
- Current stamina, progress bar, time until full, and time until the daily reset for each game.
- Games sorted by urgency, with full bars and wasted points shown first.
- Quick update with spend shortcuts that keep progress toward the next point.
- Server selection with the daily reset shown in local time.

### Reminders
- Notify when stamina is full, at a custom amount, or one hour before the daily reset.
- Reminders survive reboots, app updates, and time zone changes.

### Interface
- Home screen widget showing up to five games.
- Light and dark themes that follow the system setting.
