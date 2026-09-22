// routes/auth.js - login and account setup.

// Login flow:
//   1. The app signs in with Google via Firebase and gets a Firebase ID token.
//   2. The app sends that token to POST /auth/login.
//   3. We ask Firebase to verify it, find (or create) the user's record, and reply with OUR OWN JWT.
//   4. From then on the app sends our JWT instead of the Firebase token.
import { Router } from 'express';
import { Timestamp } from 'firebase-admin/firestore';
import { ah } from '../asyncHandler.js';
import { badRequest, unauthorized, conflict } from '../errors.js';
import { bodyOf, requireEnum } from '../validators.js';
import { ROLES } from '../constants.js';
import { signSessionToken } from '../tokens.js';
import { profileResponse } from '../serialize.js';

/** Auth & session. `auth` is the JWT middleware; only POST /login is unauthenticated. */
export function authRouter({ db, firebaseAuth, cfg, auth }) {
  const r = Router();

  // POST /auth/login - body: { firebaseIdToken }  ->  { token, user: { id, role, isNewUser } }
  r.post(
    '/login',
    ah(async (req, res) => {
      const { firebaseIdToken } = bodyOf(req);
      if (typeof firebaseIdToken !== 'string' || firebaseIdToken.trim() === '') {
        throw badRequest('firebaseIdToken is required.', 'firebaseIdToken');
      }

      // `decoded` then holds the verified details: uid, email, name and picture.
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
      // It runs as a transaction so "look it up, and create it if missing" happens as one step;
      // two logins at the same moment can't both create the account.
      const ref = db.collection('users').doc(decoded.uid);
      const { user, isNewUser } = await db.runTransaction(async (t) => {
        const snap = await t.get(ref);
        if (snap.exists) return { user: snap.data(), isNewUser: false }; // returning user
        // First ever login: build the new user record, pre-filled from their Google account.
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
        t.create(ref, data); // create() fails if the document already exists, as a safety net
        return { user: data, isNewUser: true };
      });

      // isNewUser lets the app decide whether to show the "choose your role" screen.
      res.json({
        token: signSessionToken(decoded.uid, cfg),
        user: { id: decoded.uid, role: user.role ?? null, isNewUser },
      });
    })
  );

  // POST /auth/role - body: { role: "volunteer" | "organization" }. Allowed once per account.
  r.post(
    '/role',
    auth,
    ah(async (req, res) => {
      const role = requireEnum(bodyOf(req).role, 'role', ROLES);
      const ref = db.collection('users').doc(req.user.id);
      // A transaction again, so two requests can't both pass the "role not set yet" check.
      const updated = await db.runTransaction(async (t) => {
        const snap = await t.get(ref);
        if (snap.data().role) {
          throw conflict('role_already_set', 'Your role has already been chosen and can’t be changed.');
        }
        const patch = { role, updatedAt: Timestamp.now() };
        t.update(ref, patch);
        return { ...snap.data(), ...patch, id: snap.id }; // the record as it will look after the update
      });
      res.json(profileResponse(updated));
    })
  );

  // JWTs are stateless, so there is nothing to revoke server-side; the app clears its stored token.
  // 204 = success with no response body.
  r.post('/logout', auth, (req, res) => res.status(204).end());

  return r;
}