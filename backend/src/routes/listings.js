import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { requireRole } from '../middleware/auth.js';
import { badRequest, forbidden, notFound } from '../errors.js';
import { LISTING_TYPES } from '../constants.js';
import { bodyOf, parseListingCreate, parseListingUpdate } from '../validators.js';
import { listingResponse } from '../serialize.js';
import { chunked, splitList, loadUserNames, countActiveSignups, isEnded, newestFirst } from '../lookups.js';

/** Table 3 - Listings. */
export function listingsRouter({ db, cfg }) {
  const r = Router();
  const anyRole = requireRole('volunteer', 'organization');
  const orgOnly = requireRole('organization');

  const assertOwner = (listing, user) => {
    if (listing.organizationId !== user.id) throw forbidden('You can only change your own listings.');
  };

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

  const loadListing = async (id) => {
    const snap = await db.collection('listings').doc(id).get();
    if (!snap.exists) throw notFound('Listing not found.');
    return { ref: snap.ref, listing: snap.data() };
  };

  /**
   * GET /listings?cause=&skill=&type=&area=
   * Filtering happens here, not on the phone.
   * - type and area are hard filters: both must match when given.
   * - cause and skill are an INTEREST match, not a hard filter: within each, values are OR-ed
   *   (cause=Animal Welfare,Children & Youth matches either one); ACROSS cause and skill they are also OR-ed -
   *   a listing counts as a match if it overlaps on causes OR on skills, not both. Requiring
   *   both (the old behaviour) regularly left a volunteer who'd set both interests and skills
   *   with an empty feed, since no single listing needs every one of their causes AND every one
   *   of their skills. Each returned listing also carries matchedCauses/matchedSkills so the app
   *   can show the volunteer why it matched, instead of just dropping it in the feed unexplained.
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
      const causes = splitList(q.cause).map((s) => s.toLowerCase());
      const skills = splitList(q.skill).map((s) => s.toLowerCase());
      const area = typeof q.area === 'string' ? q.area.trim().toLowerCase() : '';

      // The listing's own values that intersect with what was asked for (case-insensitive) -
      // used both to decide a match and to tell the caller WHY a listing matched.
      const matching = (have, wanted) => (have ?? []).filter((h) => wanted.includes(String(h).toLowerCase()));

      // No cause/skill filter at all -> every listing passes this check (type/area still apply
      // below). Otherwise a listing matches if it overlaps on EITHER causes or skills.
      const hasInterestFilter = causes.length > 0 || skills.length > 0;
      const matchesInterest = (l) =>
        !hasInterestFilter ||
        matching(l.causeCategories, causes).length > 0 ||
        matching(l.skillsRequired, skills).length > 0;

      let query = db.collection('listings');
      if (req.user.role === 'organization') query = query.where('organizationId', '==', req.user.id);
      const snap = await query.get();

      const now = Date.now();
      let listings = snap.docs.map((d) => ({ ...d.data(), id: d.id }));
      if (req.user.role === 'volunteer') listings = listings.filter((l) => !isEnded(l, now));
      listings = listings
        .filter(
          (l) =>
            (!q.type || l.type === q.type) &&
            matchesInterest(l) &&
            (!area || (l.area ?? '').toLowerCase().includes(area))
        )
        .sort(newestFirst)
        .slice(0, 100);

      const [names, counts] = await Promise.all([
        loadUserNames(db, listings.map((l) => l.organizationId)),
        countActiveSignups(db, listings.map((l) => l.id)),
      ]);
      res.json({
        items: listings.map((l) =>
          listingResponse(l.id, l, {
            organizationName: names.get(l.organizationId) ?? null,
            signupCount: counts.get(l.id) ?? 0,
            // null = no interest filter was sent, nothing to explain. [] (empty array) is a
            // real possibility too: it means the match came from the OTHER list, e.g. a listing
            // that matched on skills but shares none of the volunteer's chosen causes.
            matchedCauses: hasInterestFilter ? matching(l.causeCategories, causes) : null,
            matchedSkills: hasInterestFilter ? matching(l.skillsRequired, skills) : null,
          })
        ),
      });
    })
  );

  r.get(
    '/:id',
    anyRole,
    ah(async (req, res) => {
      const { listing } = await loadListing(req.params.id);
      res.json(await presentOne(req.params.id, listing));
    })
  );

  r.post(
    '/',
    orgOnly,
    ah(async (req, res) => {
      const data = parseListingCreate(bodyOf(req), cfg);
      const ref = db.collection('listings').doc();
      const now = Timestamp.now();
      const doc = {
        id: ref.id,
        ...data,
        dateTime: data.dateTime ? Timestamp.fromDate(data.dateTime) : null,
        organizationId: req.user.id, // always the caller - never taken from the request body
        createdAt: now,
        updatedAt: now,
      };
      await ref.set(doc);
      res.status(201).json(listingResponse(ref.id, doc, { organizationName: req.user.name || null, signupCount: 0 }));
    })
  );

  r.put(
    '/:id',
    orgOnly,
    ah(async (req, res) => {
      const { ref, listing } = await loadListing(req.params.id);
      assertOwner(listing, req.user);

      const patch = parseListingUpdate(bodyOf(req), listing, cfg);
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
      res.json(await presentOne(req.params.id, { ...listing, ...update }));
    })
  );

  /** A listing that's no longer wanted is simply deleted. Anyone still confirmed on it is marked cancelled so they aren't left hanging. */
  r.delete(
    '/:id',
    orgOnly,
    ah(async (req, res) => {
      const { ref, listing } = await loadListing(req.params.id);
      assertOwner(listing, req.user);

      const signups = await db.collection('signups').where('listingId', '==', req.params.id).get();
      const confirmed = signups.docs.filter((d) => d.data().status === 'confirmed');
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