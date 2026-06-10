import userEvent from '@testing-library/user-event'
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { categoryService } from '@/contracts/category'
import { orderService } from '@/contracts/order'
import AdminConsolePage from '@/pages/admin/AdminConsolePage'
import { productService } from '@/contracts/product'
import { renderWithProviders } from './test-utils'

vi.mock('@/contracts/category', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/contracts/category')>()
    return {
        ...actual,
        categoryService: {
            ...actual.categoryService,
            list: vi.fn(),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
        },
    }
})

vi.mock('@/contracts/product', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/contracts/product')>()
    return {
        ...actual,
        productService: {
            ...actual.productService,
            list: vi.fn(),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
        },
    }
})

vi.mock('@/contracts/order', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/contracts/order')>()
    return {
        ...actual,
        orderService: {
            ...actual.orderService,
            listAll: vi.fn(),
            updateStatus: vi.fn(),
        },
    }
})

beforeEach(() => {
    vi.clearAllMocks()

    vi.mocked(categoryService.list).mockResolvedValue({
        data: [
            {
                id: 'category-smartphones',
                slug: 'smartphones',
                name: 'Smartphone-uri',
                description: 'Telefoane premium',
                parentId: null,
                imageUrl: '/smartphones.png',
                displayOrder: 1,
                isActive: true,
            },
        ],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
    })

    vi.mocked(productService.list).mockResolvedValue({
        data: [
            {
                id: 'product-1',
                categoryId: 'category-smartphones',
                categorySlug: 'smartphones',
                slug: 'apple-iphone-15-pro',
                name: 'Apple iPhone 15 Pro',
                brand: 'Apple',
                model: 'iPhone 15 Pro',
                country: 'US',
                price: 21999,
                promotionalPrice: 19999,
                currency: 'MDL',
                stock: 7,
                imageUrls: ['/iphone.png'],
                specs: { processor: 'A17 Pro' },
                isActive: true,
                createdAt: '2026-06-10T10:00:00Z',
                updatedAt: '2026-06-10T10:00:00Z',
            },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
    })

    vi.mocked(orderService.listAll).mockResolvedValue({
        data: [
            {
                id: 'order-1',
                orderNumber: 'MEC-20260610-0001',
                userId: 'user-1',
                items: [
                    {
                        productId: 'product-1',
                        name: 'Apple iPhone 15 Pro',
                        brand: 'Apple',
                        imageUrl: '/iphone.png',
                        quantity: 1,
                        unitPrice: 19999,
                    },
                ],
                deliveryAddress: {
                    recipientName: 'Ana Popescu',
                    recipientPhone: '+37360000000',
                    city: 'Chisinau',
                    district: 'Centru',
                    street: 'Stefan cel Mare 1',
                    postalCode: null,
                },
                payment: {
                    method: 'CARD',
                    status: 'PENDING',
                    transactionId: null,
                },
                status: 'CREATED',
                totalAmount: 19999,
                currency: 'MDL',
                notes: null,
                createdAt: '2026-06-10T10:15:00Z',
                updatedAt: '2026-06-10T10:15:00Z',
            },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
    })
})

describe('AdminConsolePage', () => {
    it('renders dedicated product, category, and order sections after the refactor', async () => {
        const user = userEvent.setup()

        renderWithProviders(<AdminConsolePage />)

        expect(await screen.findByText('Apple iPhone 15 Pro')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Adaugă produs' })).toBeInTheDocument()

        await user.click(screen.getByRole('tab', { name: /Categorii/i }))
        expect(await screen.findByText('Telefoane premium')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Adaugă categorie' })).toBeInTheDocument()

        await user.click(screen.getByRole('tab', { name: /Comenzi/i }))
        expect(await screen.findByRole('cell', { name: 'MEC-20260610-0001' })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Actualizează' })).toBeInTheDocument()
    })
})