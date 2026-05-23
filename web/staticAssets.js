import path from 'node:path'

export const STATIC_URL_PREFIX = '/static/'

const staticContentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.gif', 'image/gif'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.jpeg', 'image/jpeg'],
  ['.jpg', 'image/jpeg'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.map', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.txt', 'text/plain; charset=utf-8'],
  ['.webp', 'image/webp'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2'],
])

export function getStaticContentType(filePath) {
  return staticContentTypes.get(path.extname(filePath).toLowerCase()) ?? 'application/octet-stream'
}

export function getStaticResponseHeaders(filePath) {
  const immutableAsset = filePath.includes(`${path.sep}static${path.sep}assets${path.sep}`)
  return {
    'Content-Type': getStaticContentType(filePath),
    'X-Content-Type-Options': 'nosniff',
    'Cache-Control': immutableAsset ? 'public, max-age=31536000, immutable' : 'no-cache',
  }
}
