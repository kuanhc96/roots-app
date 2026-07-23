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
      <!-- "check status" and "authorize" hit the bff endpoints directly (testing);
           "login" runs the full flow: status check, then authorize if needed. -->
      <v-btn @click="checkLoginStatus">check status</v-btn>
      <v-btn @click="authorize">authorize</v-btn>
      <v-btn @click="login">login</v-btn>
      <v-btn v-if="isLoggedIn" @click="logout">logout</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
const client = useSimpleResourceClient()
const { checkStatus, authorize, login, logout, isLoggedIn } = useOAuth()

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

async function checkLoginStatus() {
  response.value = ''
  error.value = ''
  try {
    response.value = JSON.stringify(await checkStatus())
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Status check failed'
  }
}

</script>
