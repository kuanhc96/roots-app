import { describe, it, expect } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import MagicLinkLoginForm from '~/components/MagicLinkLoginForm.vue'
import { errorMessages } from '~/utils/errorMessages'

describe('MagicLinkLoginForm', () => {
  it('posts the token from the emailed link as a hidden field to /magic-link/login', async () => {
    const wrapper = await mountSuspended(MagicLinkLoginForm, {
      route: '/magic-link/login?magicLinkToken=abc-123',
    })

    const form = wrapper.find('form')
    expect(form.attributes('action')).toBe('/magic-link/login')
    expect(form.attributes('method')).toBe('post')

    const hidden = form.find('input[name="magicLinkToken"]')
    expect(hidden.attributes('type')).toBe('hidden')
    expect(hidden.attributes('value')).toBe('abc-123')
  })

  it('requires a button click to consume the token — no auto-submitting script', async () => {
    const wrapper = await mountSuspended(MagicLinkLoginForm, {
      route: '/magic-link/login?magicLinkToken=abc-123',
    })

    const submit = wrapper.find('button[type="submit"]')
    expect(submit.text()).toContain('Continue with login')
  })

  it('posts an empty token when the link carries none (the server answers with invalid_token)', async () => {
    const wrapper = await mountSuspended(MagicLinkLoginForm, { route: '/magic-link/login' })

    expect(wrapper.find('input[name="magicLinkToken"]').attributes('value')).toBe('')
  })

  it('shows the invalid_token message when verification fails and the server redirects back', async () => {
    const wrapper = await mountSuspended(MagicLinkLoginForm, {
      route: '/magic-link/login?e=invalid_token',
    })

    expect(wrapper.text()).toContain(errorMessages.invalid_token)
  })
})
