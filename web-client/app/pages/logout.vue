<template>
  <v-container class="d-flex justify-center align-center" style="min-height: 60vh">
    <v-card>
      <v-card-text>You have been logged out.</v-card-text>
      <v-card-actions>
        <v-btn @click="authorize">authorize</v-btn>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
const { authorize } = useOAuth()

onMounted(() => {
  sessionStorage.removeItem('access_token')
  sessionStorage.removeItem('id_token')
  sessionStorage.removeItem('refresh_token')
  sessionStorage.removeItem('oauth_state')
})
</script>
