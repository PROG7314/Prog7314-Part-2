import { chunked } from './lookups.js';

/**
 * Push notifications (FCM, via the Firebase Admin SDK).
 *
 * Every notification is sent from the route where the real event happens (a sign-up, a
 * completion, a new listing) - not from a console test send. A failed notification never fails
 * the API call that caused it: errors are logged and swallowed.
 *
 * Rules, applied to every send:
 *  - the user must have notificationsEnabled (the Settings toggle) and a registered fcmToken
 *  - the text is in the user's preferredLanguage (en / af / zu, English for anything else)
 *  - a token FCM says is dead is removed from the user, so we stop sending to it
 */

// The Android app must create a notification channel with this id on start-up.
export const CHANNEL_ID = 'aegis_default';

const STALE_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);

// Most recipients one new listing will notify. Plenty for this app; a bigger one would use
// sendEachForMulticast instead of one send per user.
const MAX_LISTING_RECIPIENTS = 500;

// af / zu are first drafts - have a fluent speaker check them before the final submission.
const TEXT = {
  en: {
    new_signup: (v) => ({ title: 'New sign-up', body: `${v.volunteerName} signed up for "${v.listingTitle}".` }),
    signup_completed: (v) => ({ title: 'Hours logged', body: `${v.hours} hours were added for "${v.listingTitle}". Thank you!` }),
    new_listing: (v) => ({ title: 'A new opportunity matches you', body: `"${v.listingTitle}" - ${v.organizationName}` }),
  },
  af: {
    new_signup: (v) => ({ title: 'Nuwe inskrywing', body: `${v.volunteerName} het ingeskryf vir "${v.listingTitle}".` }),
    signup_completed: (v) => ({ title: 'Ure aangeteken', body: `${v.hours} ure is bygevoeg vir "${v.listingTitle}". Dankie!` }),
    new_listing: (v) => ({ title: 'Nuwe geleentheid wat by jou pas', body: `"${v.listingTitle}" - ${v.organizationName}` }),
  },
  zu: {
    new_signup: (v) => ({ title: 'Ukubhalisa okusha', body: `${v.volunteerName} ubhalisile ku-"${v.listingTitle}".` }),
    signup_completed: (v) => ({ title: 'Amahora afakiwe', body: `Amahora angu-${v.hours} afakiwe ku-"${v.listingTitle}". Siyabonga!` }),
    new_listing: (v) => ({ title: 'Ithuba elisha elikufanele', body: `"${v.listingTitle}" - ${v.organizationName}` }),
  },
};

const lowerSet = (list) => new Set((list ?? []).map((x) => String(x).toLowerCase()));
const overlaps = (have, wanted) => (have ?? []).some((x) => wanted.has(String(x).toLowerCase()));

/** messaging = firebase-admin's getMessaging(); without it (the tests' default) every call does nothing. */
export function createNotifier({ db, messaging }) {
  const pending = new Set();

  /** Runs in the background, never throws, and is tracked so tests can wait for it. */
  const fire = (label, fn) => {
    if (!messaging) return;
    const p = (async () => {
      try {
        await fn();
      } catch (e) {
        console.warn(`Notification (${label}) failed: ${e?.code ?? e?.message ?? e}`);
      }
    })();
    pending.add(p);
    p.finally(() => pending.delete(p));
  };

  /** Sends one notification to a user document we have already loaded. */
  const deliver = async (userId, user, type, vars, data) => {
    if (user.notificationsEnabled === false || !user.fcmToken) return;
    const lang = TEXT[user.preferredLanguage] ? user.preferredLanguage : 'en';
    const { title, body } = TEXT[lang][type](vars);
    try {
      await messaging.send({
        token: user.fcmToken,
        notification: { title, body },
        // The app reads "type" (+ ids) to open the right screen. FCM data values must be strings.
        data: Object.fromEntries(Object.entries({ type, ...data }).map(([k, v]) => [k, String(v)])),
        android: { priority: 'high', notification: { channelId: CHANNEL_ID } },
      });
    } catch (e) {
      if (STALE_TOKEN_CODES.has(e?.code)) {
        await db.collection('users').doc(userId).update({ fcmToken: null });
      } else {
        throw e;
      }
    }
  };

  const toUser = async (userId, type, vars, data) => {
    const snap = await db.collection('users').doc(userId).get();
    if (!snap.exists) return;
    await deliver(userId, snap.data(), type, vars, data);
  };

  return {
    /** A volunteer signed up -> tell the organization that owns the listing. */
    newSignup({ listing, signupId, volunteerName }) {
      fire('new_signup', () =>
        toUser(
          listing.organizationId,
          'new_signup',
          { volunteerName: volunteerName || 'A volunteer', listingTitle: listing.title },
          // The title and minimum hours let the app open the sign-ups screen straight from the notification.
          { listingId: listing.id, signupId, listingTitle: listing.title, minHours: listing.minHours }
        )
      );
    },

    /** The organization marked a sign-up complete -> tell the volunteer. */
    signupCompleted({ listing, signupId, volunteerId, hours }) {
      fire('signup_completed', () =>
        toUser(
          volunteerId,
          'signup_completed',
          { hours, listingTitle: listing.title },
          { listingId: listing.id, signupId, hours }
        )
      );
    },

    /** A new listing was posted -> tell volunteers whose interests OR skills overlap it. */
    newListing({ listing, organizationName }) {
      fire('new_listing', async () => {
        const causes = lowerSet(listing.causeCategories);
        const skills = lowerSet(listing.skillsRequired);
        const snap = await db.collection('users').where('role', '==', 'volunteer').get();
        const recipients = snap.docs
          .filter((d) => {
            const u = d.data();
            return u.fcmToken && u.notificationsEnabled !== false && (overlaps(u.interests, causes) || overlaps(u.skills, skills));
          })
          .slice(0, MAX_LISTING_RECIPIENTS);

        for (const group of chunked(recipients, 20)) {
          await Promise.all(
            group.map((d) =>
              deliver(d.id, d.data(), 'new_listing', { listingTitle: listing.title, organizationName: organizationName || '' }, { listingId: listing.id }).catch(
                (e) => console.warn(`Notification (new_listing) failed for one volunteer: ${e?.code ?? e?.message ?? e}`)
              )
            )
          );
        }
      });
    },

    /** Resolves once every notification started so far has finished (used by the tests). */
    async settled() {
      while (pending.size > 0) await Promise.allSettled([...pending]);
    },
  };
}