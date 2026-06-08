import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import type { PagedResponseDto } from '@/contracts/common'
import { DeliveryPage, PayPage } from '@/pages/cart/CheckoutPages'
import { cartService } from '@/contracts/cart'
import { orderService, type OrderDto } from '@/contracts/order'
import { userService } from '@/contracts/user'
import { queryKeys } from '@/lib/queryKeys'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'
import { useCheckoutStore } from '@/stores/checkoutStore'
import { createTestQueryClient, makeJwt, renderWithProviders } from './test-utils'

vi.mock('@/contracts/user', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/contracts/user')>()
    return {
        ...actual,
        userService: {
            ...actual.userService,
            getMe: vi.fn(),
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

vi.mock('@/contracts/order', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/contracts/order')>()
    return {
        ...actual,
        orderService: {
            ...actual.orderService,
            create: vi.fn(),
        },
    }
})

const mockedUserService = vi.mocked(userService)
const mockedCartService = vi.mocked(cartService)
const mockedOrderService = vi.mocked(orderService)

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

describe('DeliveryPage', () => {
    it('prefills recipient and default address from the saved profile', async () => {
        authenticate()
        mockedUserService.getMe.mockResolvedValue({
            id: 'user-1',
            authId: 'auth-1',
            email: 'customer@example.com',
            firstName: 'Irina',
            lastName: 'Ionescu',
            phone: '+37369000123',
            birthDate: '1995-06-01',
            addresses: [
                {
                    label: 'Acasă',
                    street: 'Strada Ștefan cel Mare 10',
                    city: 'Chișinău',
                    district: 'Centru',
                    postalCode: 'MD-2001',
                    isDefault: true,
                },
            ],
            preferences: null,
            createdAt: '2026-06-01T10:00:00Z',
            updatedAt: '2026-06-01T10:00:00Z',
        })

        renderWithProviders(
            <Routes>
                <Route path="/cart/delivery" element={<DeliveryPage />} />
            </Routes>,
            { route: '/cart/delivery' },
        )

        await waitFor(() => {
            expect(screen.getByLabelText('recipientName')).toHaveValue('Irina Ionescu')
        })
        expect(screen.getByLabelText('recipientPhone')).toHaveValue('+37369000123')
        expect(screen.getByLabelText('city')).toHaveValue('Chișinău')
        expect(screen.getByLabelText('district')).toHaveValue('Centru')
        expect(screen.getByLabelText('street')).toHaveValue('Strada Ștefan cel Mare 10')
        expect(screen.getByLabelText('Cod poștal')).toHaveValue('MD-2001')
    })

    it('keeps only the corresponding fields blank when profile data is incomplete', async () => {
        authenticate()
        mockedUserService.getMe.mockResolvedValue({
            id: 'user-1',
            authId: 'auth-1',
            email: 'customer@example.com',
            firstName: 'Irina',
            lastName: 'Ionescu',
            phone: null,
            birthDate: null,
            addresses: [],
            preferences: null,
            createdAt: '2026-06-01T10:00:00Z',
            updatedAt: '2026-06-01T10:00:00Z',
        })

        renderWithProviders(
            <Routes>
                <Route path="/cart/delivery" element={<DeliveryPage />} />
            </Routes>,
            { route: '/cart/delivery' },
        )

        await waitFor(() => {
            expect(screen.getByLabelText('recipientName')).toHaveValue('Irina Ionescu')
        })
        expect(screen.getByLabelText('recipientPhone')).toHaveValue('')
        expect(screen.getByLabelText('city')).toHaveValue('')
        expect(screen.getByLabelText('district')).toHaveValue('')
        expect(screen.getByLabelText('street')).toHaveValue('')
        expect(screen.getByLabelText('Cod poștal')).toHaveValue('')
    })

    it('preserves a manually saved checkout address instead of overwriting it from profile data', async () => {
        authenticate()
        useCheckoutStore.getState().setDeliveryAddress({
            recipientName: 'Manual Recipient',
            recipientPhone: '+37360000000',
            city: 'Bălți',
            district: 'Bălți',
            street: 'Independenței 1',
            postalCode: 'MD-3100',
        })
        mockedUserService.getMe.mockResolvedValue({
            id: 'user-1',
            authId: 'auth-1',
            email: 'customer@example.com',
            firstName: 'Irina',
            lastName: 'Ionescu',
            phone: '+37369000123',
            birthDate: '1995-06-01',
            addresses: [
                {
                    label: 'Acasă',
                    street: 'Strada Ștefan cel Mare 10',
                    city: 'Chișinău',
                    district: 'Centru',
                    postalCode: 'MD-2001',
                    isDefault: true,
                },
            ],
            preferences: null,
            createdAt: '2026-06-01T10:00:00Z',
            updatedAt: '2026-06-01T10:00:00Z',
        })

        renderWithProviders(
            <Routes>
                <Route path="/cart/delivery" element={<DeliveryPage />} />
            </Routes>,
            { route: '/cart/delivery' },
        )

        await waitFor(() => {
            expect(screen.getByLabelText('recipientName')).toHaveValue('Manual Recipient')
        })
        expect(screen.getByLabelText('city')).toHaveValue('Bălți')
    })
})

describe('PayPage', () => {
    it('replaces the cart query cache with an empty snapshot after a successful order', async () => {
        authenticate()
        useCheckoutStore.getState().setDeliveryAddress({
            recipientName: 'Ana Ionescu',
            recipientPhone: '+37369000111',
            city: 'Chișinău',
            district: 'Centru',
            street: 'Ștefan cel Mare 1',
            postalCode: 'MD-2001',
        })

        const queryClient = createTestQueryClient()
        queryClient.setQueryData(queryKeys.cart('user-1'), {
            id: 'cart-1',
            userId: 'user-1',
            createdAt: '2026-06-03T10:00:00Z',
            updatedAt: '2026-06-03T10:00:00Z',
            items: [
                {
                    productId: 'product-1',
                    quantity: 1,
                    priceAtAdd: 1200,
                    productSnapshot: {
                        name: 'Telefon',
                        imageUrl: '/phone.png',
                        categorySlug: 'smartphones',
                    },
                },
            ],
        })
        queryClient.setQueryData<PagedResponseDto<OrderDto>>(queryKeys.orders('user-1', 0), {
            data: [],
            page: 0,
            size: 10,
            totalElements: 0,
            totalPages: 0,
            first: true,
            last: true,
        })
        queryClient.setQueryData<PagedResponseDto<OrderDto>>(queryKeys.ordersDashboard('user-1'), {
            data: [],
            page: 0,
            size: 100,
            totalElements: 0,
            totalPages: 0,
            first: true,
            last: true,
        })
        mockedCartService.getMe.mockResolvedValue({
            id: 'cart-1',
            userId: 'user-1',
            createdAt: '2026-06-03T10:00:00Z',
            updatedAt: '2026-06-03T10:00:00Z',
            items: [
                {
                    productId: 'product-1',
                    quantity: 1,
                    priceAtAdd: 1200,
                    productSnapshot: {
                        name: 'Telefon',
                        imageUrl: '/phone.png',
                        categorySlug: 'smartphones',
                    },
                },
            ],
        })
        mockedOrderService.create.mockResolvedValue({
            status: 'ACCEPTED',
            orderId: 'order-1',
            orderNumber: 'ORD-1',
            message: 'accepted',
        })

        renderWithProviders(
            <Routes>
                <Route path="/cart/delivery" element={<div>Livrare</div>} />
                <Route path="/cart/pay" element={<PayPage />} />
                <Route path="/profile/account/order-history" element={<div>Istoric</div>} />
            </Routes>,
            { route: '/cart/pay', queryClient },
        )

        await userEvent.click(screen.getByRole('button', { name: /plasează comanda/i }))

        await waitFor(() => {
            expect(mockedOrderService.create).toHaveBeenCalledTimes(1)
        })

        expect(queryClient.getQueryData(queryKeys.cart('user-1'))).toMatchObject({ items: [] })
        expect(useCartStore.getState().items).toEqual([])
        expect(queryClient.getQueryState(queryKeys.orders('user-1', 0))?.isInvalidated).toBe(true)
        expect(queryClient.getQueryState(queryKeys.ordersDashboard('user-1'))?.isInvalidated).toBe(true)
    })
})