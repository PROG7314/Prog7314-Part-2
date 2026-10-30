// devServer.js - runs the real API on http://localhost:3000 against an in-memory fake database
// seeded with a small demo (one organization, two volunteers, listings, a chat). No Firebase needed.
//   node testing/devServer.js
// It prints a session token for each demo user; the Android emulator reaches this server at
// http://10.0.2.2:3000 (the debug build's API_BASE_URL).
import { Timestamp } from 'firebase-admin/firestore';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { signSessionToken } from '../src/tokens.js';
import { createFakeDb } from './fakeFirestore.js';

const cfg = loadConfig({ JWT_SECRET: 'dev-only-secret-dev-only-secret-12345', JWT_EXPIRES_IN: '7d', NODE_ENV: 'test' });
const now = Date.now();
const ts = (offsetMs) => Timestamp.fromMillis(now + offsetMs);
const H = 3600_000;

const db = createFakeDb({
  users: {
    org1: { name: 'Sunrise Shelter', role: 'organization', email: 'hello@sunrise.org', profileImageUrl: 'https://picsum.photos/seed/aegis-sunrise/300/300', bannerImageUrl: 'https://picsum.photos/seed/aegis-banner-org/900/400', description: 'We feed and house families in Athlone.' },
    vol1: { name: 'Thandi Nkosi', role: 'volunteer', email: 'thandi@example.com', profileImageUrl: 'https://picsum.photos/seed/aegis-thandi/300/300', bannerImageUrl: 'https://picsum.photos/seed/aegis-banner-vol/900/400', bio: 'Loves cooking for a crowd.', interests: ['Soup Kitchens', 'Children & Youth'], skills: ['Cooking or Food Prep'], preferredLanguage: 'en', notificationsEnabled: true },
    vol2: { name: 'Pieter van Wyk', role: 'volunteer', email: 'pieter@example.com', interests: [], skills: [] },
  },
  listings: {
    L1: { id: 'L1', organizationId: 'org1', title: 'Saturday soup run', description: 'Help prepare and hand out 200 meals.', causeCategories: ['Soup Kitchens'], skillsRequired: ['Cooking or Food Prep'], type: 'Scheduled', dateTime: ts(30 * H), spacesTotal: 10, area: 'Athlone, Cape Town', minHours: 3, images: ['https://picsum.photos/seed/aegis-soup/900/500'], createdAt: ts(-2 * H) },
    L2: { id: 'L2', organizationId: 'org1', title: 'Homework club helpers', description: 'Ongoing help with after-school homework.', causeCategories: ['Children & Youth'], skillsRequired: ['Tutoring'], type: 'Ongoing', dateTime: null, spacesTotal: null, area: 'Athlone, Cape Town', minHours: 2, images: ['https://picsum.photos/seed/aegis-homework/900/500'], createdAt: ts(-3 * H) },
  },
  signups: {
    S1: { id: 'S1', listingId: 'L1', volunteerId: 'vol1', status: 'confirmed', hoursCompleted: null, signedUpAt: ts(-H), completedAt: null },
    S2: { id: 'S2', listingId: 'L2', volunteerId: 'vol1', status: 'confirmed', hoursCompleted: null, signedUpAt: ts(-2 * H), completedAt: null },
  },
  conversations: {
    L1_vol1: { id: 'L1_vol1', listingId: 'L1', listingTitle: 'Saturday soup run', organizationId: 'org1', volunteerId: 'vol1', lastMessage: 'Great, please arrive 15 minutes early.', lastMessageAt: ts(-20 * 60_000), lastSenderId: 'org1', unread: { vol1: 2 }, createdAt: ts(-H) },
  },
  messages: {
    m1: { id: 'm1', conversationId: 'L1_vol1', senderId: 'vol1', text: 'Hi! What should I bring on Saturday?', sentAt: ts(-50 * 60_000) },
    m2: { id: 'm2', conversationId: 'L1_vol1', senderId: 'org1', text: 'Just yourself and closed shoes.', sentAt: ts(-30 * 60_000) },
    m3: { id: 'm3', conversationId: 'L1_vol1', senderId: 'org1', text: 'Great, please arrive 15 minutes early.', sentAt: ts(-20 * 60_000) },
  },
});

createApp({ db, firebaseAuth: {}, cfg }).listen(3000, () => {
  console.log('Aegis dev API on http://localhost:3000/api/v1');
  for (const id of ['vol1', 'vol2', 'org1']) console.log(`TOKEN ${id} ${signSessionToken(id, cfg)}`);
});
