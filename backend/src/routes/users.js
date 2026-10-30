// routes/users.js - the logged-in user's own profile and settings.

// Every route here acts on the caller (req.user), so there is no user id in the URL and nobody can
// read or edit anyone else's profile.
import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { requireRole } from '../middleware/auth.js';
import { bodyOf, parseProfileUpdate, parseSettings } from '../validators.js';
import { profileResponse } from '../serialize.js';

/** Users / Profile. */
export function usersRouter({ db, cfg }) {
  const r = Router();
  // Profile edits need a chosen role, because the editable fields depend on it.
  const anyRole = requireRole('volunteer', 'organization');

  // GET /users/me - req.user was already loaded by the authenticate middleware, so no database read is needed.
  r.get('/me', (req, res) => res.json(profileResponse(req.user)));

  // PUT /users/me - update the profile fields allowed for this role.
  r.put(
    '/me',
    anyRole,
    ah(async (req, res) => {
      // Validates the input and returns only the fields the app actually sent.
      const patch = parseProfileUpdate(bodyOf(req), req.user.role, cfg);
      patch.updatedAt = Timestamp.now();
      await db.collection('users').doc(req.user.id).update(patch);
      // Merge the changes onto the record we already have instead of reading it again.
      res.json(profileResponse({ ...req.user, ...patch }));
    })
  );

  // PUT /users/me/settings - language and notifications. Works even before a role is chosen.
  r.put(
    '/me/settings',
    ah(async (req, res) => {
      const patch = parseSettings(bodyOf(req));
      patch.updatedAt = Timestamp.now();
      await db.collection('users').doc(req.user.id).update(patch);
      const merged = { ...req.user, ...patch };
      res.json({
        preferredLanguage: merged.preferredLanguage,
        notificationsEnabled: merged.notificationsEnabled,
        darkMode: merged.darkMode ?? false,
      });
    })
  );

  return r;
}