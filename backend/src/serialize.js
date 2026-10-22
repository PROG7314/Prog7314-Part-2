// serialize.js - turns database documents into the JSON the Android app expects.

// Firestore Timestamp -> "2026-10-17T14:00:00.000Z". Returns null if there is no date.
// (`typeof ts.toDate === 'function'` checks it really is a Timestamp.)
export const iso = (ts) => (ts && typeof ts.toDate === 'function' ? ts.toDate().toISOString() : null);

// The user's profile. Private fields such as fcmToken and firebaseUid are deliberately left out.
export function profileResponse(u) {
  const base = {
    id: u.id,
    email: u.email ?? null,
    role: u.role ?? null,
    name: u.name ?? '',
    profileImageUrl: u.profileImageUrl ?? null,
    preferredLanguage: u.preferredLanguage ?? 'en',
    notificationsEnabled: u.notificationsEnabled ?? true,
  };
  // "Full profile (fields vary by role)": each role only sees its own extra fields.
  if (u.role === 'volunteer') {
    return { ...base, bio: u.bio ?? '', interests: u.interests ?? [], skills: u.skills ?? [] };
  }
  if (u.role === 'organization') {
    return { ...base, description: u.description ?? '' };
  }
  return base; // no role chosen yet
}

/**
 * signupCount / spacesRemaining are computed at query time from active sign-ups (there is no
 * stored counter), so callers pass signupCount in. null = "not calculated for this response".
 */
export function listingResponse(id, l, { organizationName = null, signupCount = null } = {}) {
  const scheduled = l.type === 'Scheduled';
  return {
    id,
    organizationId: l.organizationId,
    // Looked up from the organization's profile by the caller; not stored on the listing itself.
    organizationName,
    title: l.title,
    description: l.description,
    causeCategories: l.causeCategories ?? [],
    skillsRequired: l.skillsRequired ?? [],
    type: l.type,
    dateTime: iso(l.dateTime),
    // Only Scheduled listings have a space limit.
    spacesTotal: scheduled ? l.spacesTotal ?? null : null,
    // Spaces left = total minus people signed up, never below zero. null for Ongoing listings,
    // or when the caller didn't work out the count.
    spacesRemaining:
      scheduled && signupCount != null ? Math.max(0, (l.spacesTotal ?? 0) - signupCount) : null,
    signupCount,
    area: l.area,
    minHours: l.minHours,
    images: l.images ?? [],
    createdAt: iso(l.createdAt),
    updatedAt: iso(l.updatedAt),
  };
}

// One sign-up. `extra` lets a route attach more fields, e.g. the volunteer's name (organization view)
// or the listing summary (volunteer's commitments view).
export function signupResponse(id, s, extra = {}) {
  return {
    id,
    listingId: s.listingId,
    volunteerId: s.volunteerId,
    status: s.status,
    hoursCompleted: s.hoursCompleted ?? null,
    signedUpAt: iso(s.signedUpAt),
    completedAt: iso(s.completedAt),
    ...extra,
  };
}

// One row of the volunteer's hours history.
export function hoursEntryResponse(id, h) {
  return {
    id,
    volunteerId: h.volunteerId,
    listingId: h.listingId,
    organizationId: h.organizationId,
    listingTitle: h.listingTitle,
    hoursCompleted: h.hoursCompleted,
    completedAt: iso(h.completedAt),
  };
}

// Accepts and returns the match information
export function listingResponse(
  id,
  l,
  { organizationName = null, signupCount = null, matchedCauses = null, matchedSkills = null } = {}
) {
  const scheduled = l.type === 'Scheduled';
  return {
    id,
    organizationId: l.organizationId,
    organizationName,
    title: l.title,
    description: l.description,
    causeCategories: l.causeCategories ?? [],
    skillsRequired: l.skillsRequired ?? [],
    type: l.type,
    dateTime: iso(l.dateTime),
    spacesTotal: scheduled ? l.spacesTotal ?? null : null,
    spacesRemaining:
      scheduled && signupCount != null ? Math.max(0, (l.spacesTotal ?? 0) - signupCount) : null,
    signupCount,
    area: l.area,
    minHours: l.minHours,
    images: l.images ?? [],
    // Only set on the GET /listings feed, and only when a cause/skill filter was sent - null
    // elsewhere (a single listing fetch, create, update) because there's nothing to explain there.
    matchedCauses,
    matchedSkills,
    createdAt: iso(l.createdAt),
    updatedAt: iso(l.updatedAt),
  };
}