# web-client-bff frontend

This Nuxt 4 SPA is packaged and served by the Spring Boot application in the parent
directory. From `web-client-bff/`, `mvn package` runs `npm install` and `npm run generate`,
then copies the generated static files into the application JAR.

The app is built with `ssr: false` and uses the Spring Boot API at the same origin
for `/api/auth/**`. Simple-resource-server requests continue to use the gateway URL
configured by `NUXT_PUBLIC_SIMPLE_RESOURCE_SERVER_URL`; for a non-local gateway,
set that variable in the Maven build environment.
