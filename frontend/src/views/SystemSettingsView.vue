<script setup>
import { RefreshCw, Save, Trash2 } from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'

const {
  busy,
  cleanupExpiredStorage,
  formatDate,
  formatSize,
  loadAdminSettings,
  saveSystemSettings,
  settingsForm,
  settingsLoading,
  storageCleanup,
  systemSettings,
} = useCloudStorageContext()
</script>

<template>
<section class="settings-page">
  <section class="admin-section settings-section">
    <div class="admin-section-title">
      <h3>系统设置</h3>
      <button class="icon-button" title="刷新设置" :disabled="settingsLoading" @click="loadAdminSettings">
        <RefreshCw :size="18" />
      </button>
    </div>

    <form class="profile-form settings-form" @submit.prevent="saveSystemSettings">
      <label>
        <span>网站名称</span>
        <input
          id="settings-site-name"
          v-model.trim="settingsForm.siteName"
          name="siteName"
          required
          maxlength="80"
          placeholder="网站名称"
        />
      </label>

      <label class="setting-toggle">
        <input v-model="settingsForm.allowUserLogin" name="allowUserLogin" type="checkbox" />
        <span>
          <strong>允许普通用户登录</strong>
          <small>关闭后管理员仍可登录后台。</small>
        </span>
      </label>

      <label class="setting-toggle">
        <input v-model="settingsForm.allowUserRegistration" name="allowUserRegistration" type="checkbox" />
        <span>
          <strong>允许新账号注册</strong>
          <small>关闭后登录页不再提供注册入口。</small>
        </span>
      </label>

      <label class="setting-toggle">
        <input v-model="settingsForm.allowAvatarChange" name="allowAvatarChange" type="checkbox" />
        <span>
          <strong>允许普通用户修改头像</strong>
          <small>管理员账号不受此限制。</small>
        </span>
      </label>

      <div class="settings-summary">
        <div>
          <strong>{{ systemSettings.siteName }}</strong>
          <span>当前站点名称</span>
        </div>
        <div>
          <strong>{{ systemSettings.allowUserLogin ? '开启' : '关闭' }}</strong>
          <span>普通用户登录</span>
        </div>
        <div>
          <strong>{{ systemSettings.allowUserRegistration ? '开启' : '关闭' }}</strong>
          <span>新账号注册</span>
        </div>
        <div>
          <strong>{{ systemSettings.allowAvatarChange ? '开启' : '关闭' }}</strong>
          <span>普通用户头像修改</span>
        </div>
      </div>

      <div class="profile-actions">
        <button class="primary" :disabled="busy || settingsLoading">
          <Save :size="18" />保存设置
        </button>
        <span v-if="systemSettings.updatedAt" class="settings-updated">
          更新时间 {{ formatDate(systemSettings.updatedAt) }}
        </span>
      </div>
    </form>
  </section>

  <section class="admin-section settings-section">
    <div class="admin-section-title">
      <h3>存储维护</h3>
    </div>

    <div class="maintenance-panel">
      <div>
        <strong>清理过期临时文件</strong>
        <span>删除过期上传分片，以及超过 1 小时的上传合并临时文件。正式文件不会被删除。</span>
      </div>
      <button class="text-button danger-text" :disabled="busy || storageCleanup.running" @click="cleanupExpiredStorage">
        <Trash2 :size="16" />{{ storageCleanup.running ? '清理中' : '开始清理' }}
      </button>
    </div>

    <div v-if="storageCleanup.result" class="cleanup-summary">
      <div>
        <strong>{{ formatSize(storageCleanup.result.releasedBytes || 0) }}</strong>
        <span>释放空间</span>
      </div>
      <div>
        <strong>{{ storageCleanup.result.expiredUploadSessions || 0 }}</strong>
        <span>过期上传任务</span>
      </div>
      <div>
        <strong>{{ storageCleanup.result.deletedTemporaryFiles || 0 }}</strong>
        <span>临时文件</span>
      </div>
      <div>
        <strong>{{ storageCleanup.result.failedTemporaryFiles || 0 }}</strong>
        <span>失败文件</span>
      </div>
    </div>
  </section>
</section>
</template>
