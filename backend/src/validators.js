import { badRequest } from './errors.js';
import { MAX_IMAGES, LISTING_TYPES, LANGUAGES, CAUSE_CATEGORIES, SKILLS, SYNC_ACTIONS, MAX_SYNC_CHANGES } from './constants.js';

const isObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/** Express 5 leaves req.body undefined when there is no body, so always go through this. */
export const bodyOf = (req) => (isObject(req.body) ? req.body : {});

export function requireString(v, field, { max = 200 } = {}) {
  if (typeof v !== 'string') throw badRequest(`${field} is required and must be text.`, field);
  const s = v.trim();
  if (s.length === 0) throw badRequest(`${field} can't be empty.`, field);
  if (s.length > max) throw badRequest(`${field} must be ${max} characters or fewer.`, field);
  return s;
}

export function optionalString(v, field, { max = 200 } = {}) {
  if (typeof v !== 'string') throw badRequest(`${field} must be text.`, field);
  const s = v.trim();
  if (s.length > max) throw badRequest(`${field} must be ${max} characters or fewer.`, field);
  return s;
}

export function requireNumber(v, field, { min = 0, max = 1000, integer = false } = {}) {
  // A missing value gets its own clear message - never a silent default.
  if (v === undefined || v === null || v === '') throw badRequest(`${field} is required.`, field);
  if (typeof v !== 'number' || !Number.isFinite(v)) throw badRequest(`${field} must be a number.`, field);
  if (integer && !Number.isInteger(v)) throw badRequest(`${field} must be a whole number.`, field);
  if (v < min || v > max) throw badRequest(`${field} must be between ${min} and ${max}.`, field);
  return v;
}

export function requireEnum(v, field, allowed) {
  if (!allowed.includes(v)) throw badRequest(`${field} must be one of: ${allowed.join(', ')}.`, field);
  return v;
}

export function requireBoolean(v, field) {
  if (typeof v !== 'boolean') throw badRequest(`${field} must be true or false.`, field);
  return v;
}

/** allowed = null switches the fixed-list check off (ENFORCE_FIXED_LISTS=false). */
export function requireStringList(v, field, { allowed = null, min = 0, max = 20 } = {}) {
  if (!Array.isArray(v)) throw badRequest(`${field} must be a list.`, field);
  const items = [
    ...new Set(
      v.map((x) => {
        if (typeof x !== 'string' || x.trim() === '') throw badRequest(`${field} can only contain non-empty text.`, field);
        return x.trim();
      })
    ),
  ];
  if (items.length < min) throw badRequest(`${field} needs at least ${min} value${min === 1 ? '' : 's'}.`, field);
  if (items.length > max) throw badRequest(`${field} can have at most ${max} values.`, field);
  if (allowed) {
    const bad = items.find((i) => !allowed.includes(i));
    if (bad !== undefined) {
      throw badRequest(`Unknown value '${bad}' in ${field}. Allowed: ${allowed.join(', ')}.`, field);
    }
  }
  return items;
}

export function requireIsoDate(v, field) {
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}T/.test(v)) {
    throw badRequest(`${field} must be an ISO-8601 date-time, e.g. 2026-10-17T14:00:00Z.`, field);
  }
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) throw badRequest(`${field} is not a valid date-time.`, field);
  return d;
}

export function requireImageList(v, field) {
  if (!Array.isArray(v)) throw badRequest(`${field} must be a list.`, field);
  if (v.length > MAX_IMAGES) throw badRequest(`A listing can have at most ${MAX_IMAGES} images.`, field);
  return v.map((u) => {
    let ok = typeof u === 'string';
    if (ok) {
      try {
        ok = new URL(u).protocol === 'https:';
      } catch {
        ok = false;
      }
    }
    if (!ok) throw badRequest(`${field} can only contain https image URLs.`, field);
    return u;
  });
}

