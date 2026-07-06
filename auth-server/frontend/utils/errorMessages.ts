// Maps machine-readable error codes (the `error` query param on redirects from the
// auth-server) to the messages the frontend displays. The server only ever sends a
// code; the display text is owned here. An unmapped code displays nothing.
export const errorMessages: Record<string, string> = {
  invalidLogin: 'The provided email/password combination is invalid',
  invalidToken: 'This verification code or link is invalid or has expired. Please request a new one.',
  invalidPassword: 'The new password does not meet the password requirements',
  oauthRedirectFailed: 'Something went wrong resuming your sign-in. Please start over.',
  noMfaPending: 'Your login session has expired. Please log in again.',
}
