import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import ResetPasswordForm from '~/components/ResetPasswordForm.vue'
import { errorMessages } from '~/utils/errorMessages'
import { flushAsync } from '../testUtils'

let nativeSubmit: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  nativeSubmit = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {})
})

afterEach(() => {
  nativeSubmit.mockRestore()
})

async function mountForm(route = '/reset-password') {
  return mountSuspended(ResetPasswordForm, { route })
}

// The visible v-form (no action) drives validation; the hidden native form
// (action="/reset-password") is what actually navigates.
function visibleForm(wrapper: Awaited<ReturnType<typeof mountForm>>) {
  return wrapper.findAll('form').find(f => f.attributes('action') === undefined)!
}

async function submit(wrapper: Awaited<ReturnType<typeof mountForm>>, values: { newPassword?: string; confirmNewPassword?: string }) {
  for (const [field, value] of Object.entries(values)) {
    // :not([type="hidden"]) - the native form carries a hidden input with the same name
    await wrapper.find(`input[name="${field}"]:not([type="hidden"])`).setValue(value)
  }
  await visibleForm(wrapper).trigger('submit')
  await flushAsync()
}

describe('ResetPasswordForm', () => {
  describe('form contract', () => {
    it('carries only newPassword in the hidden native form posting to /reset-password', async () => {
      const wrapper = await mountForm()

      const nativeForm = wrapper.find('form[action="/reset-password"]')
      expect(nativeForm.attributes('method')).toBe('post')

      const inputs = nativeForm.findAll('input')
      expect(inputs).toHaveLength(1)
      expect(inputs[0].attributes('type')).toBe('hidden')
      expect(inputs[0].attributes('name')).toBe('newPassword')
    })
  })

  describe('server redirect handling', () => {
    it('shows the invalid_password message when the server redirects back with ?e=invalid_password', async () => {
      const wrapper = await mountForm('/reset-password?e=invalid_password')

      expect(wrapper.text()).toContain(errorMessages.invalid_password)
    })

    it('shows no error alert on a clean visit', async () => {
      const wrapper = await mountForm()

      expect(wrapper.findComponent({ name: 'VAlert' }).exists()).toBe(false)
    })
  })

  describe('client-side validation', () => {
    it('blocks an empty submit and shows the required-field messages', async () => {
      const wrapper = await mountForm()

      await submit(wrapper, {})

      expect(wrapper.text()).toContain('Password is required')
      expect(wrapper.text()).toContain('Please confirm your password')
      expect(nativeSubmit).not.toHaveBeenCalled()
    })

    it.each([
      ['shorter than 8 characters', 'Pass1', 'Password must be at least 8 characters'],
      ['missing an uppercase letter', 'password1', 'Password must include at least one uppercase letter'],
      ['missing a lowercase letter', 'PASSWORD1', 'Password must include at least one lowercase letter'],
      ['missing a number', 'Passwordx', 'Password must include at least one number'],
    ])('rejects a new password %s', async (_case, password, message) => {
      const wrapper = await mountForm()

      await submit(wrapper, { newPassword: password, confirmNewPassword: password })

      expect(wrapper.text()).toContain(message)
      expect(nativeSubmit).not.toHaveBeenCalled()
    })

    it('rejects mismatched passwords - the one rule with no server backstop', async () => {
      const wrapper = await mountForm()

      await submit(wrapper, { newPassword: 'Password1', confirmNewPassword: 'Different1' })

      expect(wrapper.text()).toContain('Passwords must match')
      expect(nativeSubmit).not.toHaveBeenCalled()
    })

    it('submits the hidden native form carrying the new password when validation passes', async () => {
      const wrapper = await mountForm()

      await submit(wrapper, { newPassword: 'Password1', confirmNewPassword: 'Password1' })

      expect(nativeSubmit).toHaveBeenCalledTimes(1)
      const submitted = nativeSubmit.mock.instances[0] as HTMLFormElement
      expect(submitted.getAttribute('action')).toBe('/reset-password')

      const hidden = wrapper.find('form[action="/reset-password"] input[name="newPassword"]')
      expect(hidden.attributes('value')).toBe('Password1')
    })
  })
})
