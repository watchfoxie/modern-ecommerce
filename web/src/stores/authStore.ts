import { jwtDecode } from 'jwt-decode'
import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { AuthTokenResponse } from '@/contracts/auth'

export interface AuthUser {
  authId: string
  userId: string
  email: string
  roles: string[]
}

interface AccessTokenClaims {
  authId?: string
  userId?: string
  email?: string
  roles?: string[] | string
  exp?: number
  sub?: string
  type?: string
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  tokenType: string
  expiresAt: number | null
  user: AuthUser | null
  setAuth: (response: AuthTokenResponse) => void
  setAccessToken: (response: AuthTokenResponse) => void
  clearAuth: () => void
  isAuthenticated: () => boolean
  hasRole: (role: string) => boolean
}

function normalizeRoles(roles?: string[] | string) {
  if (Array.isArray(roles)) {
    return roles
  }

  if (typeof roles === 'string' && roles.trim()) {
    return roles.split(',').map((role) => role.trim()).filter(Boolean)
  }

  return []
}

function decodeUser(accessToken: string): { user: AuthUser | null; expiresAt: number | null } {
  try {
    const claims = jwtDecode<AccessTokenClaims>(accessToken)
    const roles = normalizeRoles(claims.roles)
    return {
      user: {
        authId: claims.authId ?? '',
        userId: claims.userId ?? '',
        email: claims.email ?? claims.sub ?? '',
        roles,
      },
      expiresAt: claims.exp ? claims.exp * 1000 : null,
    }
  } catch {
    return { user: null, expiresAt: null }
  }
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      tokenType: 'Bearer',
      expiresAt: null,
      user: null,
      setAuth: (response) => {
        const decoded = decodeUser(response.accessToken)
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          tokenType: response.tokenType,
          expiresAt: decoded.expiresAt ?? Date.now() + response.expiresIn * 1000,
          user: decoded.user,
        })
      },
      setAccessToken: (response) => {
        const decoded = decodeUser(response.accessToken)
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          tokenType: response.tokenType,
          expiresAt: decoded.expiresAt ?? Date.now() + response.expiresIn * 1000,
          user: decoded.user,
        })
      },
      clearAuth: () =>
        set({
          accessToken: null,
          refreshToken: null,
          tokenType: 'Bearer',
          expiresAt: null,
          user: null,
        }),
      isAuthenticated: () => {
        const { accessToken, expiresAt, refreshToken } = get()
        return Boolean(accessToken && ((!expiresAt || expiresAt > Date.now()) || refreshToken))
      },
      hasRole: (role) => get().user?.roles.includes(role) ?? false,
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        tokenType: state.tokenType,
        expiresAt: state.expiresAt,
        user: state.user,
      }),
    },
  ),
)

export const getAccessToken = () => useAuthStore.getState().accessToken
export const getRefreshToken = () => useAuthStore.getState().refreshToken
