import { flushPromises } from '@vue/test-utils'

// Drains several rounds of microtasks + macrotask ticks. One round is not enough where
// async work schedules more async work across ticks: vee-validate resolves yup schemas
// through chained timer/promise continuations (observed flaky on slow CI runners with a
// single tick), and vue-router settles replace()/push() navigations after mount. The
// work is tick-bounded, not time-based, so a fixed number of rounds drains it
// deterministically regardless of machine speed.
export async function flushAsync(rounds = 6) {
  for (let i = 0; i < rounds; i++) {
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 0))
  }
  await flushPromises()
}
