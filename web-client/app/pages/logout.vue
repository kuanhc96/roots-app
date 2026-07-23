<template>
  <v-container class="d-flex justify-center align-center" style="min-height: 60vh">
    <v-card>
      <v-card-text>You have been logged out.</v-card-text>
      <v-card-actions>
        <v-btn @click="login">login</v-btn>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
// Post-logout landing (the registered post_logout_redirect_uri): the future bff
// server-side logout will send the browser here after OIDC logout completes.
const { login, logout } = useOAuth()

onMounted(() => {
  logout()
})
</script>
