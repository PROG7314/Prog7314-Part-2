import test from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import { createApp } from '../src/app.js';
import { FakeFirestore } from '../testutils/fakeFirestore.js';
import { ensureTestUser, seedSampleListings } from '../src/devTools.js';
import { signSessionToken } from '../src/tokens.js';

const cfg = { jwtSecret: 'x'.repeat(40), jwtExpiresIn: '1h', enforceFixedLists: true, logRequests: false };

// Stands in for Firebase Admin: "good:<uid>:<name>" is a valid Google ID token, anything else is rejected.
const firebaseAuth = {
  async verifyIdToken(token) {
    const m = /^good:([^:]+):?(.*)$/.exec(token);
    if (!m) throw Object.assign(new Error('bad token'), { code: 'auth/argument-error' });
    return { uid: m[1], email: `${m[1]}@example.test`, name: m[2] || 'Someone', picture: null };
  },
};

const day = 24 * 60 * 60 * 1000;
const inDays = (n) => new Date(Date.now() + n * day).toISOString();
const listingBody = (over = {}) => ({
  title: 'Read to Kids',
  description: 'Stories and laughs',
  causeCategories: ['Children & Youth'],
  skillsRequired: ['Reading to Children'],
  type: 'Scheduled',
  dateTime: inDays(7),
  spacesTotal: 2,
  area: '12 Crew Street, Newlands',
  minHours: 5,
  images: [],
  ...over,
});

