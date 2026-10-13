# YouTube Android App

A native Android application built with Kotlin and Jetpack Compose. The initial scaffold includes a home feed preview and bottom navigation for Home, Shorts preview, and Library.

## Requirements

- Android Studio with a JDK 17 toolchain
- Android SDK Platform 35
- Gradle sync with internet access

## Build

Open this repository in Android Studio and allow Gradle to sync, then run the `:app` configuration on an Android device or emulator.

A command-line build can be run from the repository root once the Gradle wrapper is added or Gradle is installed:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Roadmap

- [x] Native Android/Compose app scaffold and bottom navigation
- [x] Demo home feed screen
- [ ] Official YouTube embedded-player prototype
- [ ] Local watch history, continue watching, playlists, queue and mini-player
- [ ] Admin-approved channel list and YouTube Data API integration
- [ ] Real comments and authentication, subject to YouTube API policies
- [ ] Shorts-style feed, captions, playback settings and PiP feasibility review
- [ ] Automated integration checks, policy review and release validation

## Platform and security constraints

Playback behavior and controls such as captions, playback speed, quality selection, background playback, and Picture-in-Picture depend on the official YouTube player, video capabilities, Android version, and YouTube policies. This app will not download videos, extract audio, or hide required YouTube branding, ads, or player controls. API credentials must not be committed to source control; production API access should use appropriately restricted credentials and a secure architecture.

## Current limitations

The feed currently contains placeholder/demo data. The YouTube Data API, embedded player, authentication, and admin channel configuration are not wired up yet. The Android build has not been executed in this environment.
