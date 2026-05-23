import { api } from '@/config/axios'
import type { CurrencyCode, IsoDateTimeString, PagedResponseDto, PageRequestParams } from './common'

export interface ProductDto {
  id: string
  categoryId?: string | null
  categorySlug: string
  slug: string
  name: string
  brand: string
  model: string
  country: string
  price: number
  promotionalPrice?: number | null
  currency: CurrencyCode
  stock: number
  imageUrls: string[]
  specs: Record<string, string>
  isActive: boolean
  createdAt?: IsoDateTimeString | null
  updatedAt?: IsoDateTimeString | null
}

export interface ProductListParams extends PageRequestParams {
  categorySlug?: string
  hasPromotion?: boolean
}

export interface ProductSearchParams extends Pick<PageRequestParams, 'page' | 'size'> {
  q: string
}

export interface UpsertProductRequest {
  categoryId: string
  categorySlug: string
  slug: string
  name: string
  brand: string
  model: string
  country: string
  price: number
  promotionalPrice?: number | null
  currency: CurrencyCode
  stock: number
  imageUrls: string[]
  specs: Record<string, string>
  isActive: boolean
}

export const productService = {
  async list(params?: ProductListParams): Promise<PagedResponseDto<ProductDto>> {
    const response = await api.get<PagedResponseDto<ProductDto>>('/product-service/v1/products', { params })
    return response.data
  },

  async getBySlug(slug: string): Promise<ProductDto> {
    const response = await api.get<ProductDto>(`/product-service/v1/products/${slug}`)
    return response.data
  },

  async search(params: ProductSearchParams): Promise<PagedResponseDto<ProductDto>> {
    const response = await api.get<PagedResponseDto<ProductDto>>('/product-service/v1/products/search', { params })
    return response.data
  },

  async create(request: UpsertProductRequest): Promise<ProductDto> {
    const response = await api.post<ProductDto>('/product-service/v1/products', request)
    return response.data
  },

  async update(slug: string, request: UpsertProductRequest): Promise<ProductDto> {
    const response = await api.put<ProductDto>(`/product-service/v1/products/${slug}`, request)
    return response.data
  },

  async delete(slug: string): Promise<void> {
    await api.delete(`/product-service/v1/products/${slug}`)
  },
}
