import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { SearchX } from 'lucide-react'
import { ProductCard } from '@/components/commerce/ProductCard'
import { ApiErrorAlert, EmptyState, LoadingGrid, PageShell, SectionHeader } from '@/components/app/PageState'
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'
import { productService } from '@/contracts/product'
import { queryKeys } from '@/lib/queryKeys'

function buildPageRange(current: number, total: number): (number | '…start' | '…end')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)
  if (current <= 3) return [0, 1, 2, 3, 4, '…end', total - 1]
  if (current >= total - 4) return [0, '…start', total - 5, total - 4, total - 3, total - 2, total - 1]
  return [0, '…start', current - 1, current, current + 1, '…end', total - 1]
}

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
        <EmptyState icon={<SearchX />} title="Căutarea începe din bara de sus" description="Tastați cel puțin un cuvânt în bara de căutare din partea de sus." />
      )}
      {searchQuery.isLoading && <LoadingGrid count={12} />}
      {searchQuery.isError && <ApiErrorAlert error={searchQuery.error} onRetry={() => searchQuery.refetch()} />}
      {searchQuery.isSuccess && searchQuery.data.data.length === 0 && (
        <EmptyState icon={<SearchX />} title="Nu am găsit produse" description="Încercați un brand, model sau termen mai scurt." />
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {searchQuery.data?.data.map((product) => <ProductCard key={product.id} product={product} />)}
      </div>
      {searchQuery.isSuccess && searchQuery.data.totalPages > 1 && (() => {
        const data = searchQuery.data
        return (
          <Pagination className="mt-8">
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  text="Anterior"
                  href="#"
                  aria-disabled={data.first}
                  onClick={(e) => { e.preventDefault(); if (!data.first) setPage(page - 1) }}
                  className={data.first ? 'pointer-events-none opacity-50' : ''}
                />
              </PaginationItem>
              {buildPageRange(data.page, data.totalPages).map((item) =>
                typeof item === 'string' ? (
                  <PaginationItem key={item}>
                    <PaginationEllipsis />
                  </PaginationItem>
                ) : (
                  <PaginationItem key={item}>
                    <PaginationLink
                      href="#"
                      isActive={item === data.page}
                      onClick={(e) => { e.preventDefault(); setPage(item) }}
                    >
                      {item + 1}
                    </PaginationLink>
                  </PaginationItem>
                )
              )}
              <PaginationItem>
                <PaginationNext
                  text="Următor"
                  href="#"
                  aria-disabled={data.last}
                  onClick={(e) => { e.preventDefault(); if (!data.last) setPage(page + 1) }}
                  className={data.last ? 'pointer-events-none opacity-50' : ''}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        )
      })()}
    </PageShell>
  )
}
