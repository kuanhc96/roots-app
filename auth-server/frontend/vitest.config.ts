import { defineVitestConfig } from '@nuxt/test-utils/config'

export default defineVitestConfig({
  test: {
    // Runs every test inside a Nuxt runtime environment: auto-imports, useRoute/useRouter,
    // runtimeConfig, and registered modules (Vuetify) behave as they do in the real app.
    environment: 'nuxt',
    setupFiles: ['./tests/setup.ts'],
    environmentOptions: {
      nuxt: {
        domEnvironment: 'happy-dom',
      },
    },
  },
})
