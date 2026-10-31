# Aegis API (backend)

Node.js + Express + Firestore, deployed on Render. Implements the Part 1 endpoint spec (Tables 1-4) under `/api/v1`.
Push notifications (FCM) are built: see section 6b. Not built yet (POE): `/translate`, `/sync/signups`.

```
backend/
  src/         app.js (wiring) · routes/ (auth, users, listings, signups) · middleware/ · validators.js · constants.js
  scripts/     make-test-token.js · seed.js
  test/        api.test.js   (12 end-to-end scenarios)
  testutils/   fakeFirestore.js  (in-memory stand-in used by the tests)
```

## 1. Before you start

- **Use the same Firebase project as the Android app** (the one behind `google-services.json`). The API only accepts Google
  sign-in tokens from that project.
- Firebase console → **Build → Firestore Database → Create database**. Leave the security rules locked: only this API talks to
  the database (the Admin SDK ignores rules), the app never does.
- Firebase console → **Project settings → Service accounts → Generate new private key**. This JSON is a password. Never commit it.
  Turn it into a single line of text (base64) so it fits in an environment variable:
  - PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("key.json"))`
  - Mac/Linux: `base64 -w0 key.json`

## 2. Run it locally

```
cd backend
npm install
copy .env.example .env      # Mac/Linux: cp .env.example .env
```
Fill in `.env`: `FIREBASE_SERVICE_ACCOUNT` = the base64 text, `JWT_SECRET` = the output of
`node -p "require('crypto').randomBytes(48).toString('hex')"`. Then `npm run dev` and open http://localhost:3000/health.

## 3. Deploy to Render

1. Push the repo to GitHub (`backend/` folder inside it).
2. Render → **New → Web Service** → pick the repo.
3. **Root Directory** `backend` · **Build Command** `npm ci` · **Start Command** `npm start`.
4. Advanced → **Health Check Path** `/health`.
5. **Environment variables**: `NODE_VERSION` = `22`, `JWT_SECRET`, `FIREBASE_SERVICE_ACCOUNT` (base64). Optional: `JWT_EXPIRES_IN`.
6. Deploy. Your URL is `https://<service-name>.onrender.com`, so the Android base URL is
   **`https://<service-name>.onrender.com/api/v1/`** (keep the trailing slash).

Free-tier services go to sleep when idle and the first request can take a minute. Open `/health` before a demo to wake it.
Set the same `JWT_SECRET` in Render and in your local `.env`, or test tokens made locally won't work on Render.

## 4. Test accounts (build screens before Google sign-in works)

```
npm run seed                              # test organization + 3 sample listings from the mockups
npm run token -- --role volunteer         # prints a JWT for a test volunteer
npm run token -- --role organization      # ...or for the test organization
```
Paste the token into the app's `TokenProvider.getToken()` while developing. **Never commit a token.** These commands write
to your real Firestore (accounts `test-volunteer` / `test-organization`). Anyone who has `JWT_SECRET` can mint a token for any
user, so keep it out of GitHub and rotate it if it leaks (that logs everyone out).

## 5. Endpoints

