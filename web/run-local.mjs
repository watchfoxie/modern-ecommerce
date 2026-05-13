import { existsSync } from 'node:fs'
import { mkdir, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

const args = process.argv.slice(2)
const options = {
  mode: 'local',
  shutdownSignalFile: '',
  pidFile: '',
}

const readOptionValue = (currentIndex, flagName) => {
  const nextValue = args[currentIndex + 1]
  if (!nextValue) {
    throw new Error(`Missing value for ${flagName}.`)
  }

  return nextValue
}

for (let index = 0; index < args.length; index += 1) {
  const argument = args[index]

  if (argument === '--mode') {
    options.mode = readOptionValue(index, '--mode')
    index += 1
    continue
  }

  if (argument.startsWith('--mode=')) {
    options.mode = argument.slice('--mode='.length)
    continue
  }

  if (argument === '--shutdown-signal-file') {
    options.shutdownSignalFile = readOptionValue(index, '--shutdown-signal-file')
    index += 1
    continue
  }

  if (argument.startsWith('--shutdown-signal-file=')) {
    options.shutdownSignalFile = argument.slice('--shutdown-signal-file='.length)
    continue
  }

  if (argument === '--pid-file') {
    options.pidFile = readOptionValue(index, '--pid-file')
    index += 1
    continue
  }

  if (argument.startsWith('--pid-file=')) {
    options.pidFile = argument.slice('--pid-file='.length)
    continue
  }

  throw new Error(`Unknown argument: ${argument}`)
}

const scriptPath = fileURLToPath(import.meta.url)
const scriptDir = path.dirname(scriptPath)
const vitePackagePath = path.join(scriptDir, 'node_modules', 'vite', 'package.json')
const resolvedViteMode = options.mode === 'local' ? 'development' : options.mode

if (!existsSync(vitePackagePath)) {
  throw new Error(
    `Could not find Vite under '${path.dirname(vitePackagePath)}'. Run 'npm install' in '${scriptDir}' first.`,
  )
}

let server
let shutdownTimer
let stdinListener
let shutdownStarted = false

const writePidFileIfNeeded = async () => {
  if (!options.pidFile) {
    return
  }

  await mkdir(path.dirname(options.pidFile), { recursive: true })
  await writeFile(options.pidFile, String(process.pid), 'utf8')
}

const removePidFileIfNeeded = async () => {
  if (!options.pidFile) {
    return
  }

  await rm(options.pidFile, { force: true })
}

const teardownInput = () => {
  if (!process.stdin.isTTY || !stdinListener) {
    return
  }

  process.stdin.removeListener('data', stdinListener)
  process.stdin.setRawMode(false)
  process.stdin.pause()
  stdinListener = undefined
}

const shutdown = async (reason) => {
  if (shutdownStarted) {
    return
  }

  shutdownStarted = true

  if (shutdownTimer) {
    clearInterval(shutdownTimer)
    shutdownTimer = undefined
  }

  teardownInput()

  try {
    if (server) {
      await server.close()
    }
  }
  finally {
    await removePidFileIfNeeded()
  }

  console.log(`web stopped gracefully (${reason}).`)
  process.exit(0)
}

process.on('SIGINT', () => {
  void shutdown('SIGINT')
})

process.on('SIGTERM', () => {
  void shutdown('SIGTERM')
})

process.on('uncaughtException', async (error) => {
  console.error(error instanceof Error ? error.message : String(error))
  await removePidFileIfNeeded()
  process.exit(1)
})

process.on('unhandledRejection', async (reason) => {
  console.error(reason instanceof Error ? reason.message : String(reason))
  await removePidFileIfNeeded()
  process.exit(1)
})

try {
  await writePidFileIfNeeded()

  server = await createServer({
    root: scriptDir,
    configFile: path.join(scriptDir, 'vite.config.ts'),
    mode: resolvedViteMode,
  })

  await server.listen()

  console.log(
    `web is running for profile '${options.mode}' using Vite mode '${resolvedViteMode}'. Press Ctrl+C to stop gracefully.`,
  )
  console.log('Press Q or Enter if your terminal does not forward Ctrl+C as input.')
  server.printUrls()

  if (process.stdin.isTTY) {
    process.stdin.setRawMode(true)
    process.stdin.resume()
    process.stdin.setEncoding('utf8')
    stdinListener = (chunk) => {
      const key = chunk.toString()

      if (key === '\u0003' || key === '\r' || key === '\n' || key.toLowerCase() === 'q') {
        void shutdown('interactive input')
      }
    }

    process.stdin.on('data', stdinListener)
  }

  if (options.shutdownSignalFile) {
    shutdownTimer = setInterval(() => {
      if (existsSync(options.shutdownSignalFile)) {
        void shutdown('orchestrator signal')
      }
    }, 200)
  }
}
catch (error) {
  await removePidFileIfNeeded()
  throw error
}
