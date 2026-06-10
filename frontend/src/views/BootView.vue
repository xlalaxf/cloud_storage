<script setup>
import { useCloudStorageContext } from '../composables/appContext'

const { systemSettings } = useCloudStorageContext()
</script>

<template>
<main class="boot-page">
  <section class="boot-panel">
    <div class="brand-mark">CS</div>
    <div class="boot-copy">
      <strong>{{ systemSettings.siteName }}</strong>
      <span>加载中</span>
    </div>
    <div class="boot-bar" aria-hidden="true"></div>
  </section>
</main>
</template>
