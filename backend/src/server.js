import { loadConfig } from './config.js';
import { initFirebase } from './firebase.js';
import { createApp } from './app.js';

const cfg = loadConfig();
const { db, firebaseAuth, messaging } = initFirebase();

createApp({ db, firebaseAuth, cfg, messaging }).listen(cfg.port, () => {
  console.log(`Aegis API listening on port ${cfg.port}`);
});