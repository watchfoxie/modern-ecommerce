const defaultApiBaseUrl = '/api'

const configuredApiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? defaultApiBaseUrl).trim()

export const API_BASE_URL =
  configuredApiBaseUrl === ''
    ? defaultApiBaseUrl
    : configuredApiBaseUrl.replace(/\/+$/, '') || defaultApiBaseUrl

export const buildGatewayUrl = (serviceName: string) => `${API_BASE_URL}/${serviceName}`
