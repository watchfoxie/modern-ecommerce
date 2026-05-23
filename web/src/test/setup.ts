import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'
import { useCheckoutStore } from '@/stores/checkoutStore'

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

window.scrollTo = vi.fn()

afterEach(() => {
  cleanup()
  localStorage.clear()
  useAuthStore.setState({
    accessToken: null,
    refreshToken: null,
    tokenType: 'Bearer',
    expiresAt: null,
    user: null,
  })
  useCartStore.setState({ items: [], lastSyncedAt: null })
  useCheckoutStore.setState({
    deliveryAddress: null,
    contact: null,
    payment: { method: 'CARD' },
    notes: '',
  })
})
