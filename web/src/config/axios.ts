import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { API_BASE_URL } from './api'
import { useAuthStore } from '@/stores/authStore'
import type { AuthTokenResponse } from '@/contracts/auth'
import { clearSessionState } from '@/lib/session'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const AUTH_ENDPOINTS = [
  '/auth-service/v1/sign-in',
  '/auth-service/v1/sign-up',
  '/auth-service/v1/password-reset/request',
  '/auth-service/v1/password-reset/confirm',
  '/auth-service/v1/token/refresh',
]

let refreshPromise: Promise<string | null> | null = null

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function isAuthEndpoint(url?: string) {
  return Boolean(url && AUTH_ENDPOINTS.some((endpoint) => url.startsWith(endpoint)))
}

async function refreshAccessToken() {
  const refreshToken = useAuthStore.getState().refreshToken
  if (!refreshToken) {
    return null
  }

  refreshPromise ??= axios.post<AuthTokenResponse>(
    `${API_BASE_URL}/auth-service/v1/token/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } },
  )
    .then((response) => {
      useAuthStore.getState().setAccessToken(response.data)
      return response.data.accessToken
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined
    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !isAuthEndpoint(originalRequest.url)
    ) {
      originalRequest._retry = true
      try {
        const token = await refreshAccessToken()
        if (token) {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return api(originalRequest)
        }
      } catch {
        clearSessionState()
      }
    }

    if (error.response?.status === 401 && !isAuthEndpoint(originalRequest?.url)) {
      clearSessionState()
      const redirectTo = `${window.location.pathname}${window.location.search}`
      window.location.assign(`/profile/sign-in?redirectTo=${encodeURIComponent(redirectTo)}`)
    }

    return Promise.reject(error)
  },
)
