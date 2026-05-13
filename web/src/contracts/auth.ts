import { api } from '@/config/axios'
import type { IsoDateTimeString } from './common'

export interface AuthSignUpRequest {
  email: string
  password: string
  firstName: string
  lastName: string
}

export interface AuthSignInRequest {
  email: string
  password: string
}

export interface AuthTokenRefreshRequest {
  refreshToken: string
}

export interface AuthPasswordResetRequest {
  email: string
}

export interface AuthPasswordResetConfirmRequest {
  token: string
  newPassword: string
}

export interface AuthUserDto {
  id: string
  email: string
  status: string
  createdAt: IsoDateTimeString
}

export interface AuthTokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer' | string
  expiresIn: number
}

export interface AuthMessageResponse {
  message: string
}

export const authService = {
  async signUp(request: AuthSignUpRequest): Promise<AuthUserDto> {
    const response = await api.post<AuthUserDto>('/auth-service/sign-up', request)
    return response.data
  },

  async signIn(request: AuthSignInRequest): Promise<AuthTokenResponse> {
    const response = await api.post<AuthTokenResponse>('/auth-service/sign-in', request)
    return response.data
  },

  async signOut(): Promise<void> {
    await api.post('/auth-service/sign-out')
  },

  async refreshToken(request: AuthTokenRefreshRequest): Promise<AuthTokenResponse> {
    const response = await api.post<AuthTokenResponse>('/auth-service/token/refresh', request)
    return response.data
  },

  async requestPasswordReset(request: AuthPasswordResetRequest): Promise<void> {
    await api.post('/auth-service/password-reset/request', request)
  },

  async confirmPasswordReset(request: AuthPasswordResetConfirmRequest): Promise<void> {
    await api.post('/auth-service/password-reset/confirm', request)
  },
}
