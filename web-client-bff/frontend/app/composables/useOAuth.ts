export function useOAuth() {
  const isLoggedIn = ref(false)
  const idTokenClaims = ref<{
    email?: string
    userGUID?: string
    roles?: string[]
  } | null>(null)

  async function checkStatus() {
    const response = await fetch('/api/auth/status', {
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`Status check failed (${response.status})`)
    }

    const status = await response.json() as {
      isLoggedIn: boolean
      idTokenClaims?: {
        email?: string
        userGUID?: string
        roles?: string[]
      }
    }

    isLoggedIn.value = status.isLoggedIn
    idTokenClaims.value = status.idTokenClaims ?? null
    return status
  }

  function startLogout() {
    window.location.href = '/logout'
  }

  function authorize() {
    window.location.href = '/oauth2/authorization/web-client-pkce-registration'
  }

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

  return { checkStatus, authorize, login, startLogout, isLoggedIn, idTokenClaims }
}
