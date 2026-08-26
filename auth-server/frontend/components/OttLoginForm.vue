<script setup lang="ts">
// A failed verification 302s back here with ?e=<code> (e.g. invalid_token).
const ottErrorMessage = useServerErrorMessage()
const { csrfToken } = useCsrfToken()
const resendCountdown = ref(30)
const resendInFlight = ref(false)
const resendSuccessMessage = ref('')
const resendErrorMessage = ref('')

let countdownTimer: ReturnType<typeof setInterval> | null = null

function clearCountdownTimer() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function startCountdown(seconds: number) {
  clearCountdownTimer()
  resendCountdown.value = Math.max(0, seconds)
  if (resendCountdown.value === 0) {
    return
  }
  countdownTimer = setInterval(() => {
    if (resendCountdown.value <= 1) {
      clearCountdownTimer()
      resendCountdown.value = 0
      return
    }
    resendCountdown.value -= 1
  }, 1000)
}

async function resendOtt() {
  if (resendInFlight.value || resendCountdown.value > 0) {
    return
  }

  resendInFlight.value = true
  resendSuccessMessage.value = ''
  resendErrorMessage.value = ''

  try {
    const response = await fetch('/api/ott/resend', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrfToken.value,
      },
    })

    const payload = await response.json().catch(() => ({}))
    if (response.ok) {
      resendSuccessMessage.value = 'A new verification code has been sent.'
      startCountdown(30)
      return
    }

    if (response.status === 401 && payload.error === 'no_mfa_pending') {
      await navigateTo('/sso/login?e=no_mfa_pending')
      return
    }

    if (response.status === 429 && payload.error === 'ott_resend_cooldown') {
      const retryAfterSeconds = Number(payload.retryAfterSeconds) || 30
      resendErrorMessage.value = `Please wait ${retryAfterSeconds} seconds before requesting another code.`
      startCountdown(retryAfterSeconds)
      return
    }

    resendErrorMessage.value = 'Unable to resend the verification code. Please try again.'
  } catch {
    resendErrorMessage.value = 'Unable to resend the verification code. Please try again.'
  } finally {
    resendInFlight.value = false
  }
}

onMounted(() => {
  startCountdown(30)
})

onBeforeUnmount(() => {
  clearCountdownTimer()
})
</script>

<template>
  <v-card width="400">
    <form method="post" action="/ott/login">
      <input type="hidden" name="_csrf" :value="csrfToken" />
      <v-card-title>OTT Login</v-card-title>
      <v-card-text>
        <v-text-field name="ott" label="One Time Token" type="text" />
        <v-checkbox name="rememberBrowser" value="true" label="Remember this browser?" />
        <v-alert v-if="ottErrorMessage" type="warning" density="compact">
          {{ ottErrorMessage }}
        </v-alert>
        <v-alert v-if="resendErrorMessage" type="warning" density="compact">
          {{ resendErrorMessage }}
        </v-alert>
        <v-alert v-if="resendSuccessMessage" type="success" density="compact">
          {{ resendSuccessMessage }}
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit">Verify</v-btn>
        <v-btn
          type="button"
          variant="text"
          :loading="resendInFlight"
          :disabled="resendInFlight || resendCountdown > 0"
          @click="resendOtt"
        >
          {{ resendCountdown > 0 ? `Resend code (${resendCountdown}s)` : 'Resend code' }}
        </v-btn>
      </v-card-actions>
    </form>
  </v-card>

</template>

<style scoped>

</style>