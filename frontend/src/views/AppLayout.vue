<script setup>
import { computed } from 'vue'
import { Folder, FolderCog, Link, LogOut, Search, Settings, Shield, Star, UserRound } from '@lucide/vue'
import { useCloudStorageContext } from '../composables/appContext'
import AdminDashboardView from './AdminDashboardView.vue'
import AdminFilesView from './AdminFilesView.vue'
import AppDialogs from './AppDialogs.vue'
import FilesView from './FilesView.vue'
import LinksView from './LinksView.vue'
import ProfileView from './ProfileView.vue'
import SystemSettingsView from './SystemSettingsView.vue'

const {
  activeTab,
  currentUser,
  dragActive,
  isAdmin,
  logout,
  onDrop,
  searchText,
  systemSettings,
  switchTab,
} = useCloudStorageContext()

const pageTitle = computed(() => {
  const titles = {
    files: '文件中心',
    links: '链接管理',
    profile: '个人资料',
    adminFiles: '用户文件',
    admin: '管理后台',
    settings: '系统设置',
  }
  return titles[activeTab.value] || '工作台'
})
</script>

<template>
  <main class="app-shell" @dragover.prevent="dragActive = true" @dragleave.prevent="dragActive = false" @drop="onDrop">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-favorite" aria-hidden="true">
          <Star :size="18" fill="currentColor" />
        </span>
        <div class="brand-copy">
          <strong>{{ systemSettings.siteName }}</strong>
        </div>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-group">
          <span class="nav-label">网盘中心</span>
          <button :class="{ active: activeTab === 'files' }" @click="switchTab('files')"><Folder :size="18" />文件</button>
          <button :class="{ active: activeTab === 'links' }" @click="switchTab('links')"><Link :size="18" />链接</button>
          <button :class="{ active: activeTab === 'profile' }" @click="switchTab('profile')"><UserRound :size="18" />资料</button>
        </div>

        <div v-if="isAdmin" class="nav-group">
          <span class="nav-label">管理中心</span>
          <button :class="{ active: activeTab === 'adminFiles' }" @click="switchTab('adminFiles')"><FolderCog :size="18" />用户文件</button>
          <button :class="{ active: activeTab === 'admin' }" @click="switchTab('admin')"><Shield :size="18" />后台</button>
          <button :class="{ active: activeTab === 'settings' }" @click="switchTab('settings')"><Settings :size="18" />设置</button>
        </div>
      </nav>
      <button class="logout" @click="logout"><LogOut :size="18" />退出</button>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="topbar-copy">
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="search-box">
          <Search :size="17" />
          <input id="file-search" v-model="searchText" name="search" placeholder="搜索当前目录" />
        </div>
      </header>

      <Transition name="panel-switch" mode="out-in">
        <FilesView v-if="activeTab === 'files'" key="files" />
        <LinksView v-else-if="activeTab === 'links'" key="links" />
        <ProfileView v-else-if="activeTab === 'profile'" key="profile" />
        <AdminFilesView v-else-if="activeTab === 'adminFiles'" key="admin-files" />
        <AdminDashboardView v-else-if="activeTab === 'admin'" key="admin" />
        <SystemSettingsView v-else-if="activeTab === 'settings'" key="settings" />
      </Transition>
    </section>

    <AppDialogs />
  </main>
</template>
