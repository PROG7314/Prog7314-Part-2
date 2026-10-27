import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { badRequest, unauthorized, conflict } from '../errors.js';
import { bodyOf, requireEnum } from '../validators.js';
import { ROLES } from '../constants.js';
import { signSessionToken } from '../tokens.js';
import { profileResponse } from '../serialize.js';

/** Table 1 - Auth & session. `auth` is the JWT middleware; only POST /login is unauthenticated. */
export function authRouter({ db, firebaseAuth, cfg, auth }) {
  const r = Router();

  r.post(
    '/login',
    ah(async (req, res) => {
      const { firebaseIdToken } = bodyOf(req);
      if (typeof firebaseIdToken !== 'string' || firebaseIdToken.trim() === '') {
        throw badRequest('firebaseIdToken is required.', 'firebaseIdToken');
      }

      let decoded;
      try {
        decoded = await firebaseAuth.verifyIdToken(firebaseIdToken);
      } catch (e) {
        // Bad/expired Google token = the client's problem (401). Anything else (network, config) = ours (500).
        if (typeof e?.code === 'string' && e.code.startsWith('auth/')) {
          console.warn(`Firebase token rejected: ${e.code}`);
          throw unauthorized('Google sign-in could not be verified. Please try again.');
        }
        throw e;
      }

      // Get-or-create the users document. Its id is the Firebase uid, so a double login can't create two accounts.
      const ref = db.collection('users').doc(decoded.uid);
      const { user, isNewUser } = await db.runTransaction(async (t) => {
        const snap = await t.get(ref);
        if (snap.exists) return { user: snap.data(), isNewUser: false };
        const now = Timestamp.now();
        const data = {
          id: decoded.uid,
          firebaseUid: decoded.uid,
          email: decoded.email ?? null,
          role: null, // set once, by POST /auth/role
          name: decoded.name ?? '',
          profileImageUrl: decoded.picture ?? null,
          bio: '',
          description: '',
          interests: [],
          skills: [],
          preferredLanguage: 'en',
          notificationsEnabled: true,
          fcmToken: null,
          createdAt: now,
          updatedAt: now,
        };
        t.create(ref, data);
        return { user: data, isNewUser: true };
      });

      res.json({
        token: signSessionToken(decoded.uid, cfg),
        user: { id: decoded.uid, role: user.role ?? null, isNewUser },
      });
    })
  );

  r.post(
    '/role',
    auth,
    ah(async (req, res) => {
      const role = requireEnum(bodyOf(req).role, 'role', ROLES);
      const ref = db.collection('users').doc(req.user.id);
      const updated = await db.runTransaction(async (t) => {
        const snap = await t.get(ref);
        if (snap.data().role) {
          throw conflict('role_already_set', 'Your role has already been chosen and can’t be changed.');
        }
        const patch = { role, updatedAt: Timestamp.now() };
        t.update(ref, patch);
        return { ...snap.data(), ...patch, id: snap.id };
      });
      res.json(profileResponse(updated));
    })
  );

  // JWTs are stateless, so the app just discards its token. The one thing logout does server-side is
  // forget the device's FCM token, so this account stops getting pushes on this phone.
  r.post(
    '/logout',
    auth,
    ah(async (req, res) => {
      await db.collection('users').doc(req.user.id).update({ fcmToken: null, updatedAt: Timestamp.now() });
      res.status(204).end();
    })
  );

  return r;
}