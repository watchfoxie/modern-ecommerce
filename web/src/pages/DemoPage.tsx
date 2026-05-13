import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { API_BASE_URL, buildGatewayUrl } from '@/config/api'
import { api } from '@/config/axios'

const reactLogo = 'static/assets/icons/dev-icons/react.svg'
const viteLogo = 'static/assets/icons/dev-icons/vite.svg'
const heroImg = 'static/assets/images/dev-images/hero.png'

export default function DemoPage() {
  const [count, setCount] = useState(0)
  const gatewayExampleUrl = buildGatewayUrl('order-service/orders')

  return (
    <section className="bg-background text-foreground">
      <div className="mx-auto flex w-full max-w-6xl flex-col justify-center px-6 py-12 lg:px-10">
        <div className="grid gap-8 lg:grid-cols-[1.2fr_0.8fr]">
          <section className="rounded-3xl border bg-card p-8 shadow-sm">
            <div className="mb-8 flex flex-wrap items-center gap-4">
              <img src={reactLogo} className="h-10 w-10" alt="React logo" />
              <img src={viteLogo} className="h-10 w-10" alt="Vite logo" />
              <span className="rounded-full border px-3 py-1 text-sm text-muted-foreground">
                shadcn/ui + Tailwind CSS
              </span>
            </div>

            <div className="grid gap-8 md:grid-cols-[minmax(0,1fr)_220px] md:items-center">
              <div className="space-y-4">
                <p className="text-sm font-medium uppercase tracking-[0.24em] text-muted-foreground">
                  modern-ecommerce
                </p>
                <h1 className="text-4xl font-semibold tracking-tight md:text-5xl">
                  Frontend Vite pregatit pentru integrarea UI reutilizabila.
                </h1>
                <p className="max-w-2xl text-base leading-7 text-muted-foreground md:text-lg">
                  Aplicatia foloseste acum aliasul <code className="rounded bg-muted px-1.5 py-1 text-sm">@/</code>,
                  pipeline-ul Tailwind pentru Vite si componenta <code className="rounded bg-muted px-1.5 py-1 text-sm">Button</code>{' '}
                  generata de CLI-ul <code className="rounded bg-muted px-1.5 py-1 text-sm">shadcn</code>.
                </p>
              </div>

              <div className="flex justify-center">
                <img
                  src={heroImg}
                  className="w-full max-w-[220px] drop-shadow-sm"
                  width="220"
                  height="232"
                  alt="Illustration for the modern-ecommerce frontend"
                />
              </div>
            </div>
          </section>

          <section className="rounded-3xl border bg-card p-8 shadow-sm">
            <div className="space-y-6">
              <div className="space-y-2">
                <h2 className="text-2xl font-semibold tracking-tight">
                  Contractul cu gateway-ul
                </h2>
                <p className="text-sm leading-6 text-muted-foreground">
                  Frontend-ul continua sa foloseasca baza <code className="rounded bg-muted px-1.5 py-1 text-sm">{API_BASE_URL}</code>{' '}
                  si rutele gateway de forma <code className="rounded bg-muted px-1.5 py-1 text-sm">{gatewayExampleUrl}</code>.
                </p>
              </div>

              <div className="rounded-2xl border bg-muted/40 p-4">
                <p className="text-sm text-muted-foreground">Stare demo componenta</p>
                <p className="mt-2 text-3xl font-semibold">{count}</p>
                <div className="mt-4 flex flex-wrap gap-3">
                  <Button onClick={() => setCount((currentCount) => currentCount + 1)}>
                    Count is {count}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => setCount(0)}
                  >
                    Reset
                  </Button>
                </div>
              </div>

              <div className="flex flex-wrap gap-3">
                <Button asChild variant="secondary">
                  <a href="https://ui.shadcn.com/docs/installation/vite" target="_blank" rel="noreferrer">
                    shadcn/ui docs
                  </a>
                </Button>
                <Button asChild variant="ghost">
                  <a href="https://vite.dev/" target="_blank" rel="noreferrer">
                    Explore Vite
                  </a>
                </Button>
              </div>
            </div>
          </section>
        </div>

        <section className="mt-8 rounded-3xl border bg-card p-8 shadow-sm">
          <div className="space-y-4">
            <h2 className="text-2xl font-semibold tracking-tight">
              TanStack Query — Integrare API Gateway
            </h2>
            <p className="text-sm leading-6 text-muted-foreground">
              Demonstrație <code className="rounded bg-muted px-1.5 py-1 text-sm">useQuery</code> cu ciclul
              complet: loading → success / error. Interogare:{' '}
              <code className="rounded bg-muted px-1.5 py-1 text-sm">GET {buildGatewayUrl('auth-service/actuator/info')}</code>
            </p>

            <GatewayHealthCheck />
          </div>
        </section>
      </div>
    </section>
  )
}

function GatewayHealthCheck() {
  const { data, error, isLoading, isError, refetch } = useQuery({
    queryKey: ['gateway', 'auth-service', 'info'],
    queryFn: async () => {
      const response = await api.get('/auth-service/actuator/info')
      return response.data
    },
    enabled: false,
  })

  return (
    <div className="rounded-2xl border bg-muted/40 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <Button onClick={() => refetch()} disabled={isLoading}>
          {isLoading ? 'Se interogează…' : 'Interogare Gateway'}
        </Button>
      </div>

      {isLoading && (
        <p className="mt-3 text-sm text-muted-foreground">⏳ Se încarcă răspunsul de la API Gateway…</p>
      )}

      {isError && (
        <div className="mt-3 rounded-lg border border-destructive/50 bg-destructive/10 p-3">
          <p className="text-sm font-medium text-destructive">Eroare la interogare</p>
          <p className="mt-1 text-xs text-destructive/80">
            {error instanceof Error ? error.message : 'Eroare necunoscută'}
          </p>
        </div>
      )}

      {data && !isError && (
        <div className="mt-3 rounded-lg border bg-background p-3">
          <p className="text-sm font-medium text-green-600 dark:text-green-400">✓ Răspuns primit de la gateway</p>
          <pre className="mt-2 max-h-40 overflow-auto text-xs text-muted-foreground">
            {JSON.stringify(data, null, 2)}
          </pre>
        </div>
      )}
    </div>
  )
}
