<script setup lang="ts">
import { useForm, useField } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/yup'
import * as yup from 'yup'

// Same complexity rules as account creation; confirm-match is enforced here client-side
// (the server only receives newPassword, mirroring how account creation doesn't re-check
// the confirm field).
const schema = toTypedSchema(
  yup.object({
    newPassword: yup
      .string()
      .required('Password is required')
      .min(8, 'Password must be at least 8 characters')
      .matches(/[A-Z]/, 'Password must include at least one uppercase letter')
      .matches(/[a-z]/, 'Password must include at least one lowercase letter')
      .matches(/[0-9]/, 'Password must include at least one number'),
    confirmNewPassword: yup
      .string()
      .required('Please confirm your password')
      .oneOf([yup.ref('newPassword')], 'Passwords must match'),
  })
)

const { handleSubmit, isSubmitting } = useForm({ validationSchema: schema })
const { value: newPassword, errorMessage: newPasswordError } = useField<string>('newPassword')
const { value: confirmNewPassword, errorMessage: confirmNewPasswordError } =
  useField<string>('confirmNewPassword')

const resetForm = ref<HTMLFormElement | null>(null)

// A server-side rejection of the new password 302s back here with
// ?error=invalidPassword (the client-side rules should catch it first).
const resetErrorMessage = useServerErrorMessage()

const onSubmit = handleSubmit(() => {
  // Submit the native form so the browser navigates and follows the server's 302 into the
  // saved OAuth2 request (a fetch couldn't drive that top-level navigation).
  resetForm.value?.submit()
})
</script>

<template>
  <v-card width="400">
    <!-- Native form submitted after client-side validation passes -->
    <form ref="resetForm" method="post" action="/reset-password" style="display: none">
      <input type="hidden" name="newPassword" :value="newPassword" />
    </form>
    <v-form @submit.prevent="onSubmit">
      <v-card-title>Set a New Password</v-card-title>
      <v-card-text>
        <v-text-field
          v-model="newPassword"
          name="newPassword"
          label="New Password"
          type="password"
          :error-messages="newPasswordError"
        />
        <v-text-field
          v-model="confirmNewPassword"
          name="confirmNewPassword"
          label="Confirm New Password"
          type="password"
          :error-messages="confirmNewPasswordError"
        />
        <v-alert v-if="resetErrorMessage" type="warning" density="compact">
          {{ resetErrorMessage }}
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit" :loading="isSubmitting" :disabled="isSubmitting">
          Set New Password
        </v-btn>
      </v-card-actions>
    </v-form>
  </v-card>
</template>

<style scoped>
</style>
