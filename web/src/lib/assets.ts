export const STATIC_PREFIX = '/static/'

export function assetUrl(path?: string | null): string {
  if (!path) {
    return '/static/assets/images/prod-images/categories-offers/generic-smartphones-1.png'
  }

  if (/^https?:\/\//i.test(path) || path.startsWith(STATIC_PREFIX)) {
    return path
  }

  const normalized = `/${path.replace(/^\/+/, '')}`
  return normalized.startsWith(STATIC_PREFIX)
    ? normalized
    : '/static/assets/images/prod-images/categories-offers/generic-smartphones-1.png'
}

export function firstAsset(paths?: string[] | null): string {
  return assetUrl(paths?.find(Boolean))
}
