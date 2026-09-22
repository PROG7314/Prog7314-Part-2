import { loadConfig } from './config.js';
import { initFirebase } from './firebase.js';
import { createApp } from './app.js';

// Stops immediately with a message if JWT_SECRET is missing or too short.
const cfg = loadConfig();

// Connects to the database (Firestore) and Firebase authentication
const { db, firebaseAuth } = initFirebase();

// Builds the Express app with those external connections above
createApp({ db, firebaseAuth, cfg }).listen(cfg.port, () => {
  console.log(`Aegis API listening on port ${cfg.port}`);
});