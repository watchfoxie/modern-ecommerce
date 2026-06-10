import { expect, test, type Page, type Route } from '@playwright/test'

const product = {
    id: 'product-iphone-15-pro',
    categoryId: 'category-smartphones',
    categorySlug: 'smartphones',
    slug: 'iphone-15-pro',
    name: 'Apple iPhone 15 Pro',
    brand: 'Apple',
    model: 'iPhone 15 Pro',
    country: 'US',
    price: 21999,
    promotionalPrice: 19999,
    currency: 'MDL',
    stock: 7,
    imageUrls: ['/static/assets/images/prod-images/smartphones/apple/apple-iphone-15-pro-1.png'],
    specs: {
        processor: 'A17 Pro',
        ram: '8 GB',
        storage: '256 GB',
    },
    isActive: true,
    createdAt: '2026-05-22T08:00:00Z',
    updatedAt: '2026-05-22T08:00:00Z',
}

const categories = [
    {
        id: 'category-smartphones',
        slug: 'smartphones',
        name: 'Smartphone-uri',
        description: 'Telefoane premium',
        parentId: null,
        imageUrl: '/static/assets/images/prod-images/categories-offers/generic-smartphones-1.png',
        displayOrder: 1,
        isActive: true,
    },
    {
        id: 'category-laptops',
        slug: 'laptops',
        name: 'Laptop-uri',
        description: 'Laptopuri premium',
        parentId: null,
        imageUrl: '/static/assets/images/prod-images/categories-offers/generic-laptops-1.png',
        displayOrder: 2,
        isActive: true,
    },
]

function base64Url(value: object) {
    return Buffer.from(JSON.stringify(value)).toString('base64url')
}

function makeJwt({
    email,
    roles,
    userId = 'user-1',
}: {
    email: string
    roles: string[]
    userId?: string
}) {
    return [
        base64Url({ alg: 'HS256', typ: 'JWT' }),
        base64Url({
            authId: 'auth-1',
            userId,
            email,
            roles,
            exp: Math.floor(Date.now() / 1000) + 3600,
        }),
        'signature',
    ].join('.')
}

async function fulfillJson(route: Route, payload: unknown, status = 200) {
    await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(payload),
    })
}

function paged<T>(data: T[]) {
    return {
        data,
        page: 0,
        size: 12,
        totalElements: data.length,
        totalPages: data.length > 0 ? 1 : 0,
        first: true,
        last: true,
    }
}

function emptyCart() {
    return {
        id: 'cart-1',
        userId: 'user-1',
        items: [],
        createdAt: '2026-05-22T09:00:00Z',
        updatedAt: '2026-05-22T09:00:00Z',
    }
}

function createOrder() {
    return {
        id: 'order-1',
        orderNumber: 'MEC-20260522-0001',
        userId: 'user-1',
        items: [
            {
                productId: product.id,
                name: product.name,
                brand: product.brand,
                imageUrl: product.imageUrls[0],
                quantity: 1,
                unitPrice: product.promotionalPrice,
            },
        ],
        deliveryAddress: {
            recipientName: 'Ana Popescu',
            recipientPhone: '+37360000000',
            city: 'Chisinau',
            district: 'Chisinau',
            street: 'Stefan cel Mare 1',
            postalCode: null,
        },
        payment: {
            method: 'CARD',
            status: 'PENDING',
            transactionId: null,
        },
        status: 'CREATED',
        totalAmount: product.promotionalPrice,
        currency: 'MDL',
        notes: null,
        createdAt: '2026-05-22T10:00:00Z',
        updatedAt: '2026-05-22T10:00:00Z',
    }
}

