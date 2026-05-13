import express from 'express'
import helmet from 'helmet'
import path from 'node:path'
import process from 'node:process'
import { Readable } from 'node:stream'
import { fileURLToPath } from 'node:url'
import { getStaticResponseHeaders } from './staticAssets.js'

const scriptPath = fileURLToPath(import.meta.url)
const scriptDir = path.dirname(scriptPath)
const distDir = path.join(scriptDir, 'dist')
const port = Number.parseInt(process.env.VITE_PORT ?? '4173', 10)
const apiBaseUrl = normalizeApiBaseUrl(process.env.VITE_API_BASE_URL ?? '/api')
const proxyTarget = normalizeProxyTarget(process.env.VITE_API_PROXY_TARGET ?? 'http://api-gateway:8080')
const app = express()

function normalizeApiBaseUrl(value) {
  const trimmed = value.trim()
  if (trimmed === '' || trimmed === '/') {
    return '/api'
  }
  return (trimmed.startsWith('/') ? trimmed : `/${trimmed}`).replace(/\/+$/, '') || '/api'
}

function normalizeProxyTarget(value) {
  const trimmed = value.trim()
  return new URL(trimmed.endsWith('/') ? trimmed : `${trimmed}/`)
}

function copyRequestHeaders(request) {
  const headers = new Headers()
  for (const [headerName, headerValue] of Object.entries(request.headers)) {
    if (!headerValue || ['host', 'connection', 'content-length'].includes(headerName.toLowerCase())) {
      continue
    }
    if (Array.isArray(headerValue)) {
      headerValue.forEach((value) => headers.append(headerName, value))
    } else {
      headers.set(headerName, headerValue)
    }
  }
  return headers
}

async function proxyRequest(request, response) {
  const targetUrl = new URL(request.originalUrl, proxyTarget)
  const hasRequestBody = request.method !== 'GET' && request.method !== 'HEAD'
  const upstreamResponse = await fetch(targetUrl, {
    method: request.method,
    headers: copyRequestHeaders(request),
    body: hasRequestBody ? request : undefined,
    duplex: hasRequestBody ? 'half' : undefined,
    redirect: 'manual',
  })

  response.status(upstreamResponse.status)
  upstreamResponse.headers.forEach((value, headerName) => {
    if (headerName.toLowerCase() !== 'connection') {
      response.setHeader(headerName, value)
    }
  })

  if (!upstreamResponse.body || request.method === 'HEAD') {
    response.end()
    return
  }

  Readable.fromWeb(upstreamResponse.body).pipe(response)
}

app.disable('x-powered-by')
app.use(
  helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        scriptSrc: ["'self'"],
        styleSrc: ["'self'", "'unsafe-inline'"],
        imgSrc: ["'self'", 'data:', 'blob:'],
        fontSrc: ["'self'", 'data:'],
        connectSrc: ["'self'"],
        objectSrc: ["'none'"],
        frameAncestors: ["'none'"],
      },
    },
    crossOriginResourcePolicy: { policy: 'same-origin' },
    referrerPolicy: { policy: 'strict-origin-when-cross-origin' },
  }),
)

app.use(apiBaseUrl, async (request, response, next) => {
  try {
    await proxyRequest(request, response)
  } catch (error) {
    next(error)
  }
})

app.use(
  express.static(distDir, {
    setHeaders(response, filePath) {
      const headers = getStaticResponseHeaders(filePath)
      for (const [headerName, headerValue] of Object.entries(headers)) {
        response.setHeader(headerName, headerValue)
      }
    },
  }),
)

app.get('/{*splat}', (_request, response) => {
  response.sendFile(path.join(distDir, 'index.html'))
})

app.use((error, _request, response, _next) => {
  const message = error instanceof Error ? error.message : String(error)
  console.error(message)
  response.status(500).type('text/plain; charset=utf-8').send('Internal Server Error')
})

const server = app.listen(port, '0.0.0.0', () => {
  console.log(`web container listening on port ${port}`)
  console.log(`Serving static files from ${distDir}`)
  console.log(`Proxying ${apiBaseUrl} -> ${proxyTarget.href}`)
})

function shutdown(signal) {
  server.close(() => {
    console.log(`web container stopped (${signal}).`)
    process.exit(0)
  })
}

process.on('SIGINT', () => shutdown('SIGINT'))
process.on('SIGTERM', () => shutdown('SIGTERM'))
