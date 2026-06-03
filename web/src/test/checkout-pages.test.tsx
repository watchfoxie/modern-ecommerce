import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { DeliveryPage } from '@/pages/cart/CheckoutPages'
import { userService } from '@/contracts/user'
import { useAuthStore } from '@/stores/authStore'
import { useCheckoutStore } from '@/stores/checkoutStore'
import { makeJwt, renderWithProviders } from './test-utils'

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

const mockedUserService = vi.mocked(userService)

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