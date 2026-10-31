import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { requireRole } from '../middleware/auth.js';
import { AppError, conflict, forbidden, notFound } from '../errors.js';
import { SIGNUP_STATUS } from '../constants.js';
import { bodyOf, parseHours, parseSyncChanges } from '../validators.js';
import { hoursEntryResponse, listingResponse, signupResponse } from '../serialize.js';
import { loadUserNames } from '../lookups.js';
import { cancelSignup, createSignup } from '../signupService.js';

const millis = (ts) => ts?.toMillis?.() ?? 0;

/** Table 4 - Sign-ups & Hours. Mounted at the API root because the paths span /listings, /users and /signups. */
export function signupsRouter({ db, notifier }) {
  const r = Router();
  const volunteerOnly = requireRole('volunteer');
  const orgOnly = requireRole('organization');

  // Volunteer signs up. A transaction makes "check the space, then take it" one atomic step,
  // so two volunteers can't both get the last space.
  r.post(
    '/listings/:id/signups',
    volunteerOnly,
    ah(async (req, res) => {
      const { signup, listing } = await createSignup(db, { listingId: req.params.id, volunteerId: req.user.id });
      res.status(201).json(signupResponse(signup.id, signup));
      // Real event -> push to the organization that owns the listing (runs in the background).
      notifier.newSignup({ listing, signupId: signup.id, volunteerName: req.user.name });
    })
  );

  // Offline sync. The app queues sign-ups and cancels made without a connection (each carries the
  // id of its Room row and the moment the user made it) and sends them here when it is back online.
  //  - Idempotent: a sign-up is stored under the app's own id, so retrying a batch that half-worked
  //    never creates a second sign-up (those come back as "duplicate").
  //  - Applied oldest-first, so sign-up -> cancel -> sign-up again ends in the right state.
  //  - Last write wins: a change older than the server's last update of that sign-up is "stale".
  //  - One change failing (listing full, event over...) never fails the batch: it is "rejected" with
  //    the same code the normal route would give, and the rest still apply.
  r.post(
    '/sync/signups',
    volunteerOnly,
    ah(async (req, res) => {
      const changes = parseSyncChanges(bodyOf(req)).sort((a, b) => a.updatedAt - b.updatedAt); // sort is stable
      const now = Date.now();
      const results = [];

      for (const change of changes) {
        const base = { id: change.id, action: change.action };
        // A phone clock that runs fast can't win from the future.
        const changedAtMs = Math.min(change.updatedAt.getTime(), now);
        try {
          if (change.action === 'signup') {
            const { signup, listing, created } = await createSignup(db, {
              listingId: change.listingId,
              volunteerId: req.user.id,
              id: change.id,
              atMs: changedAtMs,
            });
            results.push({ ...base, status: created ? 'applied' : 'duplicate', signup: signupResponse(signup.id, signup) });
            if (created) notifier.newSignup({ listing, signupId: signup.id, volunteerName: req.user.name });
          } else {
            const { signup, outcome } = await cancelSignup(db, {
              signupId: change.id,
              volunteerId: req.user.id,
              changedAtMs,
            });
            results.push({ ...base, status: outcome, signup: signupResponse(signup.id, signup) });
          }
        } catch (e) {
          if (!(e instanceof AppError)) throw e;
          results.push({ ...base, status: 'rejected', code: e.code, message: e.message });
        }
      }
      res.json({ results, serverTime: new Date().toISOString() });
    })
  );

  // Organization: everyone signed up to one of ITS listings (cancelled sign-ups are left out).
  r.get(
    '/listings/:id/signups',
    orgOnly,
    ah(async (req, res) => {
      const listingSnap = await db.collection('listings').doc(req.params.id).get();
      if (!listingSnap.exists) throw notFound('Listing not found.');
      if (listingSnap.data().organizationId !== req.user.id) {
        throw forbidden('You can only view sign-ups for your own listings.');
      }
      const snap = await db.collection('signups').where('listingId', '==', req.params.id).get();
      const signups = snap.docs
        .map((d) => ({ ...d.data(), id: d.id }))
        .filter((s) => s.status !== SIGNUP_STATUS.CANCELLED)
        .sort((a, b) => millis(a.signedUpAt) - millis(b.signedUpAt));
      const names = await loadUserNames(db, signups.map((s) => s.volunteerId));
      res.json(
        signups.map((s) =>
          signupResponse(s.id, s, { volunteer: { id: s.volunteerId, name: names.get(s.volunteerId) ?? null } })
        )
      );
    })
  );

  // Volunteer: my commitments. Every status is returned (the app shows the confirmed ones);
  // each sign-up embeds a summary of its listing, or null if that listing has since been deleted.
  r.get(
    '/users/me/signups',
    volunteerOnly,
    ah(async (req, res) => {
      const snap = await db.collection('signups').where('volunteerId', '==', req.user.id).get();
      const signups = snap.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => millis(b.signedUpAt) - millis(a.signedUpAt));

      const listingIds = [...new Set(signups.map((s) => s.listingId))];
      const listingSnaps = listingIds.length
        ? await db.getAll(...listingIds.map((id) => db.collection('listings').doc(id)))
        : [];
      const listings = new Map(listingSnaps.filter((s) => s.exists).map((s) => [s.id, s.data()]));
      const orgNames = await loadUserNames(db, [...listings.values()].map((l) => l.organizationId));

      res.json(
        signups.map((s) => {
          const l = listings.get(s.listingId);
          return signupResponse(s.id, s, {
            listing: l ? listingResponse(s.listingId, l, { organizationName: orgNames.get(l.organizationId) ?? null }) : null,
          });
        })
      );
    })
  );

  // Volunteer cancels their own sign-up (the "Dismiss" button).
  r.patch(
    '/signups/:id',
    volunteerOnly,
    ah(async (req, res) => {
      const { signup } = await cancelSignup(db, { signupId: req.params.id, volunteerId: req.user.id });
      res.json(signupResponse(signup.id, signup));
    })
  );

  // Organization marks a sign-up complete. Hours default to the listing's minHours.
  // Calling it again on a completed sign-up EDITS the hours. hoursLog uses the sign-up id as its
  // own id, so there is always exactly one entry per completed commitment.
  r.patch(
    '/signups/:id/complete',
    orgOnly,
    ah(async (req, res) => {
      const requested = parseHours(bodyOf(req));
      const signupRef = db.collection('signups').doc(req.params.id);

      const result = await db.runTransaction(async (t) => {
        const signupSnap = await t.get(signupRef);
        if (!signupSnap.exists) throw notFound('Sign-up not found.');
        const signup = signupSnap.data();

        const listingSnap = await t.get(db.collection('listings').doc(signup.listingId));
        if (!listingSnap.exists) throw conflict('listing_missing', 'That listing no longer exists.');
        const listing = listingSnap.data();
        if (listing.organizationId !== req.user.id) {
          throw forbidden('You can only complete sign-ups for your own listings.');
        }
        if (signup.status === SIGNUP_STATUS.CANCELLED) {
          throw conflict('signup_cancelled', 'That sign-up was cancelled.');
        }

        const hours = requested ?? signup.hoursCompleted ?? listing.minHours;
        const completedAt = signup.completedAt ?? Timestamp.now();
        const updatedAt = Timestamp.now();
        const updated = { ...signup, status: SIGNUP_STATUS.COMPLETED, hoursCompleted: hours, completedAt, updatedAt };

        const firstCompletion = signup.status !== SIGNUP_STATUS.COMPLETED; // editing hours later shouldn't ping again
        t.update(signupRef, { status: SIGNUP_STATUS.COMPLETED, hoursCompleted: hours, completedAt, updatedAt });
        t.set(db.collection('hoursLog').doc(signupSnap.id), {
          id: signupSnap.id,
          volunteerId: signup.volunteerId,
          listingId: signup.listingId,
          organizationId: listing.organizationId,
          listingTitle: listing.title, // denormalized, so the log survives the listing being deleted
          hoursCompleted: hours,
          completedAt,
        });
        return { id: signupSnap.id, updated, listing: { ...listing, id: listingSnap.id }, firstCompletion };
      });
      res.json(signupResponse(result.id, result.updated));
      // Real event -> push to the volunteer whose hours were just logged (runs in the background).
      if (result.firstCompletion) {
        notifier.signupCompleted({
          listing: result.listing,
          signupId: result.id,
          volunteerId: result.updated.volunteerId,
          hours: result.updated.hoursCompleted,
        });
      }
    })
  );

  r.get(
    '/users/me/hours-log',
    volunteerOnly,
    ah(async (req, res) => {
      const snap = await db.collection('hoursLog').where('volunteerId', '==', req.user.id).get();
      const rows = snap.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => millis(b.completedAt) - millis(a.completedAt));
      const totalHours = Math.round(rows.reduce((sum, h) => sum + (h.hoursCompleted ?? 0), 0) * 100) / 100;
      res.json({ entries: rows.map((h) => hoursEntryResponse(h.id, h)), totalHours });
    })
  );

  return r;
}