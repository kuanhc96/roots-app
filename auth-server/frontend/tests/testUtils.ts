import { flushPromises } from '@vue/test-utils'

// Drains microtasks and one macrotask tick. Needed where a plain flushPromises falls
// short: vee-validate resolves yup schemas through timer-scheduled continuations, and
// vue-router settles replace()/push() navigations asynchronously after mount.
export async function flushAsync() {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 0))
  await flushPromises()
}
