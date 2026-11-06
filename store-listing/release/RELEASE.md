# Release 1.0.0 — signed production build

Evidence for the "Store Publication Readiness" requirement: a signed, shrunk (R8) production build, the store
listing assets, and the steps to reproduce them.

| | |
|---|---|
| Version | `1.0.0` (versionCode `1`) |
| Package | `com.example.aegis` (see "Before uploading to Google Play") |
| SDK | minSdk 25, targetSdk 36 |
| Build | `release`, R8 minify + resource shrinking on, rules in `app/proguard-rules.pro` |
| API | `https://aegis-api-phuj.onrender.com/api/v1/` (HTTPS only; cleartext is refused in release) |
| Signing | APK Signature Scheme v2, RSA 2048, SHA256withRSA, valid until 2054 |

## Signing certificate

| | |
|---|---|
| Subject | `CN=Aegis Volunteer Connect, OU=PROG7314, O=Aegis, L=Cape Town, C=ZA` |
| SHA-1 | `48:5F:B6:9B:87:9C:84:5C:46:CA:C3:03:A5:77:44:C2:A5:B7:F5:69` |
| SHA-256 | `0E:2D:F4:64:6E:F7:01:C0:9D:96:06:6E:44:0B:EA:BB:93:8C:AC:D4:B2:22:98:24:53:91:93:50:AB:2D:B7:5F` |

The keystore and its passwords are **not** in the repository. They sit outside the project folder and are read
through `keystore.properties` (git-ignored; template in `keystore.properties.example`). Back the keystore up:
an app already published with it can never be updated if it is lost.

## Artifacts (attached to the GitHub Release `v1.0.0`)

| File | SHA-256 |
|---|---|
| `Aegis-v1.0.0-release.apk` (4.6 MB, signed) | `ac6c30342301379d55d87b43e81dd8a9341d44227ecc6ad94f0e8fc5c89e4272` |
| `Aegis-v1.0.0-release.aab` (7.7 MB, for Play) | `8871e3b39ccd9d50d4a9b81a7fcb650d1dee00711adba72ec8af14979f773238` |

Check a download with `shasum -a 256 Aegis-v1.0.0-release.apk` and confirm the signature with:

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --verbose --print-certs Aegis-v1.0.0-release.apk
```

Expected: `Verifies`, v2 scheme `true`, and the SHA-256 certificate digest above.

## Reproduce the build

```bash
cp keystore.properties.example keystore.properties   # then fill in your keystore path and passwords
./gradlew :app:assembleRelease :app:bundleRelease
# app/build/outputs/apk/release/app-release.apk
# app/build/outputs/bundle/release/app-release.aab
```

Without `keystore.properties` the release APK is built unsigned, so CI and teammates can still compile it.

## What was verified

- The signed APK passes `apksigner verify` (see above).
- It installs and starts on an Android 16 emulator (API 36) with no crash and reaches the login screen
  (`evidence/signed-release-running-login.png`); the new launcher icon shows on the home screen
  (`evidence/launcher-icon-installed.png`).
- R8 keeps the Gson/Retrofit models (`ListingDto`, `Listing`, `AegisApi` are not renamed in `mapping.txt`).
- Debug and unit tests: `./gradlew :app:testDebugUnitTest`; backend tests: `cd backend && npm test`.

**Not verified:** signing in and using the release build against the hosted API. Google sign-in needs the
release SHA-1 above registered in Firebase first. The screenshots in `../screenshots/` were taken from the app
running on an emulator against a local demo API with seeded data, not from a physical phone.

## Before uploading to Google Play

1. **Change the package name.** Play Console rejects `com.example.*`. Pick a real id (for example
   `za.co.<yourteam>.aegis`), change `namespace`/`applicationId`, add that app in the Firebase console and replace
   `app/google-services.json`.
2. **Register the release SHA-1 and SHA-256 above in Firebase** (Project settings, the Android app), otherwise
   Google sign-in fails in the release build.
3. Host `privacy-policy.md` as a public page and enter its URL in Play Console.
4. Review `content-rating-and-data-safety.md` against what the app really collects.
5. Upload the `.aab`, the icon, the feature graphic and the screenshots from this folder.
