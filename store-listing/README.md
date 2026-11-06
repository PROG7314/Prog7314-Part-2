# Store listing — checklist

What the POE brief needs here, and the status of each piece. The listing text, the graphics, the screenshots
and the signed release build (with the steps to reproduce it) are all in this folder.

| Item | Status | Notes |
|---|---|---|
| App name (≤30 chars) | Done | See `listing-copy.md` |
| Short description (≤80 chars) | Done | See `listing-copy.md` |
| Full description (≤4,000 chars) | Done | See `listing-copy.md` |
| Privacy policy | Drafted | See `privacy-policy.md` — host it as a GitHub Pages page and link the URL from the main README once live |
| Content rating & data safety answers | Drafted | See `content-rating-and-data-safety.md` — review for accuracy before submitting |
| 512×512 PNG launcher icon | Done | `icon/icon-512.png`; the same artwork is the app's launcher icon (adaptive + legacy) |
| 1024×500 feature graphic | Done | `feature-graphic/feature-graphic-1024x500.png` |
| 4+ real screenshots | Done | `screenshots/` (8 × 1080×1920): volunteer home, commitments, chat, profile, Afrikaans, dark mode, organisation home, create listing with translations. Taken on an emulator against a local demo API; retake on a physical phone with real data if the marker requires it |
| Signed production APK / AAB | Done | `release/RELEASE.md` (signature, checksums, how to rebuild); the files are attached to the GitHub Release `v1.0.0` |

## Notes

- The images were produced from the actual app: the launcher icon comes from the logo artwork, the screenshots
  from the debug build running on an emulator, and the signed build from `./gradlew :app:assembleRelease`.
- `release/` also holds `evidence/` (the signed build running, the icon installed) and `SHA256SUMS.txt`.
- Remaining steps before a real Play upload are listed at the end of `release/RELEASE.md`.
