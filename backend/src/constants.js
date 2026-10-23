// Single source of truth for the fixed value lists and enums used across the API.

export const ROLES = ['volunteer', 'organization'];
export const LISTING_TYPES = ['Scheduled', 'Ongoing'];
export const LANGUAGES = ['en', 'af', 'zu', 'xh'];

export const SIGNUP_STATUS = {
  CONFIRMED: 'confirmed',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
};
// A sign-up that still occupies a space on the listing.
export const ACTIVE_SIGNUP_STATUSES = [SIGNUP_STATUS.CONFIRMED, SIGNUP_STATUS.COMPLETED];

export const MAX_IMAGES = 3;

// ---------------------------------------------------------------------------------------------
// These lists must match, character for character, the causes and skills shown in the app's
// chips. They're copied from InterestMatchingViewModel.availableCauses / availableSkills (the
// Figma-wording version, not the shorter Part 1 design-doc placeholder these used to hold) -
// matching between volunteers and listings is exact string comparison, so if either list ever
// changes on the Android side, update it here too in the same commit, or every match silently
// breaks. Set ENFORCE_FIXED_LISTS=false to switch validation off if the two drift again.
// ---------------------------------------------------------------------------------------------
export const CAUSE_CATEGORIES = [
  'Children & Youth',
  'Elderly Care',
  'Soup Kitchens',
  'Animal Welfare',
  'Homeless Shelters',
  'Tutoring & Reading',
  'Environment & Conservation',
  'Health & Wellbeing',
  'Disaster Relief',
  'Community Development',
  'Disability Support',
];
export const SKILLS = [
  'Tutoring',
  'Reading to Children',
  'Manual Labour',
  'Cooking or Food Prep',
  'Admin & Organising',
  'Fundraising',
  'Marketing & Social Media',
  'Translation or Multilingual',
  'First Aid',
];