import { describe, expect, it } from 'vitest'
import type { CartDto } from '@/contracts/cart'
import { queryClient } from '@/config/queryClient'
import { clearSessionState } from '@/lib/session'
import { queryKeys } from '@/lib/queryKeys'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'
import { useCheckoutStore } from '@/stores/checkoutStore'
import { makeJwt } from './test-utils'

describe('Zustand stores', () => {
  it('aligns auth state with AuthTokenResponse and JWT claims', () => {
    const accessToken = makeJwt({
      authId: 'auth-1',
      userId: 'user-1',
      email: 'customer@example.com',
      roles: ['ROLE_USER', 'ROLE_ADMIN'],
      exp: Math.floor(Date.now() / 1000) + 3600,
    })

    useAuthStore.getState().setAuth({
      accessToken,
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })

    const state = useAuthStore.getState()
    expect(state.isAuthenticated()).toBe(true)
    expect(state.hasRole('ROLE_ADMIN')).toBe(true)
    expect(state.user).toMatchObject({
      authId: 'auth-1',
      userId: 'user-1',
      email: 'customer@example.com',
    })
  })

  it('keeps expired access tokens refreshable when a refresh token exists', () => {
    useAuthStore.getState().setAuth({
      accessToken: makeJwt({ sub: 'expired@example.com', exp: Math.floor(Date.now() / 1000) - 10 }),
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })

    expect(useAuthStore.getState().isAuthenticated()).toBe(true)
  })

  it('treats expired access tokens without refresh tokens as unauthenticated', () => {
    useAuthStore.getState().setAuth({
      accessToken: makeJwt({ sub: 'expired@example.com', exp: Math.floor(Date.now() / 1000) - 10 }),
      refreshToken: '',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })

    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
  })

  it('syncs cart summary from CartDto and applies optimistic changes', () => {
    const cart: CartDto = {
      id: 'cart-1',
      userId: 'user-1',
      createdAt: '2026-05-22T10:00:00Z',
      updatedAt: '2026-05-22T10:00:00Z',
      items: [
        {
          productId: 'product-1',
          quantity: 2,
          priceAtAdd: 1000,
          productSnapshot: {
            name: 'iPhone 15 Pro',
            imageUrl: '/static/assets/images/prod-images/smartphones/apple/apple-iphone-15-pro-1.png',
            categorySlug: 'smartphones',
          },
        },
      ],
    }

    useCartStore.getState().syncFromCart(cart)
    expect(useCartStore.getState().totalItems()).toBe(2)
    expect(useCartStore.getState().totalPrice()).toBe(2000)

    useCartStore.getState().applyOptimisticItem({
      productId: 'product-1',
      name: 'iPhone 15 Pro',
      price: 1000,
      quantity: 1,
    })
    expect(useCartStore.getState().totalItems()).toBe(3)

    useCartStore.getState().updateOptimisticQuantity('product-1', 0)
    expect(useCartStore.getState().items).toEqual([])
  })

  it('persists transient checkout state and resets it after order creation', () => {
    useCheckoutStore.getState().setDeliveryAddress({
      recipientName: 'Ana Popescu',
      recipientPhone: '+37360000000',
      city: 'Chisinau',
      district: 'Chisinau',
      street: 'Stefan cel Mare 1',
      postalCode: null,
    })
    useCheckoutStore.getState().setPayment({ method: 'CARD', transactionId: 'txn-1' })
    useCheckoutStore.getState().setNotes('Call before delivery')

    expect(useCheckoutStore.getState().deliveryAddress?.recipientName).toBe('Ana Popescu')
    expect(useCheckoutStore.getState().payment.transactionId).toBe('txn-1')

    useCheckoutStore.getState().resetCheckout()
    expect(useCheckoutStore.getState().deliveryAddress).toBeNull()
    expect(useCheckoutStore.getState().payment).toEqual({ method: 'CARD' })
    expect(useCheckoutStore.getState().notes).toBe('')
  })

  it('clears auth, cart and checkout state when the session is invalidated', () => {
    useAuthStore.getState().setAuth({
      accessToken: makeJwt({ sub: 'customer@example.com', exp: Math.floor(Date.now() / 1000) + 3600 }),
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })
    useCartStore.getState().applyOptimisticItem({
      productId: 'product-1',
      name: 'Phone Pro',
      price: 1000,
      quantity: 2,
    })
    useCheckoutStore.getState().setDeliveryAddress({
      recipientName: 'Ana Popescu',
      recipientPhone: '+37360000000',
      city: 'Chișinău',
      district: 'Chișinău',
      street: 'Stefan cel Mare 1',
      postalCode: null,
    })
    queryClient.setQueryData(queryKeys.profile('user-1'), { email: 'customer@example.com' })
    queryClient.setQueryData(queryKeys.orders('user-1', 0), { data: [] })

    clearSessionState()

    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useCartStore.getState().items).toEqual([])
    expect(useCheckoutStore.getState().deliveryAddress).toBeNull()
    expect(queryClient.getQueryState(queryKeys.profile('user-1'))).toBeUndefined()
    expect(queryClient.getQueryState(queryKeys.orders('user-1', 0))).toBeUndefined()
  })
})
