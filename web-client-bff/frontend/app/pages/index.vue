<template>
  <v-container class="d-flex justify-center align-center" style="min-height: 60vh">
    <v-card max-width="600">
      <v-card-title>Web Client BFF</v-card-title>
      <v-card-text>Start OAuth2 login, then use the role buttons on the home page.</v-card-text>
      <v-card-actions>
        <v-btn @click="login">Login</v-btn>
        <v-btn @click="navigateTo('/home')">Go to Home</v-btn>
      </v-card-actions>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
const { login } = useOAuth()
</script>
