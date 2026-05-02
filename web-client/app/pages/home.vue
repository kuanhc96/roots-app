<script setup lang="ts">
const config = useRuntimeConfig()

onBeforeMount(() => {
})
</script>

<template>
  <v-container class="px-6">
    <v-row justify="center" class="mb-4">
      <v-col cols="10" md="4">
        <HomeCard title="Announcements" />
      </v-col>
      <v-col cols="10" md="4">
        <HomeCard title="Small Group" />
      </v-col>
    </v-row>
    <v-row justify="center" class="mb-4">
      <v-col cols="10" md="4">
        <HomeCard title="Service Areas" />
      </v-col>
      <v-col cols="10" md="4">
        <HomeCard title="My Profile" />
      </v-col>
    </v-row>
    <v-row justify="center" class="mb-4">
      <v-col cols="10" md="8">
        <RoleApiCard />
      </v-col>
    </v-row>
  </v-container>
</template>
