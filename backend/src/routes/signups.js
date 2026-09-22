// routes/signups.js - volunteers signing up to listings, and organizations confirming the hours worked

import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { requireRole } from '../middleware/auth.js';
import { conflict, forbidden, notFound } from '../errors.js';
import { ACTIVE_SIGNUP_STATUSES, SIGNUP_STATUS } from '../constants.js';
import { bodyOf, parseHours } from '../validators.js';
import { hoursEntryResponse, iso, listingResponse, signupResponse } from '../serialize.js';
import { isEnded, loadUserNames } from '../lookups.js';

// Firestore Timestamp -> number of milliseconds, used for sorting. A missing date counts as 0.
const millis = (ts) => ts?.toMillis?.() ?? 0;

/** Sign-ups & Hours. Mounted at the API root because the paths span /listings, /users and /signups. */
export function signupsRouter({ db }) {
  const r = Router();
  const volunteerOnly = requireRole('volunteer');
  const orgOnly = requireRole('organization');

  // Volunteer signs up. A transaction makes "check the space, then take it" one atomic step,
  // so two volunteers can't both get the last space.
  // POST /listings/{id}/signups  ->  201 with the new sign-up
  r.post(
    '/listings/:id/signups',
    volunteerOnly,
    ah(async (req, res) => {
      const listingRef = db.collection('listings').doc(req.params.id);
      // Everything inside runTransaction either happens together or not at all. Firestore makes competing
      // transactions on the same data wait or retry, so the space count we read here is still true when we write.
      const created = await db.runTransaction(async (t) => {
        const listingSnap = await t.get(listingRef);
        if (!listingSnap.exists) throw notFound('Listing not found.');
        const listing = listingSnap.data();
        if (isEnded(listing)) throw conflict('listing_ended', 'That event has already taken place.');

        // Read this listing's existing sign-ups, keeping only the active ones (cancelled ones free their space).
        const existing = await t.get(db.collection('signups').where('listingId', '==', listingSnap.id));
        const active = existing.docs.map((d) => d.data()).filter((s) => ACTIVE_SIGNUP_STATUSES.includes(s.status));
        if (active.some((s) => s.volunteerId === req.user.id)) {
          throw conflict('already_signed_up', "You're already signed up for this listing.");
        }
        // Only Scheduled listings have a limit; Ongoing listings accept everyone.
        if (listing.type === 'Scheduled' && active.length >= listing.spacesTotal) {
          throw conflict('listing_full', 'Sorry, this listing is full.');
        }

        const ref = db.collection('signups').doc();
        const data = {
          id: ref.id,
          listingId: listingSnap.id,
          volunteerId: req.user.id, // always the caller, never taken from the request body
          status: SIGNUP_STATUS.CONFIRMED,
          hoursCompleted: null, // filled in when the organization marks it complete
          signedUpAt: Timestamp.now(),
          completedAt: null,
        };
        t.create(ref, data);
        return data;
      });
      res.status(201).json(signupResponse(created.id, created));
    })
  );

  // Organization: everyone signed up to one of ITS listings (cancelled sign-ups are left out).
  // GET /listings/{id}/signups
  r.get(
    '/listings/:id/signups',
    orgOnly,
    ah(async (req, res) => {
      const listingSnap = await db.collection('listings').doc(req.params.id).get();
      if (!listingSnap.exists) throw notFound('Listing not found.');
      // Only the organization that owns the listing may see who signed up.
      if (listingSnap.data().organizationId !== req.user.id) {
        throw forbidden('You can only view sign-ups for your own listings.');
      }
      const snap = await db.collection('signups').where('listingId', '==', req.params.id).get();
      const signups = snap.docs
        .map((d) => ({ ...d.data(), id: d.id }))
        .filter((s) => s.status !== SIGNUP_STATUS.CANCELLED)
        .sort((a, b) => millis(a.signedUpAt) - millis(b.signedUpAt)); // earliest sign-up first
      // One batched read for all the volunteers' names, so the organization sees names, not just ids.
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
  // GET /users/me/signups
  r.get(
    '/users/me/signups',
    volunteerOnly,
    ah(async (req, res) => {
      const snap = await db.collection('signups').where('volunteerId', '==', req.user.id).get();
      const signups = snap.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => millis(b.signedUpAt) - millis(a.signedUpAt));

      // Load all the listings these sign-ups point to in one batched read (not one read per sign-up).
      const listingIds = [...new Set(signups.map((s) => s.listingId))];
      const listingSnaps = listingIds.length
        ? await db.getAll(...listingIds.map((id) => db.collection('listings').doc(id)))
        : [];
      const listings = new Map(listingSnaps.filter((s) => s.exists).map((s) => [s.id, s.data()]));
      const orgNames = await loadUserNames(db, [...listings.values()].map((l) => l.organizationId));

      res.json(
        signups.map((s) => {
          const l = listings.get(s.listingId);
          // If the listing was deleted, `l` is undefined and the app receives listing: null.
          return signupResponse(s.id, s, {
            listing: l ? listingResponse(s.listingId, l, { organizationName: orgNames.get(l.organizationId) ?? null }) : null,
          });
        })
      );
    })
  );

  // Volunteer cancels their own sign-up (the "Dismiss" button).
  // PATCH /signups/{id}
  r.patch(
    '/signups/:id',
    volunteerOnly,
    ah(async (req, res) => {
      const ref = db.collection('signups').doc(req.params.id);
      const snap = await ref.get();
      if (!snap.exists) throw notFound('Sign-up not found.');
      const signup = snap.data();
      // Volunteers can only cancel their own sign-ups, never someone else's.
      if (signup.volunteerId !== req.user.id) throw forbidden('You can only cancel your own sign-ups.');
      // Once the organization has recorded the hours, the volunteer can no longer withdraw.
      if (signup.status === SIGNUP_STATUS.COMPLETED) {
        throw conflict('already_completed', "This one has already been completed, so it can't be cancelled.");
      }
      if (signup.status === SIGNUP_STATUS.CANCELLED) return res.json(signupResponse(snap.id, signup)); // idempotent

      await ref.update({ status: SIGNUP_STATUS.CANCELLED });
      res.json(signupResponse(snap.id, { ...signup, status: SIGNUP_STATUS.CANCELLED }));
    })
  );

  // Organization marks a sign-up complete. Hours default to the listing's minHours.
  // Calling it again on a completed sign-up EDITS the hours. hoursLog uses the sign-up id as its
  // own id, so there is always exactly one entry per completed commitment.
  // PATCH /signups/{id}/complete  - body (optional): { hoursCompleted }
  r.patch(
    '/signups/:id/complete',
    orgOnly,
    ah(async (req, res) => {
      const requested = parseHours(bodyOf(req)); // undefined if the organization didn't send hours
      const signupRef = db.collection('signups').doc(req.params.id);

      // A transaction, because two documents change together (the sign-up and the hours log entry)
      // and they must never end up out of step.
      const result = await db.runTransaction(async (t) => {
        const signupSnap = await t.get(signupRef);
        if (!signupSnap.exists) throw notFound('Sign-up not found.');
        const signup = signupSnap.data();

        const listingSnap = await t.get(db.collection('listings').doc(signup.listingId));
        if (!listingSnap.exists) throw conflict('listing_missing', 'That listing no longer exists.');
        const listing = listingSnap.data();
        // The key rule: only the organization that owns the listing can confirm hours.
        if (listing.organizationId !== req.user.id) {
          throw forbidden('You can only complete sign-ups for your own listings.');
        }
        if (signup.status === SIGNUP_STATUS.CANCELLED) {
          throw conflict('signup_cancelled', 'That sign-up was cancelled.');
        }

        // Hours to record: what the organization sent, else the hours already recorded (when editing),
        // else the listing's minimum hours.
        const hours = requested ?? signup.hoursCompleted ?? listing.minHours;
        // Keep the original completion time if this is an edit.
        const completedAt = signup.completedAt ?? Timestamp.now();
        const updated = { ...signup, status: SIGNUP_STATUS.COMPLETED, hoursCompleted: hours, completedAt };

        t.update(signupRef, { status: SIGNUP_STATUS.COMPLETED, hoursCompleted: hours, completedAt });
        // Using the sign-up's id as the hoursLog id means set() creates the entry the first time
        // and overwrites it on an edit, so it can never be duplicated.
        t.set(db.collection('hoursLog').doc(signupSnap.id), {
          id: signupSnap.id,
          volunteerId: signup.volunteerId,
          listingId: signup.listingId,
          organizationId: listing.organizationId,
          listingTitle: listing.title, // denormalized, so the log survives the listing being deleted
          hoursCompleted: hours,
          completedAt,
        });
        return { id: signupSnap.id, updated };
      });
      res.json(signupResponse(result.id, result.updated));
    })
  );

  // Volunteer: hours history and the running total shown on the Hours screen.
  // GET /users/me/hours-log
  r.get(
    '/users/me/hours-log',
    volunteerOnly,
    ah(async (req, res) => {
      const snap = await db.collection('hoursLog').where('volunteerId', '==', req.user.id).get();
      const rows = snap.docs.map((d) => ({ ...d.data(), id: d.id })).sort((a, b) => millis(b.completedAt) - millis(a.completedAt));
      // Add up the hours, rounded to 2 decimal places to avoid floating-point noise like 3.5000000001.
      const totalHours = Math.round(rows.reduce((sum, h) => sum + (h.hoursCompleted ?? 0), 0) * 100) / 100;
      res.json({ entries: rows.map((h) => hoursEntryResponse(h.id, h)), totalHours });
    })
  );

  return r;
}