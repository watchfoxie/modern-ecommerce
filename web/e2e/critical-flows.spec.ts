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

const cart = {
  id: 'cart-1',
  userId: 'user-1',
  createdAt: '2026-05-22T09:00:00Z',
  updatedAt: '2026-05-22T09:00:00Z',
  items: [
    {
      productId: product.id,
      quantity: 1,
      priceAtAdd: product.promotionalPrice,
      productSnapshot: {
        name: product.name,
        imageUrl: product.imageUrls[0],
        categorySlug: product.categorySlug,
      },
    },
  ],
}

const order = {
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

function base64Url(value: object) {
  return Buffer.from(JSON.stringify(value)).toString('base64url')
}

function makeJwt() {
  return [
    base64Url({ alg: 'HS256', typ: 'JWT' }),
    base64Url({
      authId: 'auth-1',
      userId: 'user-1',
      email: 'customer@example.com',
      roles: ['ROLE_USER'],
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

function paged<T>(content: T[]) {
  return {
    content,
    page: 0,
    size: 12,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    first: true,
    last: true,
  }
}

async function mockApi(page: Page) {
  await page.route('**/api/category-service/categories**', (route) =>
    fulfillJson(route, [
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
    ]),
  )

  await page.route('**/api/product-service/products**', (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/search')) {
      return fulfillJson(route, paged([product]))
    }

    if (/\/products\/[^/]+$/.test(url.pathname)) {
      return fulfillJson(route, product)
    }

    return fulfillJson(route, paged([product]))
  })

  await page.route('**/api/auth-service/sign-in', (route) =>
    fulfillJson(route, {
      accessToken: makeJwt(),
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    }),
  )

  await page.route('**/api/user-service/users/me**', (route) =>
    fulfillJson(route, {
      id: 'user-1',
      authId: 'auth-1',
      email: 'customer@example.com',
      firstName: 'Ana',
      lastName: 'Popescu',
      phone: '+37360000000',
      birthDate: null,
      addresses: [],
      preferences: { language: 'ro', currency: 'MDL' },
      createdAt: '2026-05-22T08:00:00Z',
      updatedAt: '2026-05-22T08:00:00Z',
    }),
  )

  await page.route('**/api/cart-service/carts/me**', (route) => fulfillJson(route, cart))

  await page.route('**/api/order-service/orders**', (route) => {
    if (route.request().method() === 'POST') {
      return fulfillJson(route, {
        status: 'ACCEPTED',
        orderId: order.id,
        orderNumber: order.orderNumber,
        message: 'Order accepted.',
      }, 202)
    }

    return fulfillJson(route, paged([order]))
  })
}

test('public routes load catalog and search data through the gateway contract', async ({ page }) => {
  await mockApi(page)

  await page.goto('/home')
  await expect(page.getByRole('heading', { name: 'MEc' }).first()).toBeVisible()
  await expect(page.getByText('Apple iPhone 15 Pro').first()).toBeVisible()

  await page.goto('/categories/smartphones')
  await expect(page.getByRole('heading', { name: 'Smartphone-uri' })).toBeVisible()
  await expect(page.getByText('Apple iPhone 15 Pro')).toBeVisible()

  await page.goto('/search?q=iphone')
  await expect(page.getByRole('heading', { name: 'Căutare' })).toBeVisible()
  await expect(page.getByText('Rezultate pentru "iphone"')).toBeVisible()
  await expect(page.getByText('Apple iPhone 15 Pro')).toBeVisible()
})

test('authenticated checkout reaches order history without touching internal endpoints', async ({ page }) => {
  await mockApi(page)

  const requestedUrls: string[] = []
  page.on('request', (request) => requestedUrls.push(request.url()))

  await page.goto('/cart')
  await expect(page).toHaveURL(/\/profile\/sign-in$/)

  await page.locator('main form input').nth(0).fill('customer@example.com')
  await page.locator('main form input[type="password"]').fill('Password123!')
  await page.getByRole('button', { name: 'Autentifică-te' }).click()

  await expect(page).toHaveURL(/\/cart$/)
  await expect(page.getByRole('heading', { name: 'Coșul de cumpărături' })).toBeVisible()
  await page.getByRole('link', { name: 'Continuă spre livrare' }).click()

  await page.locator('#recipientName').fill('Ana Popescu')
  await page.locator('#recipientPhone').fill('+37360000000')
  await page.locator('#city').fill('Chisinau')
  await page.locator('#district').fill('Chisinau')
  await page.locator('#street').fill('Stefan cel Mare 1')
  await page.getByRole('button', { name: 'Continuă' }).click()

  await expect(page.getByRole('heading', { name: 'Date personale' })).toBeVisible()
  await page.getByRole('button', { name: 'Continuă spre plată' }).click()

  await expect(page.getByRole('heading', { name: 'Plată' })).toBeVisible()
  await page.getByRole('button', { name: 'Plasează comanda' }).click()

  await expect(page).toHaveURL(/\/profile\/account\/order-history$/)
  await expect(page.getByRole('heading', { name: 'Istoric comenzi' })).toBeVisible()
  await expect(page.getByRole('cell', { name: order.orderNumber })).toBeVisible()
  expect(requestedUrls.some((url) => url.includes('/internal/notifications'))).toBe(false)
})
