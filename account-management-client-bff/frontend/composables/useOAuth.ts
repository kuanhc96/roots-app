/**
 * Client for account-management-client-bff's auth endpoints.
 *
 * The browser never sees OAuth tokens or client credentials. The bff owns the
 * OAuth2 client session and serves only id_token claims from /api/auth/status.
 * Claims are mirrored into sessionStorage; `email` indicates logged-in state.
 */

const EMAIL_STORAGE_KEY = 'id_token_email'
const USER_GUID_STORAGE_KEY = 'id_token_user_guid'
const ROLES_STORAGE_KEY = 'id_token_roles'

export interface IdTokenClaims {
  email?: string
  userGUID?: string
  roles?: string[]
}

export interface LoginStatus {
  isLoggedIn: boolean
  idTokenClaims?: IdTokenClaims
}

export function useOAuth() {
  const isLoggedIn = ref(import.meta.client ? !!sessionStorage.getItem(EMAIL_STORAGE_KEY) : false)

  /**
   * Asks the bff whether this browser session has a valid login.
   * The __Host-AMC_SESSION cookie rides along via credentials: 'include'.
   * On "logged in", id_token claims are deserialized from the response and stored
   * as sessionStorage keys; on "not logged in", stale claims are cleared.
   */
  async function checkStatus(): Promise<LoginStatus> {
    const response = await fetch('/api/auth/status', {
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`Status check failed (${response.status})`)
    }

    const status: LoginStatus = await response.json()
    if (status.isLoggedIn && status.idTokenClaims) {
      storeClaims(status.idTokenClaims)
    } else {
      clearClaims()
    }
    isLoggedIn.value = status.isLoggedIn
    return status
  }

  /**
   * Kicks off OAuth2 login via Spring Security's auto endpoint for the configured
   * client registration.
   */
  function authorize() {
    window.location.href = '/oauth2/authorization/account-management-pkce-registration'
  }

  /** The full login flow: check status, and if not logged in, authorize. */
  async function login() {
    try {
      const status = await checkStatus()
      if (!status.isLoggedIn) {
        authorize()
      }
    } catch {
      authorize()
    }
  }

  /**
   * Starts server-side logout: a full browser navigation to the bff, which
   * invalidates the HTTP session and drives OIDC RP-initiated logout.
   */
  function startLogout() {
    window.location.href = '/api/auth/logout'
  }

  /**
   * Local-only logout: forgets the claims, flipping the UI to logged-out. Run from
   * the /logout landing page after the server-side round-trip completes; never
   * triggers the server flow itself (that's `startLogout()`).
   */
  function logout() {
    clearClaims()
    isLoggedIn.value = false
  }

  /** The stored id_token claims, or null when logged out. */
  function getClaims(): IdTokenClaims | null {
    if (!import.meta.client) {
      return null
    }
    const email = sessionStorage.getItem(EMAIL_STORAGE_KEY)
    if (!email) {
      return null
    }
    const userGUID = sessionStorage.getItem(USER_GUID_STORAGE_KEY)
    const roles = sessionStorage.getItem(ROLES_STORAGE_KEY)
    return {
      email,
      userGUID: userGUID ?? undefined,
      roles: roles ? JSON.parse(roles) : undefined,
    }
  }

  function storeClaims(claims: IdTokenClaims) {
    if (claims.email) {
      sessionStorage.setItem(EMAIL_STORAGE_KEY, claims.email)
    } else {
      sessionStorage.removeItem(EMAIL_STORAGE_KEY)
    }

    // Guest login carries no userGUID claim — clear any stale value.
    if (claims.userGUID) {
      sessionStorage.setItem(USER_GUID_STORAGE_KEY, claims.userGUID)
    } else {
      sessionStorage.removeItem(USER_GUID_STORAGE_KEY)
    }

    if (claims.roles) {
      sessionStorage.setItem(ROLES_STORAGE_KEY, JSON.stringify(claims.roles))
    } else {
      sessionStorage.removeItem(ROLES_STORAGE_KEY)
    }
  }

  function clearClaims() {
    sessionStorage.removeItem(EMAIL_STORAGE_KEY)
    sessionStorage.removeItem(USER_GUID_STORAGE_KEY)
    sessionStorage.removeItem(ROLES_STORAGE_KEY)
  }

  return { checkStatus, authorize, login, startLogout, logout, getClaims, isLoggedIn }
}
