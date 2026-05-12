<script setup lang="ts">
import OttLoginForm from "~/components/OttLoginForm.vue";

const router = useRouter()

onBeforeMount(() => {
  fetch('/ott/generate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    }
  }).then(response => {
    if (response.status === 403) {
      router.replace('/login?error=noMfaPending')
    } else if (!response.ok) {
      console.log("Failed to generate OTT token")
    } else {
      console.log("generated OTT token successfully")
    }
  })
})
</script>

<template>
  <v-container class="fill-height d-flex align-center justify-center">
    <OttLoginForm />
  </v-container>
</template>

<style scoped>

</style>