const causeList = (cfg) => (cfg.enforceFixedLists ? CAUSE_CATEGORIES : null);
const skillList = (cfg) => (cfg.enforceFixedLists ? SKILLS : null);

// ---------------------------------------------------------------- listings
export function parseListingCreate(body, cfg) {
  const type = requireEnum(body.type, 'type', LISTING_TYPES);
  const data = {
    title: requireString(body.title, 'title', { max: 120 }),
    description: requireString(body.description, 'description', { max: 2000 }),
    causeCategories: requireStringList(body.causeCategories, 'causeCategories', { allowed: causeList(cfg), min: 1 }),
    skillsRequired: requireStringList(body.skillsRequired ?? [], 'skillsRequired', { allowed: skillList(cfg) }),
    type,
    dateTime: null,
    spacesTotal: null,
    area: requireString(body.area, 'area', { max: 200 }),
    minHours: requireNumber(body.minHours, 'minHours', { min: 0, max: 1000 }),
    images: requireImageList(body.images ?? [], 'images'),
  };
  if (type === 'Scheduled') {
    data.dateTime = requireIsoDate(body.dateTime, 'dateTime');
    data.spacesTotal = requireNumber(body.spacesTotal, 'spacesTotal', { min: 1, max: 10000, integer: true });
  }
  return data; // Ongoing listings ignore dateTime / spacesTotal: "Ongoing has neither".
}

/** Any subset of the listing fields. `existing` is the stored listing (Firestore types). */
export function parseListingUpdate(body, existing, cfg) {
  const patch = {};
  if ('title' in body) patch.title = requireString(body.title, 'title', { max: 120 });
  if ('description' in body) patch.description = requireString(body.description, 'description', { max: 2000 });
  if ('causeCategories' in body) {
    patch.causeCategories = requireStringList(body.causeCategories, 'causeCategories', { allowed: causeList(cfg), min: 1 });
  }
  if ('skillsRequired' in body) {
    patch.skillsRequired = requireStringList(body.skillsRequired, 'skillsRequired', { allowed: skillList(cfg) });
  }
  if ('area' in body) patch.area = requireString(body.area, 'area', { max: 200 });
  if ('minHours' in body) patch.minHours = requireNumber(body.minHours, 'minHours', { min: 0, max: 1000 });
  if ('images' in body) patch.images = requireImageList(body.images, 'images');

  const type = 'type' in body ? requireEnum(body.type, 'type', LISTING_TYPES) : existing.type;
  patch.type = type;
  if (type === 'Scheduled') {
    const dateTime = 'dateTime' in body ? requireIsoDate(body.dateTime, 'dateTime') : existing.dateTime?.toDate?.() ?? null;
    const spacesTotal =
      'spacesTotal' in body
        ? requireNumber(body.spacesTotal, 'spacesTotal', { min: 1, max: 10000, integer: true })
        : existing.spacesTotal ?? null;
    if (!dateTime) throw badRequest('dateTime is required for Scheduled listings.', 'dateTime');
    if (spacesTotal == null) throw badRequest('spacesTotal is required for Scheduled listings.', 'spacesTotal');
    patch.dateTime = dateTime;
    patch.spacesTotal = spacesTotal;
  } else {
    patch.dateTime = null;
    patch.spacesTotal = null;
  }
  return patch;
}

// ---------------------------------------------------------------- profile & settings
const PROFILE_FIELDS = {
  volunteer: ['name', 'profileImageUrl', 'bannerImageUrl', 'bio', 'interests', 'skills'],
  organization: ['name', 'description', 'profileImageUrl', 'bannerImageUrl'],
};

