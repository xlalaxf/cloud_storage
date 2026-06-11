<script setup>
import { Folder, Info, Minus, Plus } from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'
import FolderTreeNode from '../components/FolderTreeNode.vue'

const {
  avatarBusy,
  avatarCropCanvas,
  avatarEditor,
  banDialog,
  busy,
  closeAvatarEditor,
  closeFileInfo,
  closeConfirmDialog,
  closePreview,
  confirmDialog,
  dialog,
  fileInfoDialog,
  formatDate,
  formatSize,
  isMediaFile,
  isFolderTargetDisabled,
  endAvatarDrag,
  moveAvatarDrag,
  preview,
  selectFolderTarget,
  selectedTargetPath,
  startAvatarDrag,
  submitBan,
  submitAvatarEditor,
  submitDialog,
  toggleFolderNode,
  updateAvatarScale,
  wheelAvatarEditor,
  zoomAvatarEditor,
} = useCloudStorageContext()
</script>

<template>
<Transition name="modal-fade">
  <div v-if="dialog.open" class="modal-backdrop" @click.self="dialog.open = false">
    <form class="modal" :class="{ 'folder-picker-modal': ['move', 'copy'].includes(dialog.type) }" @submit.prevent="submitDialog">
      <h2>{{ dialog.title }}</h2>
      <input
        v-if="['folder', 'rename', 'share'].includes(dialog.type)"
        id="dialog-value"
        v-model.trim="dialog.value"
        name="dialogValue"
        :required="dialog.type !== 'share'"
        :placeholder="dialog.type === 'share' ? '提取码，可为空' : '名称'"
      />
      <template v-if="['move', 'copy'].includes(dialog.type)">
        <div class="folder-picker-current">
          <span>目标位置</span>
          <strong :title="selectedTargetPath">{{ selectedTargetPath }}</strong>
        </div>
        <div class="folder-picker-list">
          <button
            type="button"
            class="folder-tree-row"
            :class="{ active: dialog.targetParentId === null }"
            @click="selectFolderTarget(null)"
          >
            <span class="folder-tree-toggle empty"></span>
            <Folder :size="18" />
            <span>根目录</span>
          </button>
          <div v-if="dialog.loadingFolders" class="empty-audit">正在加载目录</div>
          <template v-else>
            <FolderTreeNode
              v-for="node in dialog.folderTree"
              :key="node.id"
              :node="node"
              :level="0"
              :selected-id="dialog.targetParentId"
              :expanded-ids="dialog.expandedFolderIds"
              :disabled-checker="isFolderTargetDisabled"
              @toggle="toggleFolderNode"
              @select="selectFolderTarget"
            />
          </template>
          <div v-if="!dialog.loadingFolders && !dialog.folderTree.length" class="empty-audit">暂无文件夹</div>
        </div>
      </template>
      <div class="modal-actions">
        <button type="button" @click="dialog.open = false">取消</button>
        <button class="primary" :disabled="busy">确定</button>
      </div>
    </form>
  </div>
</Transition>

<Transition name="modal-fade">
  <div v-if="avatarEditor.open" class="modal-backdrop" @click.self="!avatarBusy && closeAvatarEditor()">
    <section class="modal avatar-editor-modal">
      <header>
        <h2>调整头像</h2>
        <span>{{ avatarEditor.fileName }}</span>
      </header>
      <div
        class="avatar-cropper"
        :class="{ dragging: avatarEditor.dragging }"
        @pointerdown="startAvatarDrag"
        @pointermove="moveAvatarDrag"
        @pointerup="endAvatarDrag"
        @pointercancel="endAvatarDrag"
        @wheel="wheelAvatarEditor"
      >
        <canvas ref="avatarCropCanvas" width="320" height="320"></canvas>
        <div class="avatar-crop-frame" aria-hidden="true"></div>
      </div>
      <div class="avatar-zoom-control">
        <button type="button" title="缩小" :disabled="avatarBusy" @click="zoomAvatarEditor(-0.08)">
          <Minus :size="17" />
        </button>
        <input
          type="range"
          :min="avatarEditor.minScale"
          :max="avatarEditor.maxScale"
          :step="0.01"
          :value="avatarEditor.scale"
          :disabled="avatarBusy"
          aria-label="头像缩放"
          @input="updateAvatarScale($event.target.value)"
        />
        <button type="button" title="放大" :disabled="avatarBusy" @click="zoomAvatarEditor(0.08)">
          <Plus :size="17" />
        </button>
      </div>
      <div class="modal-actions">
        <button type="button" :disabled="avatarBusy" @click="closeAvatarEditor">取消</button>
        <button type="button" class="primary" :disabled="avatarBusy" @click="submitAvatarEditor">
          {{ avatarBusy ? '上传中' : '保存头像' }}
        </button>
      </div>
    </section>
  </div>
</Transition>

