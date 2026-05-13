import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { SearchX } from 'lucide-react'
import { ProductCard } from '@/components/commerce/ProductCard'
import { ApiErrorAlert, EmptyState, LoadingGrid, PageShell, SectionHeader } from '@/components/app/PageState'
import { productService } from '@/contracts/product'
import { queryKeys } from '@/lib/queryKeys'

export default function SearchPage() {
  const [params, setParams] = useSearchParams()
  const query = params.get('q')?.trim() ?? ''
  const page = Number(params.get('page') ?? '0')

  const searchQuery = useQuery({
    queryKey: queryKeys.search(query, page),
    queryFn: () => productService.search({ q: query, page, size: 12 }),
    enabled: query.length > 0,
  })

  const setPage = (nextPage: number) => {
    const next = new URLSearchParams(params)
    next.set('page', String(Math.max(0, nextPage)))
    setParams(next)
  }

  return (
    <PageShell>
      <SectionHeader
        title="Căutare"
        description={query ? `Rezultate pentru "${query}"` : 'Introduceți o interogare în bara de navigare.'}
      />
      {!query && (
        <EmptyState icon={<SearchX />} title="Căutarea începe din bara de sus" description="Nu trimitem apel REST până când există un termen de căutare valid." />
      )}
      {searchQuery.isLoading && <LoadingGrid count={12} />}
      {searchQuery.isError && <ApiErrorAlert error={searchQuery.error} onRetry={() => searchQuery.refetch()} />}
      {searchQuery.isSuccess && searchQuery.data.content.length === 0 && (
        <EmptyState icon={<SearchX />} title="Nu am găsit produse" description="Încercați un brand, model sau termen mai scurt." />
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {searchQuery.data?.content.map((product) => <ProductCard key={product.id} product={product} />)}
      </div>
      {searchQuery.data && searchQuery.data.totalPages > 1 && (
        <div className="mt-8 flex items-center justify-center gap-2">
          <button className="text-sm underline disabled:opacity-50" disabled={searchQuery.data.first} onClick={() => setPage(page - 1)}>
            Anterior
          </button>
          <span className="text-sm text-muted-foreground">{searchQuery.data.page + 1} / {searchQuery.data.totalPages}</span>
          <button className="text-sm underline disabled:opacity-50" disabled={searchQuery.data.last} onClick={() => setPage(page + 1)}>
            Următor
          </button>
        </div>
      )}
    </PageShell>
  )
}
