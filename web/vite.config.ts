import path from 'node:path'
import { createReadStream } from 'node:fs'
import type { IncomingMessage, ServerResponse } from 'node:http'
import { readFile, readdir, stat } from 'node:fs/promises'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, loadEnv, type Plugin, type ViteDevServer } from 'vite'
import react from '@vitejs/plugin-react'
import { STATIC_URL_PREFIX, getStaticResponseHeaders } from './staticAssets.js'

type NextFunction = (error?: unknown) => void

const collectFiles = async (directory: string): Promise<string[]> => {
  const entries = await readdir(directory, { withFileTypes: true })
  const nestedFiles = await Promise.all(
    entries.map(async (entry) => {
      const entryPath = path.join(directory, entry.name)

      if (entry.isDirectory()) {
        return collectFiles(entryPath)
      }

      return entry.isFile() ? [entryPath] : []
    }),
  )

  return nestedFiles.flat()
}

const createRepoStaticAssetsPlugin = (staticSourceDir: string): Plugin => ({
  name: 'repo-static-assets',
  configureServer(server: ViteDevServer) {
    server.middlewares.use(
      async (
        request: IncomingMessage,
        response: ServerResponse<IncomingMessage>,
        next: NextFunction,
      ) => {
        const pathname = request.url ? new URL(request.url, 'http://127.0.0.1').pathname : ''

        if (!pathname.startsWith(STATIC_URL_PREFIX)) {
          next()
          return
        }

        if (request.method && request.method !== 'GET' && request.method !== 'HEAD') {
          next()
          return
        }

        const relativePath = decodeURIComponent(pathname.slice(STATIC_URL_PREFIX.length))
        const candidatePath = path.normalize(path.join(staticSourceDir, relativePath))

        if (!candidatePath.startsWith(staticSourceDir)) {
          response.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' })
          response.end('Forbidden')
          return
        }

        const candidateStats = await stat(candidatePath).catch(() => null)
        if (!candidateStats?.isFile()) {
          next()
          return
        }

        response.writeHead(200, getStaticResponseHeaders(candidatePath))

        if (request.method === 'HEAD') {
          response.end()
          return
        }

        createReadStream(candidatePath).pipe(response)
      },
    )
  },
  async generateBundle() {
    const files = await collectFiles(staticSourceDir)

    await Promise.all(
      files.map(async (filePath) => {
        const relativePath = path.relative(staticSourceDir, filePath).split(path.sep).join('/')
        this.emitFile({
          type: 'asset',
          fileName: `static/${relativePath}`,
          source: await readFile(filePath),
        })
      }),
    )
  },
})

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, path.resolve(__dirname, '..'), 'VITE_')
  const port = Number.parseInt(env.VITE_PORT ?? '5173', 10)
  const proxyTarget = env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'
  const apiBaseUrl = `/${(env.VITE_API_BASE_URL ?? 'api').replace(/^\/+|\/+$/g, '') || 'api'}`
  const repoStaticDir = path.resolve(__dirname, '..', 'static')

  return {
    envDir: '..',
    plugins: [react(), tailwindcss(), createRepoStaticAssetsPlugin(repoStaticDir)],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port,
      proxy: {
        [apiBaseUrl]: {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
