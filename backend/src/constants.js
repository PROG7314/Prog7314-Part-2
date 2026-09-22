// constants.js - fixed values used across the API.
// These are in one place so they only need changing once.

// The 2 different types of accounts for users.
export const ROLES = ['volunteer', 'organization'];

// The type of listing. Scheduled - fixed date/time and limited number of spaces.
// Ongoing - neither.
export const LISTING_TYPES = ['Scheduled', 'Ongoing'];

// The language codes for the 4 different languages in settings page.
export const LANGUAGES = ['en', 'af', 'zu', 'xh'];

// The 3 stages of sign-ups.
export const SIGNUP_STATUS = {
  CONFIRMED: 'confirmed',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
};

// A sign-up that still has a space on the listing.
// The cancelled listings are left out.
export const ACTIVE_SIGNUP_STATUSES = [SIGNUP_STATUS.CONFIRMED, SIGNUP_STATUS.COMPLETED];

// A listing can have maximum 3 images.
export const MAX_IMAGES = 3;

// Lists to store the cause categories and the skills.
export const CAUSE_CATEGORIES = [
  'Children & Youth', 'Elderly Care', 'Soup Kitchens', 'Animal Welfare', 'Homeless Shelters',
  'Tutoring & Reading', 'Environment & Conservation', 'Health & Wellbeing', 'Disaster Relief',
  'Community Development', 'Disability Support',
];
export const SKILLS = [
  'Tutoring', 'Reading to Children', 'Manual Labour', 'Cooking or Food Prep', 'Admin & Organising',
  'Fundraising', 'Marketing & Social Media', 'Translation or Multilingual', 'First Aid',
];