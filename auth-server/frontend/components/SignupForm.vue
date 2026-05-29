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

const { handleSubmit, isSubmitting } = useForm({ validationSchema: schema })

const { value: name, errorMessage: nameError } = useField<string>('name')
const { value: email, errorMessage: emailError } = useField<string>('email')
const { value: password, errorMessage: passwordError } = useField<string>('password')
const { value: confirmPassword, errorMessage: confirmPasswordError } =
  useField<string>('confirmPassword')

const submitError = ref('')
const router = useRouter()

const onSubmit = handleSubmit(async (values) => {
  submitError.value = ''
  try {
    const response = await fetch('/api/accounts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: values.name,
        email: values.email,
        password: values.password,
      }),
    })

    if (!response.ok) {
      submitError.value = 'Account creation failed. Please try again.'
      return
    }

    router.replace('/signup/success')
  } catch (e) {
    submitError.value = 'Account creation failed. Please try again.'
  }
})
</script>

<template>
  <v-card width="400">
    <v-form @submit.prevent="onSubmit">
      <v-card-title>Create Account</v-card-title>
      <v-card-text>
        <v-alert v-if="submitError" type="error" density="compact" class="mb-4">
          {{ submitError }}
        </v-alert>
        <v-text-field
          v-model="name"
          name="name"
          label="Name"
          type="text"
          :error-messages="nameError"
        />
        <v-text-field
          v-model="email"
          name="email"
          label="Email"
          type="text"
          :error-messages="emailError"
        />
        <v-text-field
          v-model="password"
          name="password"
          label="Password"
          type="password"
          :error-messages="passwordError"
        />
        <v-text-field
          v-model="confirmPassword"
          name="confirmPassword"
          label="Confirm Password"
          type="password"
          :error-messages="confirmPasswordError"
        />
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit" :loading="isSubmitting" :disabled="isSubmitting">
          Create Account
        </v-btn>
        <v-spacer></v-spacer>
        <NuxtLink to="/login">Already have an account? Log in</NuxtLink>
      </v-card-actions>
    </v-form>
  </v-card>
</template>

<style scoped>
</style>
