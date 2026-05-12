<script setup lang="ts">

</script>

<template>
  <v-card width="400">
    <form method="post" action="/ott/login">
      <v-card-title>OTT Login</v-card-title>
      <v-card-text>
        <v-text-field name="ott" label="One Time Token" type="text" />
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit">Verify</v-btn>
      </v-card-actions>
    </form>
  </v-card>

</template>

<style scoped>

</style>