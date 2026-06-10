<script setup>
import { Folder, FolderCog, Link, LogOut, Search, Settings, Shield, UserRound } from '@lucide/vue'
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
  avatarInitials,
  avatarUrl,
  currentUser,
  dragActive,
  foldersStack,
  goCrumb,
  isAdmin,
  logout,
  onDrop,
  searchText,
  systemSettings,
  switchTab,
} = useCloudStorageContext()
</script>

<template>
  <main class="app-shell" @dragover.prevent="dragActive = true" @dragleave.prevent="dragActive = false" @drop="onDrop">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <img v-if="avatarUrl" :src="avatarUrl" alt="" />
          <span v-else>{{ avatarInitials }}</span>
        </div>
        <div>
          <strong>{{ systemSettings.siteName }}</strong>
          <span>{{ currentUser.username }}</span>
        </div>
      </div>
      <nav>
        <button :class="{ active: activeTab === 'files' }" @click="switchTab('files')"><Folder :size="18" />文件</button>
        <button :class="{ active: activeTab === 'links' }" @click="switchTab('links')"><Link :size="18" />链接</button>
        <button :class="{ active: activeTab === 'profile' }" @click="switchTab('profile')"><UserRound :size="18" />资料</button>
        <button v-if="isAdmin" :class="{ active: activeTab === 'adminFiles' }" @click="switchTab('adminFiles')"><FolderCog :size="18" />用户文件</button>
        <button v-if="isAdmin" :class="{ active: activeTab === 'admin' }" @click="switchTab('admin')"><Shield :size="18" />后台</button>
        <button v-if="isAdmin" :class="{ active: activeTab === 'settings' }" @click="switchTab('settings')"><Settings :size="18" />设置</button>
      </nav>
      <button class="logout" @click="logout"><LogOut :size="18" />退出</button>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div class="breadcrumbs">
          <button v-for="(crumb, index) in foldersStack" :key="`${crumb.id}-${index}`" @click="goCrumb(index)">
            {{ crumb.name }}
          </button>
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
