// tokens.js - creates and checks the session token that the app sends with every request.

// Flow: the app logs in with Google - the API checks that with Firebase - the API issues its own token
// - the app sends that token in the "Authorization: Bearer <token>" header from then on.
import jwt from 'jsonwebtoken';

export function signSessionToken(userId, cfg) {
  // "sub" (subject) is the standard JWT field for who the token belongs to.
  // HS256 = signed with our secret, so nobody can forge a token without JWT_SECRET.
  return jwt.sign({ sub: userId }, cfg.jwtSecret, { algorithm: 'HS256', expiresIn: cfg.jwtExpiresIn });
}
    // Throws if the token is expired, tampered with, or signed with a different secret.
    // Restricting `algorithms` to HS256 blocks tokens that claim to use some weaker algorithm.
    export function verifySessionToken(token, cfg) {
      return jwt.verify(token, cfg.jwtSecret, { algorithms: ['HS256'] });
}