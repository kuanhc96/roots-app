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
      script: [
        {src: 'https://accounts.google.com/gsi/client', async: true, defer: true }
      ],
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
  // Google Sign-In (client id is public; the secret has no default and must come from
  // NUXT_PUBLIC_GOOGLE_CLIENT_SECRET — it ships in the JS bundle, an accepted tradeoff
  // of the browser-side code exchange until the planned server-side exchange lands).
  runtimeConfig: {
    public: {
      googleAuthServerUrl: 'https://oauth2.googleapis.com',
      googleClientId: '444329721662-9ftcduffg48hg91douoq4tnaok5h266r.apps.googleusercontent.com',
      googleClientSecret: ''
    }
  },

  compatibilityDate: '2024-04-03'
})
