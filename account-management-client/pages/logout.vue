<template>
  <v-row class="fill-height" align="center" justify="center">
    <v-col cols="12" sm="8" md="6">
      <v-card class="mx-auto" max-width="500">
        <v-card-title class="text-center">Logged Out</v-card-title>

        <v-card-text class="text-center">
          <div class="text-body1 mb-6">
            You have been successfully logged out.
          </div>
        </v-card-text>

        <v-card-actions class="d-flex flex-column gap-2 px-4 pb-4">
          <v-btn block variant="outlined" color="primary" to="/">
            Back to Home
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
const oauth = useOAuth()

onMounted(() => {
  // Clear stored claims on mount so the UI reflects logged-out state
  oauth.logout()
})
</script>
