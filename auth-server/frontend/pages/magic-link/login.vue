<template>
  <v-container class="fill-height d-flex align-center justify-center">
    <v-card width="400">
      <!-- The magicLinkToken is captured from the URL by SpaController on GET and
           stored in the session, so this form posts no token field. The button
           click (not an auto-submit) is what consumes the one-time token, which
           keeps email link-prefetchers from burning it. -->
      <form method="post" action="/magic-link/login">
        <v-card-title>Verify your email</v-card-title>
        <v-card-text>Click below to complete your sign in.</v-card-text>
        <v-card-actions>
          <v-btn type="submit" color="primary">Continue with login</v-btn>
        </v-card-actions>
      </form>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
</script>
