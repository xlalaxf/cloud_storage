<script setup>
import { RotateCcw, Upload } from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'

const {
  avatarBusy,
  avatarInitials,
  avatarInput,
  avatarUrl,
  busy,
  canCurrentUserChangeAvatar,
  clearAvatar,
  currentUser,
  formatDate,
  profileForm,
  systemSettings,
  saveProfile,
  uploadAvatarSelected,
} = useCloudStorageContext()
</script>

<template>
<section class="profile-panel">
  <aside class="profile-card profile-summary">
    <div class="avatar-block">
      <div class="profile-avatar">
        <img v-if="avatarUrl" :src="avatarUrl" alt="" />
        <span v-else>{{ avatarInitials }}</span>
      </div>
      <input id="avatar-upload" ref="avatarInput" name="avatar" type="file" accept="image/*" hidden @change="uploadAvatarSelected" />
      <button
        class="avatar-upload"
        :class="{ locked: !canCurrentUserChangeAvatar }"
        :disabled="avatarBusy || !canCurrentUserChangeAvatar"
        @click="avatarInput?.click()"
      >
        <Upload :size="16" />{{ avatarBusy ? '上传中' : '上传头像' }}
      </button>
      <button
        v-if="currentUser.hasAvatar"
        class="avatar-upload secondary"
        :disabled="avatarBusy"
        @click="clearAvatar"
      >
        <RotateCcw :size="16" />恢复默认
      </button>
      <small v-if="canCurrentUserChangeAvatar">最大 10MB，自动压缩到 100KB 内</small>
      <small v-else>当前暂不允许普通用户修改头像</small>
    </div>
    <div>
      <h2>{{ currentUser.nickname || currentUser.username }}</h2>
      <p>{{ currentUser.username }}</p>
    </div>
    <dl>
      <div>
        <dt>角色</dt>
        <dd>{{ currentUser.role }}</dd>
      </div>
      <div>
        <dt>邮箱</dt>
        <dd>{{ currentUser.reserveEmail }}</dd>
      </div>
      <div>
        <dt>创建时间</dt>
        <dd>{{ formatDate(currentUser.createdAt) }}</dd>
      </div>
    </dl>
  </aside>

  <section class="profile-card profile-editor">
    <div class="admin-section-title">
      <h3>用户资料</h3>
      <span>{{ systemSettings.allowAvatarChange ? '基础信息' : '头像修改受限' }}</span>
    </div>
    <form class="profile-form" @submit.prevent="saveProfile">
      <label>
        <span>昵称</span>
        <input id="profile-nickname" v-model.trim="profileForm.nickname" name="nickname" placeholder="昵称" autocomplete="nickname" />
      </label>
      <label>
        <span>预留邮箱</span>
        <input id="profile-email" v-model.trim="profileForm.reserveEmail" name="reserveEmail" required type="email" placeholder="预留邮箱" autocomplete="email" />
      </label>
      <label>
        <span>手机号</span>
        <input id="profile-phone" v-model.trim="profileForm.phone" name="phone" placeholder="手机号" autocomplete="tel" />
      </label>
      <div class="profile-actions">
        <button class="primary" :disabled="busy">保存</button>
      </div>
    </form>
  </section>
</section>
</template>
