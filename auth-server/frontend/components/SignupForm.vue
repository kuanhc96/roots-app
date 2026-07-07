<script setup lang="ts">
import { useForm, useField } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/yup'
import * as yup from 'yup'

const schema = toTypedSchema(
  yup.object({
    name: yup
      .string()
      .required('Name is required')
      .max(255, 'Name must be 255 characters or fewer'),
    email: yup
      .string()
      .required('Email is required')
      .matches(/@/, 'Email must contain an "@"'),
    password: yup
      .string()
      .required('Password is required')
      .min(8, 'Password must be at least 8 characters')
      .matches(/[A-Z]/, 'Password must include at least one uppercase letter')
      .matches(/[a-z]/, 'Password must include at least one lowercase letter')
      .matches(/[0-9]/, 'Password must include at least one number'),
    confirmPassword: yup
      .string()
      .required('Please confirm your password')
      .oneOf([yup.ref('password')], 'Passwords must match'),
  })
)

// A rejected signup 302s back here with ?e=<code>&name=…&email=… — prefill the
// non-secret fields so the user only re-types the password.
const route = useRoute()

const { handleSubmit, isSubmitting } = useForm({
  validationSchema: schema,
  initialValues: {
    name: (route.query.name as string) ?? '',
    email: (route.query.email as string) ?? '',
  },
})

const { value: name, errorMessage: nameError } = useField<string>('name')
const { value: email, errorMessage: emailError } = useField<string>('email')
const { value: password, errorMessage: passwordError } = useField<string>('password')
const { value: confirmPassword, errorMessage: confirmPasswordError } =
  useField<string>('confirmPassword')

// Server error codes: e=email_taken (duplicate email) or e=invalid_request
// (server-side validation failure — the client-side rules should catch it first).
const signupErrorMessage = useServerErrorMessage()

const onSubmit = handleSubmit((_values, { evt }) => {
  // Validation passed — resume the native submission so the browser navigates: the
  // server creates the account, establishes the email-verification pending session,
  // and redirects to /signup/success (or back here with an error code).
  ;(evt?.target as HTMLFormElement).submit()
})
</script>

<template>
  <div>
    <form id="signup-form" method="post" action="/signup" @submit="onSubmit"></form>

    <v-card width="400">
      <v-card-title>Create Account</v-card-title>
      <v-card-text>
        <v-alert v-if="signupErrorMessage" type="error" density="compact" class="mb-4">
          {{ signupErrorMessage }}
        </v-alert>
        <v-text-field
          v-model="name"
          name="name"
          label="Name"
          type="text"
          form="signup-form"
          :error-messages="nameError"
        />
        <v-text-field
          v-model="email"
          name="email"
          label="Email"
          type="text"
          form="signup-form"
          :error-messages="emailError"
        />
        <v-text-field
          v-model="password"
          name="password"
          label="Password"
          type="password"
          form="signup-form"
          :error-messages="passwordError"
        />
        <!-- Confirm-match is client-side only, so this field is not bound to the form -->
        <v-text-field
          v-model="confirmPassword"
          name="confirmPassword"
          label="Confirm Password"
          type="password"
          :error-messages="confirmPasswordError"
        />
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit" form="signup-form" :loading="isSubmitting" :disabled="isSubmitting">
          Create Account
        </v-btn>
        <v-spacer></v-spacer>
        <NuxtLink to="/login">Already have an account? Log in</NuxtLink>
      </v-card-actions>
    </v-card>
  </div>
</template>

<style scoped>
</style>
