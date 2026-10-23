import { Timestamp } from 'firebase-admin/firestore';

/**
 * Development helpers: create a test account directly in Firestore so screens can be built and demoed
 * before Google sign-in is finished. Used by scripts/make-test-token.js and scripts/seed.js.
 */
export async function ensureTestUser(db, { uid, role, name, interests = [], skills = [] }) {
  const ref = db.collection('users').doc(uid);
  const snap = await ref.get();
  if (snap.exists) return snap.data();
  const now = Timestamp.now();
  const data = {
    id: uid,
    firebaseUid: uid,
    email: `${uid}@example.test`,
    role,
    name,
    profileImageUrl: null,
    bio: '',
    description: '',
    interests,
    skills,
    preferredLanguage: 'en',
    notificationsEnabled: true,
    fcmToken: null,
    createdAt: now,
    updatedAt: now,
  };
  await ref.set(data);
  return data;
}

const days = (n) => Timestamp.fromMillis(Date.now() + n * 24 * 60 * 60 * 1000);

/**
 * Sample listings modelled on the Figma mockups. Fixed ids, so running the seed twice doesn't
 * duplicate anything. causeCategories/skillsRequired are copied verbatim from the values in
 * constants.js (which themselves come from InterestMatchingViewModel.availableCauses/
 * availableSkills on the Android side) - keep these three lists in sync.
 */
export async function seedSampleListings(db, organizationId) {
  const now = Timestamp.now();
  const listings = [
    {
      id: 'seed-read-to-kids',
      title: "Read to Kids at Christine Revell Children's Home",
      description: 'Spend a morning sharing stories and laughs with children aged 5-8',
      causeCategories: ['Children & Youth'],
      skillsRequired: ['Reading to Children'],
      type: 'Scheduled',
      dateTime: days(14),
      spacesTotal: 20,
      area: '12 Crew Street, Newlands',
      minHours: 5,
    },
    {
      id: 'seed-plant-a-tree',
      title: 'Plant a tree with TreeKeepers',
      description: 'Help plant indigenous trees along the river. Gloves and tools are provided.',
      causeCategories: ['Environment & Conservation'],
      skillsRequired: ['Manual Labour'],
      type: 'Scheduled',
      dateTime: days(7),
      spacesTotal: 10,
      area: 'Rose Street, Newlands',
      minHours: 3,
    },
    {
      id: 'seed-serve-meals',
      title: 'Prepare & Serve food at our Kitchens',
      description: 'Lend a hand helping us to serve healthy hot meals to those in need.',
      causeCategories: ['Soup Kitchens'],
      skillsRequired: ['Cooking or Food Prep'],
      type: 'Ongoing',
      dateTime: null,
      spacesTotal: null,
      area: 'Athlone',
      minHours: 5,
    },
  ];
  for (const l of listings) {
    await db.collection('listings').doc(l.id).set({ ...l, images: [], organizationId, createdAt: now, updatedAt: now });
  }
  return listings.length;
}