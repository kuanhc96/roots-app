import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mountSuspended, mockNuxtImport } from '@nuxt/test-utils/runtime'
import OttLoginForm from '~/components/OttLoginForm.vue'
import { errorMessages } from '~/utils/errorMessages'

const { navigateToMock } = vi.hoisted(() => ({ navigateToMock: vi.fn() }))
mockNuxtImport('navigateTo', () => navigateToMock)

const fetchMock = vi.fn()

async function flushUi() {
  await Promise.resolve()
  await Promise.resolve()
}

beforeEach(() => {
  navigateToMock.mockReset()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

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

  it('starts with a 30-second resend countdown and disables the button', async () => {
    const wrapper = await mountSuspended(OttLoginForm, { route: '/sso/ott/login' })
    const resendButton = wrapper.findAll('button').find(button => button.text().includes('Resend code'))

    expect(resendButton).toBeDefined()
    expect(resendButton!.text()).toContain('Resend code (30s)')
    expect(resendButton!.attributes('disabled')).toBeDefined()
  })

  it('resends via /api/ott/resend after cooldown and shows success feedback', async () => {
    document.cookie = 'XSRF-TOKEN=ott-token'
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ status: 'ok' }),
    })

    const wrapper = await mountSuspended(OttLoginForm, { route: '/sso/ott/login' })
    await vi.advanceTimersByTimeAsync(30000)
    await flushUi()

    const resendButton = wrapper.findAll('button').find(button => button.text().includes('Resend code'))
    expect(resendButton).toBeDefined()
    await resendButton!.trigger('click')
    await flushUi()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/ott/resend')
    expect(init.method).toBe('POST')
    expect(init.headers['X-XSRF-TOKEN']).toBe('ott-token')
    expect(wrapper.text()).toContain('A new verification code has been sent.')
    expect(resendButton!.text()).toContain('Resend code (30s)')
  })

  it('shows cooldown feedback from 429 responses and restarts countdown', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 429,
      json: async () => ({ error: 'ott_resend_cooldown', retryAfterSeconds: 12 }),
    })

    const wrapper = await mountSuspended(OttLoginForm, { route: '/sso/ott/login' })
    await vi.advanceTimersByTimeAsync(30000)
    await flushUi()

    const resendButton = wrapper.findAll('button').find(button => button.text().includes('Resend code'))
    expect(resendButton).toBeDefined()
    await resendButton!.trigger('click')
    await flushUi()

    expect(wrapper.text()).toContain('Please wait 12 seconds before requesting another code.')
    expect(resendButton!.text()).toContain('Resend code (12s)')
  })

  it('routes to login when resend is called without an MFA-pending session', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({ error: 'no_mfa_pending' }),
    })

    const wrapper = await mountSuspended(OttLoginForm, { route: '/sso/ott/login' })
    await vi.advanceTimersByTimeAsync(30000)
    await flushUi()

    const resendButton = wrapper.findAll('button').find(button => button.text().includes('Resend code'))
    expect(resendButton).toBeDefined()
    await resendButton!.trigger('click')
    await flushUi()

    expect(navigateToMock).toHaveBeenCalledWith('/sso/login?e=no_mfa_pending')
  })
})
