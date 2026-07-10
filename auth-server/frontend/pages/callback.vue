<script setup lang="ts">
// Google redirects here with ?code&state after the user consents. The page validates
// state, exchanges the code for tokens (browser-side for now — a server-side exchange
// is planned, which will also remove the client secret from the bundle), then hands the
// id_token to the backend via an auto-submitted native form POST: a real navigation, so
// the browser follows the server's 302 chain to the saved /oauth2/authorize request and
// on to the client's callback. Any failure bounces to /login?e=social_login_failed.
const config = useRuntimeConfig()

const idToken = ref('')
const loginForm = ref<HTMLFormElement | null>(null)

async function fail() {
  await navigateTo({ path: '/login', query: { e: 'social_login_failed' } }, { replace: true })
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const returnedState = params.get('state')
  console.log("returned state " + returnedState)
  const errorParam = params.get('error')

  const expectedState = sessionStorage.getItem('google_oauth_state')
  sessionStorage.removeItem('google_oauth_state')

  if (errorParam || !code || !returnedState || !expectedState || returnedState !== expectedState) {
    await fail()
    return
  }

  const credentials = btoa(`${config.public.googleClientId}:${config.public.googleClientSecret}`)
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: `${window.location.origin}/callback`,
  })

  try {
    const response = await fetch(`${config.public.googleAuthServerUrl}/token`, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${credentials}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: body.toString(),
    })

    if (!response.ok) {
      await fail()
      return
    }

    const tokenData = await response.json()
    if (!tokenData.id_token) {
      await fail()
      return
    }

    // Kept in sessionStorage for now; nothing consumes these yet.
    if (tokenData.access_token) sessionStorage.setItem('google_access_token', tokenData.access_token)
    sessionStorage.setItem('google_id_token', tokenData.id_token)
    if (tokenData.refresh_token) sessionStorage.setItem('google_refresh_token', tokenData.refresh_token)

    idToken.value = tokenData.id_token
    await nextTick()
    loginForm.value?.submit()
  } catch {
    await fail()
  }
})
</script>

<template>
  <div>
    <form ref="loginForm" method="post" action="/login/google">
      <input type="hidden" name="idToken" :value="idToken">
    </form>
    <v-container class="d-flex justify-center align-center" style="min-height: 100vh;">
      <v-progress-circular indeterminate color="primary" size="64" />
    </v-container>
  </div>
</template>
