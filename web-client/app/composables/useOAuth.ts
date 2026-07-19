/**
 * Client for the bff-server's auth endpoints. The browser never sees tokens or the
 * client secret anymore: the bff holds them in Redis keyed by the SESSION cookie,
 * and this composable only ever handles the id_token *claims* the bff serves from
 * /api/auth/status. The claims live under one sessionStorage key; their presence is
 * what "logged in" means to the UI.
 */

const CLAIMS_STORAGE_KEY = 'id_token_claims'

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

  const isLoggedIn = ref(import.meta.client ? !!sessionStorage.getItem(CLAIMS_STORAGE_KEY) : false)

  /**
   * Asks the bff whether this browser session has a valid login (the SESSION
   * cookie rides along via credentials: 'include'). On "logged in" the id_token
   * claims are stored in sessionStorage; on "not logged in" any stale claims are
   * cleared. Returns the raw status response.
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
      const claims: IdTokenClaims = {
        email: status.email,
        userGUID: status.userGUID,
        roles: status.roles,
      }
      sessionStorage.setItem(CLAIMS_STORAGE_KEY, JSON.stringify(claims))
    } else {
      sessionStorage.removeItem(CLAIMS_STORAGE_KEY)
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
    sessionStorage.removeItem(CLAIMS_STORAGE_KEY)
    isLoggedIn.value = false
  }

  /** The stored id_token claims, or null when logged out. */
  function getClaims(): IdTokenClaims | null {
    const stored = import.meta.client ? sessionStorage.getItem(CLAIMS_STORAGE_KEY) : null
    return stored ? JSON.parse(stored) : null
  }

  return { checkStatus, authorize, login, logout, getClaims, isLoggedIn }
}
