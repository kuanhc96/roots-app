import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mountSuspended, mockNuxtImport } from '@nuxt/test-utils/runtime'
import ForgotPasswordForm from '~/components/ForgotPasswordForm.vue'
import { flushAsync } from '../testUtils'

const { navigateToMock } = vi.hoisted(() => ({ navigateToMock: vi.fn() }))
mockNuxtImport('navigateTo', () => navigateToMock)

const fetchMock = vi.fn()

beforeEach(() => {
  navigateToMock.mockReset()
  fetchMock.mockReset().mockResolvedValue({ ok: true })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

async function submitWithEmail(email?: string) {
  const wrapper = await mountSuspended(ForgotPasswordForm, { route: '/forgot-password' })
  if (email !== undefined) {
    await wrapper.find('input[name="email"]').setValue(email)
  }
  await wrapper.find('form').trigger('submit')
  await flushAsync()
  return wrapper
}

describe('ForgotPasswordForm', () => {
  it('links back to /login', async () => {
    const wrapper = await mountSuspended(ForgotPasswordForm, { route: '/forgot-password' })

    const hrefs = wrapper.findAll('a').map(a => a.attributes('href'))
    expect(hrefs).toContain('/login')
  })

  describe('client-side validation', () => {
    it('blocks an empty submit: no request, no navigation', async () => {
      const wrapper = await submitWithEmail()

      expect(wrapper.text()).toContain('Email is required')
      expect(fetchMock).not.toHaveBeenCalled()
      expect(navigateToMock).not.toHaveBeenCalled()
    })

    it('rejects an email without an @', async () => {
      const wrapper = await submitWithEmail('not-an-email')

      expect(wrapper.text()).toContain('Email must contain an "@"')
      expect(fetchMock).not.toHaveBeenCalled()
      expect(navigateToMock).not.toHaveBeenCalled()
    })
  })

  describe('temp-password request', () => {
    it('POSTs the email as JSON to /api/temp-password and returns to /login with prefill + notice', async () => {
      await submitWithEmail('user@example.com')

      expect(fetchMock).toHaveBeenCalledTimes(1)
      const [url, init] = fetchMock.mock.calls[0]
      expect(url).toBe('/api/temp-password')
      expect(init.method).toBe('POST')
      expect(init.headers['Content-Type']).toBe('application/json')
      expect(JSON.parse(init.body)).toEqual({ email: 'user@example.com' })

      expect(navigateToMock).toHaveBeenCalledWith({
        path: '/login',
        query: { email: 'user@example.com', notice: 'tempPasswordSent' },
      })
    })

    it('navigates back identically when the server answers non-2xx (anti-enumeration)', async () => {
      fetchMock.mockResolvedValue({ ok: false, status: 500 })

      await submitWithEmail('user@example.com')

      expect(navigateToMock).toHaveBeenCalledWith({
        path: '/login',
        query: { email: 'user@example.com', notice: 'tempPasswordSent' },
      })
    })

    it('navigates back identically when the request throws (anti-enumeration)', async () => {
      fetchMock.mockRejectedValue(new TypeError('network down'))

      await submitWithEmail('user@example.com')

      expect(navigateToMock).toHaveBeenCalledWith({
        path: '/login',
        query: { email: 'user@example.com', notice: 'tempPasswordSent' },
      })
    })
  })
})
