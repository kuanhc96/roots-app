<template>
  <div>
    <form id="login-form" method="post" action="/login"></form>
    <form id="guest-form" method="post" action="/login/guest"></form>

    <v-card class="login-card" width="400" elevation="0">

      <div class="brand">
        <div class="brand__mark">根</div>
        <span class="brand__name">Roots Church</span>
      </div>

      <v-card-title class="login-title">Welcome home</v-card-title>
      <p class="login-sub">Sign in to connect with your church family, groups, and giving.</p>

      <v-card-text class="pa-0">
        <label class="field-label">Email address</label>
        <v-text-field
            name="email"
            placeholder="you@example.com"
            type="email"
            form="login-form"
            variant="outlined"
            density="comfortable"
            color="primary"
            hide-details
            class="field"
        />
        <div class="field-label field-label--row">
          <v-label>Password</v-label>
          <NuxtLink to="/forgot-password" class="link link--sm">Forgot?</NuxtLink>
        </div>
        <v-text-field
            name="password"
            placeholder="********"
            type="password"
            form="login-form"
            variant="outlined"
            density="comfortable"
            color="primary"
            hide-details
            class="field"
        />
        <v-checkbox
            name="remember-me"
            value="true"
            label="Remember My Login"
            form="login-form"
            density="comfortable"
            color="primary"
            hide-details
            class="remember"
        />
      </v-card-text>

      <v-card-actions class="pa-0 actions">
        <v-btn
            type="submit"
            form="login-form"
            block
            flat
            height="52"
            color="primary"
            class="btn-primary"
        >Sign In</v-btn>
        <v-btn
            type="submit"
            form="guest-form"
            block
            flat
            height="50"
            variant="outlined"
            class="btn-guest">Continue as Guest
        </v-btn>
      </v-card-actions>

      <p class="signup">
        New To Roots?
        <NuxtLink to="/signup">Create an account</NuxtLink>
      </p>
    </v-card>
  </div>
</template>

<script setup lang="ts">
</script>
<style scoped>
/* Color scheme derived from the church mark: #1C985E on warm off-white */
.login-card {
  background: #ffffff;
  border-radius: 18px;
  padding: 40px 36px 32px;
  font-family: "Hanken Grotesk", "Helvetica Neue", Arial, sans-serif;
  color: #1d211c;
  border: 1px solid #ecebe1;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}
.brand__mark {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #1C985E;
  color: #eef6f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}
.brand__name {
  font-family: "Spectral", Georgia, serif;
  font-size: 18px;
  color: #2a322a;
}

.login-title {
  font-family: "Spectral", Georgia, serif;
  font-weight: 500;
  font-size: 32px;
  line-height: 1.15;
  letter-spacing: 0;
  padding: 0;
  margin-bottom: 8px;
  white-space: normal;
}
.login-sub {
  font-size: 15px;
  color: #5b635a;
  line-height: 1.5;
  margin: 0 0 30px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #3a4239;
  margin-bottom: 7px;
}
.field-label--row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.field {
  margin-bottom: 18px;
}

/* Vuetify field overrides for warm, minimal look */
.field :deep(.v-field) {
  border-radius: 11px;
  background: #fff;
}
.field :deep(.v-field__outline) {
  --v-field-border-opacity: 1;
  color: #dcdcd2;
}
.field :deep(.v-field--focused .v-field__outline) {
  color: #1C985E;
}

.remember {
  margin: 4px 0 18px;
}
.remember :deep(.v-label) {
  font-size: 14px;
  color: #4a524a;
  opacity: 1;
}

.actions {
  flex-direction: column;
  gap: 12px;
}
.btn-primary {
  border-radius: 11px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.01em;
  text-transform: none;
}
.btn-guest {
  border-radius: 11px;
  font-size: 15px;
  font-weight: 600;
  color: #3a4239;
  text-transform: none;
  border-color: #dcdcd2;
}

.signup {
  text-align: center;
  font-size: 14.5px;
  color: #5b635a;
  margin: 26px 0 0;
}
.link {
  color: #1C985E;
  font-weight: 600;
  text-decoration: none;
}
.link--sm {
  font-size: 13px;
}
</style>