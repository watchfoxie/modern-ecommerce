import { api } from '@/config/axios'
import type { IsoDateTimeString } from './common'

export interface CategoryDto {
  id: string
  slug: string
  name: string
  description?: string | null
  parentId?: string | null
  imageUrl?: string | null
  displayOrder: number
  isActive: boolean
  createdAt?: IsoDateTimeString | null
  updatedAt?: IsoDateTimeString | null
}

export interface CategoryListParams {
  parentId?: string
}

export interface UpsertCategoryRequest {
  slug: string
  name: string
  description?: string
  parentId?: string | null
  imageUrl?: string
  displayOrder: number
  isActive: boolean
}

export const categoryService = {
  async list(params?: CategoryListParams): Promise<CategoryDto[]> {
    const response = await api.get<CategoryDto[]>('/category-service/categories', { params })
    return response.data
  },

  async getBySlug(slug: string): Promise<CategoryDto> {
    const response = await api.get<CategoryDto>(`/category-service/categories/${slug}`)
    return response.data
  },

  async create(request: UpsertCategoryRequest): Promise<CategoryDto> {
    const response = await api.post<CategoryDto>('/category-service/categories', request)
    return response.data
  },

  async update(slug: string, request: UpsertCategoryRequest): Promise<CategoryDto> {
    const response = await api.put<CategoryDto>(`/category-service/categories/${slug}`, request)
    return response.data
  },

  async delete(slug: string): Promise<void> {
    await api.delete(`/category-service/categories/${slug}`)
  },
}
