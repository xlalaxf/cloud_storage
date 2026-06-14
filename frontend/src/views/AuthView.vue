<script setup>
import { computed, ref } from 'vue'
import { Check, ChevronDown, Globe2, LoaderCircle, LogIn, RefreshCcw, Star, UserPlus } from '@lucide/vue'
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

const savedLanguage = typeof window !== 'undefined'
  ? window.localStorage.getItem('cloud_storage_auth_language')
  : ''
const authLanguage = ref(savedLanguage === 'zh' ? 'zh' : 'en')
const languageOpen = ref(false)
const isRegister = computed(() => authMode.value === 'register')
const passwordAutocomplete = computed(() => (isRegister.value ? 'new-password' : 'current-password'))
const authCopy = {
  en: {
    language: 'Language',
    english: 'English',
    chinese: '中文',
    signIn: 'Sign in',
    signUp: 'Sign up',
    signingIn: 'Signing in...',
    signingUp: 'Signing up...',
    or: 'or',
    createAccount: 'create an account',
    signInAccount: 'sign in to your account',
    username: 'Username',
    usernamePlaceholder: 'Enter your username',
    email: 'Email address',
    emailPlaceholder: 'Enter your email address',
    password: 'Password',
    passwordPlaceholder: 'Enter your password',
    nickname: 'Nickname',
    nicknamePlaceholder: 'Enter your nickname',
    captcha: 'Verification code',
    captchaPlaceholder: 'Enter verification code',
    refreshCaptcha: 'Refresh verification code',
    captchaLoading: 'Loading',
    loginClosed: 'User login is currently disabled. Administrators can still sign in.',
    registerClosed: 'Account creation is currently closed.',
  },
  zh: {
    language: '语言',
    english: 'English',
    chinese: '中文',
    signIn: '登录',
    signUp: '注册',
    signingIn: '登录中...',
    signingUp: '注册中...',
    or: '或',
    createAccount: '创建账号',
    signInAccount: '登录已有账号',
    username: '用户名',
    usernamePlaceholder: '请输入用户名',
    email: '邮箱地址',
    emailPlaceholder: '请输入邮箱地址',
    password: '密码',
    passwordPlaceholder: '请输入密码',
    nickname: '昵称',
    nicknamePlaceholder: '请输入昵称',
    captcha: '验证码',
    captchaPlaceholder: '请输入验证码',
    refreshCaptcha: '刷新验证码',
    captchaLoading: '加载中',
    loginClosed: '普通用户登录当前已关闭，管理员仍可登录。',
    registerClosed: '账号注册当前已关闭。',
  },
}
const copy = computed(() => authCopy[authLanguage.value])
const authTitle = computed(() => (isRegister.value ? copy.value.signUp : copy.value.signIn))
const submitLabel = computed(() => {
  if (busy.value) {
    return isRegister.value ? copy.value.signingUp : copy.value.signingIn
  }
  return authTitle.value
})

function switchAuthMode(mode) {
  if (mode === 'register' && !canRegister.value) return
  authMode.value = mode
  void loadCaptcha().catch(() => {})
}

function setLanguage(language) {
  authLanguage.value = language
  languageOpen.value = false
  if (typeof window !== 'undefined') {
    window.localStorage.setItem('cloud_storage_auth_language', language)
  }
}
</script>