`Authorization: Bearer <JWT>` on everything except `POST /auth/login`.

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/auth/login` | anyone | `{ firebaseIdToken }` → `{ token, user: { id, role, isNewUser } }` |
| POST | `/auth/role` | logged in | one-time `{ role: "volunteer" \| "organization" }` |
| POST | `/auth/logout` | logged in | 204; also clears the account's FCM token |
| POST | `/users/me/fcm-token` | logged in | `{ fcmToken }` → 204; send after every login and in `onNewToken` |
| GET / PUT | `/users/me` | either role | profile; PUT only accepts the fields for your role |
| PUT | `/users/me/settings` | logged in | `{ preferredLanguage, notificationsEnabled }` |
| GET | `/listings?cause=&skill=&type=&area=` | either role | filtered feed → `{ items }` |
| GET | `/listings/{id}` | either role | one listing |
| POST / PUT / DELETE | `/listings`, `/listings/{id}` | organization (owner) | create / edit / delete |
| POST | `/listings/{id}/signups` | volunteer | 201, or 409 `listing_full` / `already_signed_up` / `listing_ended` |
| GET | `/listings/{id}/signups` | organization (owner) | who signed up |
| GET | `/users/me/signups` | volunteer | my commitments, each with its `listing` |
| PATCH | `/signups/{id}` | volunteer (own) | cancel ("Dismiss") |
| PATCH | `/signups/{id}/complete` | organization (owner) | `{ hoursCompleted? }`, writes the hours log |
| GET | `/users/me/hours-log` | volunteer | `{ entries, totalHours }` |

Errors always look like `{ "error": { "code", "message", "field"? } }`. Codes: `validation_error` (400) · `unauthorized` (401) ·
`forbidden`, `role_required` (403) · `not_found` (404) · `role_already_set`, `listing_full`, `already_signed_up`, `listing_ended`,
`already_completed`, `signup_cancelled`, `listing_missing` (409) · `internal_error` (500).

## 6. Decisions the Part 1 spec left open

- **`GET /listings` depends on the caller.** Volunteers get listings that haven't ended; an organization gets only its own
  (ended ones included), which is how Org Home works.
- **Filters:** `cause=a,b` and `cause=a&cause=b` both work, and matching ignores case. `type` and `area` are hard filters. `cause`
  and `skill` are an *interest match*: a listing is returned if it overlaps on causes **or** skills, and each item carries
  `matchedCauses` / `matchedSkills` so the app can show why it matched (`null` when no cause/skill filter was sent).
- **Computed, not stored:** `signupCount` and `spacesRemaining` come from confirmed + completed sign-ups (cancelled frees a space).
  `organizationName` is looked up from the organization's profile.
- **Last space race:** signing up runs in a Firestore transaction, so two people can't both take the last space.
- **Hours:** only the owning organization can complete a sign-up. Calling it again edits the hours. The hours log entry uses the
  sign-up's id, so there is one entry per commitment. Completed hours survive the listing being deleted.
- **Deleting a listing** cancels everyone still confirmed on it. A volunteer's `/users/me/signups` returns every status; the
  app should show the confirmed ones, and handle `listing: null` for deleted listings.
- **Accounts:** the `users` document id is the Firebase uid. The JWT only carries that id; the role is read from Firestore on
  each request (one extra read), so it is correct straight after `POST /auth/role`. Logout is stateless apart from clearing the FCM token: the app just discards the JWT.
- **Fixed lists:** causes, skills and interests are checked against `src/constants.js`. **The team must agree the exact wording**
  (the mockups say "Children & Youth", the design doc says "Children"). Set `ENFORCE_FIXED_LISTS=false` to switch the check off meanwhile.

## 6b. Push notifications (FCM)

Sent with the Firebase Admin SDK (`src/notifications.js`) **from the route where the real event happens**, in the background,
so a failed push never fails the API call.

| Event | Route | Who gets it | `data.type` |
|---|---|---|---|
| A volunteer signs up | `POST /listings/{id}/signups` | the listing's organization | `new_signup` |
| An organization completes a sign-up (first time only; editing hours doesn't re-send) | `PATCH /signups/{id}/complete` | that volunteer | `signup_completed` |
| A new listing is posted | `POST /listings` | volunteers whose interests **or** skills overlap it | `new_listing` |

- Every message carries `data: { type, listingId, signupId?, ... }` (`new_signup` also has `listingTitle` and `minHours`; `signup_completed` has `hours`) so the app can open the right screen (`new_signup` → the
  listing's sign-ups screen, `signup_completed` → Hours, `new_listing` → Home).
- Only sent if the user has `notificationsEnabled` (the Settings toggle) and a registered token. The text follows their
  `preferredLanguage` (en / af / zu; English otherwise). The af / zu wording is a first draft: have a fluent speaker check it.
- A token FCM reports as dead is removed. One token belongs to one account: registering it on a second account (same phone)
  removes it from the first, and logout clears it.
- Android channel id is `aegis_default`: the app must create a channel with that id on start-up.
- Not built: the "day before a Scheduled commitment" reminder (needs a scheduler, e.g. Render Cron Job).


## 6c. Offline sync (`POST /sync/signups`)

The app queues sign-ups and cancels made without a connection in Room, then sends them here when it is back online.

Request: `{ "changes": [ { "id": "<uuid>", "action": "signup" | "cancel", "listingId": "...", "updatedAt": "<ISO time the user tapped>" } ] }`
(max 100; `listingId` only for `signup`). `id` is the app's own UUID for that sign-up, used for both actions.

Response: `{ "results": [ { "id", "action", "status", "signup"?, "code"?, "message"? } ], "serverTime" }`, one result per change.

| status | meaning | what the app does |
|---|---|---|
| `applied` | change made | mark the Room row synced, store `signup` |
| `duplicate` | already done (a retried batch, or already cancelled) | mark synced |
| `stale` | server's copy was changed after the user's change; server wins | replace the Room row with `signup`, mark synced |
| `rejected` | refused, with a `code` (`listing_full`, `listing_ended`, `not_found`, `already_signed_up`, `already_completed`, `forbidden`, `id_in_use`) | drop the row, tell the user why |

Conflict rules:
- **Idempotent.** The sign-up is stored under the app's UUID, so resending a batch never makes a second one.
- **Oldest first.** Changes are sorted by `updatedAt`, so sign-up, cancel, sign-up again ends in the right state.
- **Last write wins.** A cancel older than the server's last update of that sign-up is `stale`. Times are clamped to server time, so a fast phone clock can't win from the future.
- **One bad change never fails the batch.** It comes back `rejected`; the rest still apply.
- Sign-ups made offline are stamped with the time the user tapped, not the time of sync.

## 7. Firestore

Collections: `users`, `listings`, `signups`, `hoursLog` (fields as in the Part 1 data model). No composite indexes are needed:
queries only use equality filters, and sorting/filtering is done in code. That is fine at prototype scale.

## Listing images: Cloudinary instead of Firebase Storage

- Part 1 planned Firebase Storage for listing images (max 3 per listing).
- Firebase Storage now needs the paid Blaze plan (card required). Our group has no paid plan, so we changed it.
- We now upload to **Cloudinary** (free tier) straight from the app.
  - Unsigned upload preset, folder `aegis_listings`.
  - The app shrinks each photo first (max 1600 px, JPEG 85%).
  - Cloudinary returns an https URL. We send that URL to our API in `images`.
- Nothing else changed:
  - API still accepts up to 3 https image URLs per listing.
  - Firestore stores URLs only, never image data.
- No Cloudinary API key or secret is in the app or the repo. Only the cloud name and preset name, which are public by design for unsigned uploads.

## 8. Tests

`npm test` runs 17 scenarios (auth, roles, validation, filtering, sign-up race, cancel, complete, hours, delete, push notifications) against an
in-memory Firestore stand-in, so it needs no Firebase account and works in GitHub Actions (`working-directory: backend`,
Node 22, `npm ci`, `npm test`). It has not been run against real Firestore, so do one manual pass on Render.

## 9. Known

`npm audit` lists 2 moderate advisories from `uuid`, a dependency of `firebase-admin`, in code this API doesn't call.
Re-check after future `firebase-admin` updates.