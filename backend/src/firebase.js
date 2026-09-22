// firebase.js - connects the API to Firebase using Admin SDK

import { initializeApp, cert, getApps } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';

// Reads the service account key from an environment variable.
function readServiceAccount(env) {
  const raw = env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) {
    throw new Error('FIREBASE_SERVICE_ACCOUNT is not set. Paste the service-account JSON (or its base64) into it.');
  }
  // Accept the raw JSON, or base64 of it.
  // Raw JSON always starts with { and anything else is treated as base64
  const text = raw.trim().startsWith('{') ? raw : Buffer.from(raw, 'base64').toString('utf8');
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('FIREBASE_SERVICE_ACCOUNT is not valid JSON (or base64 of the JSON).');
  }
}

export function initFirebase(env = process.env) {
// Firebase must only be initialised once per process
  if (getApps().length === 0) {
    initializeApp({ credential: cert(readServiceAccount(env)) });
  }
  return { db: getFirestore(), firebaseAuth: getAuth() };
}