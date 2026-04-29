<script setup lang="ts">
const config = useRuntimeConfig()
const router = useRouter()

const error = ref<string | null>(null)

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const code = params.get('code')
  const returnedState = params.get('state')
  const errorParam = params.get('error')

  if (errorParam) {
    error.value = `Authorization error: ${errorParam} — ${params.get('error_description') ?? ''}`
    return
  }

  if (!code || !returnedState) {
    error.value = 'Missing code or state in callback URL.'
    return
  }

  const storedState = sessionStorage.getItem('oauth_state')
  if (!storedState || returnedState !== storedState) {
    error.value = 'State mismatch — possible CSRF attack. Please try logging in again.'
    sessionStorage.removeItem('oauth_state')
    return
  }
  sessionStorage.removeItem('oauth_state')

  const credentials = btoa(`${config.public.webClientId}:${config.public.webClientSecret}`)
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: `${window.location.origin}/callback`,
  })

  try {
    const response = await fetch(`${config.public.authServerUrl}/oauth2/token`, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${credentials}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: body.toString(),
    })

    if (!response.ok) {
      error.value = `Token exchange failed (${response.status}): ${await response.text()}`
      return
    }

    const tokenData = await response.json()
    if (!tokenData.access_token) {
      error.value = 'Token response did not contain an access_token.'
      return
    }

    sessionStorage.setItem('access_token', tokenData.access_token)
    if (tokenData.id_token) sessionStorage.setItem('id_token', tokenData.id_token)

    router.replace('/home')
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Unknown error during token exchange.'
  }
})
</script>

<template>
  <v-container class="d-flex justify-center align-center" style="min-height: 100vh;">
    <v-card v-if="error" max-width="600">
      <v-card-title class="text-error">Authentication Error</v-card-title>
      <v-card-text>{{ error }}</v-card-text>
      <v-card-actions>
        <v-btn @click="$router.push('/home')">Try Again</v-btn>
      </v-card-actions>
    </v-card>
    <v-progress-circular v-else indeterminate color="primary" size="64" />
  </v-container>
</template>
