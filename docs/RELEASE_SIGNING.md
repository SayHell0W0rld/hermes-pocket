# Release signing

Hermes Pocket builds an installable debug APK when signing secrets are absent.
When the GitHub secrets below are configured, the tag workflow builds and
publishes a release-signed APK.

## 1. Create a release keystore

Run this once and keep the file private. Do **not** commit it.

```powershell
keytool -genkeypair -v `
  -keystore hermes-pocket-release.keystore `
  -alias hermes-pocket `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storepass "<store-password>" `
  -keypass "<key-password>" `
  -dname "CN=Hermes Pocket,O=YourName,C=US"
```

Back up the keystore and passwords in a private password manager. Losing them
means future updates cannot use the same Android signing identity.

## 2. Local release builds

Create an untracked `keystore.properties` file in the repository root:

```properties
storeFile=C:/secure/path/hermes-pocket-release.keystore
storePassword=<store-password>
keyAlias=hermes-pocket
keyPassword=<key-password>
```

Then run:

```bash
./gradlew assembleRelease
```

## 3. GitHub Actions secrets

Base64-encode the keystore:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("hermes-pocket-release.keystore")) `
  | Set-Content keystore.base64
```

Add these repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The next release tag will publish `HermesPocket-v<version>-release.apk`.
Without the secrets, the workflow falls back to
`HermesPocket-v<version>-debug.apk`.
