export function useOAuth() {
  function startLogout() {
    window.location.href = '/logout'
  }

  function login() {
    window.location.href = '/oauth2/authorization/web-client-pkce-registration'
  }

  return { login, startLogout }
}
