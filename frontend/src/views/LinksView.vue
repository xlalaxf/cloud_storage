<script setup>
import { Copy, Trash2 } from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'

const {
  copyText,
  deleteDirectLink,
  deleteShareLink,
  links,
  shareCopyText,
  shareMeta,
} = useCloudStorageContext()
</script>

<template>
<section class="links-layout">
  <div class="link-column">
    <h2>直链</h2>
    <div v-for="item in links.direct" :key="item.id" class="link-card">
      <div class="link-info">
        <span>{{ item.url }}</span>
        <small>{{ item.downloadCount }} 次下载</small>
      </div>
      <button class="icon-button" title="复制" @click="copyText(item.url)"><Copy :size="17" /></button>
      <button class="icon-button danger" title="删除直链" @click="deleteDirectLink(item)"><Trash2 :size="17" /></button>
    </div>
  </div>
  <div class="link-column">
    <h2>分享</h2>
    <div v-for="item in links.shares" :key="item.id" class="link-card">
      <div class="link-info">
        <strong>{{ item.rootFileName || '分享项目' }}</strong>
        <span>{{ item.url }}</span>
        <small>{{ shareMeta(item) }}</small>
      </div>
      <button class="icon-button" title="复制分享信息" @click="copyText(shareCopyText(item))"><Copy :size="17" /></button>
      <button class="icon-button danger" title="删除分享" @click="deleteShareLink(item)"><Trash2 :size="17" /></button>
    </div>
  </div>
</section>
</template>
