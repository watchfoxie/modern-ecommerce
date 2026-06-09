import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import ProductDetailPage from '@/pages/catalog/ProductDetailPage'
import { productService } from '@/contracts/product'
import { formatMoney } from '@/lib/format'
import { renderWithProviders } from './test-utils'

vi.mock('@/contracts/product', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/contracts/product')>()
    return {
        ...actual,
        productService: {
            ...actual.productService,
            getBySlug: vi.fn(),
        },
    }
})

const mockedProductService = vi.mocked(productService)

function normalizeText(value: string) {
    return value.replace(/\s+/g, ' ').trim()
}

function hasFormattedMoney(value: number, currency: 'MDL') {
    const expected = normalizeText(formatMoney(value, currency))
    return (_content: string, node: Element | null) => normalizeText(node?.textContent ?? '') === expected
}

describe('ProductDetailPage', () => {
    it('recalculates the displayed total price when quantity changes', async () => {
        const user = userEvent.setup()
        mockedProductService.getBySlug.mockResolvedValue({
            id: 'product-1',
            categoryId: 'cat-1',
            categorySlug: 'smartphones',
            slug: 'phone-pro',
            name: 'Phone Pro',
            brand: 'MEc',
            model: 'Pro',
            country: 'MD',
            price: 1000,
            promotionalPrice: 750,
            currency: 'MDL',
            stock: 5,
            imageUrls: [],
            specs: { memory: '256GB' },
            isActive: true,
            createdAt: '2026-06-01T10:00:00Z',
            updatedAt: '2026-06-01T10:00:00Z',
        })

        renderWithProviders(
            <Routes>
                <Route path="/categories/smartphones/:productId" element={<ProductDetailPage />} />
            </Routes>,
            { route: '/categories/smartphones/phone-pro' },
        )

        expect(await screen.findByText('Total pentru 1 unitate')).toBeInTheDocument()
        expect(screen.getByText(hasFormattedMoney(750, 'MDL'))).toBeInTheDocument()
        expect(screen.getByText(hasFormattedMoney(1000, 'MDL'))).toBeInTheDocument()
        expect(screen.getByText('Total pentru 1 unitate')).toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: 'Crește cantitatea' }))

        expect(screen.getByText(hasFormattedMoney(1500, 'MDL'))).toBeInTheDocument()
        expect(screen.getByText(hasFormattedMoney(2000, 'MDL'))).toBeInTheDocument()
        expect(screen.getByText('Total pentru 2 unități')).toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: 'Scade cantitatea' }))

        expect(screen.getByText(hasFormattedMoney(750, 'MDL'))).toBeInTheDocument()
        expect(screen.getByText('Total pentru 1 unitate')).toBeInTheDocument()
        expect(screen.getByRole('textbox', { name: 'Cantitate selectată' })).toHaveAttribute('readonly')
    })

    it('redirects promotional routes back to the canonical product route when no active discount exists', async () => {
        mockedProductService.getBySlug.mockResolvedValue({
            id: 'product-2',
            categoryId: 'cat-2',
            categorySlug: 'laptops',
            slug: 'matebook-d-16-2024',
            name: 'MateBook D 16 (2024)',
            brand: 'Huawei',
            model: 'MateBook D 16',
            country: 'China',
            price: 28357.73,
            promotionalPrice: null,
            currency: 'MDL',
            stock: 60,
            imageUrls: [],
            specs: { screenSize: '16 inch' },
            isActive: true,
            createdAt: '2026-06-01T10:00:00Z',
            updatedAt: '2026-06-01T10:00:00Z',
        })

        renderWithProviders(
            <Routes>
                <Route path="/categories/offers/laptops/:productId" element={<ProductDetailPage promotional />} />
                <Route path="/categories/laptops/:productId" element={<div>canonical route reached</div>} />
            </Routes>,
            { route: '/categories/offers/laptops/matebook-d-16-2024' },
        )

        expect(await screen.findByText('canonical route reached')).toBeInTheDocument()
    })
})