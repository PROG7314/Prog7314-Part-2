// errors.js - one way to report problems, so the Android app always gets the same JSON shape log back.

/**
 * Every error the API sends has one shape:
 *   { "error": { "code": "...", "message": "...", "field": "..."? } }
 * The Android client turns these into user-facing messages.
 */
export class AppError extends Error {
  constructor(status, code, message, field) {
    super(message);
    this.status = status;
    this.code = code;
    this.field = field;
  }
}

// Routes just write `throw notFound('Listing not found.')`
// and the error handler (middleware/errorHandler.js) turns it into the JSON response.

// 400 - the request itself is wrong (missing field, wrong type, value not allowed)
export const badRequest = (message, field, code = 'validation_error') => new AppError(400, code, message, field);

// 401 - not logged in, or the token is missing, expired, invalid
export const unauthorized = (message = 'Authentication required.') => new AppError(401, 'unauthorized', message);

// 403 - logged in, but not allowed to do this (wrong role, or someone else's data)
export const forbidden = (message = "You don't have permission to do that.", code = 'forbidden') =>
  new AppError(403, code, message);

// 404 - the thing asked for doesn't exist
export const notFound = (message = 'Not found.') => new AppError(404, 'not_found', message);

// 409 - the request is fine, but clashes with the current state (listing full, already signed up)
export const conflict = (code, message) => new AppError(409, code, message);