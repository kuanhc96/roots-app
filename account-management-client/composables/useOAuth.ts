/**
 * Client for the account-management-bff's auth endpoints. Mirrors the web-client's
 * useOAuth composable but points to account-management-bff instead of bff-server.
 * 
 * The browser never sees tokens or the client secret — the account-management-bff
 * holds them in Redis keyed by the __Host-AMC_SESSION cookie, and this composable
 * only handles the id_token claims the bff serves from /api/auth/status. The claims
 * are deserialized from the response and stored as sessionStorage keys; `email`'s
 * presence is what "logged in" means to the UI.
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
  email?: string
  userGUID?: string
  roles?: string[]
}

export function useOAuth() {
  const config = useRuntimeConfig()

  const isLoggedIn = ref(import.meta.client ? !!sessionStorage.getItem(EMAIL_STORAGE_KEY) : false)

  /**
   * Asks the account-management-bff whether this browser session has a valid login.
   * The __Host-AMC_SESSION cookie rides along via credentials: 'include'.
   * On "logged in", id_token claims are deserialized from the response and stored
   * as sessionStorage keys; on "not logged in", stale claims are cleared.
   */
  async function checkStatus(): Promise<LoginStatus> {
    const response = await fetch(
      `${config.public.accountManagementBffUrl}/auth/status`,
      {
        credentials: 'include',
      }
    )
    if (!response.ok) {
      throw new Error(`Status check failed (${response.status})`)
    }

    const status: LoginStatus = await response.json()
    if (status.isLoggedIn) {
      storeClaims({ email: status.email, userGUID: status.userGUID, roles: status.roles })
    } else {
      clearClaims()
    }
    isLoggedIn.value = status.isLoggedIn
    return status
  }

  /**
   * Kicks off the authorization-code flow: a full browser navigation to the bff,
   * which owns the state and every OAuth2 parameter and 302s on to auth-server.
   */
  function authorize() {
    window.location.href = `${config.public.accountManagementBffUrl}/auth/authorize`
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
   * Starts server-side logout: a full browser navigation to the bff, which deletes
   * the session's Redis token keys and drives OIDC RP-initiated logout against
   * auth-server with the id_token it holds. auth-server redirects the browser to
   * account-management-client's /logout landing page.
   */
  function startLogout() {
    window.location.href = `${config.public.accountManagementBffUrl}/auth/logout`
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
