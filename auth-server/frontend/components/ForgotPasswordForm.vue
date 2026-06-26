<script setup lang="ts">
import {toTypedSchema} from "@vee-validate/yup";
import * as yup from "yup";
import {useField, useForm} from "vee-validate";

const schema = toTypedSchema(
    yup.object({
      email: yup
          .string()
          .required('Email is required')
          .matches(/@/, 'Email must contain an "@"'),
    })
)

const { handleSubmit, isSubmitting } = useForm({ validationSchema: schema })
const { value: email, errorMessage: emailError } = useField<string>('email')

const onSubmit = handleSubmit(async (values) => {
  // The endpoint always responds 200 (it never reveals whether the email is on file),
  // so regardless of the outcome we send the user back to /login with the email
  // pre-filled and a neutral notice. Errors are swallowed for the same reason — the UX
  // must not differ based on whether an account exists. navigateTo is in-SPA client
  // navigation, so the query survives (a full reload would strip it on hydration).
  try {
    await fetch('/api/temp-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: values.email,
      }),
    })
  } catch (e) {
    // Intentionally ignored — see above.
  } finally {
    await navigateTo({ path: '/login', query: { email: values.email, notice: 'tempPasswordSent' } })
  }
})
</script>

<template>
  <v-card width="400">
    <v-form @submit.prevent="onSubmit">
      <v-card-title>Forgot Password</v-card-title>
      <v-card-text>
        <v-text-field
            v-model="email"
            name="email"
            label="Email"
            type="text"
            :error-messages="emailError"
        />
      </v-card-text>
      <v-card-actions>
        <v-btn type="submit" :loading="isSubmitting" :disabled="isSubmitting">
          Get Temporary Password
        </v-btn>
        <v-spacer></v-spacer>
        <NuxtLink to="/login">Back To Log in</NuxtLink>
      </v-card-actions>
    </v-form>
  </v-card>

</template>

<style scoped>

</style>
