<template>
  <div>
    <v-card width="400">
      <form method="post" action="/login">
        <v-card-title>Login</v-card-title>
        <v-card-text>
          <v-text-field name="email" label="Email" type="email" />
          <v-text-field name="password" label="Password" type="password" />
          <v-checkbox name="remember-me" value="true" label="Remember Me?" />
        </v-card-text>
        <v-card-actions>
          <v-btn type="submit">Login</v-btn>
        </v-card-actions>
      </form>
    </v-card>

    <form method="post" action="/login/guest">
      <v-card width="400" class="mt-2">
        <v-card-actions class="justify-center">
          <v-btn type="submit" variant="text">Continue as Guest</v-btn>
        </v-card-actions>
      </v-card>
    </form>
  </div>
</template>

<script setup lang="ts">
</script>
