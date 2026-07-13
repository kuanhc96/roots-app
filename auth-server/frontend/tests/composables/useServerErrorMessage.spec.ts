import { describe, it, expect } from 'vitest'
import { defineComponent, h } from 'vue'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { useServerErrorMessage } from '~/composables/useServerErrorMessage'
import { flushAsync } from '../testUtils'
import { errorMessages } from '~/utils/errorMessages'

// Minimal host: the composable must run inside a component's setup to use
// useRoute/onMounted. Exposes the message and the live route for assertions.
const Host = defineComponent({
  setup() {
    const message = useServerErrorMessage()
    const route = useRoute()
    return { message, route }
  },
  render() {
    return h('div', this.message ?? '')
  },
})

async function mountHost(route: string) {
  const wrapper = await mountSuspended(Host, { route })
  await flushAsync() // let the onMounted router.replace scrub settle
  return wrapper
}

describe('useServerErrorMessage', () => {
  it('maps a known ?e= code to its display text and scrubs it from the URL, keeping other params', async () => {
    const wrapper = await mountHost('/login?e=invalid_login&email=user%40example.com')

    expect(wrapper.text()).toBe(errorMessages.invalid_login)

    // flash semantics: the code is gone after mount, the rest of the query survives
    expect(wrapper.vm.route.query.e).toBeUndefined()
    expect(wrapper.vm.route.query.email).toBe('user@example.com')
  })

  it('displays nothing for an unmapped code but still scrubs it', async () => {
    const wrapper = await mountHost('/login?e=not_a_real_code')

    expect(wrapper.text()).toBe('')
    expect(wrapper.vm.route.query.e).toBeUndefined()
  })

  it('returns undefined and leaves the URL alone when no code is present', async () => {
    const wrapper = await mountHost('/login?email=user%40example.com')

    expect(wrapper.text()).toBe('')
    expect(wrapper.vm.route.query.email).toBe('user@example.com')
  })
})
