import { api } from '@/config/axios'
import type { IsoDateTimeString } from './common'

export interface CartProductSnapshotDto {
  name: string
  imageUrl: string
  categorySlug: string
}

export interface CartItemDto {
  productId: string
  quantity: number
  priceAtAdd: number
  productSnapshot: CartProductSnapshotDto
}

export interface CartDto {
  id: string
  userId: string
  items: CartItemDto[]
  createdAt: IsoDateTimeString
  updatedAt: IsoDateTimeString
}

export interface AddCartItemRequest {
  productId: string
  quantity: number
  priceAtAdd: number
  productSnapshot: CartProductSnapshotDto
}

export interface UpdateCartItemRequest {
  quantity: number
}

export const cartService = {
  async getMe(): Promise<CartDto> {
    const response = await api.get<CartDto>('/cart-service/v1/carts/me')
    return response.data
  },

  async addItem(request: AddCartItemRequest): Promise<CartDto> {
    const response = await api.post<CartDto>('/cart-service/v1/carts/me/items', request)
    return response.data
  },

  async updateItem(productId: string, request: UpdateCartItemRequest): Promise<CartDto> {
    const response = await api.put<CartDto>(`/cart-service/v1/carts/me/items/${productId}`, request)
    return response.data
  },

  async removeItem(productId: string): Promise<void> {
    await api.delete(`/cart-service/v1/carts/me/items/${productId}`)
  },

  async clear(): Promise<void> {
    await api.delete('/cart-service/v1/carts/me')
  },
}