<template>
  <main class="auth-page">
    <div class="auth-language" @keydown.esc="languageOpen = false">
      <button
        type="button"
        class="auth-language-trigger"
        :aria-expanded="languageOpen"
        aria-haspopup="menu"
        @click="languageOpen = !languageOpen"
      >
        <Globe2 :size="18" aria-hidden="true" />
        <span>{{ copy.language }}</span>
        <strong>{{ authLanguage === 'zh' ? copy.chinese : copy.english }}</strong>
        <ChevronDown :size="16" aria-hidden="true" />
      </button>
      <Transition name="auth-language-pop">
        <div v-if="languageOpen" class="auth-language-menu" role="menu">
          <button
            type="button"
            role="menuitemradio"
            :aria-checked="authLanguage === 'zh'"
            @click="setLanguage('zh')"
          >
            <Check v-if="authLanguage === 'zh'" :size="16" aria-hidden="true" />
            <span v-else class="auth-language-check-space"></span>
            <span>{{ copy.chinese }}</span>
          </button>
          <button
            type="button"
            role="menuitemradio"
            :aria-checked="authLanguage === 'en'"
            @click="setLanguage('en')"
          >
            <Check v-if="authLanguage === 'en'" :size="16" aria-hidden="true" />
            <span v-else class="auth-language-check-space"></span>
            <span>{{ copy.english }}</span>
          </button>
        </div>
      </Transition>
    </div>

    <section class="auth-panel" :class="{ 'is-register': isRegister }">
      <div class="auth-brand">
        <span class="auth-logo" aria-hidden="true">
          <Star :size="36" fill="currentColor" />
        </span>
        <span class="auth-brand-name">{{ systemSettings.siteName }}</span>
      </div>

      <div class="auth-heading">
        <h1>{{ authTitle }}</h1>
        <p v-if="canRegister || isRegister" class="auth-switch">
          <span>{{ copy.or }}</span>
          <button
            type="button"
            class="auth-link"
            @click="switchAuthMode(isRegister ? 'login' : 'register')"
          >
            {{ isRegister ? copy.signInAccount : copy.createAccount }}
          </button>
        </p>
      </div>

      <form class="auth-form" @submit.prevent="isRegister ? register() : login()">
        <Transition name="auth-mode" mode="out-in">
          <div :key="authMode" class="auth-fields">
            <label class="auth-field" for="auth-username">
              <span>{{ copy.username }}</span>
              <input
                id="auth-username"
                v-model.trim="authForm.username"
                name="username"
                required
                :placeholder="copy.usernamePlaceholder"
                autocomplete="username"
              />
            </label>

            <label v-if="isRegister" class="auth-field" for="auth-email">
              <span>{{ copy.email }}</span>
              <input
                id="auth-email"
                v-model.trim="authForm.reserveEmail"
                name="reserveEmail"
                required
                type="email"
                :placeholder="copy.emailPlaceholder"
                autocomplete="email"
              />
            </label>

            <label class="auth-field" for="auth-password">
              <span>{{ copy.password }}</span>
              <input
                id="auth-password"
                v-model="authForm.password"
                name="password"
                required
                type="password"
                :placeholder="copy.passwordPlaceholder"
                :autocomplete="passwordAutocomplete"
              />
            </label>

            <label v-if="isRegister" class="auth-field" for="auth-nickname">
              <span>{{ copy.nickname }}</span>
              <input
                id="auth-nickname"
                v-model.trim="authForm.nickname"
                name="nickname"
                :placeholder="copy.nicknamePlaceholder"
                autocomplete="nickname"
              />
            </label>

            <label class="auth-field" for="auth-captcha">
              <span>{{ copy.captcha }}</span>
              <div class="auth-captcha-grid">
                <input
                  id="auth-captcha"
                  v-model.trim="authForm.captchaCode"
                  name="captchaCode"
                  required
                  :placeholder="copy.captchaPlaceholder"
                  autocomplete="off"
                />
                <button
                  type="button"
                  class="auth-captcha-card"
                  :title="copy.refreshCaptcha"
                  :aria-label="copy.refreshCaptcha"
                  @click="loadCaptcha"
                >
                  <span v-if="authForm.captchaSvg" class="auth-captcha-svg" v-html="authForm.captchaSvg"></span>
                  <span v-else class="auth-captcha-empty">{{ copy.captchaLoading }}</span>
                  <RefreshCcw class="auth-captcha-refresh" :size="16" aria-hidden="true" />
                </button>
              </div>
            </label>
          </div>
        </Transition>

        <button
          class="auth-submit"
          :class="{ loading: busy }"
          :disabled="busy || !authForm.captchaId"
        >
          <LoaderCircle v-if="busy" class="auth-submit-spinner" :size="20" aria-hidden="true" />
          <UserPlus v-else-if="isRegister" :size="20" aria-hidden="true" />
          <LogIn v-else :size="20" aria-hidden="true" />
          <span>{{ submitLabel }}</span>
        </button>

        <Transition name="auth-hint" mode="out-in">
          <p v-if="!systemSettings.allowUserLogin" key="login-closed" class="auth-hint danger">
            {{ copy.loginClosed }}
          </p>
          <p v-else-if="!systemSettings.allowUserRegistration" key="register-closed" class="auth-hint">
            {{ copy.registerClosed }}
          </p>
          <p v-else-if="authBusyText" key="busy" class="auth-hint">{{ submitLabel }}</p>
        </Transition>
      </form>
    </section>
  </main>
</template>
