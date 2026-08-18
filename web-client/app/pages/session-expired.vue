<template>
  <v-container class="d-flex justify-center align-center" style="min-height: 60vh">
    <v-card>
      <v-card-text>Your login session has expired.</v-card-text>
      <v-card-actions>
        <v-btn @click="login">login</v-btn>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
const { login, logout } = useOAuth()

onMounted(() => {
  logout()
})
</script>
