// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  devtools: { enabled: true },

  // Pure SPA: Spring Boot serves one static index.html for every page path and the
  // client router owns routing from there. With prerendering (ssr: true + generate),
  // each route hydrated against a build-time payload and dropped its runtime query
  // string — which forced server-side workarounds (session-stashed tokens, per-route
  // controller forwards). No hydration means useRoute().query works on full page loads.
  ssr: false,

  modules: ['vuetify-nuxt-module'],

  app: {
    head: {
      title: 'Auth Server',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'Auth Server — Vue/Nuxt + Spring Boot' }
      ],
      link: [
        { rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' }
      ]
    }
  },

  compatibilityDate: '2024-04-03'
})
