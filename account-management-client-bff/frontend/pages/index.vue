<template>
  <v-row class="fill-height" align="center" justify="center">
    <v-col cols="12" sm="8" md="6">
      <v-card class="mx-auto" max-width="500">
        <v-card-title class="text-center">Account Management</v-card-title>

        <v-card-text class="text-center">
          <!-- Login status display -->
          <div v-if="oauth.isLoggedIn.value" class="mb-6">
            <div class="text-subtitle1 font-weight-bold">Logged in</div>
            <div class="text-body2 mt-2">{{ claims?.email }}</div>
            <div v-if="claims?.roles && claims.roles.length > 0" class="text-body2 mt-1">
              Roles: {{ claims.roles.join(', ') }}
            </div>
          </div>
          <div v-else class="mb-6">
            <div class="text-subtitle1 font-weight-bold">Not logged in</div>
          </div>
        </v-card-text>

        <v-card-actions class="d-flex flex-column gap-2 px-4 pb-4">
          <v-btn block variant="outlined" @click="handleCheckStatus">
            Check Status
          </v-btn>
          <v-btn block variant="outlined" @click="oauth.authorize()">
            Authorize
          </v-btn>
          <v-btn block variant="outlined" @click="oauth.login()">
            Login
          </v-btn>
          <v-btn
            block
            variant="outlined"
            color="red"
            :disabled="!oauth.isLoggedIn.value"
            @click="oauth.startLogout()"
          >
            Logout
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
const oauth = useOAuth()
const claims = computed(() => oauth.getClaims())

const handleCheckStatus = async () => {
  try {
    const status = await oauth.checkStatus()
    console.log('Status check result:', status)
  } catch (error) {
    console.error('Status check failed:', error)
  }
}
</script>
