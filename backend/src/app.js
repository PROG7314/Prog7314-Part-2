import express from 'express';
import { authenticate } from './middleware/auth.js';
import { errorHandler, notFoundHandler } from './middleware/errorHandler.js';
import { authRouter } from './routes/auth.js';
import { usersRouter } from './routes/users.js';
import { listingsRouter } from './routes/listings.js';
import { signupsRouter } from './routes/signups.js';
import { messagesRouter } from './routes/messages.js';
import { createNotifier } from './notifications.js';

/**
 * db, firebaseAuth and messaging are passed in (not imported) so the tests can swap in fakes.
 * Without `messaging`, notifications are simply switched off.
 * All API endpoints live under /api/v1, as in the Part 1 spec.
 */
export function createApp({ db, firebaseAuth, cfg, messaging }) {
  const app = express();
  const notifier = createNotifier({ db, messaging });
  app.locals.notifier = notifier; // the tests await notifier.settled()
  app.disable('x-powered-by');
  app.use(express.json({ limit: '100kb' }));

  // One line per request in the host's logs - handy evidence for the demo video. Never logs bodies or tokens.
  if (cfg.logRequests) {
    app.use((req, res, next) => {
      const start = Date.now();
      res.on('finish', () => console.log(`${req.method} ${req.originalUrl} -> ${res.statusCode} (${Date.now() - start}ms)`));
      next();
    });
  }

  // Render's health check, and a cheap way to wake a sleeping free-tier service before a demo.
  app.get('/health', (req, res) => res.json({ status: 'ok' }));

  const auth = authenticate({ db, cfg });
  const api = express.Router();
  api.use('/auth', authRouter({ db, firebaseAuth, cfg, auth })); // POST /auth/login is the only unauthenticated endpoint
  api.use(auth); // everything below needs a valid JWT
  api.use('/users', usersRouter({ db, cfg }));
  api.use('/listings', listingsRouter({ db, cfg, notifier }));
  api.use('/', signupsRouter({ db, notifier }));
  api.use('/', messagesRouter({ db }));

  app.use('/api/v1', api);
  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}