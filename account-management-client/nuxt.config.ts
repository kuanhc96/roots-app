// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },
  modules: ['vuetify-nuxt-module'],
  devServer: {
    port: 3001,
  },
  runtimeConfig: {
    public: {
      // All auth traffic goes through the gateway, which proxies to account-management-bff.
      // The account-management-bff owns the OAuth2 flow and the tokens.
      accountManagementBffUrl: 'http://localhost:8080/roots-app/account-management-bff',
    },
  },
})
