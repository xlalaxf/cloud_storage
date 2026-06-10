<script setup>
import { RefreshCw, Shield, Trash2 } from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'

const {
  admin,
  busy,
  clearAllAudits,
  clearAuditRange,
  currentUser,
  deleteAdminUser,
  formatBanUntil,
  formatDate,
  loadAdminAudits,
  openBanDialog,
  operationLabel,
  selectAdminUser,
  unbanUser,
} = useCloudStorageContext()
</script>

<template>
<section class="admin-layout">
  <div class="users-panel">
    <button
      v-for="user in admin.users"
      :key="user.id"
      :class="{ active: admin.selectedUser?.id === user.id }"
      @click="selectAdminUser(user)"
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
        <button
          v-if="admin.selectedUser && !admin.selectedUser.banned"
          class="tool danger-tool"
          :disabled="busy || admin.selectedUser.id === currentUser.id"
          @click="openBanDialog(admin.selectedUser)"
        >
          <Shield :size="18" />封禁
        </button>
        <button
          v-if="admin.selectedUser?.banned"
          class="tool secondary-tool"
          :disabled="busy"
          @click="unbanUser(admin.selectedUser)"
        >
          <Shield :size="18" />解封
        </button>
        <button
          v-if="admin.selectedUser"
          class="tool danger-tool"
          :disabled="busy || admin.selectedUser.id === currentUser.id"
          @click="deleteAdminUser(admin.selectedUser)"
        >
          <Trash2 :size="18" />删除用户
        </button>
      </div>
      <div v-if="admin.selectedUser" class="admin-user-status" :class="{ banned: admin.selectedUser.banned }">
        <strong>{{ admin.selectedUser.username }}</strong>
        <span v-if="admin.selectedUser.banned">
          已封禁至 {{ formatBanUntil(admin.selectedUser.bannedUntil) }} · {{ admin.selectedUser.banReason }}
        </span>
        <span v-else>账号状态正常</span>
      </div>
    </section>

    <section class="admin-section">
      <div class="admin-section-title">
        <h3>记录管理</h3>
        <div class="audit-range">
          <input id="audit-from" v-model="admin.auditFrom" name="auditFrom" type="date" title="开始日期" @change="loadAdminAudits" />
          <span>至</span>
          <input id="audit-to" v-model="admin.auditTo" name="auditTo" type="date" title="结束日期" @change="loadAdminAudits" />
          <button class="icon-button" title="刷新记录" @click="loadAdminAudits"><RefreshCw :size="16" /></button>
          <button class="text-button" :disabled="!admin.auditFrom && !admin.auditTo" @click="clearAuditRange">清空</button>
          <button class="text-button danger-text" :disabled="busy" @click="clearAllAudits">
            <Trash2 :size="15" />清除所有记录
          </button>
        </div>
      </div>
      <div class="audit-grid">
        <section class="audit-panel">
          <header>
            <h3>登录记录</h3>
            <span>最近 {{ admin.loginAudits.length }} / 30 条</span>
          </header>
          <div class="audit-list">
            <div v-if="!admin.loginAudits.length" class="empty-audit">暂无登录记录</div>
            <div v-for="audit in admin.loginAudits" :key="audit.id" class="audit-row">
              <div>
                <strong>{{ audit.ipAddress || 'unknown' }}</strong>
                <small>{{ formatDate(audit.createdAt) }}</small>
              </div>
              <span class="audit-status" :class="{ failed: !audit.successful }">{{ audit.successful ? '成功' : '失败' }}</span>
              <p>{{ audit.message || '-' }}</p>
            </div>
          </div>
        </section>
        <section class="audit-panel">
          <header>
            <h3>文件操作记录</h3>
            <span>最近 {{ admin.fileAudits.length }} / 80 条</span>
          </header>
          <div class="audit-list">
            <div v-if="!admin.fileAudits.length" class="empty-audit">暂无文件操作记录</div>
            <div v-for="audit in admin.fileAudits" :key="audit.id" class="audit-row">
              <div>
                <strong>{{ operationLabel(audit.operation) }}</strong>
                <small>{{ formatDate(audit.createdAt) }}</small>
              </div>
              <span class="audit-file">{{ audit.fileName || `#${audit.fileId || '-'}` }}</span>
              <p>{{ audit.detail || '-' }}</p>
            </div>
          </div>
        </section>
      </div>
    </section>
  </div>
</section>
</template>
