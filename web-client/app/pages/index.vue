<script setup lang="ts">
// The post-login landing: the bff redirects the browser here after the callback —
// "/" on success, "/?e=login_failed" on any failure. On success (or a plain visit)
// a status check stores the id_token claims before moving on to /home; on failure
// the retry card re-runs the whole login flow.
const route = useRoute()
const { checkStatus, login } = useOAuth()

const loginFailed = computed(() => route.query.e !== undefined)

onMounted(async () => {
  if (loginFailed.value) {
    return
  }
  try {
    await checkStatus()
  } catch {
    // The bff being unreachable shouldn't strand the user on a blank page.
  }
  navigateTo('/home', { replace: true })
})
</script>

<template>
  <v-container class="d-flex justify-center align-center" style="min-height: 60vh">
    <v-card v-if="loginFailed" max-width="600">
      <v-card-title class="text-error">Login failed</v-card-title>
      <v-card-text>Something went wrong while logging you in. Please try again.</v-card-text>
      <v-card-actions>
        <v-btn @click="login">Try again</v-btn>
      </v-card-actions>
    </v-card>
    <v-progress-circular v-else indeterminate color="primary" size="64" />
  </v-container>
</template>
