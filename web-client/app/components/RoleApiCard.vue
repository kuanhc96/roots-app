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

function authorize() {
  const accessToken = sessionStorage.getItem('access_token')
  if (!accessToken?.trim()) {
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

}
</script>
