/** Turns Firestore documents into the JSON shapes in the Part 1 spec (Timestamps -> ISO strings). */

export const iso = (ts) => (ts && typeof ts.toDate === 'function' ? ts.toDate().toISOString() : null);

export function profileResponse(u) {
  const base = {
    id: u.id,
    email: u.email ?? null,
    role: u.role ?? null,
    name: u.name ?? '',
    profileImageUrl: u.profileImageUrl ?? null,
    bannerImageUrl: u.bannerImageUrl ?? null,
    preferredLanguage: u.preferredLanguage ?? 'en',
    notificationsEnabled: u.notificationsEnabled ?? true,
    darkMode: u.darkMode ?? false,
  };
  // "Full profile (fields vary by role)"
  if (u.role === 'volunteer') {
    return { ...base, bio: u.bio ?? '', interests: u.interests ?? [], skills: u.skills ?? [] };
  }
  if (u.role === 'organization') {
    return { ...base, description: u.description ?? '' };
  }
  return base;
}

/**
 * signupCount / spacesRemaining are computed at query time from active sign-ups (there is no
 * stored counter), so callers pass signupCount in. null = "not calculated for this response".
 */
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
    translations: l.translations ?? {},
    // Only set on the GET /listings feed, and only when a cause/skill filter was sent - null
    // elsewhere (a single listing fetch, create, update) because there's nothing to explain there.
    matchedCauses,
    matchedSkills,
    createdAt: iso(l.createdAt),
    updatedAt: iso(l.updatedAt),
  };
}

export function signupResponse(id, s, extra = {}) {
  return {
    id,
    listingId: s.listingId,
    volunteerId: s.volunteerId,
    status: s.status,
    hoursCompleted: s.hoursCompleted ?? null,
    signedUpAt: iso(s.signedUpAt),
    completedAt: iso(s.completedAt),
    updatedAt: iso(s.updatedAt), // null on sign-ups made before this field existed
    ...extra,
  };
}

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
// One conversation as the caller sees it: `me` is the caller's id, so unreadCount is theirs and
// otherParty is whoever they are talking to.
export function conversationResponse(id, c, me, names = new Map()) {
  const otherId = c.organizationId === me ? c.volunteerId : c.organizationId;
  return {
    id,
    listingId: c.listingId,
    listingTitle: c.listingTitle,
    organizationId: c.organizationId,
    volunteerId: c.volunteerId,
    otherParty: { id: otherId, name: names.get(otherId) ?? null },
    lastMessage: c.lastMessage ?? null,
    lastMessageAt: iso(c.lastMessageAt),
    lastSenderId: c.lastSenderId ?? null,
    unreadCount: c.unread?.[me] ?? 0,
    createdAt: iso(c.createdAt),
  };
}

export function messageResponse(id, m) {
  return {
    id,
    conversationId: m.conversationId,
    senderId: m.senderId,
    text: m.text,
    sentAt: iso(m.sentAt),
  };
}
