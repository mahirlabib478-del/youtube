# YouTube Android App

A native Android application built with Kotlin and Jetpack Compose. The initial app includes a home feed preview, bottom navigation, and a playback prototype using the official YouTube IFrame embed in an Android WebView.

## Requirements

- Android Studio with a JDK 17 toolchain
- Android SDK Platform 35
- Gradle sync with internet access

## Build

Open this repository in Android Studio and allow Gradle to sync, then run the `:app` configuration on an Android device or emulator.

If Gradle is installed locally, run:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions is configured to run the unit tests and assemble a debug APK on pushes and pull requests to `main`.

## Roadmap

- [x] Native Android/Compose app scaffold and bottom navigation
- [x] Demo home feed screen
- [x] Official YouTube IFrame embed player prototype
- [ ] Local watch history, continue watching, playlists, queue and mini-player
- [ ] Admin-approved channel list and YouTube Data API integration
- [ ] Real comments and authentication, subject to YouTube API policies
- [ ] Shorts-style feed, captions, playback settings and PiP feasibility review
- [ ] Automated integration checks, policy review and release validation

## Platform and security constraints

Playback behavior and controls such as captions, playback speed, quality selection, background playback, and Picture-in-Picture depend on the official YouTube player, video capabilities, Android version, and YouTube policies. This app will not download videos, extract audio, or hide required YouTube branding, ads, or player controls. API credentials must not be committed to source control; production API access should use appropriately restricted credentials and a secure architecture.

## Current limitations

The home feed currently uses demo/placeholder data; only the IFrame API sample video has a play action. The YouTube Data API, admin channel configuration, real comments, authentication, and local watch history are not implemented yet. The app build has not yet been confirmed successful by CI.
