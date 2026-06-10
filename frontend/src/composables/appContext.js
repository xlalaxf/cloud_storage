import { inject, provide } from 'vue'

const CLOUD_STORAGE_APP_KEY = Symbol('cloud-storage-app')

export function provideCloudStorageApp(app) {
  provide(CLOUD_STORAGE_APP_KEY, app)
}

export function useCloudStorageContext() {
  const app = inject(CLOUD_STORAGE_APP_KEY)
  if (!app) {
    throw new Error('Cloud storage app context is not provided')
  }
  return app
}
