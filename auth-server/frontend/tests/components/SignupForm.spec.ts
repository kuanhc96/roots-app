import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import SignupForm from '~/components/SignupForm.vue'
import { errorMessages } from '~/utils/errorMessages'
import { flushAsync } from '../testUtils'

// The native submission vee-validate resumes after successful validation; stubbed
// because test DOMs cannot perform a real browser navigation.
let nativeSubmit: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  nativeSubmit = vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(() => {})
})

afterEach(() => {
  nativeSubmit.mockRestore()
})

async function mountForm(route = '/signup') {
  return mountSuspended(SignupForm, { route })
}

async function fillFields(wrapper: Awaited<ReturnType<typeof mountForm>>, values: {
  name?: string
  email?: string
  password?: string
  confirmPassword?: string
}) {
  for (const [field, value] of Object.entries(values)) {
    await wrapper.find(`input[name="${field}"]`).setValue(value)
  }
}

async function submit(wrapper: Awaited<ReturnType<typeof mountForm>>) {
  await wrapper.find('form#signup-form').trigger('submit')
  await flushAsync()
}

const validInput = {
  name: 'Test User',
  email: 'user@example.com',
  password: 'Password1',
  confirmPassword: 'Password1',
}

describe('SignupForm', () => {
  describe('form contract', () => {
    it('posts name, email, and password natively to /signup - confirm-password stays client-side', async () => {
      const wrapper = await mountForm()

      const form = wrapper.find('form#signup-form')
      expect(form.attributes('action')).toBe('/signup')
      expect(form.attributes('method')).toBe('post')

      for (const field of ['name', 'email', 'password']) {
        expect(wrapper.find(`input[name="${field}"]`).attributes('form')).toBe('signup-form')
      }
      // The confirm field must NOT be posted: the server never re-checks the match
      expect(wrapper.find('input[name="confirmPassword"]').attributes('form')).toBeUndefined()

      expect(wrapper.find('button[type="submit"]').attributes('form')).toBe('signup-form')
    })

    it('links back to /login', async () => {
      const wrapper = await mountForm()

      const hrefs = wrapper.findAll('a').map(a => a.attributes('href'))
      expect(hrefs).toContain('/login')
    })
  })

  describe('server redirect handling', () => {
    it('pre-fills name and email from the error-redirect query', async () => {
      const wrapper = await mountForm('/signup?e=invalid_request&name=Test%20User&email=user%40example.com')

      expect((wrapper.find('input[name="name"]').element as HTMLInputElement).value).toBe('Test User')
      expect((wrapper.find('input[name="email"]').element as HTMLInputElement).value).toBe('user@example.com')
    })

    it('shows the email_taken message when the server redirects back with ?e=email_taken', async () => {
      const wrapper = await mountForm('/signup?e=email_taken')

      expect(wrapper.text()).toContain(errorMessages.email_taken)
    })

    it('shows the invalid_request message when the server redirects back with ?e=invalid_request', async () => {
      const wrapper = await mountForm('/signup?e=invalid_request')

      expect(wrapper.text()).toContain(errorMessages.invalid_request)
    })

    it('shows no error alert on a clean visit', async () => {
      const wrapper = await mountForm()

      expect(wrapper.findComponent({ name: 'VAlert' }).exists()).toBe(false)
    })
  })

  describe('client-side validation', () => {
    it('blocks an empty submit and shows the required-field messages', async () => {
      const wrapper = await mountForm()

      await submit(wrapper)

      expect(wrapper.text()).toContain('Name is required')
      expect(wrapper.text()).toContain('Email is required')
      expect(wrapper.text()).toContain('Password is required')
      expect(wrapper.text()).toContain('Please confirm your password')
      expect(nativeSubmit).not.toHaveBeenCalled()
    })

    it('rejects a name longer than 255 characters', async () => {
      const wrapper = await mountForm()
      await fillFields(wrapper, { ...validInput, name: 'a'.repeat(256) })

      await submit(wrapper)

      expect(wrapper.text()).toContain('Name must be 255 characters or fewer')
      expect(nativeSubmit).not.toHaveBeenCalled()
    })

    it('rejects an email without an @', async () => {
      const wrapper = await mountForm()
      await fillFields(wrapper, { ...validInput, email: 'not-an-email' })

      await submit(wrapper)

      expect(wrapper.text()).toContain('Email must contain an "@"')
      expect(nativeSubmit).not.toHaveBeenCalled()
    })

    it.each([
      ['shorter than 8 characters', 'Pass1', 'Password must be at least 8 characters'],
      ['missing an uppercase letter', 'password1', 'Password must include at least one uppercase letter'],
      ['missing a lowercase letter', 'PASSWORD1', 'Password must include at least one lowercase letter'],
      ['missing a number', 'Passwordx', 'Password must include at least one number'],
    ])('rejects a password %s', async (_case, password, message) => {
      const wrapper = await mountForm()
      await fillFields(wrapper, { ...validInput, password, confirmPassword: password })

      await submit(wrapper)

      expect(wrapper.text()).toContain(message)
      expect(nativeSubmit).not.toHaveBeenCalled()
    })

    it('rejects mismatched passwords - the one rule with no server backstop', async () => {
      const wrapper = await mountForm()
      await fillFields(wrapper, { ...validInput, confirmPassword: 'Different1' })

      await submit(wrapper)

      expect(wrapper.text()).toContain('Passwords must match')
      expect(nativeSubmit).not.toHaveBeenCalled()
    })

    it('resumes the native submission when every rule passes', async () => {
      const wrapper = await mountForm()
      await fillFields(wrapper, validInput)

      await submit(wrapper)

      expect(nativeSubmit).toHaveBeenCalledTimes(1)
      expect((nativeSubmit.mock.instances[0] as HTMLFormElement).id).toBe('signup-form')
    })
  })
})