async function setup() {
  const db = new FakeFirestore();
  const server = await new Promise((resolve) => {
    const s = createApp({ db, firebaseAuth, cfg }).listen(0, () => resolve(s));
  });
  const root = `http://127.0.0.1:${server.address().port}`;
  let n = 0;

  const call = async (method, path, { token, body, rawBody } = {}) => {
    const hasBody = body !== undefined || rawBody !== undefined;
    const res = await fetch(root + path, {
      method,
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(hasBody ? { 'Content-Type': 'application/json' } : {}) },
      body: hasBody ? rawBody ?? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    return { status: res.status, body: text ? JSON.parse(text) : null };
  };
  const api = (method, path, opts) => call(method, `/api/v1${path}`, opts);

  const makeUser = async (role, extra = {}) => {
    const uid = `u${++n}-${role ?? 'norole'}`;
    await ensureTestUser(db, { uid, role, name: extra.name ?? uid, interests: extra.interests, skills: extra.skills });
    return { uid, token: signSessionToken(uid, cfg) };
  };
  const createListing = async (org, over) => {
    const r = await api('POST', '/listings', { token: org.token, body: listingBody(over) });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return r.body;
  };
  const signUp = (vol, listingId) => api('POST', `/listings/${listingId}/signups`, { token: vol.token, body: {} });

  return { db, root, call, api, makeUser, createListing, signUp, close: () => server.close() };
}

function scenario(name, fn) {
  test(name, async () => {
    const ctx = await setup();
    try {
      await fn(ctx);
    } finally {
      ctx.close();
    }
  });
}

const errorShape = (r, status, code, field) => {
  assert.equal(r.status, status, JSON.stringify(r.body));
  assert.equal(r.body.error.code, code);
  assert.equal(typeof r.body.error.message, 'string');
  if (field) assert.equal(r.body.error.field, field);
};

// ------------------------------------------------------------------ auth
scenario('health check, unknown routes and malformed JSON use the one error shape', async ({ root, api, call, makeUser }) => {
  assert.equal((await fetch(`${root}/health`)).status, 200);
  errorShape(await call('GET', '/whatever'), 404, 'not_found');

  const org = await makeUser('organization');
  errorShape(await api('GET', '/nope', { token: org.token }), 404, 'not_found');
  errorShape(await api('POST', '/listings', { token: org.token, rawBody: '{not json' }), 400, 'validation_error');
});

scenario('login creates the account once, then role can be chosen once', async ({ api }) => {
  errorShape(await api('POST', '/auth/login', { body: {} }), 400, 'validation_error', 'firebaseIdToken');
  errorShape(await api('POST', '/auth/login', { body: { firebaseIdToken: 'nonsense' } }), 401, 'unauthorized');

  const first = await api('POST', '/auth/login', { body: { firebaseIdToken: 'good:g1:Julie Beckett' } });
  assert.equal(first.status, 200);
  assert.equal(first.body.user.isNewUser, true);
  assert.equal(first.body.user.role, null);
  assert.equal(first.body.user.id, 'g1');

  const again = await api('POST', '/auth/login', { body: { firebaseIdToken: 'good:g1:Julie Beckett' } });
  assert.equal(again.body.user.isNewUser, false);

  const token = first.body.token;
  const me = await api('GET', '/users/me', { token });
  assert.equal(me.status, 200);
  assert.equal(me.body.name, 'Julie Beckett');
  assert.equal(me.body.role, null);

  // No role yet: role-gated endpoints say so clearly.
  errorShape(await api('GET', '/listings', { token }), 403, 'role_required');
  errorShape(await api('POST', '/auth/role', { token, body: { role: 'admin' } }), 400, 'validation_error', 'role');

  const set = await api('POST', '/auth/role', { token, body: { role: 'volunteer' } });
  assert.equal(set.status, 200);
  assert.equal(set.body.role, 'volunteer');
  assert.deepEqual(set.body.interests, []);
  errorShape(await api('POST', '/auth/role', { token, body: { role: 'organization' } }), 409, 'role_already_set');

  assert.equal((await api('POST', '/auth/logout', { token })).status, 204);
});

scenario('every endpoint except login needs a valid JWT', async ({ api, makeUser }) => {
  errorShape(await api('GET', '/users/me'), 401, 'unauthorized');
  errorShape(await api('GET', '/users/me', { token: 'garbage' }), 401, 'unauthorized');

  const expired = jwt.sign({ sub: 'someone' }, cfg.jwtSecret, { expiresIn: -10 });
  const r = await api('GET', '/users/me', { token: expired });
  errorShape(r, 401, 'unauthorized');
  assert.match(r.body.error.message, /expired/i);

  const wrongSecret = jwt.sign({ sub: 'someone' }, 'y'.repeat(40));
  errorShape(await api('GET', '/users/me', { token: wrongSecret }), 401, 'unauthorized');

  // Valid signature but the account doesn't exist.
  errorShape(await api('GET', '/users/me', { token: signSessionToken('ghost', cfg) }), 401, 'unauthorized');

  // The role can change after the token was issued, so a role-less token still works once the role is set.
  const u = await makeUser(null);
  errorShape(await api('GET', '/listings', { token: u.token }), 403, 'role_required');
});

// ------------------------------------------------------------------ profile & settings
scenario('profile and settings updates', async ({ api, makeUser }) => {
  const vol = await makeUser('volunteer');
  const org = await makeUser('organization');

  const up = await api('PUT', '/users/me', {
    token: vol.token,
    body: { name: 'Julie B', bio: 'Loves kids', interests: ['Children & Youth', 'Animal Welfare'], skills: ['Reading to Children'] },
  });
  assert.equal(up.status, 200);
  assert.deepEqual(up.body.interests, ['Children & Youth', 'Animal Welfare']);
  assert.equal((await api('GET', '/users/me', { token: vol.token })).body.name, 'Julie B');

  errorShape(await api('PUT', '/users/me', { token: vol.token, body: { description: 'x' } }), 400, 'validation_error', 'description');
  errorShape(await api('PUT', '/users/me', { token: vol.token, body: { interests: ['Skydiving'] } }), 400, 'validation_error', 'interests');
  const orgUp = await api('PUT', '/users/me', { token: org.token, body: { description: 'We help kids' } });
  assert.equal(orgUp.body.description, 'We help kids');
  errorShape(await api('PUT', '/users/me', { token: org.token, body: { interests: ['Children & Youth'] } }), 400, 'validation_error', 'interests');

  const s = await api('PUT', '/users/me/settings', { token: vol.token, body: { preferredLanguage: 'zu', notificationsEnabled: false } });
  assert.deepEqual(s.body, { preferredLanguage: 'zu', notificationsEnabled: false });
  errorShape(await api('PUT', '/users/me/settings', { token: vol.token, body: { preferredLanguage: 'fr' } }), 400, 'validation_error', 'preferredLanguage');
  errorShape(await api('PUT', '/users/me/settings', { token: vol.token, body: {} }), 400, 'validation_error');
});

// ------------------------------------------------------------------ listings
scenario('creating listings: role check and validation', async ({ api, makeUser, createListing }) => {
  const org = await makeUser('organization', { name: "Christine Revell Children's Home" });
  const vol = await makeUser('volunteer');

  errorShape(await api('POST', '/listings', { token: vol.token, body: listingBody() }), 403, 'forbidden');

  const bad = (over, field) => api('POST', '/listings', { token: org.token, body: listingBody(over) }).then((r) => errorShape(r, 400, 'validation_error', field));
  await bad({ dateTime: undefined }, 'dateTime');
  await bad({ dateTime: 'next friday' }, 'dateTime');
  await bad({ spacesTotal: undefined }, 'spacesTotal');
  await bad({ spacesTotal: 0 }, 'spacesTotal');
  await bad({ causeCategories: ['Skydiving'] }, 'causeCategories');
  await bad({ causeCategories: [] }, 'causeCategories');
  await bad({ skillsRequired: ['Juggling'] }, 'skillsRequired');
  await bad({ title: '   ' }, 'title');
  await bad({ area: undefined }, 'area');
  await bad({ minHours: -1 }, 'minHours');
  await bad({ minHours: undefined }, 'minHours'); // left blank: rejected, never defaulted to 0
  await bad({ minHours: null }, 'minHours');
  await bad({ minHours: '5' }, 'minHours'); // a string is not a number
  const blank = await api('POST', '/listings', { token: org.token, body: listingBody({ minHours: undefined }) });
  assert.match(blank.body.error.message, /minHours is required/);
  await bad({ type: 'Weekly' }, 'type');
  await bad({ images: ['http://insecure.example/x.png'] }, 'images');
  await bad({ images: Array(4).fill('https://example.com/a.png') }, 'images');

  const ok = await createListing(org);
  assert.equal(ok.organizationId, org.uid);
  assert.equal(ok.organizationName, "Christine Revell Children's Home");
  assert.equal(ok.spacesRemaining, 2);
  assert.equal(ok.signupCount, 0);

  const ongoing = await createListing(org, { type: 'Ongoing', dateTime: inDays(3), spacesTotal: 5 });
  assert.equal(ongoing.dateTime, null); // "Ongoing has neither"
  assert.equal(ongoing.spacesTotal, null);
  assert.equal(ongoing.spacesRemaining, null);

  assert.equal((await api('GET', `/listings/${ok.id}`, { token: vol.token })).body.title, 'Read to Kids');
  errorShape(await api('GET', '/listings/does-not-exist', { token: vol.token }), 404, 'not_found');
});

scenario('feed filtering happens on the server', async ({ db, api, makeUser }) => {
  const org = await makeUser('organization');
  await ensureTestUser(db, { uid: 'test-organization', role: 'organization', name: "Christine Revell Children's Home" });
  await seedSampleListings(db, 'test-organization');
  const vol = await makeUser('volunteer');

  // The cause/skill values here are the real chip wording from InterestMatchingViewModel
  // (availableCauses/availableSkills), copied into constants.js and the seed data - not the
  // shorter design-doc placeholders this test used to check against.
  const feed = async (qs = '') => (await api('GET', `/listings${qs}`, { token: vol.token })).body.items;
  assert.equal((await feed()).length, 3);
  assert.deepEqual((await feed('?cause=Environment%20%26%20Conservation')).map((l) => l.title), ['Plant a tree with TreeKeepers']);
  assert.equal((await feed('?cause=Environment%20%26%20Conservation,Children%20%26%20Youth')).length, 2); // OR within one parameter (comma-separated)
  assert.equal((await feed('?cause=Environment%20%26%20Conservation&cause=Children%20%26%20Youth')).length, 2); // ...and when repeated
  // cause and skill are OR-ed across each other too (not AND): a listing matches if it overlaps
  // on EITHER - "Read to Kids" matches on cause, "Plant a tree" matches on skill, "Serve meals"
  // matches neither and is excluded. The old AND behaviour asserted 0 here and regularly left a
  // volunteer with both interests and skills set staring at an empty feed.
  const crossMatch = await feed('?cause=Children%20%26%20Youth&skill=Manual%20Labour');
  assert.deepEqual(crossMatch.map((l) => l.title).sort(), ['Plant a tree with TreeKeepers', "Read to Kids at Christine Revell Children's Home"]);
  assert.deepEqual(crossMatch.find((l) => l.title.startsWith('Read to Kids')).matchedCauses, ['Children & Youth']);
  assert.deepEqual(crossMatch.find((l) => l.title.startsWith('Read to Kids')).matchedSkills, []); // matched on cause, not skill
  assert.deepEqual(crossMatch.find((l) => l.title.startsWith('Plant a tree')).matchedSkills, ['Manual Labour']);
  assert.equal((await feed()).every((l) => l.matchedCauses === null && l.matchedSkills === null), true); // no filter sent -> nothing to explain
  assert.equal((await feed('?type=Ongoing')).length, 1);
  assert.equal((await feed('?area=newlands')).length, 2); // case-insensitive contains
  assert.equal((await feed('?cause=children%20%26%20youth')).length, 1); // case-insensitive values
  errorShape(await api('GET', '/listings?type=Weekly', { token: vol.token }), 400, 'validation_error', 'type');

  const first = (await feed())[0];
  assert.equal(first.organizationName, "Christine Revell Children's Home");
  assert.equal(typeof first.signupCount, 'number');

  // An organization only ever sees its own listings.
  assert.equal((await api('GET', '/listings', { token: org.token })).body.items.length, 0);
});

scenario('ended listings: hidden from volunteers, visible to the owner, closed for sign-up', async ({ api, makeUser, createListing, signUp }) => {
  const org = await makeUser('organization');
  const vol = await makeUser('volunteer');
  const ended = await createListing(org, { dateTime: new Date(Date.now() - day).toISOString() });
  const upcoming = await createListing(org, { title: 'Upcoming one' });

  const volFeed = (await api('GET', '/listings', { token: vol.token })).body.items.map((l) => l.id);
  assert.deepEqual(volFeed, [upcoming.id]);
  const orgFeed = (await api('GET', '/listings', { token: org.token })).body.items.map((l) => l.id);
  assert.equal(orgFeed.length, 2);
  assert.ok(orgFeed.includes(ended.id));

  errorShape(await signUp(vol, ended.id), 409, 'listing_ended');
});

scenario('editing and deleting listings: ownership rules', async ({ api, makeUser, createListing, signUp }) => {
  const org = await makeUser('organization');
  const other = await makeUser('organization');
  const vol = await makeUser('volunteer');
  const l = await createListing(org, { spacesTotal: 3 });

  errorShape(await api('PUT', `/listings/${l.id}`, { token: other.token, body: { title: 'Hijacked' } }), 403, 'forbidden');
  errorShape(await api('DELETE', `/listings/${l.id}`, { token: other.token }), 403, 'forbidden');
  errorShape(await api('PUT', `/listings/${l.id}`, { token: vol.token, body: { title: 'x' } }), 403, 'forbidden');
  errorShape(await api('PUT', '/listings/nope', { token: org.token, body: { title: 'x' } }), 404, 'not_found');

  const edited = await api('PUT', `/listings/${l.id}`, { token: org.token, body: { title: 'Read to Kids (updated)', minHours: 4 } });
  assert.equal(edited.status, 200);
  assert.equal(edited.body.title, 'Read to Kids (updated)');
  assert.equal(edited.body.minHours, 4);
  assert.equal(edited.body.spacesTotal, 3); // untouched fields survive a partial update

  // Can't shrink below the people already signed up.
  await signUp(vol, l.id);
  const vol2 = await makeUser('volunteer');
  await signUp(vol2, l.id);
  errorShape(await api('PUT', `/listings/${l.id}`, { token: org.token, body: { spacesTotal: 1 } }), 400, 'validation_error', 'spacesTotal');
  assert.equal((await api('PUT', `/listings/${l.id}`, { token: org.token, body: { spacesTotal: 2 } })).body.spacesRemaining, 0);

  // Switching to Ongoing drops the date and space cap.
  const ongoing = await api('PUT', `/listings/${l.id}`, { token: org.token, body: { type: 'Ongoing' } });
  assert.equal(ongoing.body.dateTime, null);
  assert.equal(ongoing.body.spacesTotal, null);
  // ...and back to Scheduled needs them again.
  errorShape(await api('PUT', `/listings/${l.id}`, { token: org.token, body: { type: 'Scheduled' } }), 400, 'validation_error', 'dateTime');
});

// ------------------------------------------------------------------ sign-ups
scenario('signing up: duplicates, full listings, and the last-space race', async ({ api, makeUser, createListing, signUp }) => {
  const org = await makeUser('organization');
  const vol = await makeUser('volunteer');
  const l = await createListing(org, { spacesTotal: 1 });

  errorShape(await signUp(org, l.id), 403, 'forbidden'); // organizations can't sign up
  errorShape(await signUp(vol, 'nope'), 404, 'not_found');

  // Two volunteers go for the LAST space at the same moment: exactly one wins.
  const [a, b] = await Promise.all([makeUser('volunteer'), makeUser('volunteer')]);
  const results = await Promise.all([signUp(a, l.id), signUp(b, l.id)]);
  assert.deepEqual(results.map((r) => r.status).sort(), [201, 409]);
  const loser = results.find((r) => r.status === 409);
  errorShape(loser, 409, 'listing_full');

  const winner = results.find((r) => r.status === 201).body;
  assert.equal(winner.status, 'confirmed');
  assert.equal(winner.hoursCompleted, null);
  const winnerUser = results[0].status === 201 ? a : b;
  errorShape(await signUp(winnerUser, l.id), 409, 'already_signed_up');

  const after = await api('GET', `/listings/${l.id}`, { token: vol.token });
  assert.equal(after.body.signupCount, 1);
  assert.equal(after.body.spacesRemaining, 0);

  // Ongoing listings have no cap.
  const open = await createListing(org, { type: 'Ongoing' });
  for (let i = 0; i < 4; i++) assert.equal((await signUp(await makeUser('volunteer'), open.id)).status, 201);
});

scenario('commitments, cancelling ("Dismiss") and freeing the space', async ({ api, makeUser, createListing, signUp }) => {
  const org = await makeUser('organization', { name: 'Kids Home' });
  const vol = await makeUser('volunteer');
  const other = await makeUser('volunteer');
  const l = await createListing(org, { spacesTotal: 1 });
  const s = (await signUp(vol, l.id)).body;

  const mine = await api('GET', '/users/me/signups', { token: vol.token });
  assert.equal(mine.body.length, 1);
  assert.equal(mine.body[0].listing.title, 'Read to Kids'); // listing summary embedded for the Commitments screen
  assert.equal(mine.body[0].listing.organizationName, 'Kids Home');
  assert.equal(mine.body[0].listing.area, '12 Crew Street, Newlands');
  assert.equal((await api('GET', '/users/me/signups', { token: other.token })).body.length, 0);
  errorShape(await api('GET', '/users/me/signups', { token: org.token }), 403, 'forbidden');

  errorShape(await signUp(other, l.id), 409, 'listing_full');
  errorShape(await api('PATCH', `/signups/${s.id}`, { token: other.token }), 403, 'forbidden'); // not theirs
  errorShape(await api('PATCH', '/signups/nope', { token: vol.token }), 404, 'not_found');

  const cancelled = await api('PATCH', `/signups/${s.id}`, { token: vol.token });
  assert.equal(cancelled.body.status, 'cancelled');
  assert.equal((await api('PATCH', `/signups/${s.id}`, { token: vol.token })).status, 200); // idempotent

  assert.equal((await signUp(other, l.id)).status, 201); // the space is free again
  assert.equal((await signUp(vol, l.id)).status, 409); // ...so the original volunteer now finds it full
});

scenario('organization sees its sign-ups; only the owner can complete them; hours log follows', async ({ api, makeUser, createListing, signUp }) => {
  const org = await makeUser('organization');
  const otherOrg = await makeUser('organization');
  const julie = await makeUser('volunteer', { name: 'Julie Beckett' });
  const sam = await makeUser('volunteer', { name: 'Sam' });
  const l = await createListing(org, { minHours: 5, spacesTotal: 5 });
  const julieSignup = (await signUp(julie, l.id)).body;
  const samSignup = (await signUp(sam, l.id)).body;
  await api('PATCH', `/signups/${samSignup.id}`, { token: sam.token }); // Sam cancels

  const list = await api('GET', `/listings/${l.id}/signups`, { token: org.token });
  assert.equal(list.body.length, 1); // cancelled sign-ups are left out
  assert.deepEqual(list.body[0].volunteer, { id: julie.uid, name: 'Julie Beckett' });
  errorShape(await api('GET', `/listings/${l.id}/signups`, { token: otherOrg.token }), 403, 'forbidden');
  errorShape(await api('GET', `/listings/${l.id}/signups`, { token: julie.token }), 403, 'forbidden');

  // Volunteers can't self-report hours; other organizations can't complete them either.
  errorShape(await api('PATCH', `/signups/${julieSignup.id}/complete`, { token: julie.token, body: {} }), 403, 'forbidden');
  errorShape(await api('PATCH', `/signups/${julieSignup.id}/complete`, { token: otherOrg.token, body: {} }), 403, 'forbidden');
  errorShape(await api('PATCH', `/signups/${julieSignup.id}/complete`, { token: org.token, body: { hoursCompleted: -2 } }), 400, 'validation_error', 'hoursCompleted');
  errorShape(await api('PATCH', `/signups/${samSignup.id}/complete`, { token: org.token, body: {} }), 409, 'signup_cancelled');
  errorShape(await api('PATCH', '/signups/nope/complete', { token: org.token, body: {} }), 404, 'not_found');

  // Default hours = the listing's minHours.
  const done = await api('PATCH', `/signups/${julieSignup.id}/complete`, { token: org.token, body: {} });
  assert.equal(done.status, 200);
  assert.equal(done.body.status, 'completed');
  assert.equal(done.body.hoursCompleted, 5);
  assert.ok(done.body.completedAt);

  let log = (await api('GET', '/users/me/hours-log', { token: julie.token })).body;
  assert.equal(log.totalHours, 5);
  assert.equal(log.entries.length, 1);
  assert.equal(log.entries[0].listingTitle, 'Read to Kids');

  // Calling it again edits the hours; there is still exactly one log entry.
  const edited = await api('PATCH', `/signups/${julieSignup.id}/complete`, { token: org.token, body: { hoursCompleted: 3.5 } });
  assert.equal(edited.body.hoursCompleted, 3.5);
  log = (await api('GET', '/users/me/hours-log', { token: julie.token })).body;
  assert.equal(log.entries.length, 1);
  assert.equal(log.totalHours, 3.5);

  // A completed sign-up can't be cancelled by the volunteer.
  errorShape(await api('PATCH', `/signups/${julieSignup.id}`, { token: julie.token }), 409, 'already_completed');
  // Sam has no hours.
  assert.deepEqual((await api('GET', '/users/me/hours-log', { token: sam.token })).body, { entries: [], totalHours: 0 });
  errorShape(await api('GET', '/users/me/hours-log', { token: org.token }), 403, 'forbidden');
});

scenario('deleting a listing cancels confirmed sign-ups but keeps earned hours', async ({ api, makeUser, createListing, signUp }) => {
  const org = await makeUser('organization');
  const a = await makeUser('volunteer');
  const b = await makeUser('volunteer');
  const l = await createListing(org, { spacesTotal: 5 });
  const sa = (await signUp(a, l.id)).body;
  await signUp(b, l.id);
  await api('PATCH', `/signups/${sa.id}/complete`, { token: org.token, body: {} });

  assert.equal((await api('DELETE', `/listings/${l.id}`, { token: org.token })).status, 204);
  errorShape(await api('GET', `/listings/${l.id}`, { token: a.token }), 404, 'not_found');

  const bSignups = (await api('GET', '/users/me/signups', { token: b.token })).body;
  assert.equal(bSignups[0].status, 'cancelled');
  assert.equal(bSignups[0].listing, null); // listing is gone; the app must cope with null
  assert.equal((await api('GET', '/users/me/hours-log', { token: a.token })).body.totalHours, 5);
});