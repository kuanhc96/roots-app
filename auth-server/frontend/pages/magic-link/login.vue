<template>
  <v-container class="fill-height d-flex align-center justify-center">
    <v-card width="400">
      <!-- The magicLinkToken is read from the emailed link's query string and posted
           as a hidden field. The button click (not an auto-submit) is what consumes
           the one-time token, which keeps email link-prefetchers from burning it. -->
      <form method="post" action="/magic-link/login">
        <input type="hidden" name="magicLinkToken" :value="magicLinkToken" />
        <v-card-title>Verify your email</v-card-title>
        <v-card-text>
          Click below to complete your sign in.
          <v-alert v-if="magicLinkErrorMessage" type="warning" density="compact">
            {{ magicLinkErrorMessage }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-btn type="submit" color="primary">Continue with login</v-btn>
        </v-card-actions>
      </form>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
const route = useRoute()
const magicLinkToken = (route.query.magicLinkToken as string) ?? ''

// A failed verification 302s back here with ?e=invalid_token.
const magicLinkErrorMessage = useServerErrorMessage()
</script>
