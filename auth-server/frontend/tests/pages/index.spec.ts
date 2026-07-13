import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mountSuspended, mockNuxtImport } from '@nuxt/test-utils/runtime'
import Index from '~/pages/index.vue'

const { navigateToMock } = vi.hoisted(() => ({ navigateToMock: vi.fn() }))
mockNuxtImport('navigateTo', () => navigateToMock)

beforeEach(() => {
  navigateToMock.mockReset()
})

describe('index page', () => {
  it('redirects / to /login, replacing the history entry', async () => {
    await mountSuspended(Index, { route: '/' })

    expect(navigateToMock).toHaveBeenCalledWith('/login', { replace: true })
  })
})
