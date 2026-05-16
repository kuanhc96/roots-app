<script setup lang="ts">
const cooldownActive = ref(false)
const showTimer = ref(false)
const secondsLeft = ref(60)

const timerDisplay = computed(() => {
  const m = Math.floor(secondsLeft.value / 60)
  const s = secondsLeft.value % 60
  return `${m}:${s.toString().padStart(2, '0')}`
})

function requestNewCode() {
  fetch('/ott/generate', { method: 'POST' }).then(response => {
    if (!response.ok) {
      console.log('Failed to generate OTT token')
      return
    }
    cooldownActive.value = true
    showTimer.value = true
    secondsLeft.value = 60

    const interval = setInterval(() => {
      secondsLeft.value--
      if (secondsLeft.value <= 0) {
        clearInterval(interval)
        setTimeout(() => {
          showTimer.value = false
          cooldownActive.value = false
        }, 1000)
      }
    }, 1000)
  })
}
</script>

<template>
  <v-card width="400">
    <form method="post" action="/ott/login">
      <v-card-title>OTT Login</v-card-title>
      <v-card-text>
        <v-text-field name="ott" label="One Time Token" type="text" />
        <v-checkbox name="rememberBrowser" value="true" label="Remember this browser?" />
        <div v-if="showTimer" class="text-center text-body-2 text-medium-emphasis mb-1">
          {{ timerDisplay }}
        </div>
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit">Verify</v-btn>
        <v-btn :disabled="cooldownActive" variant="text" @click="requestNewCode">
          Request another code
        </v-btn>
      </v-card-actions>
    </form>
  </v-card>

</template>

<style scoped>

</style>