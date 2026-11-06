# Content rating & data safety — draft answers

Drafted from what the app actually collects and does (confirmed against the codebase). Review and adjust before
submitting the real Play Console questionnaire — these are a starting point, not a substitute for checking them
against the live forms.

## Content rating questionnaire (IARC)

- Category: Social networking / Utility
- Violence: None
- Sexual content: None
- Profanity: None (user-generated text in messages/listings is not moderated — note this if the form asks about
  unmoderated user-generated content)
- Controlled substances: None
- Gambling: None
- User-generated content: Yes — listing descriptions and direct messages between users are free text, not
  pre-moderated. No public-facing content (no feed visible to non-users).
- Location sharing: No
- Personal info shared with other users: Limited — a volunteer's name is visible to the organization running a
  listing they signed up to, and vice versa, so they can message each other about it.
- Digital purchases: None
- Expected rating: Everyone / PEGI 3 (standard for an unmoderated-text social/utility app with no mature content)

## Data safety form

**Does your app collect or share any of the required user data types?** Yes.

| Data type | Collected | Shared with third parties | Purpose |
|---|---|---|---|
| Name | Yes | No | Account management, app functionality (shown to matched users) |
| Email address | Yes | No | Account management (sign-in) |
| User IDs | Yes | No | Account management, app functionality |
| Photos (profile picture, listing images) | Yes (optional) | Yes — stored with Cloudinary (image hosting, free tier) | App functionality |
| In-app messages | Yes | No | App functionality (messaging between volunteer and organization about a listing) |
| App activity (sign-ups, hours logged) | Yes | No | App functionality, analytics (your own hours log) |
| Device/other IDs (FCM push token) | Yes | Yes — Firebase Cloud Messaging (Google) | Push notifications |

**Is data encrypted in transit?** Yes (HTTPS throughout).

**Can users request data deletion?** Yes — via the app (edit/remove profile fields) or by contacting the team
(see `privacy-policy.md`).

**Is data collection required or optional?** Account creation (name, email) is required to use the app. Profile
picture, bio, interests/skills are optional.

## Before submitting

- [ ] Confirm the final third-party list (Firebase, Cloudinary) matches what's actually in production
- [ ] Have a team member not involved in writing this review it against the live Play Console form, since the
      exact categories/wording in the Play Console UI change over time
