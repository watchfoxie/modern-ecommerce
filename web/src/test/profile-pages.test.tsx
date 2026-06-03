import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { orderService } from '@/contracts/order'
import { ExpenseDashboardPage } from '@/pages/profile/ProfilePages'
import { renderWithProviders } from './test-utils'

vi.mock('recharts', async (importOriginal) => {
    const actual = await importOriginal<typeof import('recharts')>()
    return {
        ...actual,
        ResponsiveContainer: ({ children }: { children: unknown }) => <div data-testid="responsive-container">{children}</div>,
        LineChart: ({ children }: { children: unknown }) => <div data-testid="line-chart">{children}</div>,
        PieChart: ({ children }: { children: unknown }) => <div data-testid="pie-chart">{children}</div>,
        BarChart: ({ children }: { children: unknown }) => <div data-testid="bar-chart">{children}</div>,
        CartesianGrid: () => null,
        XAxis: () => null,
        YAxis: ({ tick }: { tick?: { fill?: string } }) => <span data-testid="chart-y-axis" data-tick-fill={tick?.fill} />,
        Tooltip: ({ contentStyle, itemStyle, labelStyle }: { contentStyle?: { color?: string }; itemStyle?: { color?: string }; labelStyle?: { color?: string } }) => (
            <div
                data-testid="chart-tooltip"
                data-content-color={contentStyle?.color}
                data-item-color={itemStyle?.color}
                data-label-color={labelStyle?.color}
            />
        ),
        Pie: ({ data, label }: { data?: Array<{ fill?: string }>; label?: boolean | { fill?: string } }) => (
            <div data-testid="pie-series" data-label-fill={typeof label === 'object' ? label.fill : undefined}>
                {data?.map((entry) => <span key={entry.fill} data-testid="chart-cell" data-fill={entry.fill} />)}
            </div>
        ),
        Bar: ({ fill }: { fill?: string }) => <span data-testid="bar-cell" data-fill={fill} />,
        Line: ({ stroke }: { stroke?: string }) => <span data-testid="chart-line" data-stroke={stroke} />,
    }
})

vi.mock('@/contracts/order', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/contracts/order')>()
    return {
        ...actual,
        orderService: {
            ...actual.orderService,
            listMine: vi.fn(),
        },
    }
})

const mockedOrderService = vi.mocked(orderService)

describe('ExpenseDashboardPage', () => {
    it('renders distribution and order count charts with the Mozilla blue palette', async () => {
        mockedOrderService.listMine.mockResolvedValue({
            data: [
                {
                    id: 'order-1',
                    orderNumber: 'ORD-1',
                    userId: 'user-1',
                    items: [
                        {
                            productId: 'phone-1',
                            name: 'Telefon',
                            brand: 'Brand',
                            imageUrl: '/phone.png',
                            quantity: 1,
                            unitPrice: 1000,
                        },
                    ],
                    deliveryAddress: {
                        recipientName: 'Ana',
                        recipientPhone: '+37360000000',
                        city: 'Chisinau',
                        district: 'Centru',
                        street: 'Main 1',
                        postalCode: 'MD-2001',
                    },
                    payment: { method: 'CARD', status: 'PENDING', transactionId: null },
                    status: 'CREATED',
                    totalAmount: 1000,
                    currency: 'MDL',
                    notes: null,
                    createdAt: '2026-06-03T10:00:00Z',
                    updatedAt: '2026-06-03T10:00:00Z',
                },
                {
                    id: 'order-2',
                    orderNumber: 'ORD-2',
                    userId: 'user-1',
                    items: [
                        {
                            productId: 'laptop-1',
                            name: 'MateBook',
                            brand: 'Brand',
                            imageUrl: '/laptop.png',
                            quantity: 1,
                            unitPrice: 2000,
                        },
                    ],
                    deliveryAddress: {
                        recipientName: 'Ana',
                        recipientPhone: '+37360000000',
                        city: 'Chisinau',
                        district: 'Centru',
                        street: 'Main 1',
                        postalCode: 'MD-2001',
                    },
                    payment: { method: 'CARD', status: 'PENDING', transactionId: null },
                    status: 'CREATED',
                    totalAmount: 2000,
                    currency: 'MDL',
                    notes: null,
                    createdAt: '2026-05-03T10:00:00Z',
                    updatedAt: '2026-05-03T10:00:00Z',
                },
            ],
            page: 0,
            size: 100,
            totalElements: 2,
            totalPages: 1,
            first: true,
            last: true,
        })

        const { container } = renderWithProviders(
            <Routes>
                <Route path="/profile/account/expense-dashboard" element={<ExpenseDashboardPage />} />
            </Routes>,
            { route: '/profile/account/expense-dashboard' },
        )

        await waitFor(() => {
            expect(screen.getByText('Distribuție')).toBeInTheDocument()
        })

        expect(container.querySelector('[data-fill="#aaf2ff"]')).not.toBeNull()
        expect(container.querySelector('[data-fill="#80ebff"]')).not.toBeNull()
        expect(container.querySelector('[data-fill="#00ddff"]')).not.toBeNull()
        expect(container.querySelector('[data-stroke="#0090ed"]')).not.toBeNull()
        expect(container.querySelector('[data-label-fill="#000000"]')).not.toBeNull()
        expect(container.querySelector('[data-content-color="#000000"]')).not.toBeNull()
        expect(container.querySelector('[data-item-color="#000000"]')).not.toBeNull()
        expect(container.querySelector('[data-label-color="#000000"]')).not.toBeNull()
        expect(container.querySelectorAll('[data-tick-fill="#000000"]').length).toBe(2)
    })
})