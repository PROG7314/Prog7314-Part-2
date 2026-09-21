// Usage:  npm run seed
// Creates the test organization plus three sample listings modelled on the Figma mockups. Safe to re-run.
import { loadConfig } from '../src/config.js';
import { initFirebase } from '../src/firebase.js';
import { ensureTestUser, seedSampleListings } from '../src/devTools.js';

loadConfig(); // fail early if the environment isn't set up
const { db } = initFirebase();
// The sample listings belong to this test organization, so log in as it (npm run token -- --role organization)
// to see them on Org Home.
const orgId = 'test-organization';
await ensureTestUser(db, { uid: orgId, role: 'organization', name: "Christine Revell Children's Home" });
const count = await seedSampleListings(db, orgId);
console.log(`Seeded ${count} listings for ${orgId}.`);
// Exit explicitly: the Firebase connection would otherwise keep the script running.
process.exit(0);