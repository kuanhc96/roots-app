// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  modules: ['vuetify-nuxt-module'],
  routeRules: {
    '/': { redirect: '/home' },
  },
  runtimeConfig: {
    public: {
      simpleResourceServerUrl: 'http://localhost:8081',
      authServerUrl: 'http://localhost:9000',
      webClientId: 'WEB_CLIENT',
      webClientSecret: '',
    },
  },
})