async function mockRouteRegressionApi(page: Page) {
    let currentCart = emptyCart()
    let currentOrders = [createOrder()]
    let currentCategories = [...categories]
    let currentEmail = 'administrator@gmail.com'
    let currentRoles = ['ROLE_USER', 'ROLE_ADMIN']
    let categoryCreateCalls = 0

    await page.route('**/api/category-service/v1/categories**', async (route) => {
        if (route.request().method() === 'POST') {
            categoryCreateCalls += 1
            const payload = route.request().postDataJSON() as {
                slug: string
                name: string
                description: string
                parentId?: string | null
                imageUrl?: string
                displayOrder: number
                isActive: boolean
            }
            const createdCategory = {
                id: `category-${payload.slug}`,
                slug: payload.slug,
                name: payload.name,
                description: payload.description,
                parentId: payload.parentId ?? null,
                imageUrl: payload.imageUrl,
                displayOrder: payload.displayOrder,
                isActive: payload.isActive,
            }
            currentCategories = [...currentCategories, createdCategory]
            await fulfillJson(route, createdCategory, 201)
            return
        }

        await fulfillJson(route, paged(currentCategories))
    })

    await page.route('**/api/product-service/v1/products**', (route) => {
        const url = new URL(route.request().url())

        if (url.pathname.endsWith('/search')) {
            return fulfillJson(route, paged([product]))
        }

        if (/\/products\/[^/]+$/.test(url.pathname)) {
            return fulfillJson(route, product)
        }

        const categorySlug = url.searchParams.get('categorySlug')
        if (categorySlug && categorySlug !== product.categorySlug) {
            return fulfillJson(route, paged([]))
        }

        return fulfillJson(route, paged([product]))
    })

    await page.route('**/api/auth-service/v1/sign-in', async (route) => {
        const payload = route.request().postDataJSON() as { email?: string } | undefined
        currentEmail = payload?.email ?? 'customer@example.com'
        currentRoles = currentEmail.includes('admin') ? ['ROLE_USER', 'ROLE_ADMIN'] : ['ROLE_USER']

        await fulfillJson(route, {
            accessToken: makeJwt({ email: currentEmail, roles: currentRoles }),
            refreshToken: 'refresh-token',
            tokenType: 'Bearer',
            expiresIn: 3600,
        })
    })

    await page.route('**/api/user-service/v1/users/me', (route) =>
        fulfillJson(route, {
            id: 'user-1',
            authId: 'auth-1',
            email: currentEmail,
            firstName: 'Ana',
            lastName: 'Popescu',
            phone: '+37360000000',
            birthDate: null,
            addresses: [
                {
                    label: 'Acasă',
                    street: 'Stefan cel Mare 1',
                    city: 'Chisinau',
                    district: 'Chisinau',
                    postalCode: null,
                    isDefault: true,
                },
            ],
            preferences: { language: 'ro', currency: 'MDL' },
            createdAt: '2026-05-22T08:00:00Z',
            updatedAt: '2026-05-22T08:00:00Z',
        }),
    )

    await page.route('**/api/cart-service/v1/carts/me/items', async (route) => {
        const payload = route.request().postDataJSON() as {
            productId: string
            quantity: number
            priceAtAdd: number
            productSnapshot: { name: string; imageUrl: string; categorySlug: string }
        }

        const existingItem = currentCart.items.find((item) => item.productId === payload.productId)
        if (existingItem) {
            existingItem.quantity += payload.quantity
        } else {
            currentCart.items.push({
                productId: payload.productId,
                quantity: payload.quantity,
                priceAtAdd: payload.priceAtAdd,
                productSnapshot: payload.productSnapshot,
            })
        }

        currentCart.updatedAt = '2026-05-22T09:05:00Z'
        await fulfillJson(route, currentCart)
    })

    await page.route('**/api/cart-service/v1/carts/me/items/*', async (route) => {
        const productId = route.request().url().split('/').pop() ?? ''

        if (route.request().method() === 'PUT') {
            const payload = route.request().postDataJSON() as { quantity: number }
            currentCart.items = currentCart.items.map((item) => (
                item.productId === productId ? { ...item, quantity: payload.quantity } : item
            ))
            currentCart.updatedAt = '2026-05-22T09:10:00Z'
            await fulfillJson(route, currentCart)
            return
        }

        currentCart.items = currentCart.items.filter((item) => item.productId !== productId)
        currentCart.updatedAt = '2026-05-22T09:11:00Z'
        await route.fulfill({ status: 204, body: '' })
    })

    await page.route('**/api/cart-service/v1/carts/me', async (route) => {
        if (route.request().method() === 'DELETE') {
            currentCart = emptyCart()
            await route.fulfill({ status: 204, body: '' })
            return
        }

        await fulfillJson(route, currentCart)
    })

    await page.route('**/api/order-service/v1/orders/all**', (route) => fulfillJson(route, paged(currentOrders)))

    await page.route('**/api/order-service/v1/orders/*/status', async (route) => {
        const orderId = route.request().url().split('/').slice(-2)[0]
        const payload = route.request().postDataJSON() as { status: string }
        currentOrders = currentOrders.map((order) => (
            order.id === orderId ? { ...order, status: payload.status } : order
        ))
        const updatedOrder = currentOrders.find((order) => order.id === orderId)
        await fulfillJson(route, updatedOrder)
    })

    await page.route('**/api/order-service/v1/orders**', async (route) => {
        if (route.request().method() === 'POST') {
            const nextOrder = {
                ...createOrder(),
                id: `order-${currentOrders.length + 1}`,
                orderNumber: `MEC-20260522-000${currentOrders.length + 1}`,
            }
            currentOrders = [nextOrder, ...currentOrders]
            currentCart = emptyCart()
            await fulfillJson(route, {
                status: 'ACCEPTED',
                orderId: nextOrder.id,
                orderNumber: nextOrder.orderNumber,
                message: 'Order accepted.',
            }, 202)
            return
        }

        await fulfillJson(route, paged(currentOrders))
    })

    return {
        getCategoryCreateCalls: () => categoryCreateCalls,
    }
}