export function parseProfileUpdate(body, role, cfg) {
  const allowed = PROFILE_FIELDS[role];
  for (const key of Object.keys(body)) {
    if (!allowed.includes(key)) {
      throw badRequest(`'${key}' can't be changed on a ${role} profile. Allowed: ${allowed.join(', ')}.`, key);
    }
  }
  const patch = {};
  if ('name' in body) patch.name = requireString(body.name, 'name', { max: 100 });
  if ('profileImageUrl' in body) {
    patch.profileImageUrl = body.profileImageUrl === null ? null : requireImageList([body.profileImageUrl], 'profileImageUrl')[0];
  }
  if ('bannerImageUrl' in body) {
    patch.bannerImageUrl = body.bannerImageUrl === null ? null : requireImageList([body.bannerImageUrl], 'bannerImageUrl')[0];
  }
  if ('bio' in body) patch.bio = optionalString(body.bio, 'bio', { max: 500 });
  if ('description' in body) patch.description = optionalString(body.description, 'description', { max: 1000 });
  if ('interests' in body) patch.interests = requireStringList(body.interests, 'interests', { allowed: causeList(cfg) });
  if ('skills' in body) patch.skills = requireStringList(body.skills, 'skills', { allowed: skillList(cfg) });
  return patch;
}

export function parseSettings(body) {
  const patch = {};
  if ('preferredLanguage' in body) patch.preferredLanguage = requireEnum(body.preferredLanguage, 'preferredLanguage', LANGUAGES);
  if ('notificationsEnabled' in body) patch.notificationsEnabled = requireBoolean(body.notificationsEnabled, 'notificationsEnabled');
  if ('darkMode' in body) patch.darkMode = requireBoolean(body.darkMode, 'darkMode');
  if (Object.keys(patch).length === 0) {
    throw badRequest('Send preferredLanguage, notificationsEnabled and/or darkMode.', 'preferredLanguage');
  }
  return patch;
}

// ---------------------------------------------------------------- offline sync
const SYNC_ID = /^[A-Za-z0-9_-]{8,64}$/;

/**
 * Body of POST /sync/signups: { changes: [{ id, action: 'signup'|'cancel', listingId?, updatedAt }] }.
 * `id` is the app's own UUID for the sign-up (its Room row), for both actions. Returns the changes
 * with updatedAt as a Date. Errors name the exact field, e.g. "changes[2].listingId".
 */
export function parseSyncChanges(body) {
  if (!Array.isArray(body.changes)) throw badRequest('changes must be a list.', 'changes');
  if (body.changes.length === 0) throw badRequest('changes needs at least one change.', 'changes');
  if (body.changes.length > MAX_SYNC_CHANGES) {
    throw badRequest(`Send at most ${MAX_SYNC_CHANGES} changes per request.`, 'changes');
  }
  return body.changes.map((c, i) => {
    const field = (name) => `changes[${i}].${name}`;
    if (!isObject(c)) throw badRequest(`changes[${i}] must be an object.`, `changes[${i}]`);
    if (typeof c.id !== 'string' || !SYNC_ID.test(c.id)) {
      throw badRequest(`${field('id')} must be 8-64 letters, digits, '-' or '_' (use the sign-up's UUID).`, field('id'));
    }
    const action = requireEnum(c.action, field('action'), SYNC_ACTIONS);
    const change = { id: c.id, action, updatedAt: requireIsoDate(c.updatedAt, field('updatedAt')) };
    if (action === 'signup') change.listingId = requireString(c.listingId, field('listingId'), { max: 200 });
    return change;
  });
}

/** Hours are optional on "mark complete"; undefined means "use the default". */
export function parseHours(body) {
  if (!('hoursCompleted' in body) || body.hoursCompleted === null) return undefined;
  return requireNumber(body.hoursCompleted, 'hoursCompleted', { min: 0, max: 1000 });
}
// A chat message: plain text, trimmed, 1-1000 characters.
export function parseMessage(body) {
  return { text: requireString(body.text, 'text', { max: 1000 }) };
}

// Optional ?since=<ISO date> on the message list, so the app can ask only for what's new.
export function parseSince(v) {
  if (v === undefined) return null;
  return requireIsoDate(v, 'since');
}
