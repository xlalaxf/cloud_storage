<script setup>
import {
  Archive,
  ArrowLeft,
  Copy,
  Download,
  ExternalLink,
  Eye,
  Folder,
  FolderPlus,
  Info,
  MoveRight,
  Pencil,
  RefreshCw,
  Share2,
  Trash2,
  Upload,
} from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'

const {
  allVisibleSelected,
  busy,
  clearSelection,
  cancelUpload,
  createDirectLink,
  createFolder,
  deleteSelected,
  deleteSelectedFiles,
  downloadFile,
  downloadSelectedFiles,
  archiveProgress,
  dragActive,
  extractProgress,
  extractSelected,
  fileInput,
  foldersStack,
  formatDate,
  formatDuration,
  formatEta,
  formatSize,
  formatSpeed,
  goParentFolder,
  goCrumb,
  iconFor,
  loadFiles,
  openFileInfo,
  openMoveDialog,
  openPreview,
  openShareDialog,
  isZipFile,
  partialVisibleSelected,
  pauseUpload,
  renameSelected,
  resumeUpload,
  selected,
  selectedFileItems,
  selectedFiles,
  selectedFolderItems,
  selectedIds,
  toggleAllVisible,
  toggleFileSelection,
  uploadProgress,
  uploadSelected,
  visibleFiles,
} = useCloudStorageContext()
</script>

