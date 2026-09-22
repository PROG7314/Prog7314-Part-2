// lookups.js - shared helpers that several routes use: batched database reads, filtering and sorting.

import { ACTIVE_SIGNUP_STATUSES } from './constants.js';

// Splits a list into groups of `size`: chunked([1,2,3,4,5], 2) -> [[1,2],[3,4],[5]].
// Firestore limits how many items one query or batch can hold, so big jobs are done in groups.
export function chunked(items, size) {
  const out = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}

/** Comma-separated (?cause=a,b) and repeated (?cause=a&cause=b) query values both work. */
// Turns a query-string value into a clean list: "Animals, Children" -> ['Animals', 'Children'].
// [].concat(v) makes a single value and a repeated value (already an array) look the same.
export function splitList(v) {
  if (v === undefined) return [];
  return [].concat(v).flatMap((x) => String(x).split(',')).map((s) => s.trim()).filter(Boolean);
}

/** userId -> display name, in one batched read. */
// Used to attach organization names to listings and volunteer names to sign-ups, without
// reading each user document one at a time.
export async function loadUserNames(db, ids) {
  const unique = [...new Set(ids.filter(Boolean))]; // remove blanks and duplicates
  if (unique.length === 0) return new Map();
  // db.getAll reads many documents in a single round trip.
  const snaps = await db.getAll(...unique.map((id) => db.collection('users').doc(id)));
  return new Map(snaps.filter((s) => s.exists).map((s) => [s.id, s.data().name || null]));
}

/** listingId -> number of sign-ups that still occupy a space (confirmed + completed; cancelled frees it). */
// There is no stored counter; the number is worked out from the signups collection each time,
// so it can never drift out of sync.
export async function countActiveSignups(db, listingIds) {
  const counts = new Map(listingIds.map((id) => [id, 0])); // every listing starts at 0
  // Firestore's 'in' filter accepts only a limited number of values, so we ask in groups of 10.
  for (const group of chunked(listingIds, 10)) {
    if (group.length === 0) continue;
    const snap = await db.collection('signups').where('listingId', 'in', group).get();
    for (const d of snap.docs) {
      const s = d.data();
      // Only confirmed and completed sign-ups take up a space; cancelled ones don't.
      if (ACTIVE_SIGNUP_STATUSES.includes(s.status)) counts.set(s.listingId, (counts.get(s.listingId) ?? 0) + 1);
    }
  }
  return counts;
}

/** A Scheduled listing whose date has passed. Ongoing listings never end. */
// listing.dateTime is a Firestore Timestamp, so .toMillis() converts it to a number we can compare.
export function isEnded(listing, nowMs = Date.now()) {
  return listing.type === 'Scheduled' && !!listing.dateTime && listing.dateTime.toMillis() < nowMs;
}

// Sort comparison for newest listings first. A missing createdAt counts as 0 (oldest).
export const newestFirst = (a, b) => (b.createdAt?.toMillis?.() ?? 0) - (a.createdAt?.toMillis?.() ?? 0);