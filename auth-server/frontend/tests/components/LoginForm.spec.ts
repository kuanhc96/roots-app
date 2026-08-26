import { describe, it, expect } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import LoginForm from '~/components/LoginForm.vue'
import { errorMessages } from '~/utils/errorMessages'

describe('LoginForm', () => {
  describe('form contract', () => {
    it('declares the credential form posting to /sso/login and the guest form posting to /login/guest', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login' })

      const loginForm = wrapper.find('form#login-form')
      expect(loginForm.attributes('action')).toBe('/login')
      expect(loginForm.attributes('method')).toBe('post')
      expect(loginForm.find('input[name="_csrf"]').attributes('type')).toBe('hidden')

      const guestForm = wrapper.find('form#guest-form')
      expect(guestForm.attributes('action')).toBe('/login/guest')
      expect(guestForm.attributes('method')).toBe('post')
      expect(guestForm.find('input[name="_csrf"]').attributes('type')).toBe('hidden')
    })

    it('populates each form\'s _csrf field from the XSRF-TOKEN cookie on mount', async () => {
      document.cookie = 'XSRF-TOKEN=test-token'
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login' })

      const loginCsrf = wrapper.find('form#login-form input[name="_csrf"]')
      expect((loginCsrf.element as HTMLInputElement).value).toBe('test-token')

      const guestCsrf = wrapper.find('form#guest-form input[name="_csrf"]')
      expect((guestCsrf.element as HTMLInputElement).value).toBe('test-token')
    })

    it('binds email, password, and remember-me to the login form via the HTML form attribute', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login' })

      const email = wrapper.find('input[name="email"]')
      expect(email.attributes('form')).toBe('login-form')
      expect(email.attributes('type')).toBe('email')

      const password = wrapper.find('input[name="password"]')
      expect(password.attributes('form')).toBe('login-form')
      expect(password.attributes('type')).toBe('password')

      const rememberMe = wrapper.find('input[name="remember-me"]')
      expect(rememberMe.attributes('form')).toBe('login-form')
      expect(rememberMe.attributes('type')).toBe('checkbox')
      // Spring Security's remember-me filter looks for the literal value "true"
      expect(rememberMe.attributes('value')).toBe('true')
    })

    it('submits the login form from the Login button and the guest form from the guest button', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login' })

      const buttons = wrapper.findAll('button[type="submit"]')
      const loginButton = buttons.find(b => b.text().includes('Login'))
      const guestButton = buttons.find(b => b.text().includes('Continue as Guest'))

      expect(loginButton?.attributes('form')).toBe('login-form')
      expect(guestButton?.attributes('form')).toBe('guest-form')
    })

    it('links to /sso/forgot-password and /sso/signup', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login' })

      const hrefs = wrapper.findAll('a').map(a => a.attributes('href'))
      expect(hrefs).toContain('/sso/forgot-password')
      expect(hrefs).toContain('/sso/signup')
    })

    it('the Google button navigates the browser to /login/google/authorize', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login' })

      const googleButton = wrapper.findAll('a').find(a => a.text().includes('Sign in with Google'))
      expect(googleButton?.attributes('href')).toBe('/login/google/authorize')
    })
  })

  describe('server redirect handling', () => {
    it('shows the invalid_login message when the server redirects back with ?e=invalid_login', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login?e=invalid_login' })

      expect(wrapper.text()).toContain(errorMessages.invalid_login)
    })

    it('shows no error alert on a clean visit', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login' })

      expect(wrapper.findComponent({ name: 'VAlert' }).exists()).toBe(false)
    })

    it('pre-fills the email and shows the temp-password notice when arriving from forgot-password', async () => {
      const wrapper = await mountSuspended(LoginForm, {
        route: '/sso/login?email=user%40example.com&notice=tempPasswordSent',
      })

      const email = wrapper.find('input[name="email"]')
      expect((email.element as HTMLInputElement).value).toBe('user@example.com')

      const snackbar = wrapper.findComponent({ name: 'VSnackbar' })
      expect(snackbar.props('modelValue')).toBe(true)
    })

    it('does not show the notice snackbar without the query param', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/sso/login' })

      const snackbar = wrapper.findComponent({ name: 'VSnackbar' })
      expect(snackbar.props('modelValue')).toBe(false)
    })
  })
})
