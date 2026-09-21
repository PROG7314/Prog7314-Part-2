// asyncHandler.js - a small wrapper for route handlers that use async/await.

// Forwards rejected promises to the error handler.
export const ah = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);