import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { FileArchive, FileAudio, FileImage, FileText, Folder } from '@lucide/vue'
import { blobRequest, downloadBlob, getToken, request, setToken, uploadChunkRequest, uploadRequest } from '../api/client'

export function useCloudStorageApp() {
  const token = ref(getToken())
  const currentUser = ref(null)
  const appReady = ref(false)
  const busy = ref(false)
  const authBusyText = ref('')
  const notification = reactive({ visible: false, text: '', type: 'info' })
  const authMode = ref('login')
  const activeTab = ref('files')
  const dragActive = ref(false)
  const fileInput = ref(null)
  const avatarInput = ref(null)
  const avatarCropCanvas = ref(null)
  const avatarUrl = ref('')
  const avatarBusy = ref(false)
  const selected = ref(null)
  const selectedIds = ref(new Set())
  const files = ref([])
  const links = ref({ direct: [], shares: [] })
  const systemSettings = reactive({
    siteName: 'Cloud Storage',
    allowUserLogin: true,
    allowUserRegistration: true,
    allowAvatarChange: true,
    updatedAt: '',
  })
  const settingsForm = reactive({
    siteName: 'Cloud Storage',
    allowUserLogin: true,
    allowUserRegistration: true,
    allowAvatarChange: true,
  })
  const settingsLoading = ref(false)
  const storageCleanup = reactive({
    running: false,
    result: null,
    orphanRunning: false,
    orphanResult: null,
  })
  const foldersStack = ref([{ id: null, name: '全部文件' }])
  const searchText = ref('')
  const preview = reactive({ open: false, name: '', url: '', type: '', text: '' })
  const fileInfoDialog = reactive({
    open: false,
    file: null,
    durationLoading: false,
    durationText: '',
    durationError: '',
    mediaUrl: '',
  })
  const dialog = reactive({ open: false, type: '', title: '', value: '', targetParentId: null, folderTree: [], expandedFolderIds: new Set(), loadingFolders: false })
  const banDialog = reactive({ open: false, user: null, reason: '', bannedUntil: '' })
  const confirmDialog = reactive({ open: false, title: '', message: '', confirmText: '确定', danger: false, resolve: null })
  const avatarEditor = reactive({
    open: false,
    fileName: '',
    previewUrl: '',
    naturalWidth: 0,
    naturalHeight: 0,
    scale: 1,
    minScale: 1,
    maxScale: 1,
    offsetX: 0,
    offsetY: 0,
    dragging: false,
  })
  
  const authForm = reactive({
    username: '',
    password: '',
    reserveEmail: '',
    nickname: '',
    captchaId: '',
    captchaCode: '',
    captchaSvg: '',
  })
  
  const profileForm = reactive({
    nickname: '',
    reserveEmail: '',
    phone: '',
  })
  
  const uploadProgress = reactive({
    active: false,
    label: '',
    status: '',
    percent: 0,
    loaded: 0,
    total: 0,
    speed: 0,
    eta: 0,
    totalChunks: 0,
    uploadedChunks: 0,
    paused: false,
    cancellable: false,
  })
  const extractProgress = reactive({
    active: false,
    fileId: null,
    jobId: '',
    label: '',
    status: '',
    percent: 0,
    processedEntries: 0,
    totalEntries: 0,
    processedBytes: 0,
    totalBytes: 0,
    currentEntryName: '',
    speed: 0,
    elapsed: 0,
    message: '',
  })
  const archiveProgress = reactive({
    active: false,
    fileId: null,
    jobId: '',
    label: '',
    status: '',
    percent: 0,
    processedEntries: 0,
    totalEntries: 0,
    processedBytes: 0,
    totalBytes: 0,
    currentEntryName: '',
    speed: 0,
    elapsed: 0,
    message: '',
  })
  
  const MAX_AVATAR_SOURCE_BYTES = 10 * 1024 * 1024
  const MAX_AVATAR_STORED_BYTES = 100 * 1024
  const AVATAR_MAX_SIDE = 512
  const AVATAR_EDITOR_SIZE = 320
  const MEDIA_AUDIO_EXTENSIONS = new Set(['mp3', 'wav', 'flac', 'aac', 'm4a', 'ogg', 'opus', 'wma'])
  const MEDIA_VIDEO_EXTENSIONS = new Set(['mp4', 'webm', 'mov', 'mkv', 'avi', 'm4v', 'wmv'])
  const CHUNK_UPLOAD_SIZE = 32 * 1024 * 1024
  const SIMPLE_UPLOAD_MAX_SIZE = CHUNK_UPLOAD_SIZE
  const CHUNK_UPLOAD_CONCURRENCY = 3
  const CHUNK_UPLOAD_RETRIES = 3
  const CHUNK_UPLOAD_CACHE_PREFIX = 'cloud_storage_chunk_upload:'
  const uploadControl = {
    paused: false,
    canceled: false,
    controllers: new Set(),
    resume: null,
    currentUploadId: '',
    currentCacheKey: '',
    pausedStartedAt: 0,
    pausedDuration: 0,
  }
  const extractControl = {
    polling: null,
    startingFileIds: new Set(),
  }
  const archiveControl = {
    polling: null,
    startingFileIds: new Set(),
    pendingQueue: [],
    downloadingJobIds: new Set(),
  }
  let avatarEditorImage = null
  const avatarEditorDrag = {
    pointerId: null,
    startX: 0,
    startY: 0,
    originX: 0,
    originY: 0,
  }
  
  const admin = reactive({
    users: [],
    selectedUser: null,
    files: [],
    parentId: null,
    folderStack: [{ id: null, name: '根目录' }],
    loginAudits: [],
    fileAudits: [],
    auditFrom: '',
    auditTo: '',
  })
  
  const shareRouteToken = getShareToken()
  const shareState = reactive({
    token: shareRouteToken,
    code: '',
    requiresCode: false,
    unlocked: false,
    root: null,
    parentId: null,
    files: [],
    downloadCount: 0,
    message: '',
  })
  
  const currentParentId = computed(() => foldersStack.value[foldersStack.value.length - 1]?.id ?? null)
  const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')
  const visibleFiles = computed(() => {
    const keyword = searchText.value.trim().toLowerCase()
    if (!keyword) return files.value
    return files.value.filter((item) => item.name.toLowerCase().includes(keyword))
  })
  const selectedFiles = computed(() => files.value.filter((item) => selectedIds.value.has(item.id)))
  const selectedFileItems = computed(() => selectedFiles.value.filter((item) => item.fileKind === 'FILE'))
  const selectedFolderItems = computed(() => selectedFiles.value.filter((item) => item.fileKind === 'FOLDER'))
  const allVisibleSelected = computed(() => visibleFiles.value.length > 0 && visibleFiles.value.every((item) => selectedIds.value.has(item.id)))
  const partialVisibleSelected = computed(() => selectedFiles.value.length > 0 && !allVisibleSelected.value)
  const avatarInitials = computed(() => (currentUser.value?.username || 'CS').slice(0, 2).toUpperCase())
  const canCurrentUserChangeAvatar = computed(() => isAdmin.value || systemSettings.allowAvatarChange)
  const canRegister = computed(() => systemSettings.allowUserLogin && systemSettings.allowUserRegistration)
  const fileNameSorter = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' })
  const movingFolderIds = computed(() => new Set((selectedFiles.value.length ? selectedFiles.value : [selected.value])
    .filter((item) => item?.fileKind === 'FOLDER')
    .map((item) => item.id)))
  const selectedTargetPath = computed(() => findFolderPath(dialog.folderTree, dialog.targetParentId))
  
  function getShareToken() {
    const match = window.location.pathname.match(/^\/share\/([^/]+)/)
    return match ? decodeURIComponent(match[1]) : ''
  }
  
  function notify(text, type = 'info') {
    notification.text = text
    notification.type = type
    notification.visible = true
    window.clearTimeout(notify.timer)
    notify.timer = window.setTimeout(() => {
      notification.visible = false
    }, type === 'error' ? 3600 : 2400)
  }
  
  function fail(error) {
    notify(error?.message || '操作失败', 'error')
  }

  function applySystemSettings(data) {
    if (!data) return
    systemSettings.siteName = data.siteName || 'Cloud Storage'
    systemSettings.allowUserLogin = Boolean(data.allowUserLogin)
    systemSettings.allowUserRegistration = Boolean(data.allowUserRegistration)
    systemSettings.allowAvatarChange = Boolean(data.allowAvatarChange)
    systemSettings.updatedAt = data.updatedAt || systemSettings.updatedAt || ''
    if (!canRegister.value && authMode.value === 'register') {
      authMode.value = 'login'
    }
  }

  function fillSettingsForm(data = systemSettings) {
    settingsForm.siteName = data.siteName || 'Cloud Storage'
    settingsForm.allowUserLogin = Boolean(data.allowUserLogin)
    settingsForm.allowUserRegistration = Boolean(data.allowUserRegistration)
    settingsForm.allowAvatarChange = Boolean(data.allowAvatarChange)
  }

  async function loadPublicSettings() {
    try {
      const data = await request('/public/settings')
      applySystemSettings(data)
      fillSettingsForm(data)
      document.title = data.siteName || 'Cloud Storage'
    } catch (error) {
      fail(error)
    }
  }
  
  function confirmAction({ title = '确认操作', message = '', confirmText = '确定', danger = false } = {}) {
    confirmDialog.open = true
    confirmDialog.title = title
    confirmDialog.message = message
    confirmDialog.confirmText = confirmText
    confirmDialog.danger = danger
    return new Promise((resolve) => {
      confirmDialog.resolve = resolve
    })
  }
  
  function closeConfirmDialog(result = false) {
    const resolve = confirmDialog.resolve
    confirmDialog.open = false
    confirmDialog.title = ''
    confirmDialog.message = ''
    confirmDialog.confirmText = '确定'
    confirmDialog.danger = false
    confirmDialog.resolve = null
    resolve?.(result)
  }
  
  async function loadCaptcha() {
    const data = await request('/auth/captcha')
    authForm.captchaId = data.captchaId
    authForm.captchaSvg = data.svg
    authForm.captchaCode = ''
  }
  
  async function login() {
    if (busy.value) return
    busy.value = true
    authBusyText.value = '登录中...'
    try {
      const data = await request('/auth/login', {
        method: 'POST',
        timeoutMs: 10000,
        body: {
          username: authForm.username,
          password: authForm.password,
          captchaId: authForm.captchaId,
          captchaCode: authForm.captchaCode,
        },
      })
      setToken(data.token)
      token.value = data.token
      currentUser.value = data.user
      fillProfile(data.user)
      void refreshAvatar(data.user)
      authBusyText.value = '加载文件...'
      void refreshAfterAuth()
    } catch (error) {
      fail(error)
      await loadCaptcha()
    } finally {
      busy.value = false
      authBusyText.value = ''
    }
  }
  
  async function register() {
    if (busy.value) return
    if (!canRegister.value) {
      notify('当前暂不允许新账号注册', 'error')
      authMode.value = 'login'
      return
    }
    busy.value = true
    authBusyText.value = '注册中...'
    try {
      const data = await request('/auth/register', {
        method: 'POST',
        body: {
          username: authForm.username,
          password: authForm.password,
          reserveEmail: authForm.reserveEmail,
          nickname: authForm.nickname,
        },
      })
      setToken(data.token)
      token.value = data.token
      currentUser.value = data.user
      fillProfile(data.user)
      void refreshAvatar(data.user)
      authBusyText.value = '加载文件...'
      void refreshAfterAuth()
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
      authBusyText.value = ''
    }
  }
  
  async function refreshAfterAuth() {
    try {
      await Promise.all([loadFiles(), loadLinks()])
    } catch (error) {
      fail(error)
    }
  }
  
  async function loadMe() {
    if (!token.value) {
      await loadCaptcha()
      return
    }
    try {
      const user = await request('/users/me')
      currentUser.value = user
      fillProfile(user)
      await Promise.all([refreshAvatar(user), loadFiles(), loadLinks()])
    } catch (error) {
      if (error.status === 401 || error.status === 403) {
        setToken('')
        token.value = ''
        currentUser.value = null
        await loadCaptcha()
        fail(error)
        return
      }
      fail(error)
      await loadCaptcha().catch(() => {})
    }
  }
  
  function logout() {
    setToken('')
    token.value = ''
    currentUser.value = null
    clearAvatarUrl()
    closeAvatarEditor()
    files.value = []
    selected.value = null
    selectedIds.value = new Set()
    foldersStack.value = [{ id: null, name: '全部文件' }]
    loadCaptcha()
  }
  
  function fillProfile(user) {
    profileForm.nickname = user?.nickname || ''
    profileForm.reserveEmail = user?.reserveEmail || ''
    profileForm.phone = user?.phone || ''
  }
  
  function clearAvatarUrl() {
    if (avatarUrl.value) {
      URL.revokeObjectURL(avatarUrl.value)
    }
    avatarUrl.value = ''
  }
  
  async function refreshAvatar(user = currentUser.value) {
    clearAvatarUrl()
    if (!user?.hasAvatar) return
    try {
      const response = await blobRequest('/users/me/avatar')
      avatarUrl.value = URL.createObjectURL(await response.blob())
    } catch (error) {
      if (error.status !== 404) {
        fail(error)
      }
    }
  }
  
  async function saveProfile() {
    busy.value = true
    try {
      currentUser.value = await request('/users/me', { method: 'PUT', body: profileForm })
      notify('资料已保存', 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  async function uploadAvatarSelected(event) {
    const [file] = Array.from(event.target.files || [])
    if (!file) return
    if (!canCurrentUserChangeAvatar.value) {
      notify('当前暂不允许普通用户修改头像', 'error')
      event.target.value = ''
      return
    }
    try {
      await openAvatarEditor(file)
    } catch (error) {
      fail(error)
    } finally {
      event.target.value = ''
    }
  }

  async function openAvatarEditor(file) {
    if (!file.type.startsWith('image/')) {
      throw new Error('请选择图片文件')
    }
    if (file.size > MAX_AVATAR_SOURCE_BYTES) {
      throw new Error('头像不能超过 10MB')
    }

    closeAvatarEditor()
    const { image, url } = await loadImage(file)
    avatarEditorImage = image
    avatarEditor.fileName = file.name || 'avatar'
    avatarEditor.previewUrl = url
    avatarEditor.naturalWidth = image.naturalWidth
    avatarEditor.naturalHeight = image.naturalHeight
    avatarEditor.minScale = AVATAR_EDITOR_SIZE / Math.min(image.naturalWidth, image.naturalHeight)
    avatarEditor.maxScale = Math.max(avatarEditor.minScale * 4, avatarEditor.minScale + 0.01)
    avatarEditor.scale = Math.min(avatarEditor.maxScale, Math.max(avatarEditor.minScale, AVATAR_EDITOR_SIZE / Math.max(image.naturalWidth, image.naturalHeight)))
    avatarEditor.offsetX = 0
    avatarEditor.offsetY = 0
    avatarEditor.dragging = false
    avatarEditor.open = true
    await nextTick()
    constrainAvatarEditor()
    drawAvatarEditor()
  }

  function closeAvatarEditor() {
    if (avatarEditor.previewUrl) {
      URL.revokeObjectURL(avatarEditor.previewUrl)
    }
    avatarEditor.open = false
    avatarEditor.fileName = ''
    avatarEditor.previewUrl = ''
    avatarEditor.naturalWidth = 0
    avatarEditor.naturalHeight = 0
    avatarEditor.scale = 1
    avatarEditor.minScale = 1
    avatarEditor.maxScale = 1
    avatarEditor.offsetX = 0
    avatarEditor.offsetY = 0
    avatarEditor.dragging = false
    avatarEditorImage = null
    avatarEditorDrag.pointerId = null
  }

  function drawAvatarEditor() {
    const canvas = avatarCropCanvas.value
    if (!canvas || !avatarEditorImage) return
    const context = canvas.getContext('2d')
    if (!context) return
    const displaySize = AVATAR_EDITOR_SIZE
    canvas.width = displaySize
    canvas.height = displaySize
    context.clearRect(0, 0, displaySize, displaySize)
    context.fillStyle = '#f2f5f8'
    context.fillRect(0, 0, displaySize, displaySize)
    const width = avatarEditor.naturalWidth * avatarEditor.scale
    const height = avatarEditor.naturalHeight * avatarEditor.scale
    const x = (displaySize - width) / 2 + avatarEditor.offsetX
    const y = (displaySize - height) / 2 + avatarEditor.offsetY
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
    context.drawImage(avatarEditorImage, x, y, width, height)
  }

  function constrainAvatarEditor() {
    const width = avatarEditor.naturalWidth * avatarEditor.scale
    const height = avatarEditor.naturalHeight * avatarEditor.scale
    const maxX = Math.max(0, (width - AVATAR_EDITOR_SIZE) / 2)
    const maxY = Math.max(0, (height - AVATAR_EDITOR_SIZE) / 2)
    avatarEditor.offsetX = clamp(avatarEditor.offsetX, -maxX, maxX)
    avatarEditor.offsetY = clamp(avatarEditor.offsetY, -maxY, maxY)
  }

  function updateAvatarScale(value) {
    const previousScale = avatarEditor.scale || avatarEditor.minScale
    const nextScale = clamp(Number(value) || avatarEditor.minScale, avatarEditor.minScale, avatarEditor.maxScale)
    if (Math.abs(nextScale - previousScale) < 0.0001) return
    avatarEditor.offsetX *= nextScale / previousScale
    avatarEditor.offsetY *= nextScale / previousScale
    avatarEditor.scale = nextScale
    constrainAvatarEditor()
    drawAvatarEditor()
  }

  function zoomAvatarEditor(step) {
    updateAvatarScale(avatarEditor.scale + step)
  }

  function startAvatarDrag(event) {
    if (!avatarEditor.open || avatarBusy.value) return
    avatarEditor.dragging = true
    avatarEditorDrag.pointerId = event.pointerId
    avatarEditorDrag.startX = event.clientX
    avatarEditorDrag.startY = event.clientY
    avatarEditorDrag.originX = avatarEditor.offsetX
    avatarEditorDrag.originY = avatarEditor.offsetY
    try {
      event.currentTarget?.setPointerCapture?.(event.pointerId)
    } catch {
      // Synthetic pointer events do not always have an active pointer capture target.
    }
  }

  function moveAvatarDrag(event) {
    if (!avatarEditor.dragging || avatarEditorDrag.pointerId !== event.pointerId) return
    avatarEditor.offsetX = avatarEditorDrag.originX + event.clientX - avatarEditorDrag.startX
    avatarEditor.offsetY = avatarEditorDrag.originY + event.clientY - avatarEditorDrag.startY
    constrainAvatarEditor()
    drawAvatarEditor()
  }

  function endAvatarDrag(event) {
    if (avatarEditorDrag.pointerId !== event.pointerId) return
    avatarEditor.dragging = false
    avatarEditorDrag.pointerId = null
    try {
      event.currentTarget?.releasePointerCapture?.(event.pointerId)
    } catch {
      // Pointer capture may already be released by the browser.
    }
  }

  function wheelAvatarEditor(event) {
    if (!avatarEditor.open || avatarBusy.value) return
    event.preventDefault()
    zoomAvatarEditor(event.deltaY < 0 ? 0.08 : -0.08)
  }

  async function submitAvatarEditor() {
    if (!avatarEditor.open || avatarBusy.value) return
    avatarBusy.value = true
    try {
      const compressed = await compressAvatarCanvas()
      const form = new FormData()
      form.append('avatar', compressed, compressed.name)
      currentUser.value = await uploadRequest('/users/me/avatar', form, { timeoutMs: 60000 })
      await refreshAvatar(currentUser.value)
      closeAvatarEditor()
      notify('头像已更新', 'success')
    } catch (error) {
      fail(error)
    } finally {
      avatarBusy.value = false
    }
  }

  async function clearAvatar() {
    if (!currentUser.value?.hasAvatar || avatarBusy.value) return
    const confirmed = await confirmAction({
      title: '恢复默认头像',
      message: '清除当前头像并使用默认头像？',
      confirmText: '清除头像',
      danger: false,
    })
    if (!confirmed) return
    avatarBusy.value = true
    try {
      currentUser.value = await request('/users/me/avatar', { method: 'DELETE' })
      clearAvatarUrl()
      notify('已恢复默认头像', 'success')
    } catch (error) {
      fail(error)
    } finally {
      avatarBusy.value = false
    }
  }
  
  async function compressAvatar(file) {
    if (!file.type.startsWith('image/')) {
      throw new Error('请选择图片文件')
    }
    if (file.size > MAX_AVATAR_SOURCE_BYTES) {
      throw new Error('头像不能超过 10MB')
    }
  
    const { image, url } = await loadImage(file)
    try {
      const canvas = document.createElement('canvas')
      const context = canvas.getContext('2d')
      if (!context) throw new Error('当前浏览器不支持头像压缩')
  
      let maxSide = Math.min(AVATAR_MAX_SIDE, Math.max(image.naturalWidth, image.naturalHeight))
      let quality = 0.86
      let blob = null
  
      for (let attempt = 0; attempt < 18; attempt += 1) {
        const scale = maxSide / Math.max(image.naturalWidth, image.naturalHeight)
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
        context.clearRect(0, 0, canvas.width, canvas.height)
        context.fillStyle = '#ffffff'
        context.fillRect(0, 0, canvas.width, canvas.height)
        context.drawImage(image, 0, 0, canvas.width, canvas.height)
        blob = await canvasToBlob(canvas, 'image/jpeg', quality)
        if (blob.size <= MAX_AVATAR_STORED_BYTES) break
        if (quality > 0.5) {
          quality = Math.max(0.5, quality - 0.08)
        } else {
          maxSide = Math.max(96, Math.round(maxSide * 0.82))
        }
      }
  
      if (!blob || blob.size > MAX_AVATAR_STORED_BYTES) {
        throw new Error('头像压缩后仍超过 100KB，请更换图片')
      }
      return new File([blob], `avatar-${Date.now()}.jpg`, { type: 'image/jpeg' })
    } finally {
      URL.revokeObjectURL(url)
    }
  }

  async function compressAvatarCanvas() {
    const sourceCanvas = avatarCropCanvas.value
    if (!sourceCanvas) {
      throw new Error('当前浏览器不支持头像裁剪')
    }

    const canvas = document.createElement('canvas')
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器不支持头像压缩')

    let side = AVATAR_MAX_SIDE
    let quality = 0.86
    let blob = null
    for (let attempt = 0; attempt < 18; attempt += 1) {
      canvas.width = side
      canvas.height = side
      context.clearRect(0, 0, side, side)
      context.fillStyle = '#ffffff'
      context.fillRect(0, 0, side, side)
      context.imageSmoothingEnabled = true
      context.imageSmoothingQuality = 'high'
      context.drawImage(sourceCanvas, 0, 0, sourceCanvas.width, sourceCanvas.height, 0, 0, side, side)
      blob = await canvasToBlob(canvas, 'image/jpeg', quality)
      if (blob.size <= MAX_AVATAR_STORED_BYTES) break
      if (quality > 0.5) {
        quality = Math.max(0.5, quality - 0.08)
      } else {
        side = Math.max(96, Math.round(side * 0.82))
      }
    }

    if (!blob || blob.size > MAX_AVATAR_STORED_BYTES) {
      throw new Error('头像压缩后仍超过 100KB，请更换图片')
    }
    return new File([blob], `avatar-${Date.now()}.jpg`, { type: 'image/jpeg' })
  }
  
  function loadImage(file) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file)
      const image = new Image()
      image.onload = () => resolve({ image, url })
      image.onerror = () => {
        URL.revokeObjectURL(url)
        reject(new Error('头像图片读取失败'))
      }
      image.src = url
    })
  }
  
  function canvasToBlob(canvas, type, quality) {
    return new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (blob) {
          resolve(blob)
        } else {
          reject(new Error('头像压缩失败'))
        }
      }, type, quality)
    })
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value))
  }
  
  async function loadFiles(parentId = currentParentId.value) {
    const query = parentId ? `?parentId=${parentId}` : ''
    files.value = await request(`/files${query}`)
    selected.value = null
    selectedIds.value = new Set()
  }
  
  function sortFileList(list) {
    return [...list].sort((left, right) => {
      if (left.fileKind !== right.fileKind) {
        return left.fileKind === 'FOLDER' ? -1 : 1
      }
      return fileNameSorter.compare(left.name, right.name)
    })
  }
  
  function setVisibleFiles(nextFiles) {
    files.value = sortFileList(nextFiles)
  }
  
  function mergeVisibleFiles(items) {
    const updates = Array.isArray(items) ? items : [items]
    const next = new Map(files.value.map((file) => [file.id, file]))
    updates.filter(Boolean).forEach((file) => next.set(file.id, file))
    setVisibleFiles([...next.values()])
    if (selected.value && next.has(selected.value.id)) {
      selected.value = next.get(selected.value.id)
    }
  }
  
  function appendVisibleFiles(items) {
    const additions = (Array.isArray(items) ? items : [items]).filter(Boolean)
    if (!additions.length) return
    const currentIds = new Set(files.value.map((file) => file.id))
    setVisibleFiles([...files.value, ...additions.filter((file) => !currentIds.has(file.id))])
  }
  
  function removeVisibleFiles(ids) {
    const idSet = new Set(Array.isArray(ids) ? ids : [ids])
    setVisibleFiles(files.value.filter((file) => !idSet.has(file.id)))
    if (selected.value && idSet.has(selected.value.id)) {
      selected.value = null
    }
    selectedIds.value = new Set([...selectedIds.value].filter((id) => !idSet.has(id)))
  }
  
  function isCurrentParent(parentId) {
    return (parentId ?? null) === currentParentId.value
  }
  
  function toggleFileSelection(file, checked) {
    const next = new Set(selectedIds.value)
    if (checked) {
      next.add(file.id)
    } else {
      next.delete(file.id)
    }
    selectedIds.value = next
  }
  
  function toggleAllVisible(checked) {
    const next = new Set(selectedIds.value)
    visibleFiles.value.forEach((file) => {
      if (checked) {
        next.add(file.id)
      } else {
        next.delete(file.id)
      }
    })
    selectedIds.value = next
  }
  
  function clearSelection() {
    selectedIds.value = new Set()
  }
  
  function findFolderPath(nodes, id) {
    if (id == null) return '根目录'
    const parts = findFolderPathParts(nodes, id)
    return parts.length ? ['根目录', ...parts].join(' / ') : '根目录'
  }
  
  function findFolderPathParts(nodes, id, parents = []) {
    for (const node of nodes || []) {
      const nextParents = [...parents, node.name]
      if (node.id === id) return nextParents
      const childParts = findFolderPathParts(node.children || [], id, nextParents)
      if (childParts.length) return childParts
    }
    return []
  }
  
  function folderContainsMovingFolder(node) {
    if (!node) return false
    if (movingFolderIds.value.has(node.id)) return true
    return (node.children || []).some(folderContainsMovingFolder)
  }
  
  function isFolderTargetDisabled(node) {
    return folderContainsMovingFolder(node)
  }
  
  function toggleFolderNode(node) {
    const next = new Set(dialog.expandedFolderIds)
    if (next.has(node.id)) {
      next.delete(node.id)
    } else {
      next.add(node.id)
    }
    dialog.expandedFolderIds = next
  }
  
  function selectFolderTarget(id) {
    dialog.targetParentId = id
  }
  
  async function loadFolderTree() {
    dialog.loadingFolders = true
    try {
      dialog.folderTree = await request('/files/folders/tree')
      const expanded = new Set()
      foldersStack.value.forEach((crumb) => {
        if (crumb.id != null) expanded.add(crumb.id)
      })
      dialog.expandedFolderIds = expanded
    } catch (error) {
      fail(error)
      dialog.folderTree = []
    } finally {
      dialog.loadingFolders = false
    }
  }
  
  async function enterFolder(file) {
    foldersStack.value.push({ id: file.id, name: file.name })
    await loadFiles(file.id)
  }
  
  async function goCrumb(index) {
    foldersStack.value = foldersStack.value.slice(0, index + 1)
    await loadFiles(currentParentId.value)
  }
  
  async function goParentFolder() {
    if (foldersStack.value.length <= 1) return
    foldersStack.value = foldersStack.value.slice(0, -1)
    await loadFiles(currentParentId.value)
  }
  
  async function createFolder() {
    dialog.open = true
    dialog.type = 'folder'
    dialog.title = '新建文件夹'
    dialog.value = ''
  }
  
  async function renameSelected() {
    if (!selected.value) return
    dialog.open = true
    dialog.type = 'rename'
    dialog.title = '重命名'
    dialog.value = selected.value.name
  }
  
  async function openMoveDialog(type) {
    if (!selected.value && !selectedFiles.value.length) return
    dialog.open = true
    dialog.type = type
    const count = selectedFiles.value.length
    if (count) {
      dialog.title = type === 'move' ? `移动 ${count} 个项目到` : `复制 ${count} 个项目到`
    } else {
      dialog.title = type === 'move' ? '移动到' : '复制到'
    }
    dialog.targetParentId = currentParentId.value
    await loadFolderTree()
  }
  
  async function submitDialog() {
    busy.value = true
    let shouldReloadFiles = false
    let shouldReloadLinks = false
    try {
      if (dialog.type === 'folder') {
        const folder = await request('/files/folders', {
          method: 'POST',
          body: { name: dialog.value, parentId: currentParentId.value },
        })
        appendVisibleFiles(folder)
      }
      if (dialog.type === 'rename') {
        const renamed = await request(`/files/${selected.value.id}/rename`, {
          method: 'PATCH',
          body: { name: dialog.value },
        })
        mergeVisibleFiles(renamed)
      }
      if (dialog.type === 'move') {
        const targets = selectedFiles.value.length ? selectedFiles.value : [selected.value]
        const moved = targets.length > 1
          ? await request('/files/batch/move', {
            method: 'PATCH',
            body: { fileIds: targets.map((file) => file.id), targetParentId: dialog.targetParentId },
          })
          : [await request(`/files/${targets[0].id}/move`, {
            method: 'PATCH',
            body: { targetParentId: dialog.targetParentId },
          })]
        notify(targets.length > 1 ? '已移动选中项目' : '已移动', 'success')
        if (isCurrentParent(dialog.targetParentId)) {
          mergeVisibleFiles(moved)
        } else {
          removeVisibleFiles(targets.map((file) => file.id))
        }
      }
      if (dialog.type === 'copy') {
        const targets = selectedFiles.value.length ? selectedFiles.value : [selected.value]
        const copied = targets.length > 1
          ? await request('/files/batch/copy', {
            method: 'POST',
            body: { fileIds: targets.map((file) => file.id), targetParentId: dialog.targetParentId },
          })
          : [await request(`/files/${targets[0].id}/copy`, {
            method: 'POST',
            body: { targetParentId: dialog.targetParentId },
          })]
        notify(targets.length > 1 ? '已复制选中项目' : '已复制', 'success')
        if (isCurrentParent(dialog.targetParentId)) {
          appendVisibleFiles(copied)
        }
      }
      if (dialog.type === 'share') {
        const extractionCode = dialog.value.trim()
        const share = await request(`/files/${selected.value.id}/shares`, {
          method: 'POST',
          body: { extractionCode: extractionCode || null },
        })
        const copied = await copyToClipboard(shareCopyText(share))
        links.value = { ...links.value, shares: [share, ...links.value.shares] }
        notify(
          copied
            ? (extractionCode ? '分享链接和提取码已复制' : '分享链接已复制')
            : '分享已创建，请手动复制',
          copied ? 'success' : 'info',
        )
        shouldReloadLinks = false
      }
      dialog.open = false
      await Promise.all([
        shouldReloadFiles ? loadFiles() : Promise.resolve(),
        shouldReloadLinks ? loadLinks() : Promise.resolve(),
      ])
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  async function uploadSelected(event) {
    await uploadFiles(Array.from(event.target.files || []))
    event.target.value = ''
  }
  
  async function uploadFiles(list) {
    if (!list.length) return
    busy.value = true
    const totalBytes = list.reduce((sum, file) => sum + file.size, 0)
    const startedAt = performance.now()
    uploadControl.paused = false
    uploadControl.canceled = false
    uploadControl.currentUploadId = ''
    uploadControl.currentCacheKey = ''
    uploadControl.pausedStartedAt = 0
    uploadControl.pausedDuration = 0
    uploadProgress.active = true
    uploadProgress.label = list.length > 1 ? `正在上传 ${list.length} 个文件` : `正在上传 ${list[0].name}`
    uploadProgress.status = 'uploading'
    uploadProgress.percent = 0
    uploadProgress.loaded = 0
    uploadProgress.total = totalBytes
    uploadProgress.speed = 0
    uploadProgress.eta = 0
    uploadProgress.totalChunks = 0
    uploadProgress.uploadedChunks = 0
    uploadProgress.paused = false
    uploadProgress.cancellable = true
    try {
      const uploaded = []
      let completedBeforeCurrent = 0
      for (let index = 0; index < list.length; index += 1) {
        const file = list[index]
        uploadProgress.label = list.length > 1 ? `正在上传 ${index + 1}/${list.length}：${file.name}` : `正在上传 ${file.name}`
        const uploadedFile = file.size <= SIMPLE_UPLOAD_MAX_SIZE
          ? await uploadFileDirect(file, completedBeforeCurrent, totalBytes, startedAt)
          : await uploadFileInChunks(file, completedBeforeCurrent, totalBytes, startedAt)
        uploaded.push(uploadedFile)
        completedBeforeCurrent += file.size
        updateUploadProgress(completedBeforeCurrent, totalBytes, startedAt)
      }
      uploadProgress.percent = 100
      uploadProgress.status = 'completed'
      uploadProgress.paused = false
      uploadProgress.cancellable = false
      appendVisibleFiles(uploaded)
      notify('上传完成', 'success')
    } catch (error) {
      if (uploadControl.canceled) {
        notify('上传已取消', 'info')
      } else {
        fail(error)
      }
    } finally {
      busy.value = false
      dragActive.value = false
      uploadControl.controllers.forEach((controller) => controller.abort())
      uploadControl.controllers.clear()
      window.setTimeout(() => {
        if (uploadProgress.status === 'paused') return
        uploadProgress.active = false
        uploadProgress.label = ''
        uploadProgress.status = ''
        uploadProgress.loaded = 0
        uploadProgress.total = 0
        uploadProgress.speed = 0
        uploadProgress.eta = 0
        uploadProgress.totalChunks = 0
        uploadProgress.uploadedChunks = 0
        uploadProgress.percent = 0
        uploadProgress.paused = false
        uploadProgress.cancellable = false
      }, uploadControl.canceled ? 0 : 900)
    }
  }

  async function uploadFileDirect(file, baseLoaded, totalBytes, startedAt) {
    uploadProgress.totalChunks = 0
    uploadProgress.uploadedChunks = 0
    const form = new FormData()
    form.append('files', file)
    if (currentParentId.value) {
      form.append('parentId', String(currentParentId.value))
    }
    const uploadedFiles = await uploadRequest('/files/upload', form, {
      timeoutMs: 0,
      onProgress: ({ loaded }) => {
        updateUploadProgress(baseLoaded + Math.min(file.size, loaded), totalBytes, startedAt)
      },
    })
    updateUploadProgress(baseLoaded + file.size, totalBytes, startedAt)
    return uploadedFiles[0]
  }

  async function uploadFileInChunks(file, baseLoaded, totalBytes, startedAt) {
    const cacheKey = uploadCacheKey(file)
    const session = await loadOrCreateUploadSession(file, cacheKey)
    uploadControl.currentUploadId = session.uploadId
    uploadControl.currentCacheKey = cacheKey
    saveUploadCache(cacheKey, session.uploadId)
    uploadProgress.totalChunks = session.totalChunks
    const uploadedChunks = new Set((session.chunks || []).map((chunk) => chunk.chunkIndex))
    uploadProgress.uploadedChunks = uploadedChunks.size
    const uploadedBytes = new Map((session.chunks || []).map((chunk) => [chunk.chunkIndex, chunk.sizeBytes]))
    let currentFileUploaded = Array.from(uploadedBytes.values()).reduce((sum, size) => sum + size, 0)
    updateUploadProgress(baseLoaded + currentFileUploaded, totalBytes, startedAt)

    let nextChunkIndex = 0
    const inflightLoaded = new Map()
    function claimNextChunkIndex() {
      while (nextChunkIndex < session.totalChunks) {
        const chunkIndex = nextChunkIndex
        nextChunkIndex += 1
        if (!uploadedChunks.has(chunkIndex)) {
          return chunkIndex
        }
      }
      return null
    }

    async function worker() {
      while (true) {
        await waitWhileUploadPaused()
        if (uploadControl.canceled) {
          throw new Error('上传已取消')
        }
        const chunkIndex = claimNextChunkIndex()
        if (chunkIndex === null) break
        const start = chunkIndex * session.chunkSize
        const end = Math.min(file.size, start + session.chunkSize)
        const chunk = file.slice(start, end)
        await uploadChunkWithRetry(session.uploadId, chunkIndex, chunk, file.name, ({ loaded }) => {
          inflightLoaded.set(chunkIndex, Math.min(chunk.size, loaded))
          updateUploadProgress(baseLoaded + currentFileUploaded + inflightTotal(inflightLoaded), totalBytes, startedAt)
        })
        inflightLoaded.delete(chunkIndex)
        uploadedChunks.add(chunkIndex)
        uploadedBytes.set(chunkIndex, chunk.size)
        currentFileUploaded += chunk.size
        uploadProgress.uploadedChunks = uploadedChunks.size
        updateUploadProgress(baseLoaded + currentFileUploaded, totalBytes, startedAt)
      }
    }

    const workers = Array.from({ length: Math.min(CHUNK_UPLOAD_CONCURRENCY, session.totalChunks) }, () => worker())
    await Promise.all(workers)
    uploadProgress.status = 'completing'
    const completeParams = new URLSearchParams()
    const completed = await request(`/files/uploads/${session.uploadId}/complete?${completeParams}`, { method: 'POST', timeoutMs: 0 })
    clearUploadCache(cacheKey)
    uploadControl.currentUploadId = ''
    uploadControl.currentCacheKey = ''
    return completed
  }

  function inflightTotal(items) {
    return Array.from(items.values()).reduce((sum, loaded) => sum + loaded, 0)
  }

  async function loadOrCreateUploadSession(file, cacheKey) {
    const cachedUploadId = readUploadCache(cacheKey)
    if (cachedUploadId) {
      try {
        const session = await request(`/files/uploads/${cachedUploadId}`, { timeoutMs: 30000 })
        if (session.status === 'UPLOADING' && session.sizeBytes === file.size && session.fileName === file.name) {
          return session
        }
      } catch {
        clearUploadCache(cacheKey)
      }
    }
    const initParams = new URLSearchParams()
    initParams.set('fileName', file.name)
    initParams.set('contentType', file.type || 'application/octet-stream')
    initParams.set('sizeBytes', String(file.size))
    initParams.set('chunkSize', String(CHUNK_UPLOAD_SIZE))
    if (currentParentId.value) initParams.set('parentId', String(currentParentId.value))
    return request(`/files/uploads/init?${initParams}`, { method: 'POST', timeoutMs: 30000 })
  }

  function uploadCacheKey(file) {
    return [
      CHUNK_UPLOAD_CACHE_PREFIX,
      currentUser.value?.id || 'guest',
      currentParentId.value || 'root',
      file.name,
      file.size,
      file.lastModified || 0,
    ].join(':')
  }

  function readUploadCache(key) {
    try {
      return localStorage.getItem(key)
    } catch {
      return ''
    }
  }

  function saveUploadCache(key, uploadId) {
    try {
      localStorage.setItem(key, uploadId)
    } catch {
      // Upload resume cache is a convenience; uploading still works without it.
    }
  }

  function clearUploadCache(key) {
    try {
      localStorage.removeItem(key)
    } catch {
      // Ignore storage cleanup failures.
    }
  }

  async function uploadChunkWithRetry(uploadId, chunkIndex, chunk, fileName, onProgress) {
    let lastError = null
    for (let attempt = 1; attempt <= CHUNK_UPLOAD_RETRIES; attempt += 1) {
      await waitWhileUploadPaused()
      if (uploadControl.canceled) {
        throw new Error('上传已取消')
      }
      const controller = new AbortController()
      uploadControl.controllers.add(controller)
      try {
        await uploadChunkRequest(`/files/uploads/${uploadId}/chunks/${chunkIndex}`, chunk, {
          filename: `${fileName}.part${chunkIndex}`,
          signal: controller.signal,
          timeoutMs: 0,
          onProgress,
        })
        uploadControl.controllers.delete(controller)
        return
      } catch (error) {
        uploadControl.controllers.delete(controller)
        if (error.status === 409) {
          return
        }
        lastError = error
        if (uploadControl.canceled) throw error
        if (attempt < CHUNK_UPLOAD_RETRIES) {
          await delay(600 * attempt)
        }
      }
    }
    throw lastError || new Error('分片上传失败')
  }

  function updateUploadProgress(loaded, total, startedAt) {
    uploadProgress.loaded = Math.min(total, loaded)
    uploadProgress.total = total
    uploadProgress.percent = total ? Math.min(100, Math.round((uploadProgress.loaded / total) * 100)) : 0
    const activePausedMs = uploadControl.pausedStartedAt ? performance.now() - uploadControl.pausedStartedAt : 0
    const elapsedSeconds = Math.max((performance.now() - startedAt - uploadControl.pausedDuration - activePausedMs) / 1000, 0.1)
    uploadProgress.speed = uploadProgress.loaded / elapsedSeconds
    uploadProgress.eta = uploadProgress.speed > 0 ? Math.max(0, (total - uploadProgress.loaded) / uploadProgress.speed) : 0
  }

  function pauseUpload() {
    if (!uploadProgress.active || uploadProgress.status !== 'uploading') return
    uploadControl.paused = true
    uploadControl.pausedStartedAt = performance.now()
    uploadProgress.paused = true
    uploadProgress.status = 'paused'
  }

  function resumeUpload() {
    if (!uploadProgress.active || !uploadProgress.paused) return
    if (uploadControl.pausedStartedAt) {
      uploadControl.pausedDuration += performance.now() - uploadControl.pausedStartedAt
      uploadControl.pausedStartedAt = 0
    }
    uploadControl.paused = false
    uploadProgress.paused = false
    uploadProgress.status = 'uploading'
    uploadControl.resume?.()
    uploadControl.resume = null
  }

  async function cancelUpload() {
    if (!uploadProgress.active) return
    uploadControl.canceled = true
    uploadControl.paused = false
    uploadControl.pausedStartedAt = 0
    uploadProgress.status = 'canceled'
    uploadProgress.paused = false
    uploadControl.controllers.forEach((controller) => controller.abort())
    uploadControl.controllers.clear()
    uploadControl.resume?.()
    uploadControl.resume = null
    if (uploadControl.currentUploadId) {
      await request(`/files/uploads/${uploadControl.currentUploadId}`, { method: 'DELETE' }).catch(() => {})
    }
    if (uploadControl.currentCacheKey) {
      clearUploadCache(uploadControl.currentCacheKey)
    }
  }

  function waitWhileUploadPaused() {
    if (!uploadControl.paused) return Promise.resolve()
    return new Promise((resolve) => {
      uploadControl.resume = resolve
    })
  }

  function delay(ms) {
    return new Promise((resolve) => window.setTimeout(resolve, ms))
  }
  
  function onDrop(event) {
    event.preventDefault()
    uploadFiles(Array.from(event.dataTransfer?.files || []))
  }
  
  async function downloadFile(file) {
    try {
      if (file.fileKind === 'FOLDER') {
        await startArchiveDownload(file)
      } else {
        await downloadBlob(`/files/${file.id}/download`, downloadName(file))
        file.downloadCount += 1
      }
    } catch (error) {
      fail(error)
    }
  }
  
  async function openPreview(file) {
    if (file.fileKind === 'FOLDER') {
      await enterFolder(file)
      return
    }
    try {
      closePreview()
      const response = await blobRequest(`/files/${file.id}/preview`)
      await showPreviewBlob(file, await response.blob())
    } catch (error) {
      fail(error)
    }
  }

  async function showPreviewBlob(file, blob) {
    const extension = (file.extension || '').toLowerCase()
    preview.open = true
    preview.name = file.name
    preview.type = file.contentType || blob.type || ''
    preview.url = URL.createObjectURL(blob)
    if (preview.type.startsWith('text/') || ['json', 'md', 'log', 'csv'].includes(extension)) {
      preview.text = await blob.text()
    } else {
      preview.text = ''
    }
  }

  function openFileInfo(file = selected.value) {
    if (!file) return
    clearFileInfoMediaUrl()
    fileInfoDialog.file = file
    fileInfoDialog.open = true
    fileInfoDialog.durationLoading = false
    fileInfoDialog.durationText = ''
    fileInfoDialog.durationError = ''
    if (isMediaFile(file)) {
      void loadFileInfoDuration(file)
    }
  }

  async function loadFileInfoDuration(file) {
    fileInfoDialog.durationLoading = true
    fileInfoDialog.durationError = ''
    try {
      const response = await blobRequest(`/files/${file.id}/preview`)
      const blob = await response.blob()
      if (fileInfoDialog.file?.id !== file.id || !fileInfoDialog.open) return
      const url = URL.createObjectURL(blob)
      fileInfoDialog.mediaUrl = url
      const duration = await readMediaDuration(url, file.contentType || blob.type)
      if (fileInfoDialog.file?.id !== file.id || !fileInfoDialog.open) return
      fileInfoDialog.durationText = formatDuration(duration) || '未知'
    } catch (error) {
      if (fileInfoDialog.file?.id === file.id && fileInfoDialog.open) {
        fileInfoDialog.durationError = error?.message || '时长读取失败'
      }
    } finally {
      if (fileInfoDialog.file?.id === file.id && fileInfoDialog.open) {
        fileInfoDialog.durationLoading = false
      }
    }
  }

  function readMediaDuration(url, contentType = '') {
    return new Promise((resolve, reject) => {
      const element = document.createElement(contentType.startsWith('audio/') ? 'audio' : 'video')
      const cleanup = () => {
        element.removeAttribute('src')
        element.load()
      }
      element.preload = 'metadata'
      element.onloadedmetadata = () => {
        const duration = element.duration
        cleanup()
        if (Number.isFinite(duration)) {
          resolve(duration)
        } else {
          reject(new Error('无法读取媒体时长'))
        }
      }
      element.onerror = () => {
        cleanup()
        reject(new Error('无法读取媒体时长'))
      }
      element.src = url
    })
  }
  
  function closePreview() {
    if (preview.url) {
      URL.revokeObjectURL(preview.url)
    }
    preview.open = false
    preview.name = ''
    preview.url = ''
    preview.type = ''
    preview.text = ''
  }

  function closeFileInfo() {
    clearFileInfoMediaUrl()
    fileInfoDialog.open = false
    fileInfoDialog.file = null
    fileInfoDialog.durationLoading = false
    fileInfoDialog.durationText = ''
    fileInfoDialog.durationError = ''
  }

  function clearFileInfoMediaUrl() {
    if (fileInfoDialog.mediaUrl) {
      URL.revokeObjectURL(fileInfoDialog.mediaUrl)
    }
    fileInfoDialog.mediaUrl = ''
  }
  
  async function deleteSelected() {
    if (!selected.value) return
    const confirmed = await confirmAction({
      title: '删除文件',
      message: `删除 ${selected.value.name}？`,
      confirmText: '删除',
      danger: true,
    })
    if (!confirmed) return
    busy.value = true
    try {
      await request(`/files/${selected.value.id}`, { method: 'DELETE' })
      removeVisibleFiles(selected.value.id)
      notify('已删除', 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  async function downloadSelectedFiles() {
    if (!selectedFiles.value.length) return
    busy.value = true
    try {
      for (const file of selectedFiles.value) {
        if (file.fileKind === 'FOLDER') {
          await startArchiveDownload(file, { queueRemaining: true })
        } else {
          await downloadBlob(`/files/${file.id}/download`, downloadName(file))
        }
      }
      await loadFiles()
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  async function deleteSelectedFiles() {
    if (!selectedFiles.value.length) return
    const confirmed = await confirmAction({
      title: '批量删除',
      message: `删除选中的 ${selectedFiles.value.length} 个项目？`,
      confirmText: '删除',
      danger: true,
    })
    if (!confirmed) return
    busy.value = true
    try {
      await request('/files/batch', {
        method: 'DELETE',
        body: { fileIds: selectedFiles.value.map((file) => file.id) },
      })
      removeVisibleFiles(selectedFiles.value.map((file) => file.id))
      notify('已删除选中项目', 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  async function createDirectLink() {
    if (!selected.value || selected.value.fileKind !== 'FILE') return
    busy.value = true
    try {
      const link = await request(`/files/${selected.value.id}/direct-links`, {
        method: 'POST',
        body: {},
      })
      await navigator.clipboard?.writeText(link.url).catch(() => {})
      links.value = { ...links.value, direct: [link, ...links.value.direct] }
      notify('直链已创建', 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  function openShareDialog() {
    if (!selected.value) return
    dialog.open = true
    dialog.type = 'share'
    dialog.title = '创建分享'
    dialog.value = ''
  }
  
  async function extractSelected() {
    if (!selected.value) return
    const file = selected.value
    if (extractProgress.active || extractControl.startingFileIds.has(file.id)) {
      notify('解压任务正在进行', 'info')
      return
    }
    extractControl.startingFileIds.add(file.id)
    try {
      const job = await request(`/files/${file.id}/extract`, { method: 'POST', timeoutMs: 30000 })
      applyExtractJob(job)
      startExtractPolling(job.jobId)
    } catch (error) {
      fail(error)
    } finally {
      extractControl.startingFileIds.delete(file.id)
    }
  }

  function applyExtractJob(job) {
    if (!job) return
    extractProgress.active = ['PENDING', 'SCANNING', 'RUNNING'].includes(job.status)
    extractProgress.fileId = job.fileId
    extractProgress.jobId = job.jobId
    extractProgress.label = job.fileName ? `正在解压 ${job.fileName}` : '正在准备解压'
    extractProgress.status = job.status
    extractProgress.percent = job.percent || 0
    extractProgress.processedEntries = job.processedEntries || 0
    extractProgress.totalEntries = job.totalEntries || 0
    extractProgress.processedBytes = job.processedBytes || 0
    extractProgress.totalBytes = job.totalBytes || 0
    extractProgress.currentEntryName = job.currentEntryName || ''
    extractProgress.speed = job.speedBytesPerSecond || 0
    extractProgress.elapsed = Math.round((job.elapsedMillis || 0) / 1000)
    extractProgress.message = job.message || ''
  }

  function clearExtractProgress() {
    window.clearTimeout(extractControl.polling)
    extractControl.polling = null
    extractProgress.active = false
    extractProgress.fileId = null
    extractProgress.jobId = ''
    extractProgress.label = ''
    extractProgress.status = ''
    extractProgress.percent = 0
    extractProgress.processedEntries = 0
    extractProgress.totalEntries = 0
    extractProgress.processedBytes = 0
    extractProgress.totalBytes = 0
    extractProgress.currentEntryName = ''
    extractProgress.speed = 0
    extractProgress.elapsed = 0
    extractProgress.message = ''
  }

  async function pollExtractJob(jobId) {
    try {
      const job = await request(`/files/extract-jobs/${jobId}`, { timeoutMs: 10000 })
      applyExtractJob(job)
      if (job.status === 'COMPLETED') {
        notify('解压完成', 'success')
        await loadFiles()
        extractControl.polling = window.setTimeout(clearExtractProgress, 1200)
        return
      }
      if (job.status === 'FAILED') {
        notify(job.message || '解压失败', 'error')
        extractControl.polling = window.setTimeout(clearExtractProgress, 2400)
        return
      }
      startExtractPolling(jobId)
    } catch (error) {
      fail(error)
      startExtractPolling(jobId, 3000)
    }
  }

  function startExtractPolling(jobId, delay = 1000) {
    window.clearTimeout(extractControl.polling)
    extractControl.polling = window.setTimeout(() => pollExtractJob(jobId), delay)
  }

  async function startArchiveDownload(file, options = {}) {
    if (!file || file.fileKind !== 'FOLDER') return
    if (archiveProgress.active || archiveControl.startingFileIds.has(file.id)) {
      if (options.queueRemaining) {
        archiveControl.pendingQueue.push(file)
        return
      }
      notify('压缩任务正在进行', 'info')
      return
    }
    archiveControl.startingFileIds.add(file.id)
    try {
      const job = await request(`/files/${file.id}/archive`, { method: 'POST', timeoutMs: 30000 })
      applyArchiveJob(job)
      startArchivePolling(job.jobId)
    } catch (error) {
      fail(error)
    } finally {
      archiveControl.startingFileIds.delete(file.id)
    }
  }

  function applyArchiveJob(job) {
    if (!job) return
    archiveProgress.active = ['PENDING', 'SCANNING', 'RUNNING'].includes(job.status)
    archiveProgress.fileId = job.fileId
    archiveProgress.jobId = job.jobId
    archiveProgress.label = job.fileName ? `正在压缩 ${job.fileName}` : '正在准备压缩'
    archiveProgress.status = job.status
    archiveProgress.percent = job.percent || 0
    archiveProgress.processedEntries = job.processedEntries || 0
    archiveProgress.totalEntries = job.totalEntries || 0
    archiveProgress.processedBytes = job.processedBytes || 0
    archiveProgress.totalBytes = job.totalBytes || 0
    archiveProgress.currentEntryName = job.currentEntryName || ''
    archiveProgress.speed = job.speedBytesPerSecond || 0
    archiveProgress.elapsed = Math.round((job.elapsedMillis || 0) / 1000)
    archiveProgress.message = job.message || ''
  }

  function clearArchiveProgress() {
    window.clearTimeout(archiveControl.polling)
    archiveControl.polling = null
    archiveProgress.active = false
    archiveProgress.fileId = null
    archiveProgress.jobId = ''
    archiveProgress.label = ''
    archiveProgress.status = ''
    archiveProgress.percent = 0
    archiveProgress.processedEntries = 0
    archiveProgress.totalEntries = 0
    archiveProgress.processedBytes = 0
    archiveProgress.totalBytes = 0
    archiveProgress.currentEntryName = ''
    archiveProgress.speed = 0
    archiveProgress.elapsed = 0
    archiveProgress.message = ''
  }

  async function pollArchiveJob(jobId) {
    try {
      const job = await request(`/files/archive-jobs/${jobId}`, { timeoutMs: 10000 })
      applyArchiveJob(job)
      if (job.status === 'COMPLETED') {
        await downloadCompletedArchive(job)
        notify('压缩下载已准备完成', 'success')
        await loadFiles()
        archiveControl.polling = window.setTimeout(() => {
          clearArchiveProgress()
          startNextQueuedArchiveDownload()
        }, 1200)
        return
      }
      if (job.status === 'FAILED') {
        notify(job.message || '压缩失败', 'error')
        archiveControl.polling = window.setTimeout(() => {
          clearArchiveProgress()
          startNextQueuedArchiveDownload()
        }, 2400)
        return
      }
      startArchivePolling(jobId)
    } catch (error) {
      fail(error)
      startArchivePolling(jobId, 3000)
    }
  }

  function startArchivePolling(jobId, delay = 1000) {
    window.clearTimeout(archiveControl.polling)
    archiveControl.polling = window.setTimeout(() => pollArchiveJob(jobId), delay)
  }

  async function downloadCompletedArchive(job) {
    if (!job?.downloadPath || archiveControl.downloadingJobIds.has(job.jobId)) return
    archiveControl.downloadingJobIds.add(job.jobId)
    try {
      await downloadBlob(job.downloadPath, job.downloadName || downloadName({ fileKind: 'FOLDER', name: job.fileName || 'folder' }))
    } finally {
      archiveControl.downloadingJobIds.delete(job.jobId)
    }
  }

  function startNextQueuedArchiveDownload() {
    const next = archiveControl.pendingQueue.shift()
    if (next) {
      void startArchiveDownload(next, { queueRemaining: true })
    }
  }
  
  async function loadLinks() {
    if (!token.value) return
    const [direct, shares] = await Promise.all([
      request('/files/direct-links'),
      request('/files/shares'),
    ])
    links.value = { direct, shares }
  }
  
  async function deleteDirectLink(item) {
    const confirmed = await confirmAction({
      title: '删除直链',
      message: '删除后该直链将无法继续访问。',
      confirmText: '删除',
      danger: true,
    })
    if (!confirmed) return
    try {
      await request(`/files/direct-links/${item.id}`, { method: 'DELETE' })
      links.value = { ...links.value, direct: links.value.direct.filter((link) => link.id !== item.id) }
      notify('直链已删除', 'success')
    } catch (error) {
      fail(error)
    }
  }
  
  async function deleteShareLink(item) {
    const confirmed = await confirmAction({
      title: '删除分享',
      message: '删除后该分享链接将无法继续访问。',
      confirmText: '删除',
      danger: true,
    })
    if (!confirmed) return
    try {
      await request(`/files/shares/${item.id}`, { method: 'DELETE' })
      links.value = { ...links.value, shares: links.value.shares.filter((link) => link.id !== item.id) }
      notify('分享已删除', 'success')
    } catch (error) {
      fail(error)
    }
  }
  
  function iconFor(file) {
    if (file.fileKind === 'FOLDER') return Folder
    if ((file.contentType || '').startsWith('image/')) return FileImage
    if ((file.contentType || '').startsWith('audio/')) return FileAudio
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(file.extension)) return FileArchive
    return FileText
  }
  
  function formatSize(bytes) {
    if (!bytes) return '0 B'
    const units = ['B', 'KB', 'MB', 'GB', 'TB']
    let value = bytes
    let index = 0
    while (value >= 1024 && index < units.length - 1) {
      value /= 1024
      index += 1
    }
    return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`
  }

  function formatDuration(seconds) {
    if (!Number.isFinite(seconds) || seconds < 0) return ''
    const totalSeconds = Math.round(seconds)
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const restSeconds = totalSeconds % 60
    const minuteText = hours > 0 ? String(minutes).padStart(2, '0') : String(minutes)
    const secondText = String(restSeconds).padStart(2, '0')
    return hours > 0 ? `${hours}:${minuteText}:${secondText}` : `${minuteText}:${secondText}`
  }

  function isMediaFile(file) {
    if (!file || file.fileKind !== 'FILE') return false
    const contentType = file.contentType || ''
    const extension = (file.extension || '').toLowerCase()
    return contentType.startsWith('audio/')
      || contentType.startsWith('video/')
      || MEDIA_AUDIO_EXTENSIONS.has(extension)
      || MEDIA_VIDEO_EXTENSIONS.has(extension)
  }

  function isZipFile(file) {
    if (!file || file.fileKind !== 'FILE') return false
    const contentType = (file.contentType || '').toLowerCase()
    const extension = (file.extension || '').toLowerCase()
    return extension === 'zip'
      || file.name?.toLowerCase().endsWith('.zip')
      || contentType === 'application/zip'
      || contentType === 'application/x-zip-compressed'
  }
  
  function formatSpeed(bytesPerSecond) {
    return `${formatSize(bytesPerSecond)}/s`
  }

  function formatEta(seconds) {
    if (!seconds || !Number.isFinite(seconds)) return '计算中'
    return formatDuration(seconds)
  }
  
  function formatDate(value) {
    if (!value) return ''
    return new Intl.DateTimeFormat('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(value))
  }
  
  function formatBanUntil(value) {
    return value ? formatDate(value) : '永久'
  }
  
  function toLocalDateTimeInput(value) {
    if (!value) return ''
    const date = new Date(value)
    const offsetMs = date.getTimezoneOffset() * 60 * 1000
    return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
  }
  
  function fromLocalDateTimeInput(value) {
    return value ? new Date(value).toISOString() : null
  }
  
  function startOfLocalDate(value) {
    return value ? new Date(`${value}T00:00:00`).toISOString() : null
  }
  
  function endOfLocalDate(value) {
    return value ? new Date(`${value}T23:59:59.999`).toISOString() : null
  }
  
  async function copyText(text) {
    const copied = await copyToClipboard(text)
    notify(copied ? '已复制' : '复制失败，请手动复制', copied ? 'success' : 'error')
  }
  
  async function copyToClipboard(text) {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text)
      } else {
        const textarea = document.createElement('textarea')
        textarea.value = text
        textarea.setAttribute('readonly', '')
        textarea.style.position = 'fixed'
        textarea.style.left = '-9999px'
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        textarea.remove()
      }
      return true
    } catch {
      return false
    }
  }
  
  function shareCopyText(item) {
    const lines = [`分享链接：${item.url}`]
    if (item.extractionCode) {
      lines.push(`提取码：${item.extractionCode}`)
    }
    return lines.join('\n')
  }
  
  function shareMeta(item) {
    const parts = [`${item.downloadCount} 次下载`]
    if (item.extractionCode) {
      parts.push(`提取码 ${item.extractionCode}`)
    } else if (item.requiresCode) {
      parts.push('有提取码')
    }
    parts.push(item.expiresAt ? `到期 ${formatDate(item.expiresAt)}` : '永久有效')
    return parts.join(' · ')
  }
  
  async function loadAdminUsers(mode = activeTab.value) {
    if (!isAdmin.value) return
    admin.users = await request('/admin/users')
    if (admin.selectedUser) {
      admin.selectedUser = admin.users.find((user) => user.id === admin.selectedUser.id) || null
    }
    if (!admin.selectedUser && admin.users.length) {
      await selectAdminUser(admin.users[0], mode)
    } else if (admin.selectedUser) {
      await loadSelectedAdminContext(mode)
    } else {
      admin.files = []
      admin.loginAudits = []
      admin.fileAudits = []
    }
  }

  async function loadAdminSettings() {
    if (!isAdmin.value) return
    settingsLoading.value = true
    try {
      const data = await request('/admin/settings')
      applySystemSettings(data)
      fillSettingsForm(data)
      document.title = data.siteName || 'Cloud Storage'
    } catch (error) {
      fail(error)
    } finally {
      settingsLoading.value = false
    }
  }

  async function saveSystemSettings() {
    if (!isAdmin.value || busy.value) return
    busy.value = true
    try {
      const data = await request('/admin/settings', {
        method: 'PUT',
        body: {
          siteName: settingsForm.siteName,
          allowUserLogin: settingsForm.allowUserLogin,
          allowUserRegistration: settingsForm.allowUserRegistration,
          allowAvatarChange: settingsForm.allowAvatarChange,
        },
      })
      applySystemSettings(data)
      fillSettingsForm(data)
      document.title = data.siteName || 'Cloud Storage'
      if (!systemSettings.allowUserRegistration && authMode.value === 'register') {
        authMode.value = 'login'
      }
      notify('系统设置已保存', 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }

  async function cleanupExpiredStorage() {
    if (!isAdmin.value || storageCleanup.running) return
    const confirmed = await confirmAction({
      title: '清理过期临时文件',
      message: '将删除已过期上传任务的分片目录，以及超过 1 小时的上传合并临时文件。正式文件不会被删除。',
      confirmText: '开始清理',
      danger: false,
    })
    if (!confirmed) return
    storageCleanup.running = true
    try {
      const result = await request('/admin/storage/cleanup-expired', { method: 'POST', timeoutMs: 0 })
      storageCleanup.result = result
      notify(`已释放 ${formatSize(result.releasedBytes || 0)}`, 'success')
    } catch (error) {
      fail(error)
    } finally {
      storageCleanup.running = false
    }
  }

  async function cleanupOrphanStorageObjects() {
    if (!isAdmin.value || storageCleanup.orphanRunning) return
    const confirmed = await confirmAction({
      title: '清理孤立本地文件',
      message: '将扫描本地 storage/objects 目录，删除数据库 storage_objects 表中没有登记的对象文件。已登记文件不会被删除。',
      confirmText: '开始清理',
      danger: true,
    })
    if (!confirmed) return
    storageCleanup.orphanRunning = true
    try {
      const result = await request('/admin/storage/cleanup-orphans', { method: 'POST', timeoutMs: 0 })
      storageCleanup.orphanResult = result
      notify(`已释放 ${formatSize(result.releasedBytes || 0)}`, 'success')
    } catch (error) {
      fail(error)
    } finally {
      storageCleanup.orphanRunning = false
    }
  }
  
  async function selectAdminUser(user, mode = activeTab.value) {
    admin.selectedUser = user
    admin.parentId = null
    admin.folderStack = [{ id: null, name: '根目录' }]
    await loadSelectedAdminContext(mode, null)
  }
  
  async function loadSelectedAdminContext(mode = activeTab.value, parentId = admin.parentId) {
    if (!admin.selectedUser) return
    if (mode === 'adminFiles') {
      await loadAdminFiles(parentId)
      return
    }
    if (mode === 'admin') {
      await loadAdminAudits()
      return
    }
    await loadAdminDetail(parentId)
  }
  
  async function loadAdminDetail(parentId = admin.parentId) {
    await Promise.all([loadAdminFiles(parentId), loadAdminAudits()])
  }
  
  async function loadAdminFiles(parentId = null) {
    if (!admin.selectedUser) return
    admin.parentId = parentId
    const query = parentId ? `?parentId=${parentId}` : ''
    admin.files = await request(`/admin/users/${admin.selectedUser.id}/files${query}`)
  }
  
  async function openAdminFolder(file) {
    if (file.fileKind !== 'FOLDER') return
    admin.folderStack.push({ id: file.id, name: file.name })
    await loadAdminFiles(file.id)
  }
  
  async function loadAdminFolderAt(index) {
    admin.folderStack = admin.folderStack.slice(0, index + 1)
    await loadAdminFiles(admin.folderStack[admin.folderStack.length - 1]?.id ?? null)
  }
  
  async function goAdminParentFolder() {
    if (admin.folderStack.length <= 1) return
    await loadAdminFolderAt(admin.folderStack.length - 2)
  }
  
  async function loadAdminRootFolder() {
    admin.folderStack = [{ id: null, name: '根目录' }]
    await loadAdminFiles(null)
  }
  
  async function loadAdminAudits() {
    if (!admin.selectedUser) return
    const query = auditQuery()
    const [loginAudits, fileAudits] = await Promise.all([
      request(`/admin/users/${admin.selectedUser.id}/login-audits${query}`),
      request(`/admin/users/${admin.selectedUser.id}/file-operation-audits${query}`),
    ])
    admin.loginAudits = loginAudits
    admin.fileAudits = fileAudits
  }
  
  function auditQuery() {
    const params = new URLSearchParams()
    const from = startOfLocalDate(admin.auditFrom)
    const to = endOfLocalDate(admin.auditTo)
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    const text = params.toString()
    return text ? `?${text}` : ''
  }
  
  async function clearAuditRange() {
    admin.auditFrom = ''
    admin.auditTo = ''
    await loadAdminAudits()
  }

  async function clearAllAudits() {
    const confirmed = await confirmAction({
      title: '清除所有记录',
      message: '这会永久删除所有用户的登录记录和文件操作记录。',
      confirmText: '清除记录',
      danger: true,
    })
    if (!confirmed) return
    busy.value = true
    try {
      const result = await request('/admin/audits', { method: 'DELETE' })
      admin.loginAudits = []
      admin.fileAudits = []
      notify(`已清除 ${result.loginAuditCount + result.fileOperationAuditCount} 条记录`, 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  async function deleteAdminFile(file) {
    const confirmed = await confirmAction({
      title: '删除用户文件',
      message: `删除 ${file.name}？`,
      confirmText: '删除',
      danger: true,
    })
    if (!confirmed) return
    await request(`/admin/files/${file.id}`, { method: 'DELETE' })
    await loadAdminFiles(admin.parentId)
  }
  
  async function deleteAdminUser(user) {
    if (!user) return
    const confirmed = await confirmAction({
      title: '删除用户',
      message: `删除用户 ${user.username}？该账号将无法登录。`,
      confirmText: '删除用户',
      danger: true,
    })
    if (!confirmed) return
    busy.value = true
    try {
      await request(`/admin/users/${user.id}`, { method: 'DELETE' })
      admin.users = admin.users.filter((item) => item.id !== user.id)
      admin.selectedUser = admin.users[0] || null
      admin.parentId = null
      admin.folderStack = [{ id: null, name: '根目录' }]
      if (admin.selectedUser) {
        await loadSelectedAdminContext(activeTab.value, null)
      } else {
        admin.files = []
        admin.loginAudits = []
        admin.fileAudits = []
      }
      notify('用户已删除', 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  function openBanDialog(user) {
    banDialog.open = true
    banDialog.user = user
    banDialog.reason = user.banReason || ''
    banDialog.bannedUntil = toLocalDateTimeInput(user.bannedUntil)
  }
  
  async function submitBan() {
    if (!banDialog.user) return
    busy.value = true
    try {
      const updated = await request(`/admin/users/${banDialog.user.id}/ban`, {
        method: 'POST',
        body: {
          reason: banDialog.reason,
          bannedUntil: fromLocalDateTimeInput(banDialog.bannedUntil),
        },
      })
      updateAdminUser(updated)
      banDialog.open = false
      notify('已封禁', 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  async function unbanUser(user) {
    busy.value = true
    try {
      const updated = await request(`/admin/users/${user.id}/unban`, { method: 'POST', body: {} })
      updateAdminUser(updated)
      notify('已解封', 'success')
    } catch (error) {
      fail(error)
    } finally {
      busy.value = false
    }
  }
  
  function updateAdminUser(updated) {
    const index = admin.users.findIndex((user) => user.id === updated.id)
    if (index >= 0) {
      admin.users[index] = updated
    }
    if (admin.selectedUser?.id === updated.id) {
      admin.selectedUser = updated
    }
  }
  
  function operationLabel(operation) {
    const labels = {
      CREATE_FOLDER: '新建文件夹',
      UPLOAD: '上传',
      RENAME: '重命名',
      MOVE: '移动',
      COPY: '复制',
      DELETE: '删除',
      ADMIN_DELETE: '管理删除',
      DOWNLOAD: '下载',
      PREVIEW: '预览',
      EXTRACT: '解压',
      CREATE_DIRECT_LINK: '创建直链',
      DIRECT_LINK_DOWNLOAD: '直链下载',
      CREATE_SHARE: '创建分享',
      SHARE_DOWNLOAD: '分享下载',
      SHARE_PREVIEW: '分享预览',
    }
    return labels[operation] || operation
  }
  
  async function loadShare(parentId = shareState.parentId) {
    if (!shareState.token) return
    try {
      const params = new URLSearchParams()
      if (shareState.code) params.set('code', shareState.code)
      if (parentId) params.set('parentId', parentId)
      const suffix = params.toString() ? `?${params}` : ''
      const data = await request(`/public/shares/${shareState.token}${suffix}`)
      shareState.requiresCode = data.requiresCode
      shareState.unlocked = data.unlocked
      shareState.root = data.root
      shareState.files = data.files
      shareState.downloadCount = data.downloadCount
      shareState.parentId = parentId
      shareState.message = data.unlocked ? '' : '请输入提取码'
      if (!data.unlocked) {
        notify('请输入提取码', 'info')
      }
    } catch (error) {
      shareState.message = error.message
      fail(error)
    }
  }
  
  async function downloadShareFile(file) {
    if (!file) return
    const params = new URLSearchParams()
    if (shareState.code) params.set('code', shareState.code)
    const suffix = params.toString() ? `?${params}` : ''
    try {
      const response = await blobRequest(`/public/shares/${shareState.token}/files/${file.id}/download${suffix}`)
      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = shareDownloadName(file)
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      await loadShare(shareState.parentId)
    } catch (error) {
      fail(error)
    }
  }

  async function openSharePreview(file) {
    if (!file || file.fileKind !== 'FILE') return
    const params = new URLSearchParams()
    if (shareState.code) params.set('code', shareState.code)
    const suffix = params.toString() ? `?${params}` : ''
    try {
      closePreview()
      const response = await blobRequest(`/public/shares/${shareState.token}/files/${file.id}/preview${suffix}`)
      await showPreviewBlob(file, await response.blob())
    } catch (error) {
      fail(error)
    }
  }

  function shareDownloadName(file) {
    return downloadName(file)
  }

  function downloadName(file) {
    if (file.fileKind !== 'FOLDER') {
      return file.name
    }
    return file.name.toLowerCase().endsWith('.zip') ? file.name : `${file.name}.zip`
  }
  
  async function switchTab(tab) {
    activeTab.value = tab
    if (tab === 'admin' || tab === 'adminFiles') {
      await loadAdminUsers(tab)
    }
    if (tab === 'settings') {
      await loadAdminSettings()
    }
    if (tab === 'links') {
      await loadLinks()
    }
  }
  
  onMounted(async () => {
    try {
      await loadPublicSettings()
      if (shareRouteToken) {
        await loadShare(null)
        return
      }
      await loadMe()
      await nextTick()
    } finally {
      appReady.value = true
    }
  })
  
  onBeforeUnmount(() => {
    clearAvatarUrl()
    closeAvatarEditor()
    window.clearTimeout(extractControl.polling)
    window.clearTimeout(archiveControl.polling)
  })

  return {
    token,
    currentUser,
    appReady,
    busy,
    authBusyText,
    notification,
    authMode,
    activeTab,
    dragActive,
    fileInput,
    avatarInput,
    avatarCropCanvas,
    avatarUrl,
    avatarBusy,
    selected,
    selectedIds,
    files,
    links,
    systemSettings,
    settingsForm,
    settingsLoading,
    storageCleanup,
    foldersStack,
    searchText,
    preview,
    fileInfoDialog,
    dialog,
    banDialog,
    confirmDialog,
    avatarEditor,
    authForm,
    profileForm,
    uploadProgress,
    extractProgress,
    archiveProgress,
    MAX_AVATAR_SOURCE_BYTES,
    MAX_AVATAR_STORED_BYTES,
    AVATAR_MAX_SIDE,
    AVATAR_EDITOR_SIZE,
    admin,
    shareRouteToken,
    shareState,
    currentParentId,
    isAdmin,
    visibleFiles,
    selectedFiles,
    selectedFileItems,
    selectedFolderItems,
    allVisibleSelected,
    partialVisibleSelected,
    avatarInitials,
    canCurrentUserChangeAvatar,
    canRegister,
    fileNameSorter,
    movingFolderIds,
    selectedTargetPath,
    getShareToken,
    notify,
    fail,
    applySystemSettings,
    fillSettingsForm,
    loadPublicSettings,
    confirmAction,
    closeConfirmDialog,
    loadCaptcha,
    login,
    register,
    refreshAfterAuth,
    loadMe,
    logout,
    fillProfile,
    clearAvatarUrl,
    refreshAvatar,
    saveProfile,
    uploadAvatarSelected,
    closeAvatarEditor,
    updateAvatarScale,
    zoomAvatarEditor,
    startAvatarDrag,
    moveAvatarDrag,
    endAvatarDrag,
    wheelAvatarEditor,
    submitAvatarEditor,
    clearAvatar,
    compressAvatar,
    compressAvatarCanvas,
    loadImage,
    canvasToBlob,
    loadFiles,
    sortFileList,
    setVisibleFiles,
    mergeVisibleFiles,
    appendVisibleFiles,
    removeVisibleFiles,
    isCurrentParent,
    toggleFileSelection,
    toggleAllVisible,
    clearSelection,
    findFolderPath,
    findFolderPathParts,
    folderContainsMovingFolder,
    isFolderTargetDisabled,
    toggleFolderNode,
    selectFolderTarget,
    loadFolderTree,
    enterFolder,
    goCrumb,
    goParentFolder,
    createFolder,
    renameSelected,
    openMoveDialog,
    submitDialog,
    uploadSelected,
    uploadFiles,
    pauseUpload,
    resumeUpload,
    cancelUpload,
    onDrop,
    downloadFile,
    openPreview,
    openFileInfo,
    closePreview,
    closeFileInfo,
    deleteSelected,
    downloadSelectedFiles,
    deleteSelectedFiles,
    createDirectLink,
    openShareDialog,
    extractSelected,
    loadLinks,
    deleteDirectLink,
    deleteShareLink,
    iconFor,
    formatSize,
    formatDuration,
    formatEta,
    isMediaFile,
    isZipFile,
    formatSpeed,
    formatDate,
    formatBanUntil,
    toLocalDateTimeInput,
    fromLocalDateTimeInput,
    startOfLocalDate,
    endOfLocalDate,
    copyText,
    copyToClipboard,
    shareCopyText,
    shareMeta,
    loadAdminUsers,
    loadAdminSettings,
    saveSystemSettings,
    cleanupExpiredStorage,
    cleanupOrphanStorageObjects,
    selectAdminUser,
    loadSelectedAdminContext,
    loadAdminDetail,
    loadAdminFiles,
    openAdminFolder,
    loadAdminFolderAt,
    goAdminParentFolder,
    loadAdminRootFolder,
    loadAdminAudits,
    auditQuery,
    clearAuditRange,
    clearAllAudits,
    deleteAdminFile,
    deleteAdminUser,
    openBanDialog,
    submitBan,
    unbanUser,
    updateAdminUser,
    operationLabel,
    loadShare,
    downloadShareFile,
    openSharePreview,
    switchTab,
  }
}