test('route regression covers public, guest, protected, and admin flows in sequence', async ({ page }) => {
    const apiState = await mockRouteRegressionApi(page)

    await page.goto('/home')
    await expect(page.getByRole('heading', { name: 'MEc' }).first()).toBeVisible()
    await expect(page.getByText('Apple iPhone 15 Pro').first()).toBeVisible()

    await page.goto('/categories')
    await expect(page.getByText('Apple iPhone 15 Pro').first()).toBeVisible()

    await page.goto('/categories/smartphones')
    await expect(page.getByRole('heading', { name: 'Smartphone-uri' })).toBeVisible()

    await page.goto('/categories/offers')
    await expect(page.getByText('Apple iPhone 15 Pro').first()).toBeVisible()

    await page.goto('/search?q=iphone')
    await expect(page.getByRole('heading', { name: 'Căutare' })).toBeVisible()
    await expect(page.getByText('Rezultate pentru "iphone"')).toBeVisible()

    await page.goto('/support')
    await expect(page.getByText('Cum te putem ajuta?')).toBeVisible()

    await page.goto('/contacts')
    await expect(page.getByText('Contacte MEc')).toBeVisible()

    await page.goto('/about')
    await expect(page.getByText('Garanție')).toBeVisible()

    await page.goto('/does-not-exist')
    await expect(page.locator('main')).toBeVisible()

    await page.goto('/profile/sign-up')
    await expect(page.locator('main').getByText('Înregistrare')).toBeVisible()

    await page.goto('/profile/password-reset')
    await expect(page.locator('main').getByText('Resetare parolă')).toBeVisible()

    await page.goto('/cart')
    await expect(page).toHaveURL(/\/profile\/sign-in$/)
    await expect(page.locator('main').getByText('Autentificare')).toBeVisible()

    await page.locator('main form input').nth(0).fill('administrator@gmail.com')
    await page.locator('main form input[type="password"]').fill('Administrator1234*')
    await page.getByRole('button', { name: 'Autentifică-te' }).click()

    await expect(page).toHaveURL(/\/cart$/)
    await expect(page.getByText('Coșul este gol')).toBeVisible()
    await page.getByRole('link', { name: 'Descoperă produse' }).click()

    await expect(page).toHaveURL(/\/categories\/smartphones$/)
    await page.getByRole('button', { name: 'Adaugă în coș' }).first().click()

    await page.goto('/cart')
    await expect(page.getByRole('heading', { name: 'Coșul de cumpărături' })).toBeVisible()
    await expect(page.getByText('Apple iPhone 15 Pro')).toBeVisible()
    await page.getByRole('link', { name: 'Continuă spre livrare' }).click()

    await expect(page.getByRole('heading', { name: 'Livrare' })).toBeVisible()
    await expect(page.locator('#recipientName')).toHaveValue('Ana Popescu')
    await expect(page.locator('#city')).toHaveValue('Chisinau')
    await page.getByRole('button', { name: 'Continuă' }).click()

    await expect(page.getByRole('heading', { name: 'Date personale' })).toBeVisible()
    await expect(page.locator('#email')).toHaveValue('administrator@gmail.com')
    await page.getByRole('button', { name: 'Continuă spre plată' }).click()

    await expect(page.getByRole('heading', { name: 'Plată' })).toBeVisible()
    await expect(page.getByText('Total checkout')).toBeVisible()
    await page.getByRole('button', { name: 'Plasează comanda' }).click()

    await expect(page).toHaveURL(/\/profile\/account\/order-history$/)
    await expect(page.getByRole('heading', { name: 'Istoric comenzi' })).toBeVisible()
    await expect(page.getByRole('cell', { name: 'MEC-20260522-0002' })).toBeVisible()

    await page.getByRole('link', { name: 'Date personale' }).click()
    await expect(page).toHaveURL(/\/profile\/account\/personal$/)

    await page.getByRole('link', { name: 'Cheltuieli' }).click()
    await expect(page).toHaveURL(/\/profile\/account\/expense-dashboard$/)

    await page.getByRole('link', { name: 'Administrare' }).click()
    await expect(page).toHaveURL(/\/profile\/account\/admin$/)
    await expect(page.getByRole('heading', { name: 'Consolă administrare' })).toBeVisible()

    await page.getByRole('tab', { name: /Categorii/i }).click()
    await page.getByRole('button', { name: 'Adaugă categorie' }).click()
    const categoryDialog = page.getByRole('dialog')
    await expect(categoryDialog).toBeVisible()
    await categoryDialog.locator('input[name="slug"]').fill('gaming-laptops')
    await categoryDialog.locator('input[name="name"]').fill('Gaming laptops')
    await categoryDialog.locator('textarea[name="description"]').fill('Laptopuri de gaming.')
    await categoryDialog.locator('input[name="imageUrl"]').fill('/static/assets/images/prod-images/categories-offers/generic-laptops-1.png')
    await categoryDialog.getByRole('button', { name: 'Creează' }).click()
    await expect(categoryDialog).not.toBeVisible()
    expect(apiState.getCategoryCreateCalls()).toBe(1)
    await expect(page.getByText('Gaming laptops')).toBeVisible()
})