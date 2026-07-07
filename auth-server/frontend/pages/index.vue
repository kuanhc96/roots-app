<script setup lang="ts">
// The / → /login redirect lives in the app (not routeRules): route rules are a Nitro
// feature, and there is no Nitro at runtime — Spring Boot serves the static shell.
await navigateTo('/login', { replace: true })
</script>

<template>
  <div />
</template>
