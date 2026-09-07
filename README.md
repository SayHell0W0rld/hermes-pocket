# Hermes Pocket

[![CI](https://github.com/SayHell0W0rld/hermes-pocket/actions/workflows/ci.yml/badge.svg)](https://github.com/SayHell0W0rld/hermes-pocket/actions/workflows/ci.yml)
[![Release](https://github.com/SayHell0W0rld/hermes-pocket/actions/workflows/release.yml/badge.svg)](https://github.com/SayHell0W0rld/hermes-pocket/actions/workflows/release.yml)

<p align="center">
  <img src="docs/assets/brand/hermes-pocket-icon.svg" width="128" alt="Hermes Pocket icon" />
</p>

Native Android shell that runs the [hermes-webui](https://github.com/nesquena/hermes-webui) chat interface on your phone — a polished container around your own self-hosted Hermes agent.

> ⚠️ **Community project.** Not affiliated with, endorsed by, or sponsored by Nous Research. "Hermes" is used to describe compatibility with the Hermes agent ecosystem.

## Why this exists

If you run [Hermes Agent](https://hermes-agent.nousresearch.com) on a computer, hermes-webui is the best way to reach it from a browser. This app wraps that web UI in a minimal native shell so it behaves like a real app on your phone:

- **Full-quality agent access** — the backend runs the real Hermes agent core (not a stripped API). Tools, memory, skills, sessions — everything works exactly as it does on your desktop CLI.
- **Session continuity** — browse and resume the CLI conversations you started on your computer.
- **No browser chrome** — full-screen experience, installable from GitHub Releases.
- **Lightweight by design** — the native shell only handles what a web page cannot (configuring the server address, receiving intents). All UI lives in hermes-webui.

## How it fits together

<p align="center">
  <img src="docs/assets/brand/architecture.svg" width="720" alt="Hermes Pocket architecture" />
</p>

<p align="center">
  <img src="docs/assets/brand/mobile-shell.svg" width="720" alt="Hermes Pocket native shell" />
</p>

## Architecture

The shell deliberately builds **no UI system of its own**. Native screens (the settings page) inherit hermes-webui's design tokens, and any web-side UI improvement belongs upstream at [nesquena/hermes-webui](https://github.com/nesquena/hermes-webui).

## Requirements

- A running **hermes-webui** instance (see its [README](https://github.com/nesquena/hermes-webui) for deployment), reachable from your phone over your network (LAN, Tailscale, VPN, etc.).
- Android 8.0+ (minSdk 26).

## Getting started

1. **Install the app** — grab the latest APK from [Releases](https://github.com/SayHell0W0rld/hermes-pocket/releases) and install it on your phone.
2. **Run onboarding** — the first launch opens a short setup flow: welcome, server connection, language/theme sync, text size, notifications, and a completion screen.
3. **Configure your server** — enter the URL of your hermes-webui instance. HTTPS is the recommended way (voice input and PWA features require a secure context), e.g. `https://my-agent.example.com/webui` or `https://your-hostname.ts.net/webui` (Tailscale). Plain HTTP is only suggested for `localhost` or trusted LAN testing. Tap **Test connection**, then **Save**.
#### Why HTTPS?

HTTPS is strongly recommended for the server URL:

- 🎤 Voice input is available only on HTTPS; browsers and WebViews reject microphone permission over plain HTTP (except `localhost`).
- 🔔 Some notification and offline/PWA capabilities are also restricted to secure contexts.
- 🔒 HTTPS encrypts the connection end-to-end, so chat content and passwords are not visible to other devices on the same network.
- ℹ️ Tailscale Serve provides a trusted Let's Encrypt certificate with zero extra configuration.

Plain HTTP remains a valid option for temporary testing on a trusted private network, especially when voice input and encrypted transport are not required.

4. **Chat** — the app loads your hermes-webui and you can talk to your Hermes agent from anywhere on your network.

### Native shell features

- **Multi-session tabs** — the native bottom tab bar keeps up to five Hermes sessions, without overlaying WebUI content. Hot WebViews can be set from 1 to 5 so frequently used tabs can switch without reloading.
- **Text size** — choose 100%, 110%, 130%, or 150% from native settings. Gesture zoom stays off to avoid accidental touches.
- **Tailscale startup guard** — Tailscale-addressed servers wait for the VPN transport and server readiness, request Tailscale connection, and fall back to Tailscale/VPN settings if needed.

> To open native settings later: long-press the app icon and choose **Settings**, tap the gear on the splash screen, or use **Modify server address** on the error screen.

## Building from source

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Prerequisites

- JDK 17
- Android SDK (compileSdk 34+)
- No network access to Google Maven? The Gradle wrapper will fetch dependencies on first build.

## Releases

CI runs unit tests and a debug APK build on every push to `main` and on every pull request. Push a tag such as `v0.1.0` to trigger the automatic release workflow. It validates the semantic version, updates Android version metadata, runs unit tests, builds an installable APK, and attaches it to a GitHub release.

Signing secrets are optional. If they are configured, releases use a signed release APK. Otherwise the workflow publishes a debug-signed APK and records a warning. See [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md).

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Design & conventions

Native screens use the same light/dark color tokens as Hermes WebUI, and the
shell ships Chinese and English UI text.

## Security notes

- The app stores only your server URL in local SharedPreferences.
- The server address is user-supplied — connect it only to hermes-webui instances you trust (it can run commands on the host).
- No analytics, no telemetry, no network access except to the server you configure.

## Contributing

Read [AGENTS.md](AGENTS.md) first (it is the development guide for human and AI contributors alike). Keep the shell thin; push web-side UI improvements upstream to hermes-webui.

## License

MIT — see [LICENSE](LICENSE). Third-party notices in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

[中文版文档 → README.zh-CN.md](README.zh-CN.md)
