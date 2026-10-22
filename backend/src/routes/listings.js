  /**
   * GET /listings?cause=&skill=&type=&area=
   * Filtering happens here, not on the phone.
   * - type and area are hard filters: both must match when given.
   * - cause and skill are an INTEREST match, not a hard filter: within each, values are OR-ed
   *   (cause=Animals,Children matches either one); ACROSS cause and skill they are also OR-ed -
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