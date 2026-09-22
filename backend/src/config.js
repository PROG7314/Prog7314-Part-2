// config.js - reads the settings the API needs from environment variables

// When running locally they come from the .env file. When running on Render they come from the dashboard.
export function loadConfig(env = process.env) {
  const jwtSecret = env.JWT_SECRET;

  // Checking the length of the JWT secret for security.
  if (!jwtSecret || jwtSecret.length < 32) {
    throw new Error('JWT_SECRET must be set and at least 32 characters long.');
  }

  return {
  // Render sets the PORT itself. If it is missing it sets back to 3000.
    port: Number(env.PORT) || 3000,
    jwtSecret,
    // How long the login lasts before the user has to sign in again.
    jwtExpiresIn: env.JWT_EXPIRES_IN || '30d',
    // This is on unless explicitly set to the text "false"
    enforceFixedLists: env.ENFORCE_FIXED_LISTS !== 'false',
    // Request logging is switched off when NODE_ENV=test so that the test output stays clean.
    logRequests: env.NODE_ENV !== 'test',
  };
}