<Transition name="modal-fade">
  <div v-if="banDialog.open" class="modal-backdrop" @click.self="banDialog.open = false">
    <form class="modal" @submit.prevent="submitBan">
      <h2>封禁 {{ banDialog.user?.username }}</h2>
      <textarea
        id="ban-reason"
        v-model.trim="banDialog.reason"
        name="banReason"
        required
        maxlength="300"
        placeholder="封禁理由"
      ></textarea>
      <input
        id="ban-until"
        v-model="banDialog.bannedUntil"
        name="bannedUntil"
        type="datetime-local"
        title="留空表示永久封禁"
      />
      <p class="form-hint">封禁时间留空表示永久封禁。</p>
      <div class="modal-actions">
        <button type="button" @click="banDialog.open = false">取消</button>
        <button class="primary" :disabled="busy">确定封禁</button>
      </div>
    </form>
  </div>
</Transition>

<Transition name="modal-fade">
  <div v-if="fileInfoDialog.open" class="modal-backdrop" @click.self="closeFileInfo">
    <section class="modal file-info-modal">
      <header>
        <Info :size="20" />
        <h2>文件详情</h2>
      </header>
      <dl v-if="fileInfoDialog.file" class="file-info-list">
        <div>
          <dt>名称</dt>
          <dd>{{ fileInfoDialog.file.name }}</dd>
        </div>
        <div>
          <dt>类型</dt>
          <dd>{{ fileInfoDialog.file.fileKind === 'FOLDER' ? '文件夹' : '文件' }}</dd>
        </div>
        <div v-if="fileInfoDialog.file.fileKind === 'FILE'">
          <dt>文件大小</dt>
          <dd>{{ formatSize(fileInfoDialog.file.sizeBytes) }}</dd>
        </div>
        <div v-if="fileInfoDialog.file.fileKind === 'FILE'">
          <dt>MIME 类型</dt>
          <dd>{{ fileInfoDialog.file.contentType || '未知' }}</dd>
        </div>
        <div v-if="fileInfoDialog.file.fileKind === 'FILE'">
          <dt>扩展名</dt>
          <dd>{{ fileInfoDialog.file.extension || '无' }}</dd>
        </div>
        <div v-if="isMediaFile(fileInfoDialog.file)">
          <dt>内容时长</dt>
          <dd>
            <span v-if="fileInfoDialog.durationLoading">读取中...</span>
            <span v-else-if="fileInfoDialog.durationText">{{ fileInfoDialog.durationText }}</span>
            <span v-else>{{ fileInfoDialog.durationError || '未知' }}</span>
          </dd>
        </div>
        <div>
          <dt>下载次数</dt>
          <dd>{{ fileInfoDialog.file.downloadCount }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ formatDate(fileInfoDialog.file.createdAt) || '-' }}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{{ formatDate(fileInfoDialog.file.updatedAt) || '-' }}</dd>
        </div>
        <div>
          <dt>文件 ID</dt>
          <dd>#{{ fileInfoDialog.file.id }}</dd>
        </div>
        <div v-if="fileInfoDialog.file.parentId">
          <dt>父级 ID</dt>
          <dd>#{{ fileInfoDialog.file.parentId }}</dd>
        </div>
      </dl>
      <div class="modal-actions">
        <button type="button" @click="closeFileInfo">关闭</button>
      </div>
    </section>
  </div>
</Transition>

<Transition name="modal-fade">
  <div v-if="confirmDialog.open" class="modal-backdrop" @click.self="closeConfirmDialog(false)">
    <section class="modal confirm-modal">
      <h2>{{ confirmDialog.title }}</h2>
      <p>{{ confirmDialog.message }}</p>
      <div class="modal-actions">
        <button type="button" @click="closeConfirmDialog(false)">取消</button>
        <button
          type="button"
          class="primary"
          :class="{ 'danger-confirm': confirmDialog.danger }"
          :disabled="busy"
          @click="closeConfirmDialog(true)"
        >
          {{ confirmDialog.confirmText }}
        </button>
      </div>
    </section>
  </div>
</Transition>

<Transition name="modal-fade">
  <div v-if="preview.open" class="modal-backdrop" @click.self="closePreview">
    <section class="preview-modal">
      <header>
        <h2>{{ preview.name }}</h2>
        <button class="icon-button" title="关闭" @click="closePreview">×</button>
      </header>
      <img v-if="preview.type.startsWith('image/')" :src="preview.url" alt="" />
      <audio v-else-if="preview.type.startsWith('audio/')" :src="preview.url" controls autoplay />
      <video v-else-if="preview.type.startsWith('video/')" :src="preview.url" controls />
      <iframe v-else-if="preview.type === 'application/pdf'" :src="preview.url"></iframe>
      <pre v-else-if="preview.text">{{ preview.text }}</pre>
      <div v-else class="empty-preview">无法在线预览</div>
    </section>
  </div>
</Transition>
</template>
