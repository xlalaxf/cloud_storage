<script setup>
import { provideCloudStorageApp } from './composables/appContext'
import { useCloudStorageApp } from './composables/useCloudStorageApp'
import AppLayout from './views/AppLayout.vue'
import AuthView from './views/AuthView.vue'
import BootView from './views/BootView.vue'
import ShareView from './views/ShareView.vue'

const app = useCloudStorageApp()
provideCloudStorageApp(app)

const { appReady, currentUser, notification, shareRouteToken } = app
</script>

<template>
  <Transition name="page-ready" mode="out-in">
    <BootView v-if="!appReady" key="boot" />
    <ShareView v-else-if="shareRouteToken" key="share" />
    <AuthView v-else-if="!currentUser" key="auth" />
    <AppLayout v-else key="app" />
  </Transition>

  <Transition name="toast-pop">
    <div v-if="notification.visible" class="toast" :class="notification.type">
      {{ notification.text }}
    </div>
  </Transition>
</template>
