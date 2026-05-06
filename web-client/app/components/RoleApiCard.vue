<template>
  <v-card>
    <v-card-title>Roles</v-card-title>
    <v-card-text>
      <v-row class="mb-4" wrap>
        <v-col v-for="btn in roleButtons" :key="btn.label" cols="auto">
          <v-btn :disabled="loading" @click="callRole(btn.method)">
            {{ btn.label }}
          </v-btn>
        </v-col>
      </v-row>
      <v-alert v-if="response" type="success" variant="tonal">{{ response }}</v-alert>
      <v-alert v-if="error" type="error" variant="tonal">{{ error }}</v-alert>
    </v-card-text>
    <v-card-actions>
      <v-btn @click="authorize">authorize</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
const client = useSimpleResourceClient()
const config = useRuntimeConfig()

const response = ref<string>('')
const error = ref<string>('')
const loading = ref(false)

type RoleMethod = () => Promise<{ data: string }>

const roleButtons: { label: string; method: RoleMethod }[] = [
  { label: 'Pastor', method: () => client.getPastor() },
  { label: 'Deacon', method: () => client.getDeacon() },
  { label: 'Small Group Leader', method: () => client.getSmallGroupLeader() },
  { label: 'Vice Small Group Leader', method: () => client.getViceSmallGroupLeader() },
  { label: 'Member', method: () => client.getMember() },
  { label: 'Guest', method: () => client.getGuest() },
]

async function callRole(method: RoleMethod) {
  response.value = ''
  error.value = ''
  loading.value = true
  try {
    const res = await method()
    response.value = res.data
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Request failed'
  } finally {
    loading.value = false
  }
}

async function authorize() {
  const accessToken = sessionStorage.getItem('access_token')
  if (accessToken?.trim() && !isTokenExpired(accessToken)) return

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

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return Date.now() / 1000 > payload.exp
  } catch {
    return true
  }
}
</script>
