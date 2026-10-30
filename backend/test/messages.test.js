// Tests for direct messaging: who may open a conversation, who may read or write it, and that
// unread counts and previews stay correct.
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { Timestamp } from 'firebase-admin/firestore';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { signSessionToken } from '../src/tokens.js';
import { createFakeDb } from '../testing/fakeFirestore.js';

const cfg = loadConfig({ JWT_SECRET: 'x'.repeat(40), NODE_ENV: 'test' });
const ts = (ms) => Timestamp.fromMillis(ms);

let server, base, db;
const token = (id) => signSessionToken(id, cfg);

before(async () => {
  db = createFakeDb({
    users: {
      org1: { name: 'Sunrise Shelter', role: 'organization' },
      org2: { name: 'Other Org', role: 'organization' },
      vol1: { name: 'Thandi', role: 'volunteer' },
      vol2: { name: 'Pieter', role: 'volunteer' },
      vol3: { name: 'Lerato', role: 'volunteer' },
    },
    listings: { L1: { title: 'Soup run', organizationId: 'org1', type: 'Ongoing' } },
    signups: {
      S1: { listingId: 'L1', volunteerId: 'vol1', status: 'confirmed' },
      S3: { listingId: 'L1', volunteerId: 'vol3', status: 'cancelled' },
    },
  });
  const app = createApp({ db, firebaseAuth: {}, cfg });
  await new Promise((resolve) => (server = app.listen(0, resolve)));
  base = `http://127.0.0.1:${server.address().port}/api/v1`;
});
after(() => server.close());

async function call(method, path, userId, body) {
  const res = await fetch(base + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(userId ? { Authorization: `Bearer ${token(userId)}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null };
}

test('requires a login', async () => {
  const res = await call('GET', '/conversations', null);
  assert.equal(res.status, 401);
});

test('a signed-up volunteer opens a conversation, and opening it again returns the same one', async () => {
  const first = await call('POST', '/listings/L1/conversations', 'vol1');
  assert.equal(first.status, 201);
  assert.equal(first.body.id, 'L1_vol1');
  assert.equal(first.body.otherParty.name, 'Sunrise Shelter');
  assert.equal(first.body.listingTitle, 'Soup run');

  const again = await call('POST', '/listings/L1/conversations', 'vol1');
  assert.equal(again.status, 200);
  assert.equal(again.body.id, first.body.id);
});

test('a volunteer who has not signed up, or has cancelled, cannot start a conversation', async () => {
  const notSignedUp = await call('POST', '/listings/L1/conversations', 'vol2');
  assert.equal(notSignedUp.status, 403);
  assert.equal(notSignedUp.body.error.code, 'not_signed_up');

  const cancelled = await call('POST', '/listings/L1/conversations', 'vol3');
  assert.equal(cancelled.status, 403);
});

test('an organization can open a conversation only with its own signed-up volunteers', async () => {
  const ok = await call('POST', '/listings/L1/conversations', 'org1', { volunteerId: 'vol1' });
  assert.equal(ok.status, 200); // already opened by the volunteer above
  assert.equal(ok.body.otherParty.name, 'Thandi');

  const notSignedUp = await call('POST', '/listings/L1/conversations', 'org1', { volunteerId: 'vol2' });
  assert.equal(notSignedUp.status, 404);

  const wrongOrg = await call('POST', '/listings/L1/conversations', 'org2', { volunteerId: 'vol1' });
  assert.equal(wrongOrg.status, 403);

  const missingId = await call('POST', '/listings/L1/conversations', 'org1', {});
  assert.equal(missingId.status, 400);
});

test('sending updates the preview and the other side\'s unread count only', async () => {
  const sent = await call('POST', '/conversations/L1_vol1/messages', 'vol1', { text: '  Hi, what should I bring?  ' });
  assert.equal(sent.status, 201);
  assert.equal(sent.body.text, 'Hi, what should I bring?'); // trimmed
  assert.equal(sent.body.senderId, 'vol1');

  const org = await call('GET', '/conversations', 'org1');
  assert.equal(org.body.length, 1);
  assert.equal(org.body[0].lastMessage, 'Hi, what should I bring?');
  assert.equal(org.body[0].unreadCount, 1);
  assert.equal(org.body[0].otherParty.name, 'Thandi');

  const vol = await call('GET', '/conversations', 'vol1');
  assert.equal(vol.body[0].unreadCount, 0); // the sender has nothing unread
});

test('reading marks the conversation read for the reader', async () => {
  const marked = await call('PATCH', '/conversations/L1_vol1/read', 'org1');
  assert.equal(marked.status, 204);
  const org = await call('GET', '/conversations', 'org1');
  assert.equal(org.body[0].unreadCount, 0);
});

test('both people see the messages oldest first, and ?since returns only newer ones', async () => {
  // Pin timestamps so the order and the since filter are deterministic.
  const conv = db._peek('conversations', 'L1_vol1');
  assert.ok(conv);
  for (const [id, m] of Object.entries({ m1: [1000, 'org1', 'Bring gloves'], m2: [2000, 'vol1', 'Will do'] })) {
    await db.collection('messages').doc(id).set({ id, conversationId: 'L1_vol1', senderId: m[1], text: m[2], sentAt: ts(m[0]) });
  }
  const all = await call('GET', '/conversations/L1_vol1/messages', 'org1');
  assert.equal(all.status, 200);
  const times = all.body.map((m) => Date.parse(m.sentAt));
  assert.deepEqual(times, [...times].sort((a, b) => a - b));
  assert.deepEqual(all.body.slice(0, 2).map((m) => m.text), ['Bring gloves', 'Will do']);

  const since = await call('GET', `/conversations/L1_vol1/messages?since=${new Date(1500).toISOString()}`, 'vol1');
  assert.ok(since.body.every((m) => Date.parse(m.sentAt) > 1500));
  assert.ok(since.body.some((m) => m.text === 'Will do'));
  assert.ok(!since.body.some((m) => m.text === 'Bring gloves'));

  const bad = await call('GET', '/conversations/L1_vol1/messages?since=yesterday', 'vol1');
  assert.equal(bad.status, 400);
});

test('someone outside the conversation can neither read, write nor mark it read', async () => {
  for (const user of ['vol2', 'org2']) {
    assert.equal((await call('GET', '/conversations/L1_vol1/messages', user)).status, 403);
    assert.equal((await call('POST', '/conversations/L1_vol1/messages', user, { text: 'hello' })).status, 403);
    assert.equal((await call('PATCH', '/conversations/L1_vol1/read', user)).status, 403);
  }
  // and their own list does not leak it
  assert.deepEqual((await call('GET', '/conversations', 'vol2')).body, []);
});

test('unknown conversations are 404 and bad message text is rejected', async () => {
  assert.equal((await call('GET', '/conversations/nope/messages', 'vol1')).status, 404);
  assert.equal((await call('POST', '/conversations/L1_vol1/messages', 'vol1', { text: '   ' })).status, 400);
  assert.equal((await call('POST', '/conversations/L1_vol1/messages', 'vol1', {})).status, 400);
  assert.equal((await call('POST', '/conversations/L1_vol1/messages', 'vol1', { text: 'a'.repeat(1001) })).status, 400);
});
