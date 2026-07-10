<template>
  <div>
    <form id="login-form" method="post" action="/login"></form>
    <form id="guest-form" method="post" action="/login/guest"></form>

    <v-card width="400">
      <v-card-title>Login</v-card-title>
      <v-card-text>
        <v-text-field v-model="email" name="email" label="Email" type="email" form="login-form" />
        <v-text-field name="password" label="Password" type="password" form="login-form" />
        <NuxtLink to="/forgot-password">Forgot?</NuxtLink>
        <v-checkbox name="remember-me" value="true" label="Remember Me?" form="login-form" />
        <v-alert v-if="loginErrorMessage" type="warning" density="compact">
          {{ loginErrorMessage }}
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit" form="login-form">Login</v-btn>
        <v-spacer></v-spacer>
        <v-btn type="submit" form="guest-form" variant="text">Continue as Guest</v-btn>
      </v-card-actions>
      <v-card-actions>
        <NuxtLink to="/signup">Create an account</NuxtLink>
        <v-spacer></v-spacer>
        <v-btn variant="text" @click="authorizeWithGoogle()">Sign in with Google</v-btn>
      </v-card-actions>
    </v-card>

    <v-snackbar v-model="showNotice" :timeout="8000" location="top">
      <template #prepend>
        <v-icon icon="mdi-check-circle" color="success" />
      </template>
      If the email provided matches one we have on file, you will receive an email with a temporary password
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
// When the user arrives from the forgot-password page, the email is pre-filled and a
// neutral notice is shown. navigateTo passes these as route query params (in-SPA nav, so
// they survive); useRoute reads them here.
const route = useRoute()
const email = ref((route.query.email as string) ?? '')
const showNotice = ref(route.query.notice === 'tempPasswordSent')

// A failed login 302s here with ?e=<code> (e.g. invalid_login); the composable
// maps it to display text and scrubs the code from the URL after mount.
const loginErrorMessage = useServerErrorMessage()

// Starts Google's authorization-code flow (GIS code client, redirect mode). The state
// is checked by the /callback page against sessionStorage on the way back. openid+email
// are required for an id_token carrying the email claim; profile supplies the name used
// when an account is auto-created.
const config = useRuntimeConfig()

function authorizeWithGoogle() {
  const google = (window as any).google
  if (!google?.accounts?.oauth2) return // GIS script not loaded yet

  const state = crypto.randomUUID()
  console.log(state)
  sessionStorage.setItem('google_oauth_state', state)
  const client = google.accounts.oauth2.initCodeClient({
    client_id: config.public.googleClientId,
    scope: 'openid email profile',
    ux_mode: 'redirect',
    redirect_uri: `${window.location.origin}/callback`,
    state: state
  })
  client.requestCode();
}
</script>
