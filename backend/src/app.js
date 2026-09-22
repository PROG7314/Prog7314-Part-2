import express from 'express';
import { authenticate } from './middleware/auth.js';
import { errorHandler, notFoundHandler } from './middleware/errorHandler.js';
import { authRouter } from './routes/auth.js';
import { usersRouter } from './routes/users.js';
import { listingsRouter } from './routes/listings.js';
import { signupsRouter } from './routes/signups.js';

/**
* db and firebaseAuth are passed in here so that the tests can swap in fake ones.
*/
export function createApp({ db, firebaseAuth, cfg }) {
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '100kb' }));

// In the host log this uses one line per request
    if (cfg.logRequests) {
        app.use((req, res, next) => {
          const start = Date.now();
          res.on('finish', () => console.log(`${req.method} ${req.originalUrl} -> ${res.statusCode} (${Date.now() - start}ms)`));
          next();
        });
      }

// Render's a health check
    app.get('/health', (req, res) => res.json({ status: 'ok' }));

     const auth = authenticate({ db, cfg });
      const api = express.Router();
      api.use('/auth', authRouter({ db, firebaseAuth, cfg, auth })); // POST /auth/login is the only unauthenticated endpoint
      api.use(auth); // everything below needs a valid JWT
      api.use('/users', usersRouter({ db, cfg }));
      api.use('/listings', listingsRouter({ db, cfg }));
      api.use('/', signupsRouter({ db }));

      app.use('/api/v1', api);
      app.use(notFoundHandler);
      app.use(errorHandler);
      return app;
}