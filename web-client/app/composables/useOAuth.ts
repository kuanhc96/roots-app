export function useOAuth() {
  const config = useRuntimeConfig()

  const existingToken = import.meta.client ? sessionStorage.getItem('access_token') : null
  const isLoggedIn = ref(!!(existingToken?.trim() && !isTokenExpired(existingToken)))

  async function authorize() {
    if (isLoggedIn.value) return

    const refreshToken = sessionStorage.getItem('refresh_token')
    if (refreshToken) {
      const credentials = btoa(`${config.public.webClientId}:${config.public.webClientSecret}`)
      const res = await fetch(`${config.public.authServerUrl}/oauth2/token`, {
        method: 'POST',
        headers: {
          Authorization: `Basic ${credentials}`,
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: refreshToken }),
      })
      if (res.ok) {
        const data = await res.json()
        sessionStorage.setItem('access_token', data.access_token)
        if (data.refresh_token) sessionStorage.setItem('refresh_token', data.refresh_token)
        if (data.id_token) sessionStorage.setItem('id_token', data.id_token)
        isLoggedIn.value = true
        return
      }
      sessionStorage.removeItem('refresh_token')
    }

    const state = crypto.randomUUID()
    sessionStorage.setItem('oauth_state', state)
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: config.public.webClientId,
      redirect_uri: `${window.location.origin}/callback`,
      scope: 'openid WEB_CLIENT_READ',
      state,
    })
    window.location.href = `${config.public.authServerUrl}/oauth2/authorize?${params.toString()}`
  }

  function logout() {
    const idToken = sessionStorage.getItem('id_token')
    sessionStorage.removeItem('access_token')
    sessionStorage.removeItem('id_token')
    sessionStorage.removeItem('refresh_token')
    sessionStorage.removeItem('oauth_state')

    const params = new URLSearchParams({
      post_logout_redirect_uri: `${window.location.origin}/logout`,
    })
    if (idToken) params.set('id_token_hint', idToken)

    window.location.href = `${config.public.authServerUrl}/connect/logout?${params.toString()}`
  }

  return { authorize, logout, isLoggedIn }
}

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return Date.now() / 1000 > payload.exp
  } catch {
    return true
  }
}
