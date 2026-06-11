const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const TOKEN_KEY = 'cloud_storage_token'
const DEFAULT_TIMEOUT_MS = 12000

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  const controller = new AbortController()
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS
  const timeout = timeoutMs > 0 ? window.setTimeout(() => controller.abort(), timeoutMs) : null
  const token = getToken()
  if (token && shouldSendToken(path)) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let body = options.body
  if (body && !(body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(body)
  }

  let response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
      body,
      signal: options.signal || controller.signal,
    })
  } catch (error) {
    if (error.name === 'AbortError') {
      throw apiError('请求超时，请检查后端服务或稍后重试', 0)
    }
    throw apiError('网络请求失败，请检查后端服务', 0)
  } finally {
    if (timeout) {
      window.clearTimeout(timeout)
    }
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const json = await response.json()
    if (!response.ok || json.success === false) {
      throw apiError(json.message || '请求失败', response.status)
    }
    return json.data
  }

  if (!response.ok) {
    throw apiError('请求失败', response.status)
  }
  return response
}

export function uploadRequest(path, formData, options = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    const token = getToken()
    const timeoutMs = options.timeoutMs ?? 0

    xhr.open(options.method || 'POST', `${API_BASE}${path}`)
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }
    if (timeoutMs > 0) {
      xhr.timeout = timeoutMs
    }

    xhr.upload.onprogress = (event) => {
      options.onProgress?.({
        loaded: event.loaded,
        total: event.lengthComputable ? event.total : 0,
        lengthComputable: event.lengthComputable,
      })
    }

    xhr.onload = () => {
      const contentType = xhr.getResponseHeader('content-type') || ''
      if (contentType.includes('application/json')) {
        let json
        try {
          json = JSON.parse(xhr.responseText || '{}')
        } catch {
          reject(apiError('响应解析失败', xhr.status))
          return
        }
        if (xhr.status < 200 || xhr.status >= 300 || json.success === false) {
          reject(apiError(json.message || '请求失败', xhr.status))
          return
        }
        resolve(json.data)
        return
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(apiError('请求失败', xhr.status))
        return
      }
      resolve(xhr.response)
    }

    xhr.onerror = () => reject(apiError('网络请求失败，请检查后端服务', 0))
    xhr.ontimeout = () => reject(apiError('请求超时，请检查后端服务或稍后重试', 0))
    xhr.send(formData)
  })
}

export function uploadChunkRequest(path, blob, options = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    const token = getToken()
    const timeoutMs = options.timeoutMs ?? 0
    const form = new FormData()
    form.append('chunk', blob, options.filename || 'chunk')

    xhr.open(options.method || 'PUT', `${API_BASE}${path}`)
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }
    if (timeoutMs > 0) {
      xhr.timeout = timeoutMs
    }

    xhr.upload.onprogress = (event) => {
      options.onProgress?.({
        loaded: event.loaded,
        total: event.lengthComputable ? event.total : blob.size,
        lengthComputable: event.lengthComputable,
      })
    }

    xhr.onload = () => {
      const contentType = xhr.getResponseHeader('content-type') || ''
      if (contentType.includes('application/json')) {
        let json
        try {
          json = JSON.parse(xhr.responseText || '{}')
        } catch {
          reject(apiError('响应解析失败', xhr.status))
          return
        }
        if (xhr.status < 200 || xhr.status >= 300 || json.success === false) {
          reject(apiError(json.message || '请求失败', xhr.status))
          return
        }
        resolve(json.data)
        return
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(apiError('请求失败', xhr.status))
        return
      }
      resolve(xhr.response)
    }

    xhr.onerror = () => reject(apiError('网络请求失败，请检查后端服务', 0))
    xhr.onabort = () => reject(apiError('上传已取消', 0))
    xhr.ontimeout = () => reject(apiError('请求超时，请检查后端服务或稍后重试', 0))
    options.signal?.addEventListener('abort', () => xhr.abort(), { once: true })
    xhr.send(form)
  })
}

export async function blobRequest(path) {
  const headers = new Headers()
  const token = getToken()
  if (token && shouldSendToken(path)) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`${API_BASE}${path}`, { headers })
  if (!response.ok) {
    let message = '文件请求失败'
    const contentType = response.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      const json = await response.json()
      message = json.message || message
    }
    throw apiError(message, response.status)
  }
  return response
}

export async function downloadBlob(path, filename) {
  const response = await blobRequest(path)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || 'download'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function apiError(message, status) {
  const error = new Error(message)
  error.status = status
  return error
}

function shouldSendToken(path) {
  return !path.startsWith('/auth/') && !path.startsWith('/public/')
}
