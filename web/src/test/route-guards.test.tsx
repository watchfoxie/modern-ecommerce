import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { RequireAuth, RequireGuest } from '@/components/app/RouteGuards'
import { useAuthStore } from '@/stores/authStore'
import { makeJwt, renderWithProviders } from './test-utils'

function authenticate() {
  useAuthStore.getState().setAuth({
    accessToken: makeJwt({
      authId: 'auth-1',
      userId: 'user-1',
      email: 'customer@example.com',
      roles: ['ROLE_USER'],
      exp: Math.floor(Date.now() / 1000) + 3600,
    }),
    refreshToken: 'refresh-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
  })
}

describe('route guards', () => {
  it('redirects unauthenticated users to sign-in for protected routes', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/cart" element={<RequireAuth><div>Protected cart</div></RequireAuth>} />
        <Route path="/profile/sign-in" element={<div>Sign-in route</div>} />
      </Routes>,
      { route: '/cart?step=delivery' },
    )

    expect(await screen.findByText('Sign-in route')).toBeInTheDocument()
    expect(screen.queryByText('Protected cart')).not.toBeInTheDocument()
  })

  it('renders protected content for authenticated users', () => {
    authenticate()

    renderWithProviders(
      <Routes>
        <Route path="/cart" element={<RequireAuth><div>Protected cart</div></RequireAuth>} />
      </Routes>,
      { route: '/cart' },
    )

    expect(screen.getByText('Protected cart')).toBeInTheDocument()
  })

  it('keeps protected routes mounted when access token expired but refresh token exists', () => {
    useAuthStore.getState().setAuth({
      accessToken: makeJwt({
        authId: 'auth-1',
        userId: 'user-1',
        email: 'customer@example.com',
        roles: ['ROLE_USER'],
        exp: Math.floor(Date.now() / 1000) - 60,
      }),
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })

    renderWithProviders(
      <Routes>
        <Route path="/cart" element={<RequireAuth><div>Protected cart</div></RequireAuth>} />
        <Route path="/profile/sign-in" element={<div>Sign-in route</div>} />
      </Routes>,
      { route: '/cart' },
    )

    expect(screen.getByText('Protected cart')).toBeInTheDocument()
  })

  it('redirects authenticated users away from guest-only auth pages', async () => {
    authenticate()

    renderWithProviders(
      <Routes>
        <Route path="/profile/sign-in" element={<RequireGuest><div>Guest sign-in</div></RequireGuest>} />
        <Route path="/profile/account" element={<div>Account route</div>} />
      </Routes>,
      { route: '/profile/sign-in' },
    )

    expect(await screen.findByText('Account route')).toBeInTheDocument()
    expect(screen.queryByText('Guest sign-in')).not.toBeInTheDocument()
  })
})
