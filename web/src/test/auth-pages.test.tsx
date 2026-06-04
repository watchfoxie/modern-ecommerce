import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authService } from '@/contracts/auth'
import { cartService } from '@/contracts/cart'
import { PasswordResetPage, SignInPage, SignUpPage } from '@/pages/auth/AuthPages'
import { queryClient } from '@/config/queryClient'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'
import { makeJwt, renderWithProviders } from './test-utils'

vi.mock('@/contracts/auth', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/contracts/auth')>()
  return {
    ...actual,
    authService: {
      ...actual.authService,
      signUp: vi.fn(),
      signIn: vi.fn(),
      requestPasswordReset: vi.fn(),
      confirmPasswordReset: vi.fn(),
    },
  }
})

vi.mock('@/contracts/cart', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/contracts/cart')>()
  return {
    ...actual,
    cartService: {
      ...actual.cartService,
      getMe: vi.fn(),
    },
  }
})

const mockedAuthService = vi.mocked(authService)
const mockedCartService = vi.mocked(cartService)

describe('SignInPage', () => {
  beforeEach(() => {
    mockedAuthService.signUp.mockReset()
    mockedAuthService.signIn.mockReset()
    mockedAuthService.requestPasswordReset.mockReset()
    mockedAuthService.confirmPasswordReset.mockReset()
    mockedCartService.getMe.mockReset()
    queryClient.clear()
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
    mockedCartService.getMe.mockResolvedValue({
      id: 'cart-1',
      userId: 'user-1',
      createdAt: '2026-06-03T10:00:00Z',
      updatedAt: '2026-06-03T10:00:00Z',
      items: [],
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

  it('synchronizes the persistent cart immediately after successful sign-in', async () => {
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
    mockedCartService.getMe.mockResolvedValue({
      id: 'cart-1',
      userId: 'user-1',
      createdAt: '2026-06-03T10:00:00Z',
      updatedAt: '2026-06-03T10:00:00Z',
      items: [
        {
          productId: 'phone-1',
          quantity: 2,
          priceAtAdd: 1200,
          productSnapshot: {
            name: 'Telefon',
            imageUrl: '/phone.png',
            categorySlug: 'smartphones',
          },
        },
      ],
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
    expect(mockedCartService.getMe).toHaveBeenCalledTimes(1)
    expect(useCartStore.getState().totalItems()).toBe(2)
  })

  it('redirects to the target route before cart synchronization completes', async () => {
    const user = userEvent.setup()
    let resolveCart: ((value: {
      id: string
      userId: string
      createdAt: string
      updatedAt: string
      items: never[]
    }) => void) | null = null

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
    mockedCartService.getMe.mockReturnValue(
      new Promise((resolve) => {
        resolveCart = resolve
      }),
    )

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
    expect(mockedCartService.getMe).toHaveBeenCalledTimes(1)

    resolveCart?.({
      id: 'cart-1',
      userId: 'user-1',
      createdAt: '2026-06-03T10:00:00Z',
      updatedAt: '2026-06-03T10:00:00Z',
      items: [],
    })
  })

  it('redirects to the preserved target from query parameters after successful sign-in', async () => {
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
    mockedCartService.getMe.mockResolvedValue({
      id: 'cart-1',
      userId: 'user-1',
      createdAt: '2026-06-03T10:00:00Z',
      updatedAt: '2026-06-03T10:00:00Z',
      items: [],
    })

    renderWithProviders(
      <Routes>
        <Route path="/profile/sign-in" element={<SignInPage />} />
        <Route path="/cart" element={<div>Cart route</div>} />
      </Routes>,
      { route: '/profile/sign-in?redirectTo=%2Fcart' },
    )

    await user.type(screen.getByRole('textbox'), 'customer@example.com')
    await user.type(document.querySelector('input[type="password"]') as HTMLInputElement, 'Password123!')
    await user.click(screen.getByRole('button', { name: 'Autentifică-te' }))

    expect(await screen.findByText('Cart route')).toBeInTheDocument()
  })

  it('preserves redirectTo when linking from sign-in to sign-up', () => {
    renderWithProviders(
      <Routes>
        <Route path="/profile/sign-in" element={<SignInPage />} />
      </Routes>,
      { route: '/profile/sign-in?redirectTo=%2Fcart%3Fstep%3Ddelivery' },
    )

    expect(screen.getByRole('link', { name: 'Nu ai cont? Înregistrează-te' })).toHaveAttribute(
      'href',
      '/profile/sign-up?redirectTo=%2Fcart%3Fstep%3Ddelivery',
    )
  })
})

describe('SignUpPage', () => {
  it('preserves redirectTo when linking back to sign-in', () => {
    renderWithProviders(
      <Routes>
        <Route path="/profile/sign-up" element={<SignUpPage />} />
      </Routes>,
      { route: '/profile/sign-up?redirectTo=%2Fcart%3Fstep%3Ddelivery' },
    )

    expect(screen.getByRole('link', { name: 'Ai deja cont? Autentifică-te' })).toHaveAttribute(
      'href',
      '/profile/sign-in?redirectTo=%2Fcart%3Fstep%3Ddelivery',
    )
  })
})

describe('PasswordResetPage', () => {
  beforeEach(() => {
    mockedAuthService.requestPasswordReset.mockReset()
    mockedAuthService.confirmPasswordReset.mockReset()
    queryClient.clear()
  })

  it('moves to the confirmation step only after a successful password reset request', async () => {
    const user = userEvent.setup()
    mockedAuthService.requestPasswordReset.mockResolvedValue()

    renderWithProviders(
      <Routes>
        <Route path="/profile/password-reset" element={<PasswordResetPage />} />
      </Routes>,
      { route: '/profile/password-reset' },
    )

    await user.type(screen.getByRole('textbox'), 'customer@example.com')
    await user.click(screen.getByRole('button', { name: 'Solicită resetare' }))

    expect(mockedAuthService.requestPasswordReset).toHaveBeenCalledWith(
      { email: 'customer@example.com' },
      expect.anything(),
    )
    expect(await screen.findByText('Verificați emailul')).toBeInTheDocument()
  })

  it('stays on the request step and shows the operational error when the request fails', async () => {
    const user = userEvent.setup()
    mockedAuthService.requestPasswordReset.mockRejectedValue(new Error('Notification service unavailable'))

    renderWithProviders(
      <Routes>
        <Route path="/profile/password-reset" element={<PasswordResetPage />} />
      </Routes>,
      { route: '/profile/password-reset' },
    )

    await user.type(screen.getByRole('textbox'), 'customer@example.com')
    await user.click(screen.getByRole('button', { name: 'Solicită resetare' }))

    expect(await screen.findByText('Request failed')).toBeInTheDocument()
    expect(screen.getByText('Notification service unavailable')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Solicită resetare' })).toBeInTheDocument()
    expect(screen.queryByText('Verificați emailul')).not.toBeInTheDocument()
  })
})
