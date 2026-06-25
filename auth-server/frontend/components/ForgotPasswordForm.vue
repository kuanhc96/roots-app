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
const submitError = ref('')
const onSubmit = handleSubmit(async (values) => {
  submitError.value = ''
  try {
    const response = await fetch('/api/temp-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: values.email,
      }),
    })

    if (!response.ok) {
      submitError.value = 'Temp Password Generation Failed. Please try again.'
      return
    }

  } catch (e) {
    submitError.value = 'Temp Password Generation Failed. Please try again.'
  }
})
</script>

<template>
  <v-card width="400">
    <v-form @submit.prevent="onSubmit">
      <v-card-title>Forgot Password</v-card-title>
      <v-card-text>
        <v-alert v-if="submitError" type="error" density="compact" class="mb-4">
          {{ submitError }}
        </v-alert>
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