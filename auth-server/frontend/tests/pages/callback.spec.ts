import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mountSuspended, mockNuxtImport } from '@nuxt/test-utils/runtime'
import Callback from '~/pages/callback.vue'
import { flushAsync } from '../testUtils'

const { navigateToMock } = vi.hoisted(() => ({ navigateToMock: vi.fn() }))
mockNuxtImport('navigateTo', () => navigateToMock)

const fetchMock = vi.fn()
let nativeSubmit: ReturnType<typeof vi.spyOn>

const googleTokens = {
  access_token: 'google-access',
  id_token: 'google-id',
  refresh_token: 'google-refresh',
}

beforeEach(() => {
  sessionStorage.clear()
  navigateToMock.mockReset()
  fetchMock.mockReset().mockResolvedValue({ ok: true, json: async () => ({ ...googleTokens }) })
  vi.stubGlobal('fetch', fetchMock)
  nativeSubmit = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {})
})

afterEach(() => {
  vi.unstubAllGlobals()
  nativeSubmit.mockRestore()
})

// The page reads window.location.search directly (not useRoute), so the Google
// redirect is simulated on the real location before mounting.
async function mountCallback(search: string) {
  window.history.replaceState({}, '', `/callback${search}`)
  const wrapper = await mountSuspended(Callback, { route: '/callback' })
  // drain the async onMounted chain (fetch → json → nextTick → submit)
  await flushAsync()
  return wrapper
}

function expectFailure() {
  expect(navigateToMock).toHaveBeenCalledWith(
    { path: '/login', query: { e: 'social_login_failed' } },
    { replace: true },
  )
  expect(nativeSubmit).not.toHaveBeenCalled()
}

describe('callback page', () => {
  describe('rejected before the token exchange (no fetch)', () => {
    it('fails when Google returns an error param', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')

      await mountCallback('?error=access_denied&code=auth-code&state=state-1')

      expectFailure()
      expect(fetchMock).not.toHaveBeenCalled()
    })

    it('fails when the code is missing', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')

      await mountCallback('?state=state-1')

      expectFailure()
      expect(fetchMock).not.toHaveBeenCalled()
    })

    it('fails when the returned state is missing', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')

      await mountCallback('?code=auth-code')

      expectFailure()
      expect(fetchMock).not.toHaveBeenCalled()
    })

    it('fails when no expected state was stored (nothing initiated the flow)', async () => {
      await mountCallback('?code=auth-code&state=state-1')

      expectFailure()
      expect(fetchMock).not.toHaveBeenCalled()
    })

    it('fails when the returned state does not match the stored one (CSRF guard)', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')

      await mountCallback('?code=auth-code&state=state-2')

      expectFailure()
      expect(fetchMock).not.toHaveBeenCalled()
    })

    it('consumes the stored state even on failure — it is single-use', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')

      await mountCallback('?error=access_denied')

      expect(sessionStorage.getItem('google_oauth_state')).toBeNull()
    })
  })

  describe('rejected by the token exchange', () => {
    it('fails when the token endpoint answers non-2xx', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')
      fetchMock.mockResolvedValue({ ok: false, status: 400 })

      await mountCallback('?code=auth-code&state=state-1')

      expectFailure()
    })

    it('fails when the token response carries no id_token', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')
      fetchMock.mockResolvedValue({ ok: true, json: async () => ({ access_token: 'only-access' }) })

      await mountCallback('?code=auth-code&state=state-1')

      expectFailure()
      expect(sessionStorage.getItem('google_id_token')).toBeNull()
    })

    it('fails when the request throws', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')
      fetchMock.mockRejectedValue(new TypeError('network down'))

      await mountCallback('?code=auth-code&state=state-1')

      expectFailure()
    })
  })

  describe('successful exchange', () => {
    it('exchanges the code, stores the tokens, and hands the id_token to the backend via form POST', async () => {
      sessionStorage.setItem('google_oauth_state', 'state-1')

      const wrapper = await mountCallback('?code=auth-code&state=state-1')

      // the exchange request Google's token endpoint must receive
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const [url, init] = fetchMock.mock.calls[0]
      expect(url).toBe('https://oauth2.googleapis.com/token')
      expect(init.method).toBe('POST')
      expect(init.headers['Content-Type']).toBe('application/x-www-form-urlencoded')
      expect(init.headers.Authorization).toMatch(/^Basic /)
      const body = new URLSearchParams(init.body)
      expect(body.get('grant_type')).toBe('authorization_code')
      expect(body.get('code')).toBe('auth-code')
      expect(body.get('redirect_uri')).toBe(`${window.location.origin}/callback`)

      // tokens stored for later use
      expect(sessionStorage.getItem('google_access_token')).toBe('google-access')
      expect(sessionStorage.getItem('google_id_token')).toBe('google-id')
      expect(sessionStorage.getItem('google_refresh_token')).toBe('google-refresh')

      // the id_token rides a native form POST to /login/google (a real navigation)
      const form = wrapper.find('form')
      expect(form.attributes('action')).toBe('/login/google')
      expect(form.attributes('method')).toBe('post')
      expect(form.find('input[name="idToken"]').attributes('value')).toBe('google-id')
      expect(nativeSubmit).toHaveBeenCalledTimes(1)
      expect((nativeSubmit.mock.instances[0] as HTMLFormElement).getAttribute('action')).toBe('/login/google')

      expect(navigateToMock).not.toHaveBeenCalled()
    })
  })
})
