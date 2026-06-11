<script setup>
import { Download, Eye } from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'
import AppDialogs from './AppDialogs.vue'

const {
  downloadShareFile,
  formatSize,
  iconFor,
  loadShare,
  openSharePreview,
  shareState,
} = useCloudStorageContext()
</script>

<template>
<main class="share-page">
  <section class="share-shell">
    <div class="share-top">
      <div>
        <p class="eyebrow">公开分享</p>
        <h1>{{ shareState.root?.name || '文件分享' }}</h1>
      </div>
      <div class="share-actions">
        <button
          v-if="shareState.unlocked && shareState.root?.fileKind === 'FOLDER'"
          class="share-download"
          @click="downloadShareFile(shareState.root)"
        >
          <Download :size="17" />
          <span>下载文件夹</span>
        </button>
        <div class="metric">
          <span>{{ shareState.downloadCount }}</span>
          <small>下载</small>
        </div>
      </div>
    </div>

    <form v-if="!shareState.unlocked && shareState.requiresCode" class="code-box" @submit.prevent="loadShare(null)">
      <input id="share-code" v-model="shareState.code" name="shareCode" placeholder="提取码" autocomplete="off" />
      <button type="submit">打开</button>
    </form>
    <p v-if="shareState.message" class="notice">{{ shareState.message }}</p>

    <div v-if="shareState.unlocked" class="file-table compact">
      <button v-if="shareState.parentId" class="text-action" @click="loadShare(null)">返回分享目录</button>
      <div
        v-for="file in shareState.files"
        :key="file.id"
        class="file-row"
        @dblclick="file.fileKind === 'FOLDER' ? loadShare(file.id) : openSharePreview(file)"
      >
        <component :is="iconFor(file)" class="file-icon" />
        <span class="file-name">{{ file.name }}</span>
        <span>{{ file.fileKind === 'FOLDER' ? '文件夹' : formatSize(file.sizeBytes) }}</span>
        <span>{{ file.downloadCount }} 次</span>
        <div class="share-file-actions">
          <button
            v-if="file.fileKind === 'FILE'"
            class="icon-button"
            title="预览"
            @click.stop="openSharePreview(file)"
            @dblclick.stop
          >
            <Eye :size="17" />
          </button>
          <button
            class="icon-button"
            :title="file.fileKind === 'FOLDER' ? '下载文件夹' : '下载'"
            @click.stop="downloadShareFile(file)"
            @dblclick.stop
          >
            <Download :size="17" />
          </button>
        </div>
      </div>
    </div>
  </section>
  <AppDialogs />
</main>
</template>
