<template>
  <v-card width="400">
    <form method="post" action="/login">
      <v-card-title>Login</v-card-title>
      <v-card-text>
        <v-text-field name="email" label="Email" type="email" />
        <v-text-field name="password" label="Password" type="password" />
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit">Login</v-btn>
      </v-card-actions>
    </form>
  </v-card>
</template>

<script setup lang="ts">
</script>
