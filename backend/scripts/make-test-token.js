// Usage:  npm run token -- --role volunteer|organization [--name "Some Name"]
// Creates (or reuses) a test account in Firestore and prints a JWT for it, so you can build and demo
// screens before Google sign-in works. The token is signed with JWT_SECRET, so your local .env must use
// the SAME JWT_SECRET and FIREBASE_SERVICE_ACCOUNT as the Render service the app talks to.
import { loadConfig } from '../src/config.js';
import { initFirebase } from '../src/firebase.js';
import { ensureTestUser } from '../src/devTools.js';
import { signSessionToken } from '../src/tokens.js';

// Reads the value after a command-line flag: arg('--role', 'volunteer') looks for "--role x" in the
// command and returns x, or the fallback if the flag wasn't given.
const arg = (flag, fallback) => {
  const i = process.argv.indexOf(flag);
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
};

const role = arg('--role', 'volunteer');
if (!['volunteer', 'organization'].includes(role)) {
  console.error('--role must be "volunteer" or "organization"');
  process.exit(1);
}
// Sensible defaults taken from the mockups (Julie Beckett, and the children's home organization).
const defaults =
  role === 'volunteer'
    ? { name: 'Julie Beckett', interests: ['Children', 'Animals'], skills: ['Reading'] }
    : { name: "Christine Revell Children's Home", interests: [], skills: [] };

const cfg = loadConfig();
const { db } = initFirebase();
// A fixed id per role ("test-volunteer" / "test-organization"), so the same test account is reused every time.
const uid = `test-${role}`;
await ensureTestUser(db, { uid, role, name: arg('--name', defaults.name), interests: defaults.interests, skills: defaults.skills });

console.log(`Test ${role} account: ${uid}`);
console.log('Token (valid for JWT_EXPIRES_IN). Do NOT commit it to GitHub:\n');
console.log(signSessionToken(uid, cfg));
// Exit explicitly: the Firebase connection would otherwise keep the script running.
process.exit(0);