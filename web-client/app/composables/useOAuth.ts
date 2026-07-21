/**
 * Client for the bff-server's auth endpoints. The browser never sees tokens or the
 * client secret anymore: the bff holds them in Redis keyed by the SESSION cookie,
 * and this composable only ever handles the id_token *claims* the bff serves from
 * /api/auth/status. The claims are deserialized from that JSON response and stored
 * as three separate sessionStorage keys; `email`'s presence is what "logged in"
 * means to the UI (it's the one claim every login has — guest included — unlike
 * userGUID, which a guest login never carries).
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
   * Asks the bff whether this browser session has a valid login (the SESSION
   * cookie rides along via credentials: 'include'). On "logged in" the id_token
   * claims are deserialized out of the response and stored as three separate
   * sessionStorage keys; on "not logged in" any stale claims are cleared. Returns
   * the raw status response.
   */
  async function checkStatus(): Promise<LoginStatus> {
    const response = await fetch(`${config.public.bffServerUrl}/api/auth/status`, {
      credentials: 'include',
    })
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
    window.location.href = `${config.public.bffServerUrl}/api/auth/authorize`
  }

  /** The full login flow: already logged in? claims are stored and we're done — otherwise authorize. */
  async function login() {
    const status = await checkStatus()
    if (!status.isLoggedIn) {
      authorize()
    }
  }

  /**
   * Local-only logout: forgets the claims, flipping the UI to logged-out.
   * TODO: implement server-side logout — a bff /api/auth/logout endpoint that
   * deletes the session's Redis token keys and drives OIDC RP-initiated logout
   * against auth-server with the id_token it holds (the browser no longer has the
   * id_token, so it cannot send id_token_hint itself).
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

    // A guest login carries no userGUID claim — clear any stale value rather than
    // storing one that doesn't belong to this login.
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

  return { checkStatus, authorize, login, logout, getClaims, isLoggedIn }
}
