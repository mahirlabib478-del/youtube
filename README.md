# YouTube Android App

A native Android application built with Kotlin and Jetpack Compose. It includes a home feed, official YouTube IFrame playback, local watch history, a hidden device-local admin gate for channel management, and a channel-feed prototype using the YouTube Data API v3.

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

GitHub Actions runs unit tests and assembles a debug APK on pushes and pull requests to `main`. The APK is uploaded as the `youtube-debug-apk` artifact.

## YouTube Data API setup

1. Enable **YouTube Data API v3** in a Google Cloud project and create an API key.
2. Add a repository Actions secret named `YOUTUBE_API_KEY` in **Settings → Secrets and variables → Actions**. For a local build, set the `YOUTUBE_API_KEY` Gradle property or environment variable.
3. Restrict the key to the YouTube Data API and appropriate application usage. A key embedded in an Android APK can be extracted; build-time injection does not make a mobile API key confidential.
4. Install a newly built APK. Tap the app logo seven times in quick succession to open the local admin gate, configure a PIN, then add a YouTube channel ID (typically starts with `UC` and is 24 characters) and select **Load latest videos**.

Approved-channel video metadata is cached locally and shown on Home and Search. The channel list and PIN are stored on that device for prototyping; they are **not** a server-enforced admin allowlist. Clearing app data or uninstalling may erase local settings. A production admin approval system needs an authenticated backend and server-enforced channel policy.

## Roadmap

- [x] Native Android/Compose app scaffold and bottom navigation
- [x] Official YouTube IFrame embed player prototype
- [x] Local watch history and Library screen
- [x] Device-local admin PIN gate and channel ID manager
- [x] Fetch and cache approved-channel video metadata through YouTube Data API v3
- [x] Show cached approved-channel videos on Home and filter them in Search
- [ ] Server-side admin-approved channel list and secure backend
- [ ] Real comments and authentication, subject to YouTube API policies
- [ ] Shorts-style feed, captions, playback settings and PiP feasibility review
- [ ] Automated integration checks, policy review and release validation

## Platform and security constraints

Playback behavior and controls such as captions, playback speed, quality selection, background playback, and Picture-in-Picture depend on the official YouTube player, video capabilities, Android version, and YouTube policies. This app will not download videos, extract audio, or hide required YouTube branding, ads, or player controls.

API requests require an enabled Google Cloud project, a valid API key, and available quota.
