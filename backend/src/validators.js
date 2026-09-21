// validators.js - checks everything the app sends us BEFORE it goes near the database.

import { badRequest } from './errors.js';
import { MAX_IMAGES, LISTING_TYPES, LANGUAGES, CAUSE_CATEGORIES, SKILLS } from './constants.js';

// True for a plain JSON object like { a: 1 } - not null, not an array, not text.
const isObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

// Express 5 leaves req.body undefined when there is no body.
export const bodyOf = (req) => (isObject(req.body) ? req.body : {});

// Text that must be present. Trims spaces, it rejects empty text and text over the length limit.
export function requireString(v, field, { max = 200 } = {}) {
  if (typeof v !== 'string') throw badRequest(`${field} is required and must be text.`, field);
  const s = v.trim();
  if (s.length === 0) throw badRequest(`${field} can't be empty.`, field);
  if (s.length > max) throw badRequest(`${field} must be ${max} characters or fewer.`, field);
  return s;
}

// Text that may be empty but must still be text and not too long.
export function optionalString(v, field, { max = 200 } = {}) {
  if (typeof v !== 'string') throw badRequest(`${field} must be text.`, field);
  const s = v.trim();
  if (s.length > max) throw badRequest(`${field} must be ${max} characters or fewer.`, field);
  return s;
}

// A real number within a range. Set integer: true for whole numbers only.
// Number.isFinite rejects NaN and Infinity.
export function requireNumber(v, field, { min = 0, max = 1000, integer = false } = {}) {
  if (typeof v !== 'number' || !Number.isFinite(v)) throw badRequest(`${field} must be a number.`, field);
  if (integer && !Number.isInteger(v)) throw badRequest(`${field} must be a whole number.`, field);
  if (v < min || v > max) throw badRequest(`${field} must be between ${min} and ${max}.`, field);
  return v;
}

// The value must be one of a fixed set, e.g. type must be 'Scheduled' or 'Ongoing'.
export function requireEnum(v, field, allowed) {
  if (!allowed.includes(v)) throw badRequest(`${field} must be one of: ${allowed.join(', ')}.`, field);
  return v;
}

// Strictly true or false.
export function requireBoolean(v, field) {
  if (typeof v !== 'boolean') throw badRequest(`${field} must be true or false.`, field);
  return v;
}

// allowed = null switches the fixed-list check off (ENFORCE_FIXED_LISTS=false).
// A list of text values, e.g. causeCategories. Trims each item, removes duplicates,
// enforces a minimum and maximum size, and (optionally) checks every item is on the fixed list.
export function requireStringList(v, field, { allowed = null, min = 0, max = 20 } = {}) {
  if (!Array.isArray(v)) throw badRequest(`${field} must be a list.`, field);
  const items = [
    // A Set keeps each value only once, so ['Animals', 'Animals'] becomes ['Animals'].
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
    // The error message lists the allowed values, which makes a mismatch with the app's wording easy to spot.
    const bad = items.find((i) => !allowed.includes(i));
    if (bad !== undefined) {
      throw badRequest(`Unknown value '${bad}' in ${field}. Allowed: ${allowed.join(', ')}.`, field);
    }
  }
  return items;
}

// A date-time written like "2026-10-17T14:00:00Z". The first check makes sure it LOOKS like ISO-8601,
// because new Date() would otherwise accept odd inputs (e.g. "1"). The second check catches impossible
// dates such as month 13. Returns a JavaScript Date.
export function requireIsoDate(v, field) {
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}T/.test(v)) {
    throw badRequest(`${field} must be an ISO-8601 date-time, e.g. 2026-10-17T14:00:00Z.`, field);
  }
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) throw badRequest(`${field} is not a valid date-time.`, field);
  return d;
}

// A list of image URLs: at most MAX_IMAGES, and every one must be a valid https:// address.
// (Images live in Firebase Storage; the API only stores their URLs.)
export function requireImageList(v, field) {
  if (!Array.isArray(v)) throw badRequest(`${field} must be a list.`, field);
  if (v.length > MAX_IMAGES) throw badRequest(`A listing can have at most ${MAX_IMAGES} images.`, field);
  return v.map((u) => {
    let ok = typeof u === 'string';
    if (ok) {
      try {
        // new URL() throws if the text isn't a valid web address.
        ok = new URL(u).protocol === 'https:';
      } catch {
        ok = false;
      }
    }
    if (!ok) throw badRequest(`${field} can only contain https image URLs.`, field);
    return u;
  });
}

// The fixed lists to check against, or null when the team has switched enforcement off.
const causeList = (cfg) => (cfg.enforceFixedLists ? CAUSE_CATEGORIES : null);
const skillList = (cfg) => (cfg.enforceFixedLists ? SKILLS : null);

