import { api } from '@/config/axios'
import type { IsoDateString, IsoDateTimeString } from './common'

export interface UserAddressDto {
  label?: string | null
  street: string
  city: string
  district: string
  postalCode?: string | null
  isDefault: boolean
}

export interface UserPreferencesDto {
  language?: string
  currency?: string
}

export interface UserProfileDto {
  id: string
  authId: string
  email: string
  firstName: string
  lastName: string
  phone?: string | null
  birthDate?: IsoDateString | null
  addresses?: UserAddressDto[] | null
  preferences?: UserPreferencesDto | null
  createdAt: IsoDateTimeString
  updatedAt: IsoDateTimeString
}

export interface UpdateUserProfileRequest {
  firstName: string
  lastName: string
  phone?: string | null
  birthDate?: IsoDateString | null
  preferences?: Partial<UserPreferencesDto>
}

export interface UpsertUserAddressRequest {
  label?: string | null
  street: string
  city: string
  district: string
  postalCode?: string | null
  isDefault: boolean
}

export const userService = {
  async getMe(): Promise<UserProfileDto> {
    const response = await api.get<UserProfileDto>('/user-service/v1/users/me')
    return response.data
  },

  async updateMe(request: UpdateUserProfileRequest): Promise<UserProfileDto> {
    const response = await api.put<UserProfileDto>('/user-service/v1/users/me', request)
    return response.data
  },

  async addAddress(request: UpsertUserAddressRequest): Promise<UserProfileDto> {
    const response = await api.post<UserProfileDto>('/user-service/v1/users/me/addresses', request)
    return response.data
  },

  async updateAddress(addressIndex: number, request: UpsertUserAddressRequest): Promise<UserProfileDto> {
    const response = await api.put<UserProfileDto>(`/user-service/v1/users/me/addresses/${addressIndex}`, request)
    return response.data
  },

  async deleteAddress(addressIndex: number): Promise<void> {
    await api.delete(`/user-service/v1/users/me/addresses/${addressIndex}`)
  },

}
