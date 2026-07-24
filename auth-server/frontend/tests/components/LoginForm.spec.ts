import { describe, it, expect } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import LoginForm from '~/components/LoginForm.vue'
import { errorMessages } from '~/utils/errorMessages'

describe('LoginForm', () => {
  describe('form contract', () => {
    it('declares the credential form posting to /login and the guest form posting to /login/guest', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/login' })

      const loginForm = wrapper.find('form#login-form')
      expect(loginForm.attributes('action')).toBe('/login')
      expect(loginForm.attributes('method')).toBe('post')

      const guestForm = wrapper.find('form#guest-form')
      expect(guestForm.attributes('action')).toBe('/login/guest')
      expect(guestForm.attributes('method')).toBe('post')
    })

    it('binds email, password, and remember-me to the login form via the HTML form attribute', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/login' })

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
      const wrapper = await mountSuspended(LoginForm, { route: '/login' })

      const buttons = wrapper.findAll('button[type="submit"]')
      const loginButton = buttons.find(b => b.text().includes('Login'))
      const guestButton = buttons.find(b => b.text().includes('Continue as Guest'))

      expect(loginButton?.attributes('form')).toBe('login-form')
      expect(guestButton?.attributes('form')).toBe('guest-form')
    })

    it('links to /forgot-password and /signup', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/login' })

      const hrefs = wrapper.findAll('a').map(a => a.attributes('href'))
      expect(hrefs).toContain('/forgot-password')
      expect(hrefs).toContain('/signup')
    })

    it('the Google button navigates the browser to /login/google/authorize', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/login' })

      const googleButton = wrapper.findAll('a').find(a => a.text().includes('Sign in with Google'))
      expect(googleButton?.attributes('href')).toBe('/login/google/authorize')
    })
  })

  describe('server redirect handling', () => {
    it('shows the invalid_login message when the server redirects back with ?e=invalid_login', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/login?e=invalid_login' })

      expect(wrapper.text()).toContain(errorMessages.invalid_login)
    })

    it('shows no error alert on a clean visit', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/login' })

      expect(wrapper.findComponent({ name: 'VAlert' }).exists()).toBe(false)
    })

    it('pre-fills the email and shows the temp-password notice when arriving from forgot-password', async () => {
      const wrapper = await mountSuspended(LoginForm, {
        route: '/login?email=user%40example.com&notice=tempPasswordSent',
      })

      const email = wrapper.find('input[name="email"]')
      expect((email.element as HTMLInputElement).value).toBe('user@example.com')

      const snackbar = wrapper.findComponent({ name: 'VSnackbar' })
      expect(snackbar.props('modelValue')).toBe(true)
    })

    it('does not show the notice snackbar without the query param', async () => {
      const wrapper = await mountSuspended(LoginForm, { route: '/login' })

      const snackbar = wrapper.findComponent({ name: 'VSnackbar' })
      expect(snackbar.props('modelValue')).toBe(false)
    })
  })
})
