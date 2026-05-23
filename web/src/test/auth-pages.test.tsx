import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authService } from '@/contracts/auth'
import { SignInPage } from '@/pages/auth/AuthPages'
import { useAuthStore } from '@/stores/authStore'
import { makeJwt, renderWithProviders } from './test-utils'

vi.mock('@/contracts/auth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/contracts/auth')>()
  return {
    ...actual,
    authService: {
      ...actual.authService,
      signIn: vi.fn(),
    },
  }
})

const mockedAuthService = vi.mocked(authService)

describe('SignInPage', () => {
  beforeEach(() => {
    mockedAuthService.signIn.mockReset()
  })

  it('validates the Zod form before calling auth-service', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <Routes>
        <Route path="/profile/sign-in" element={<SignInPage />} />
      </Routes>,
      { route: '/profile/sign-in' },
    )

    await user.click(screen.getByRole('button', { name: 'Autentifică-te' }))

    expect(await screen.findByText('Email invalid')).toBeInTheDocument()
    expect(mockedAuthService.signIn).not.toHaveBeenCalled()
  })

  it('stores AuthTokenResponse and redirects after successful sign-in', async () => {
    const user = userEvent.setup()
    mockedAuthService.signIn.mockResolvedValue({
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

    renderWithProviders(
      <Routes>
        <Route path="/profile/sign-in" element={<SignInPage />} />
        <Route path="/home" element={<div>Home route</div>} />
      </Routes>,
      { route: '/profile/sign-in' },
    )

    await user.type(screen.getByRole('textbox'), 'customer@example.com')
    await user.type(document.querySelector('input[type="password"]') as HTMLInputElement, 'Password123!')
    await user.click(screen.getByRole('button', { name: 'Autentifică-te' }))

    expect(await screen.findByText('Home route')).toBeInTheDocument()
    expect(useAuthStore.getState().user?.email).toBe('customer@example.com')
    expect(mockedAuthService.signIn.mock.calls[0][0]).toEqual({
      email: 'customer@example.com',
      password: 'Password123!',
    })
  })
})
