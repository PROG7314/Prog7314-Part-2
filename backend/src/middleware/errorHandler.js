// middleware/errorHandler.js - the last stop for every error. Whatever goes wrong anywhere in the API,
// the Android app gets the same JSON shape back: { "error": { "code", "message", "field"? } }
import { AppError } from '../errors.js';

// Runs when no route matched the request, and turns it into a 404 for the error handler below.
export function notFoundHandler(req, res, next) {
  next(new AppError(404, 'not_found', `No route for ${req.method} ${req.path}.`));
}

// Express recognises an error handler by its FOUR parameters (err, req, res, next),
// so `next` has to stay in the list even though it is rarely used.
// eslint-disable-next-line no-unused-vars
export function errorHandler(err, req, res, next) {
  // If a response has already started, we can't send another one; hand it to Express's default handler.
  if (res.headersSent) return next(err);

  // Errors we threw on purpose (badRequest, notFound, conflict...): send them as they are.
  if (err instanceof AppError) {
    const body = { code: err.code, message: err.message };
    if (err.field) body.field = err.field; // only included when the error is about one input
    return res.status(err.status).json({ error: body });
  }
  // express.json() failures
  if (err?.type === 'entity.parse.failed') {
    return res.status(400).json({ error: { code: 'validation_error', message: 'The request body is not valid JSON.' } });
  }
  if (err?.type === 'entity.too.large') {
    return res.status(413).json({ error: { code: 'payload_too_large', message: 'The request body is too large.' } });
  }

  // Anything else is a bug or an outage on our side. Log the details for us (Render logs) but show
  // the user only a generic message, so we never leak internal information.
  console.error('Unhandled error:', err);
  res.status(500).json({ error: { code: 'internal_error', message: 'Something went wrong on our side. Please try again.' } });
}