<template>
  <v-card>
    <v-card-title>{{ title }}</v-card-title>
    <v-card-text>
      Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
    </v-card-text>
    <v-card-actions>
      <v-btn disabled>View Details</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
defineProps<{ title: string }>()
</script>
