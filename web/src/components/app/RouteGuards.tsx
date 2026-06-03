import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'

export function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())

  if (!isAuthenticated) {
    return <Navigate to="/profile/sign-in" replace state={{ redirectTo: location.pathname + location.search }} />
  }

  return children
}

export function RequireGuest({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())

  if (isAuthenticated) {
    return <Navigate to="/profile/account" replace />
  }

  return children
}

export function RequireRole({ children, role, fallbackTo = '/profile/account' }: { children: ReactNode; role: string; fallbackTo?: string }) {
  const location = useLocation()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())
  const hasRole = useAuthStore((state) => state.hasRole(role))

  if (!isAuthenticated) {
    return <Navigate to="/profile/sign-in" replace state={{ redirectTo: location.pathname + location.search }} />
  }

  if (!hasRole) {
    return <Navigate to={fallbackTo} replace />
  }

  return children
}
