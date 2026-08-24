import { describe, it, expect } from 'vitest'
import { defineComponent, h } from 'vue'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { useCsrfToken } from '~/composables/useCsrfToken'

// Minimal host component: composable must run inside a component's setup to use onMounted.
const Host = defineComponent({
  setup() {
    const { csrfToken } = useCsrfToken()
    return { csrfToken }
  },
  render() {
    return h('span', this.csrfToken)
  },
})

describe('useCsrfToken', () => {
  it('reads the XSRF-TOKEN cookie value on mount', async () => {
    document.cookie = 'XSRF-TOKEN=my-test-token'
    const wrapper = await mountSuspended(Host, { route: '/' })
    expect(wrapper.text()).toBe('my-test-token')
  })

  it('returns an empty string when the XSRF-TOKEN cookie is absent', async () => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0'
    const wrapper = await mountSuspended(Host, { route: '/' })
    expect(wrapper.text()).toBe('')
  })
})
