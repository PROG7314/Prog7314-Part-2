// middleware/auth.js - the two checks that protect the API:
//   authenticate: "are you logged in, and who are you?"
//   requireRole:  "are you allowed to do THIS?"

// Middleware = a function that runs before the route handler; it can stop the request with an error.
import { unauthorized, forbidden } from '../errors.js';
import { verifySessionToken } from '../tokens.js';
import { ah } from '../asyncHandler.js';

/**
 * Verifies the JWT, loads the user from Firestore and attaches it as req.user.
 * All role checks then happen in requireRole() - one place, as the design doc says.
 */
export function authenticate({ db, cfg }) {
  return ah(async (req, res, next) => {
    // The app sends: Authorization: Bearer <token>. This pulls out the <token> part.
    const match = /^Bearer (.+)$/.exec(req.get('Authorization') ?? '');
    if (!match) throw unauthorized('Missing or malformed Authorization header.');

    // Checks the signature and expiry. A different message for "expired" lets the app tell the
    // user to log in again instead of showing a generic error.
    let payload;
    try {
      payload = verifySessionToken(match[1], cfg);
    } catch (e) {
      throw unauthorized(
        e.name === 'TokenExpiredError' ? 'Your session has expired. Please log in again.' : 'Invalid session token.'
      );
    }

    // The token only holds the user id, so load the current user record. Doing this on every request
    // means a role that was just chosen takes effect immediately, and a deleted account stops working.
    const snap = await db.collection('users').doc(String(payload.sub)).get();
    if (!snap.exists) throw unauthorized('That account no longer exists. Please log in again.');
    req.user = { ...snap.data(), id: snap.id }; // later handlers read the caller from req.user
    next(); // all good: carry on to the route
  });
}

// Usage: router.post('/', requireRole('organization'), handler)
// Runs after authenticate, so req.user is already set.
export function requireRole(...roles) {
  return (req, res, next) => {
    // A brand-new account has role = null until the user picks volunteer or organization.
    if (!req.user.role) {
      return next(forbidden('Choose whether you are a volunteer or an organization first.', 'role_required'));
    }
    if (!roles.includes(req.user.role)) {
      return next(forbidden(`Only ${roles.join(' or ')} accounts can do this.`));
    }
    next();
  };
}