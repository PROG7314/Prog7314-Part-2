// routes/listings.js - volunteer opportunities.

// Organizations create, edit and delete their own listings; volunteers browse and filter them.
// All the searching and filtering happens here on the server, not on the phone.
import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { requireRole } from '../middleware/auth.js';
import { badRequest, forbidden, notFound } from '../errors.js';
import { LISTING_TYPES } from '../constants.js';
import { bodyOf, parseListingCreate, parseListingUpdate } from '../validators.js';
import { listingResponse } from '../serialize.js';
import { chunked, splitList, loadUserNames, countActiveSignups, isEnded, newestFirst } from '../lookups.js';

/** Listings. */
export function listingsRouter({ db, cfg }) {
  const r = Router();
  const anyRole = requireRole('volunteer', 'organization');
  const orgOnly = requireRole('organization');

  // An organization may only change listings it owns. Compares the listing's owner with the caller.
  const assertOwner = (listing, user) => {
    if (listing.organizationId !== user.id) throw forbidden('You can only change your own listings.');
  };

  // Builds the full JSON for one listing, adding the organization's name and the live sign-up count.
  const presentOne = async (id, listing) => {
    const [names, counts] = await Promise.all([
      loadUserNames(db, [listing.organizationId]),
      countActiveSignups(db, [id]),
    ]);
    return listingResponse(id, listing, {
      organizationName: names.get(listing.organizationId) ?? null,
      signupCount: counts.get(id) ?? 0,
    });
  };

  // Fetches one listing or throws a 404. Returns both its reference (for updates/deletes) and its data.
  const loadListing = async (id) => {
    const snap = await db.collection('listings').doc(id).get();
    if (!snap.exists) throw notFound('Listing not found.');
    return { ref: snap.ref, listing: snap.data() };
  };

  /**
   * GET /listings?cause=&skill=&type=&area=
   * Filtering happens here, not on the phone. Within one parameter values are OR-ed
   * (cause=Animals,Children); across parameters they are AND-ed.
   * Role-aware: volunteers get listings that haven't ended; an organization gets its OWN listings (ended ones included).
   */
  r.get(
    '/',
    anyRole,
    ah(async (req, res) => {
      const q = req.query;
      if (q.type !== undefined && !LISTING_TYPES.includes(q.type)) {
        throw badRequest(`type must be one of: ${LISTING_TYPES.join(', ')}.`, 'type');
      }
      // Lower-case everything so matching ignores capital letters ("animals" matches "Animals").
      const causes = splitList(q.cause).map((s) => s.toLowerCase());
      const skills = splitList(q.skill).map((s) => s.toLowerCase());
      const area = typeof q.area === 'string' ? q.area.trim().toLowerCase() : '';
      // True when no filter was given, or when the listing has at least one of the wanted values.
      const overlaps = (have, wanted) =>
        wanted.length === 0 || (have ?? []).some((h) => wanted.includes(String(h).toLowerCase()));

      // Organizations only ever see their own listings, so Org Home needs no extra query parameter.
      let query = db.collection('listings');
      if (req.user.role === 'organization') query = query.where('organizationId', '==', req.user.id);
      const snap = await query.get();

      const now = Date.now();
      let listings = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
      // Volunteers shouldn't be offered events that already happened.
      if (req.user.role === 'volunteer') listings = listings.filter((l) => !isEnded(l, now));
      // Keep only listings that pass EVERY filter that was supplied (AND across parameters),
      // newest first, and cap the response at 100 so it stays small.
      listings = listings
        .filter(
          (l) =>
            (!q.type || l.type === q.type) &&
            overlaps(l.causeCategories, causes) &&
            overlaps(l.skillsRequired, skills) &&
            (!area || (l.area ?? '').toLowerCase().includes(area))
        )
        .sort(newestFirst)
        .slice(0, 100);

      // Two batched lookups for the whole page instead of one per listing: organization names and sign-up counts.
      const [names, counts] = await Promise.all([
        loadUserNames(db, listings.map((l) => l.organizationId)),
        countActiveSignups(db, listings.map((l) => l.id)),
      ]);
      res.json({
        items: listings.map((l) =>
          listingResponse(l.id, l, {
            organizationName: names.get(l.organizationId) ?? null,
            signupCount: counts.get(l.id) ?? 0,
          })
        ),
      });
    })
  );

  // GET /listings/{id} - one listing (either role).
  r.get(
    '/:id',
    anyRole,
    ah(async (req, res) => {
      const { listing } = await loadListing(req.params.id);
      res.json(await presentOne(req.params.id, listing));
    })
  );

  // POST /listings - an organization creates a listing. Replies 201 (Created) with the new listing.
  r.post(
    '/',
    orgOnly,
    ah(async (req, res) => {
      const data = parseListingCreate(bodyOf(req), cfg); // validates everything first
      const ref = db.collection('listings').doc(); // .doc() with no id generates a new unique id
      const now = Timestamp.now();
      const doc = {
        id: ref.id,
        ...data,
        // Store the date as a Firestore Timestamp (or null for Ongoing listings).
        dateTime: data.dateTime ? Timestamp.fromDate(data.dateTime) : null,
        organizationId: req.user.id, // always the caller - never taken from the request body
        createdAt: now,
        updatedAt: now,
      };
      await ref.set(doc);
      res.status(201).json(listingResponse(ref.id, doc, { organizationName: req.user.name || null, signupCount: 0 }));
    })
  );

  // PUT /listings/{id} - the owning organization edits some or all of a listing's fields.
  r.put(
    '/:id',
    orgOnly,
    ah(async (req, res) => {
      const { ref, listing } = await loadListing(req.params.id);
      assertOwner(listing, req.user);

      const patch = parseListingUpdate(bodyOf(req), listing, cfg);
      // Don't let an organization shrink the space limit below the number of people already signed up.
      if (patch.type === 'Scheduled') {
        const active = (await countActiveSignups(db, [req.params.id])).get(req.params.id) ?? 0;
        if (patch.spacesTotal < active) {
          throw badRequest(`spacesTotal can't be lower than the ${active} people already signed up.`, 'spacesTotal');
        }
      }
      const update = {
        ...patch,
        dateTime: patch.dateTime ? Timestamp.fromDate(patch.dateTime) : null,
        updatedAt: Timestamp.now(),
      };
      await ref.update(update);
      // Answer with the merged result instead of reading the document back from the database.
      res.json(await presentOne(req.params.id, { ...listing, ...update }));
    })
  );

  /** A listing that's no longer wanted is simply deleted. Anyone still confirmed on it is marked cancelled so they aren't left hanging. */
  // DELETE /listings/{id}
  r.delete(
    '/:id',
    orgOnly,
    ah(async (req, res) => {
      const { ref, listing } = await loadListing(req.params.id);
      assertOwner(listing, req.user);

      const signups = await db.collection('signups').where('listingId', '==', req.params.id).get();
      const confirmed = signups.docs.filter((d) => d.data().status === 'confirmed');
      // A batch applies many updates together. Firestore limits a batch to 500 operations,
      // so the cancellations are done in groups of 400. Completed sign-ups are left alone, so earned hours are kept.
      for (const group of chunked(confirmed, 400)) {
        const batch = db.batch();
        group.forEach((d) => batch.update(d.ref, { status: 'cancelled' }));
        await batch.commit();
      }
      await ref.delete();
      res.status(204).end();
    })
  );

  return r;
}