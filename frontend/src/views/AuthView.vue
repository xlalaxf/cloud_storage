<script setup>
import { useCloudStorageContext } from '../composables/appContext'

const {
  authBusyText,
  authForm,
  authMode,
  busy,
  canRegister,
  loadCaptcha,
  login,
  register,
  systemSettings,
} = useCloudStorageContext()
</script>

<template>
<main class="auth-page">
  <section class="auth-panel">
    <div>
      <p class="eyebrow">Cloud Storage</p>
      <h1>{{ systemSettings.siteName }}</h1>
    </div>
    <div class="segmented">
      <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">登录</button>
      <button
        :class="{ active: authMode === 'register' }"
        :disabled="!canRegister"
        @click="authMode = 'register'"
      >
        注册
      </button>
    </div>
    <form class="form" @submit.prevent="authMode === 'login' ? login() : register()">
      <input id="auth-username" v-model.trim="authForm.username" name="username" required placeholder="用户名" autocomplete="username" />
      <input id="auth-password" v-model="authForm.password" name="password" required type="password" placeholder="密码" autocomplete="current-password" />
      <template v-if="authMode === 'register'">
        <input id="auth-email" v-model.trim="authForm.reserveEmail" name="reserveEmail" required type="email" placeholder="预留邮箱" autocomplete="email" />
        <input id="auth-nickname" v-model.trim="authForm.nickname" name="nickname" placeholder="昵称" autocomplete="nickname" />
      </template>
      <div v-if="authMode === 'login'" class="captcha-row">
        <input id="auth-captcha" v-model.trim="authForm.captchaCode" name="captchaCode" required placeholder="验证码" autocomplete="off" />
        <button type="button" class="captcha" title="刷新验证码" @click="loadCaptcha" v-html="authForm.captchaSvg"></button>
      </div>
      <button class="primary" :disabled="busy">{{ authBusyText || (authMode === 'login' ? '登录' : '注册') }}</button>
      <p v-if="!systemSettings.allowUserLogin" class="form-hint danger">普通用户登录已关闭，管理员仍可登录。</p>
      <p v-else-if="!systemSettings.allowUserRegistration" class="form-hint">新账号注册已关闭。</p>
      <p v-if="authBusyText" class="form-hint">{{ authBusyText }}</p>
    </form>
  </section>
</main>
</template>
