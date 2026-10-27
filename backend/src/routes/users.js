import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { requireRole } from '../middleware/auth.js';
import { bodyOf, parseProfileUpdate, parseSettings, requireString } from '../validators.js';
import { profileResponse } from '../serialize.js';

/** Table 2 - Users / Profile, plus POST /users/me/fcm-token (Final POE). */
export function usersRouter({ db, cfg }) {
  const r = Router();
  const anyRole = requireRole('volunteer', 'organization');

  r.get('/me', (req, res) => res.json(profileResponse(req.user)));

  r.put(
    '/me',
    anyRole,
    ah(async (req, res) => {
      const patch = parseProfileUpdate(bodyOf(req), req.user.role, cfg);
      patch.updatedAt = Timestamp.now();
      await db.collection('users').doc(req.user.id).update(patch);
      res.json(profileResponse({ ...req.user, ...patch }));
    })
  );

  r.put(
    '/me/settings',
    ah(async (req, res) => {
      const patch = parseSettings(bodyOf(req));
      patch.updatedAt = Timestamp.now();
      await db.collection('users').doc(req.user.id).update(patch);
      const merged = { ...req.user, ...patch };
      res.json({ preferredLanguage: merged.preferredLanguage, notificationsEnabled: merged.notificationsEnabled });
    })
  );

  // The app sends its FCM device token after every login and whenever Firebase rotates it (onNewToken).
  // One token per account, most recent login wins. A token can only belong to one account: if this
  // phone was last used by someone else, they stop getting this account's notifications.
  r.post(
    '/me/fcm-token',
    ah(async (req, res) => {
      const body = bodyOf(req);
      const token = requireString(body.fcmToken ?? body.token, 'fcmToken', { max: 4096 });

      const holders = await db.collection('users').where('fcmToken', '==', token).get();
      await Promise.all(holders.docs.filter((d) => d.id !== req.user.id).map((d) => d.ref.update({ fcmToken: null })));

      await db.collection('users').doc(req.user.id).update({ fcmToken: token, updatedAt: Timestamp.now() });
      res.status(204).end();
    })
  );

  return r;
}