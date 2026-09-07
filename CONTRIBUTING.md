# Contributing

Thanks for your interest in contributing to Hermes Mobile!

## Branching model

This project follows a feature-branch workflow:

1. `main` is always the stable branch — every commit on it should build and be release-ready.
2. All changes go through **feature branches**:
   - `feat/<short-name>` — new features (e.g. `feat/session-tabs`)
   - `fix/<short-name>` — bug fixes (e.g. `fix/error-retry`)
   - `docs/<topic>` — documentation-only changes
   - `chore/<topic>` — tooling, CI, dependencies
3. Feature branches are merged into `main` **via Pull Request** (even for the maintainer's own work), so every change has a reviewable diff and CI-verified state.
4. Keep one logical change per PR. Split unrelated refactors.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: notification lifecycle with auto-cancel
fix: error page retry bypassing WebView cache
docs: add HTTPS rationale to README
chore: bump WorkManager to 2.9.0
```

The release workflow collects commit subjects into the changelog, so write them for end users, not for developers.

## Releases

Releases are fully automated:

1. Merge feature PRs into `main`.
2. Tag the release: `git tag v1.1 && git push origin v1.1`
3. GitHub Actions (`.github/workflows/release.yml`) builds the APK, generates release notes from commits since the previous tag, and publishes a GitHub Release with a downloadable APK.

Version scheme: `vMAJOR.MINOR[.PATCH]`. The `versionCode` is derived from the tag deterministically (`major*10000 + minor*100 + patch`), so you never hand-edit it.

## Development rules

- The shell wraps [hermes-webui](https://github.com/nesquena/hermes-webui); never fork or patch its code inside this repo. Shell-side UI inherits its design tokens (see `AGENTS.md`).
- All user-facing strings go in `res/values/strings.xml` (zh) and `res/values-en/strings.xml` (en) — both must stay in sync.
- No hardcoded colors: add paired entries to `values/colors.xml` and `values-night/colors.xml`.
- No secrets, real IPs, or private hostnames in commits — the repo is public.
- After any UI change, build with `./gradlew assembleDebug` before opening the PR.

## Reporting issues

Include: device model, Android version, app version (from the release you installed), steps to reproduce, and expected vs actual behavior. Server-side connection issues should note whether HTTPS or HTTP is in use.
