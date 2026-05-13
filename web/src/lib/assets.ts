export const STATIC_PREFIX = '/static/'

export function assetUrl(path?: string | null): string {
  if (!path) {
    return '/static/assets/images/prod-images/categories-offers/generic-smartphones-1.png'
  }

  if (/^https?:\/\//i.test(path) || path.startsWith('/')) {
    return path
  }

  return `/${path.replace(/^\/+/, '')}`
}

export function firstAsset(paths?: string[] | null): string {
  return assetUrl(paths?.find(Boolean))
}
