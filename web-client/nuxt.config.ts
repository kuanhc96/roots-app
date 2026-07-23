// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  modules: ['vuetify-nuxt-module'],
  runtimeConfig: {
    public: {
      simpleResourceServerUrl: 'http://localhost:8081',
      // The bff-server owns the OAuth2 flow and the tokens; this is the only auth
      // surface the browser talks to (override: NUXT_PUBLIC_BFF_SERVER_URL).
      bffServerUrl: 'http://localhost:8083',
    },
  },
})
