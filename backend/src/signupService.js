import { Timestamp } from 'firebase-admin/firestore';
import { conflict, forbidden, notFound } from './errors.js';
import { ACTIVE_SIGNUP_STATUSES, SIGNUP_STATUS } from './constants.js';
import { isEnded } from './lookups.js';

/**
 * The two sign-up changes a volunteer can make. The normal routes and the offline sync endpoint
 * (POST /sync/signups) both go through these, so the rules can't drift apart.
 */

/**
 * Signs a volunteer up. One transaction makes "check the space, then take it" a single atomic step,
 * so two volunteers can't both get the last space.
 *
 * `id` is for the offline sync: the app picks the sign-up's id itself (its Room row's UUID). If a
 * sign-up with that id already exists for this volunteer and listing, the earlier attempt got
 * through, so it is returned untouched (created: false) instead of creating a second one.
 *
 * Returns { signup, listing, created }.
 */
export async function createSignup(db, { listingId, volunteerId, id = null, atMs = null }) {
  const listingRef = db.collection('listings').doc(listingId);
  const signupRef = id ? db.collection('signups').doc(id) : db.collection('signups').doc();

  return db.runTransaction(async (t) => {
    const listingSnap = await t.get(listingRef);
    if (!listingSnap.exists) throw notFound('Listing not found.');
    const listing = { ...listingSnap.data(), id: listingSnap.id };

    if (id) {
      const same = await t.get(signupRef);
      if (same.exists) {
        const existing = same.data();
        if (existing.volunteerId !== volunteerId || existing.listingId !== listingId) {
          throw conflict('id_in_use', 'That id already belongs to a different sign-up.');
        }
        return { signup: { ...existing, id: same.id }, listing, created: false };
      }
    }

    if (isEnded(listing)) throw conflict('listing_ended', 'That event has already taken place.');

    const others = await t.get(db.collection('signups').where('listingId', '==', listingSnap.id));
    const active = others.docs.map((d) => d.data()).filter((s) => ACTIVE_SIGNUP_STATUSES.includes(s.status));
    if (active.some((s) => s.volunteerId === volunteerId)) {
      throw conflict('already_signed_up', "You're already signed up for this listing.");
    }
    if (listing.type === 'Scheduled' && active.length >= listing.spacesTotal) {
      throw conflict('listing_full', 'Sorry, this listing is full.');
    }

    // Offline sync stamps the moment the user tapped, not the moment the phone got signal. Otherwise
    // an offline sign-up + cancel pair would see the cancel as older than the sign-up ("stale").
    const now = atMs !== null ? Timestamp.fromMillis(atMs) : Timestamp.now();
    const signup = {
      id: signupRef.id,
      listingId: listingSnap.id,
      volunteerId,
      status: SIGNUP_STATUS.CONFIRMED,
      hoursCompleted: null,
      signedUpAt: now,
      updatedAt: now,
      completedAt: null,
    };
    t.create(signupRef, signup);
    return { signup, listing, created: true };
  });
}

/**
 * Cancels a volunteer's own sign-up (the "Dismiss" button). Cancelling twice is fine.
 *
 * `changedAtMs` is only passed by the offline sync: when the user actually pressed the button.
 * Last write wins - if the server's record was changed AFTER that moment, the server's version
 * stands and nothing is applied (outcome "stale").
 *
 * Returns { signup, outcome } where outcome is "applied", "duplicate" (already cancelled) or "stale".
 */
export async function cancelSignup(db, { signupId, volunteerId, changedAtMs = null }) {
  const ref = db.collection('signups').doc(signupId);

  return db.runTransaction(async (t) => {
    const snap = await t.get(ref);
    if (!snap.exists) throw notFound('Sign-up not found.');
    const signup = { ...snap.data(), id: snap.id };

    if (signup.volunteerId !== volunteerId) throw forbidden('You can only cancel your own sign-ups.');
    if (signup.status === SIGNUP_STATUS.COMPLETED) {
      throw conflict('already_completed', "This one has already been completed, so it can't be cancelled.");
    }
    if (signup.status === SIGNUP_STATUS.CANCELLED) return { signup, outcome: 'duplicate' };
    if (changedAtMs !== null && signup.updatedAt && signup.updatedAt.toMillis() > changedAtMs) {
      return { signup, outcome: 'stale' };
    }

    const updatedAt = Timestamp.now();
    t.update(ref, { status: SIGNUP_STATUS.CANCELLED, updatedAt });
    return { signup: { ...signup, status: SIGNUP_STATUS.CANCELLED, updatedAt }, outcome: 'applied' };
  });
}