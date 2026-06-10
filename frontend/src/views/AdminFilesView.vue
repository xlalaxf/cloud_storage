<script setup>
import { ArrowLeft, RefreshCw, Trash2 } from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'

const {
  admin,
  deleteAdminFile,
  formatBanUntil,
  formatSize,
  goAdminParentFolder,
  iconFor,
  loadAdminFiles,
  loadAdminFolderAt,
  loadAdminRootFolder,
  openAdminFolder,
  selectAdminUser,
} = useCloudStorageContext()
</script>

<template>
<section class="admin-layout admin-files-page">
  <div class="users-panel">
    <button
      v-for="user in admin.users"
      :key="user.id"
      :class="{ active: admin.selectedUser?.id === user.id }"
      @click="selectAdminUser(user, 'adminFiles')"
    >
      <span>
        <strong>{{ user.username }}</strong>
        <small>{{ user.role }}</small>
      </span>
      <em :class="{ banned: user.banned }">{{ user.banned ? '封禁中' : '正常' }}</em>
    </button>
  </div>
  <div class="admin-detail">
    <section class="admin-section">
      <div class="toolbar">
        <button class="tool secondary-tool" :disabled="admin.folderStack.length <= 1" @click="goAdminParentFolder">
          <ArrowLeft :size="18" />上一级
        </button>
        <button class="tool" @click="loadAdminRootFolder">根目录</button>
        <button class="icon-button" title="刷新文件" @click="loadAdminFiles(admin.parentId)"><RefreshCw :size="18" /></button>
      </div>
      <div class="admin-folder-crumbs">
        <button
          v-for="(crumb, index) in admin.folderStack"
          :key="`${crumb.id ?? 'root'}-${index}`"
          @click="loadAdminFolderAt(index)"
        >
          {{ crumb.name }}
        </button>
      </div>
      <div v-if="admin.selectedUser" class="admin-user-status" :class="{ banned: admin.selectedUser.banned }">
        <strong>{{ admin.selectedUser.username }}</strong>
        <span v-if="admin.selectedUser.banned">
          已封禁至 {{ formatBanUntil(admin.selectedUser.bannedUntil) }} · {{ admin.selectedUser.banReason }}
        </span>
        <span v-else>正在管理该用户的文件</span>
      </div>
      <div class="admin-section-title">
        <h3>用户文件管理</h3>
        <span>{{ admin.files.length }} 项</span>
      </div>
      <div class="file-table compact admin-file-list">
        <div
          v-for="file in admin.files"
          :key="file.id"
          class="file-row"
          @dblclick="openAdminFolder(file)"
        >
          <component :is="iconFor(file)" class="file-icon" />
          <span class="file-name">{{ file.name }}</span>
          <span>{{ file.fileKind === 'FOLDER' ? '文件夹' : formatSize(file.sizeBytes) }}</span>
          <span>{{ file.downloadCount }} 次</span>
          <button class="icon-button danger" title="删除" @click="deleteAdminFile(file)"><Trash2 :size="17" /></button>
        </div>
        <div v-if="!admin.files.length" class="empty-audit">暂无文件</div>
      </div>
    </section>
  </div>
</section>
</template>
