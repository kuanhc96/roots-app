<template>
  <v-card width="400">
    <v-card-title>Login</v-card-title>
    <v-card-text>
      <v-text-field v-model="email" label="Email" type="email" />
      <v-text-field v-model="password" label="Password" type="password" />
    </v-card-text>
    <v-card-actions>
      <v-btn @click="login">Login</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
const email = ref('')
const password = ref('')
function login() {}
</script>
