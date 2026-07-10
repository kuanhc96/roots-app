// Maps machine-readable error codes (the `e` query param on redirects from the
// auth-server) to the messages the frontend displays. The server only ever sends a
// code; the display text is owned here. An unmapped code displays nothing.
// Mirrored by the server-side ErrorCode enum (com.roots.authserver.enums.ErrorCode)
// — keep the two in sync.
export const errorMessages: Record<string, string> = {
  invalid_login: 'The provided email/password combination is invalid',
  invalid_token: 'This verification code or link is invalid or has expired. Please request a new one.',
  invalid_password: 'The new password does not meet the password requirements',
  oauth_redirect_failed: 'Something went wrong resuming your sign-in. Please start over.',
  no_mfa_pending: 'Your login session has expired. Please log in again.',
  email_taken: 'The email you provided already exists on another account. Please log in',
  invalid_request: 'The signup information provided is invalid. Please try again.',
  social_login_failed: 'Google sign-in failed. Please try again or sign in with your password.'
}
