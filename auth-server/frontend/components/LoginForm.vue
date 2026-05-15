<template>
  <div>
    <form id="login-form" method="post" action="/login"></form>
    <form id="guest-form" method="post" action="/login/guest"></form>

    <v-card width="400">
      <v-card-title>Login</v-card-title>
      <v-card-text>
        <v-text-field name="email" label="Email" type="email" form="login-form" />
        <v-text-field name="password" label="Password" type="password" form="login-form" />
        <v-checkbox name="remember-me" value="true" label="Remember Me?" form="login-form" />
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit" form="login-form">Login</v-btn>
        <v-spacer></v-spacer>
        <v-btn type="submit" form="guest-form" variant="text">Continue as Guest</v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>

<script setup lang="ts">
</script>
