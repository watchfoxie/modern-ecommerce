import axios from 'axios'
import MockAdapter from 'axios-mock-adapter'
import { afterEach, describe, expect, it } from 'vitest'
import { API_BASE_URL } from '@/config/api'
import { api } from '@/config/axios'
import { productService } from '@/contracts/product'
import { useAuthStore } from '@/stores/authStore'
import { makeJwt } from './test-utils'

describe('typed API layer and Axios interceptors', () => {
  const apiMock = new MockAdapter(api)
  const axiosMock = new MockAdapter(axios)

  afterEach(() => {
    apiMock.reset()
    axiosMock.reset()
  })

  it('uses the gateway-relative service URL convention and attaches Bearer tokens', async () => {
    useAuthStore.getState().setAuth({
      accessToken: makeJwt({ sub: 'customer@example.com', exp: Math.floor(Date.now() / 1000) + 3600 }),
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })

    apiMock.onGet('/product-service/products').reply((config) => [
      200,
      {
        authorization: config.headers?.Authorization,
        content: [],
        page: 0,
        size: 12,
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
      },
    ])

    const response = await productService.list({ page: 0, size: 12 })

    expect(apiMock.history.get[0].url).toBe('/product-service/products')
    expect(response.content).toEqual([])
    expect((await api.get('/product-service/products')).data.authorization).toMatch(/^Bearer /)
  })

  it('refreshes an expired access token once and retries the protected request', async () => {
    const expiredToken = makeJwt({ sub: 'customer@example.com', exp: Math.floor(Date.now() / 1000) - 10 })
    const refreshedToken = makeJwt({ sub: 'customer@example.com', exp: Math.floor(Date.now() / 1000) + 3600 })
    useAuthStore.getState().setAuth({
      accessToken: expiredToken,
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })

    apiMock
      .onGet('/product-service/products')
      .replyOnce(401, { title: 'Unauthorized', status: 401 })
      .onGet('/product-service/products')
      .reply((config) => [200, { authorization: config.headers?.Authorization, content: [], page: 0, size: 0, totalElements: 0, totalPages: 0, first: true, last: true }])

    axiosMock.onPost(`${API_BASE_URL}/auth-service/token/refresh`).reply(200, {
      accessToken: refreshedToken,
      refreshToken: 'refresh-token-2',
      tokenType: 'Bearer',
      expiresIn: 3600,
    })

    const response = await productService.list()

    expect(axiosMock.history.post).toHaveLength(1)
    expect(response.content).toEqual([])
    expect(apiMock.history.get).toHaveLength(2)
    expect(useAuthStore.getState().accessToken).toBe(refreshedToken)
  })
})