// ---------------------------------------------------------------- listings
// POST /listings: every required field must be present and valid.
export function parseListingCreate(body, cfg) {
  const type = requireEnum(body.type, 'type', LISTING_TYPES);
  const data = {
    title: requireString(body.title, 'title', { max: 120 }),
    description: requireString(body.description, 'description', { max: 2000 }),
    // At least one cause is needed, otherwise the listing could never match a volunteer's interests.
    causeCategories: requireStringList(body.causeCategories, 'causeCategories', { allowed: causeList(cfg), min: 1 }),
    // Skills are optional; `?? []` means "an empty list if the app didn't send any".
    skillsRequired: requireStringList(body.skillsRequired ?? [], 'skillsRequired', { allowed: skillList(cfg) }),
    type,
    // Start as null. Only Scheduled listings get a date and a space limit (filled in below).
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

// Any subset of the listing fields. `existing` is the stored listing (Firestore types).
// PUT /listings/{id}: the app may send only the fields it changed. Each `'x' in body` check means
// "was this field sent?" - fields that weren't sent are left exactly as they are.
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

  // The final type is either the new one from the request or the one already stored.
  // The date and space limit depend on that final type, so they are handled together below.
  const type = 'type' in body ? requireEnum(body.type, 'type', LISTING_TYPES) : existing.type;
  patch.type = type;
  if (type === 'Scheduled') {
    // Use the new value if sent, otherwise keep the stored one. (existing.dateTime is a Firestore
    // Timestamp, so .toDate() turns it into a normal Date.)
    const dateTime = 'dateTime' in body ? requireIsoDate(body.dateTime, 'dateTime') : existing.dateTime?.toDate?.() ?? null;
    const spacesTotal =
      'spacesTotal' in body
        ? requireNumber(body.spacesTotal, 'spacesTotal', { min: 1, max: 10000, integer: true })
        : existing.spacesTotal ?? null;
    // Switching an Ongoing listing to Scheduled must supply both, since Ongoing ones have neither.
    if (!dateTime) throw badRequest('dateTime is required for Scheduled listings.', 'dateTime');
    if (spacesTotal == null) throw badRequest('spacesTotal is required for Scheduled listings.', 'spacesTotal');
    patch.dateTime = dateTime;
    patch.spacesTotal = spacesTotal;
  } else {
    // Ongoing listings have no date and no limit, so switching to Ongoing clears both.
    patch.dateTime = null;
    patch.spacesTotal = null;
  }
  return patch;
}

// ---------------------------------------------------------------- profile & settings
// Which profile fields each role may change. Volunteers have interests/skills/bio; organizations have a description.
const PROFILE_FIELDS = {
  volunteer: ['name', 'profileImageUrl', 'bio', 'interests', 'skills'],
  organization: ['name', 'description', 'profileImageUrl'],
};

// PUT /users/me. Anything not on the role's list is rejected (not silently ignored),
// so a mistake in the app shows up straight away instead of being hidden.
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
    // null is allowed and means "remove my profile picture".
    patch.profileImageUrl = body.profileImageUrl === null ? null : requireImageList([body.profileImageUrl], 'profileImageUrl')[0];
  }
  if ('bio' in body) patch.bio = optionalString(body.bio, 'bio', { max: 500 });
  if ('description' in body) patch.description = optionalString(body.description, 'description', { max: 1000 });
  // A volunteer's interests are causes, so they are checked against the same list as a listing's causeCategories.
  if ('interests' in body) patch.interests = requireStringList(body.interests, 'interests', { allowed: causeList(cfg) });
  if ('skills' in body) patch.skills = requireStringList(body.skills, 'skills', { allowed: skillList(cfg) });
  return patch;
}

// PUT /users/me/settings: language and/or notifications switch. At least one must be sent.
export function parseSettings(body) {
  const patch = {};
  if ('preferredLanguage' in body) patch.preferredLanguage = requireEnum(body.preferredLanguage, 'preferredLanguage', LANGUAGES);
  if ('notificationsEnabled' in body) patch.notificationsEnabled = requireBoolean(body.notificationsEnabled, 'notificationsEnabled');
  if (Object.keys(patch).length === 0) {
    throw badRequest('Send preferredLanguage and/or notificationsEnabled.', 'preferredLanguage');
  }
  return patch;
}

// Hours are optional on "mark complete"; undefined means "use the default".
// The caller then falls back to the previous hours, or the listing's minHours.
export function parseHours(body) {
  if (!('hoursCompleted' in body) || body.hoursCompleted === null) return undefined;
  return requireNumber(body.hoursCompleted, 'hoursCompleted', { min: 0, max: 1000 });
}