<template>
<section class="content-grid">
  <div class="main-panel">
    <div class="toolbar">
      <button class="tool secondary-tool" :disabled="foldersStack.length <= 1" @click="goParentFolder">
        <ArrowLeft :size="18" />上一级
      </button>
      <button class="tool" @click="fileInput?.click()"><Upload :size="18" />上传</button>
      <button class="tool" @click="createFolder"><FolderPlus :size="18" />文件夹</button>
      <button class="icon-button" title="刷新" @click="loadFiles()"><RefreshCw :size="18" /></button>
      <input id="file-upload" ref="fileInput" name="files" multiple type="file" hidden @change="uploadSelected" />
    </div>

    <div class="drop-zone" :class="{ active: dragActive }" @click="fileInput?.click()">
      <Upload :size="20" />
      <span>拖拽文件到这里上传</span>
    </div>

    <div class="breadcrumbs file-path">
      <button v-for="(crumb, index) in foldersStack" :key="`${crumb.id ?? 'root'}-${index}`" @click="goCrumb(index)">
        {{ crumb.name }}
      </button>
    </div>

    <div v-if="uploadProgress.active" class="upload-progress" role="status" aria-live="polite">
      <div class="upload-progress-top">
        <strong>{{ uploadProgress.label }}</strong>
        <span>{{ uploadProgress.percent }}%</span>
      </div>
      <div class="upload-progress-meta">
        <span>{{ formatSize(uploadProgress.loaded) }} / {{ formatSize(uploadProgress.total) }}</span>
        <span>{{ formatSpeed(uploadProgress.speed) }}</span>
        <span>剩余 {{ formatEta(uploadProgress.eta) }}</span>
        <span v-if="uploadProgress.totalChunks">{{ uploadProgress.uploadedChunks }} / {{ uploadProgress.totalChunks }} 分片</span>
      </div>
      <div class="upload-actions">
        <button v-if="!uploadProgress.paused" type="button" @click="pauseUpload">暂停</button>
        <button v-else type="button" @click="resumeUpload">继续</button>
        <button type="button" class="danger" @click="cancelUpload">取消</button>
      </div>
      <div class="upload-bar" aria-hidden="true">
        <span :style="{ width: `${uploadProgress.percent}%` }"></span>
      </div>
    </div>

    <div v-if="extractProgress.active" class="upload-progress extract-progress" role="status" aria-live="polite">
      <div class="upload-progress-top">
        <strong>{{ extractProgress.label }}</strong>
        <span>{{ extractProgress.percent }}%</span>
      </div>
      <div class="upload-progress-meta">
        <span>{{ extractProgress.message }}</span>
        <span v-if="extractProgress.totalEntries">{{ extractProgress.processedEntries }} / {{ extractProgress.totalEntries }} 项</span>
        <span v-if="extractProgress.totalBytes">{{ formatSize(extractProgress.processedBytes) }} / {{ formatSize(extractProgress.totalBytes) }}</span>
        <span v-if="extractProgress.speed">{{ formatSpeed(extractProgress.speed) }}</span>
        <span v-if="extractProgress.elapsed">已用 {{ formatDuration(extractProgress.elapsed) }}</span>
        <span v-if="extractProgress.currentEntryName" class="extract-current">{{ extractProgress.currentEntryName }}</span>
      </div>
      <div class="upload-bar" aria-hidden="true">
        <span :style="{ width: `${extractProgress.percent}%` }"></span>
      </div>
    </div>

    <div v-if="archiveProgress.active" class="upload-progress extract-progress" role="status" aria-live="polite">
      <div class="upload-progress-top">
        <strong>{{ archiveProgress.label }}</strong>
        <span>{{ archiveProgress.percent }}%</span>
      </div>
      <div class="upload-progress-meta">
        <span>{{ archiveProgress.message }}</span>
        <span v-if="archiveProgress.totalEntries">{{ archiveProgress.processedEntries }} / {{ archiveProgress.totalEntries }} 项</span>
        <span v-if="archiveProgress.totalBytes">{{ formatSize(archiveProgress.processedBytes) }} / {{ formatSize(archiveProgress.totalBytes) }}</span>
        <span v-if="archiveProgress.speed">{{ formatSpeed(archiveProgress.speed) }}</span>
        <span v-if="archiveProgress.elapsed">已用 {{ formatDuration(archiveProgress.elapsed) }}</span>
        <span v-if="archiveProgress.currentEntryName" class="extract-current">{{ archiveProgress.currentEntryName }}</span>
      </div>
      <div class="upload-bar" aria-hidden="true">
        <span :style="{ width: `${archiveProgress.percent}%` }"></span>
      </div>
    </div>

    <div class="file-table">
      <div class="file-head">
        <input
          class="select-box"
          type="checkbox"
          :checked="allVisibleSelected"
          :indeterminate.prop="partialVisibleSelected"
          title="全选当前列表"
          @change="toggleAllVisible($event.target.checked)"
        />
        <span>名称</span>
        <span>大小</span>
        <span>下载</span>
        <span>更新</span>
      </div>
      <div
        v-for="file in visibleFiles"
        :key="file.id"
        class="file-row"
        :class="{ selected: selected?.id === file.id, checked: selectedIds.has(file.id) }"
        @click="selected = file"
        @dblclick="openPreview(file)"
      >
        <input
          class="select-box"
          type="checkbox"
          :checked="selectedIds.has(file.id)"
          :title="`选择 ${file.name}`"
          @click.stop
          @dblclick.stop
          @change="toggleFileSelection(file, $event.target.checked)"
        />
        <div class="name-cell">
          <component :is="iconFor(file)" class="file-icon" />
          <span class="file-name">{{ file.name }}</span>
        </div>
        <span>{{ file.fileKind === 'FOLDER' ? '文件夹' : formatSize(file.sizeBytes) }}</span>
        <span>{{ file.downloadCount }}</span>
        <span>{{ formatDate(file.updatedAt) }}</span>
      </div>
    </div>
  </div>

  <aside class="detail-panel" :class="{ 'is-empty': !selected && !selectedFiles.length }">
    <template v-if="selectedFiles.length">
      <Folder class="detail-icon muted" />
      <h2>已选择 {{ selectedFiles.length }} 个项目</h2>
      <p>{{ selectedFileItems.length }} 个文件 · {{ selectedFolderItems.length }} 个文件夹</p>
      <div class="action-grid">
        <button :disabled="!selectedFiles.length || busy || archiveProgress.active" @click="downloadSelectedFiles"><Download :size="17" />下载选中</button>
        <button :disabled="busy" @click="openMoveDialog('move')"><MoveRight :size="17" />移动到</button>
        <button :disabled="busy" @click="openMoveDialog('copy')"><Copy :size="17" />复制到</button>
        <button @click="clearSelection"><RefreshCw :size="17" />取消选择</button>
        <button class="danger" :disabled="busy" @click="deleteSelectedFiles"><Trash2 :size="17" />批量删除</button>
      </div>
    </template>
    <template v-else-if="selected">
      <component :is="iconFor(selected)" class="detail-icon" />
      <h2>{{ selected.name }}</h2>
      <p>{{ selected.fileKind === 'FOLDER' ? '文件夹' : formatSize(selected.sizeBytes) }}</p>
      <div class="action-grid">
        <button class="secondary-action" @click="openFileInfo(selected)"><Info :size="17" />详情</button>
        <button @click="openPreview(selected)"><Eye :size="17" />预览</button>
        <button :disabled="archiveProgress.active" @click="downloadFile(selected)"><Download :size="17" />下载</button>
        <button @click="renameSelected"><Pencil :size="17" />重命名</button>
        <button @click="openMoveDialog('move')"><MoveRight :size="17" />移动</button>
        <button @click="openMoveDialog('copy')"><Copy :size="17" />复制</button>
        <button v-if="selected.fileKind === 'FILE'" @click="createDirectLink"><ExternalLink :size="17" />直链</button>
        <button @click="openShareDialog"><Share2 :size="17" />分享</button>
        <button v-if="isZipFile(selected)" :disabled="extractProgress.active" @click="extractSelected"><Archive :size="17" />解压</button>
        <button class="danger" @click="deleteSelected"><Trash2 :size="17" />删除</button>
      </div>
    </template>
    <template v-else>
      <Folder class="detail-icon muted" />
      <h2>未选择文件</h2>
      <p>{{ visibleFiles.length }} 个项目</p>
    </template>
  </aside>
</section>
</template>
