# YouTube Android App

A native Android application built with Kotlin and Jetpack Compose. The app is designed to browse videos from administrator-approved YouTube channels and play them using the official YouTube embedded player.

## Planned milestones

- [ ] Native Android scaffold (Kotlin, Compose, navigation, theme)
- [ ] Official YouTube embedded-player prototype
- [ ] Local watch history, continue watching, playlist and queue
- [ ] Channel allowlist and YouTube Data API integration
- [ ] Comments and authentication, subject to YouTube API policies
- [ ] Shorts-style feed, captions, playback settings and PiP feasibility review
- [ ] Automated tests, policy review and release validation

## Important platform constraints

Playback behavior and controls such as captions, playback speed, quality selection, background playback, and Picture-in-Picture depend on the official YouTube player, the video, Android version, and YouTube policies. This project will not download videos, extract audio, or hide required YouTube branding/ads/player controls. API credentials must not be committed to source control.

## Status

Project setup is in progress. See the repository history for implemented milestones.