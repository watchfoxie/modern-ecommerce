import { isValidElement } from 'react'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { orderService } from '@/contracts/order'
import { ExpenseDashboardPage } from '@/pages/profile/ProfilePages'
import { useAuthStore } from '@/stores/authStore'
import { renderWithProviders } from './test-utils'
import { makeJwt } from './test-utils'

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
        YAxis: ({ tick, tickFormatter }: { tick?: { fill?: string }; tickFormatter?: (value: number) => string }) => (
            <span data-testid="chart-y-axis" data-tick-fill={tick?.fill} data-tick-value={tickFormatter?.(1234.567)} />
        ),
        Tooltip: ({ contentStyle, itemStyle, labelStyle, formatter }: { contentStyle?: { color?: string; backgroundColor?: string; borderColor?: string }; itemStyle?: { color?: string }; labelStyle?: { color?: string }; formatter?: (value: number) => string }) => (
            <div
                data-testid="chart-tooltip"
                data-content-color={contentStyle?.color}
                data-content-background={contentStyle?.backgroundColor}
                data-content-border={contentStyle?.borderColor}
                data-item-color={itemStyle?.color}
                data-label-color={labelStyle?.color}
                data-formatted-value={formatter?.(1234.567)}
            />
        ),
        Pie: ({ data, label }: { data?: Array<{ fill?: string; name?: string; value?: number }>; label?: boolean | { fill?: string } | ((payload: { name?: string; value?: number }) => unknown) }) => {
            const renderedLabel = typeof label === 'function' ? label({ name: data?.[0]?.name, value: data?.[0]?.value }) : undefined
            return (
                <div
                    data-testid="pie-series"
                    data-label-fill={isValidElement(renderedLabel) ? renderedLabel.props.fill : typeof label === 'object' && label !== null && 'fill' in label ? label.fill : undefined}
                    data-label-text={isValidElement(renderedLabel) ? renderedLabel.props.children : typeof renderedLabel === 'string' ? renderedLabel : undefined}
                >
                    {data?.map((entry) => <span key={entry.fill} data-testid="chart-cell" data-fill={entry.fill} />)}
                </div>
            )
        },
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
        expect(container.querySelector('[data-content-color="var(--foreground)"]')).not.toBeNull()
        expect(container.querySelector('[data-content-background="var(--popover)"]')).not.toBeNull()
        expect(container.querySelector('[data-content-border="var(--border)"]')).not.toBeNull()
        expect(container.querySelector('[data-item-color="var(--foreground)"]')).not.toBeNull()
        expect(container.querySelector('[data-label-color="var(--foreground)"]')).not.toBeNull()
        expect(container.querySelectorAll('[data-tick-fill="var(--foreground)"]').length).toBe(2)
        expect(Array.from(container.querySelectorAll('[data-tick-value]')).some((element) => element.getAttribute('data-tick-value')?.includes('1.234,57'))).toBe(true)
        expect(Array.from(container.querySelectorAll('[data-formatted-value]')).some((element) => element.getAttribute('data-formatted-value')?.includes('1.234,57'))).toBe(true)
        expect(container.querySelector('[data-label-fill="#000000"]')).not.toBeNull()
        expect(container.querySelector('[data-label-text^="Smartphone-uri: "]')?.getAttribute('data-label-text')).toContain('1.000,00')
    })
})
