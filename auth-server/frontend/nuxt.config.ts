// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  devtools: { enabled: true },

  // Generate a fully static site that Spring Boot can serve
  ssr: true,

  modules: ['vuetify-nuxt-module'],

  routeRules: {
    '/': { redirect: '/login' },
  },

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
