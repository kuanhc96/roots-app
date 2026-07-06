<script setup lang="ts">
// A failed verification 302s back here with ?error=<code> (e.g. invalidToken).
const ottErrorMessage = useServerErrorMessage()
</script>

<template>
  <v-card width="400">
    <form method="post" action="/ott/login">
      <v-card-title>OTT Login</v-card-title>
      <v-card-text>
        <v-text-field name="ott" label="One Time Token" type="text" />
        <v-checkbox name="rememberBrowser" value="true" label="Remember this browser?" />
        <v-alert v-if="ottErrorMessage" type="warning" density="compact">
          {{ ottErrorMessage }}
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit">Verify</v-btn>
      </v-card-actions>
    </form>
  </v-card>

</template>

<style scoped>

</style>