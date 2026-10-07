# web-client-bff frontend

This Nuxt 4 SPA is packaged and served by the Spring Boot application in the parent
directory. From `web-client-bff/`, `mvn package` runs `npm install` and `npm run generate`,
then copies the generated static files into the application JAR.

The app is built with `ssr: false` and uses the Spring Boot API at the same origin
for `/api/auth/**` and `/api/role/**`. Role requests are proxied by the bff, which
attaches the session's access token and forwards them through gateway-server to
simple-resource-server — the browser never calls the gateway directly.
