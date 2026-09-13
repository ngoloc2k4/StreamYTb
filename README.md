# StreamYTb 🎵📺

[![Android CI](https://github.com/ngoloc2k4/StreamYTb/actions/workflows/ci.yml/badge.svg)](https://github.com/ngoloc2k4/StreamYTb/actions/workflows/ci.yml)
[![Crowdin](https://badges.crowdin.net/badge/dark/crowdin-on-light.png)](https://crowdin.com)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![MinSdk](https://img.shields.io/badge/minSdk-23%2B-brightgreen.svg)](https://android-arsenal.com/api?level=23)

A modern, privacy-respecting, lightweight YouTube streaming client and background audio player for Android built with Jetpack Compose, Media3, and Room.

---

## ✨ Features

- 🚫 **100% Ad-Free**: Direct InnerTube API streaming without advertisements or tracking scripts.
- 🎧 **Background & Screen-Off Playback**: Powered by AndroidX Media3 `MediaSessionService`.
- ⚡ **Ultra-Low Memory (< 90MB RAM)**: Supports dedicated Audio-Only stream mode (pure Opus / M4A) disabling video decoders when locked or minimized.
- 📦 **Compact APK (< 15MB)**: Heavy R8 / ProGuard shrinkage and resource stripping.
- 🔒 **Privacy-First Local RecSys**: Recommendation algorithm running 100% offline via local SQLite Room database, without requiring a Google Account.
- 📱 **Adaptive UI**: Responsive Jetpack Compose layout adapting seamlessly from phones to foldable devices and tablets (Split Navigation Rail).
- 🌐 **Internationalization**: Localized via [Crowdin](https://crowdin.com) for easy community translations.

---

## 🌍 Community Translations

StreamYTb is translated by our community on [Crowdin](https://crowdin.com). We currently support:
- 🇺🇸 **English** (Source / Base)
- 🇻🇳 **Vietnamese** (Full translation)
- *Additional languages welcome via Crowdin!*

Interested in translating StreamYTb into your language? See [TRANSLATING.md](TRANSLATING.md) for guidelines!

---

## 🏗️ Architecture & Tech Stack

- **UI**: Jetpack Compose, Material 3, Adaptive Layouts
- **Media Engine**: AndroidX Media3 ExoPlayer, MediaSessionService
- **Networking**: OkHttp, Conscrypt (TLS 1.2/1.3 for Android 6.0+), YouTube InnerTube API
- **Local Persistence**: Room SQLite (Watch History, Subscriptions, Affinity Scores)
- **CI/CD**: GitHub Actions (Unified Gradle Cache, R8 ABI Splits, Standalone APK outputs)
