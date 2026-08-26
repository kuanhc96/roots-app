import { describe, it, expect } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import OttLoginForm from '~/components/OttLoginForm.vue'
import { errorMessages } from '~/utils/errorMessages'

describe('OttLoginForm', () => {
  it('posts the OTT natively to /ott/login with the expected field names', async () => {
    const wrapper = await mountSuspended(OttLoginForm, { route: '/sso/ott/login' })

    const form = wrapper.find('form')
    expect(form.attributes('action')).toBe('/ott/login')
    expect(form.attributes('method')).toBe('post')

    expect(form.find('input[name="ott"]').exists()).toBe(true)

    const rememberBrowser = form.find('input[name="rememberBrowser"]')
    expect(rememberBrowser.attributes('type')).toBe('checkbox')
    expect(rememberBrowser.attributes('value')).toBe('true')

    const submit = form.find('button[type="submit"]')
    expect(submit.text()).toContain('Verify')
  })

  it('populates the _csrf hidden field from the XSRF-TOKEN cookie on mount', async () => {
    document.cookie = 'XSRF-TOKEN=ott-token'
    const wrapper = await mountSuspended(OttLoginForm, { route: '/sso/ott/login' })

    const csrf = wrapper.find('input[name="_csrf"]')
    expect(csrf.attributes('type')).toBe('hidden')
    expect((csrf.element as HTMLInputElement).value).toBe('ott-token')
  })

  it('shows the invalid_token message when the server redirects back with ?e=invalid_token', async () => {
    const wrapper = await mountSuspended(OttLoginForm, {
      route: '/sso/ott/login?e=invalid_token',
    })

    expect(wrapper.text()).toContain(errorMessages.invalid_token)
  })

  it('shows no error alert on a clean visit', async () => {
    const wrapper = await mountSuspended(OttLoginForm, { route: '/sso/ott/login' })

    expect(wrapper.findComponent({ name: 'VAlert' }).exists()).toBe(false)
  })
})
