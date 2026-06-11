import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Filter, Laptop, Percent, Smartphone } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ProductCard } from '@/components/commerce/ProductCard'
import { ApiErrorAlert, EmptyState, LoadingGrid, PageShell, SectionHeader } from '@/components/app/PageState'
import { productService, type ProductDto } from '@/contracts/product'
import { queryKeys } from '@/lib/queryKeys'

type CatalogMode = 'all' | 'smartphones' | 'laptops' | 'offers'

function buildPageRange(current: number, total: number): (number | '…start' | '…end')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)
  if (current <= 3) return [0, 1, 2, 3, 4, '…end', total - 1]
  if (current >= total - 4) return [0, '…start', total - 5, total - 4, total - 3, total - 2, total - 1]
  return [0, '…start', current - 1, current, current + 1, '…end', total - 1]
}

const categoryInfo: Record<CatalogMode, { title: string; description: string; icon: typeof Smartphone }> = {
  all: {
    title: 'Categorii',
    description: 'Toate produsele electronice disponibile în magazin.',
    icon: Smartphone,
  },
  smartphones: {
    title: 'Smartphone-uri',
    description: 'Telefoane Apple, Samsung și Huawei disponibile în stoc.',
    icon: Smartphone,
  },
  laptops: {
    title: 'Laptop-uri',
    description: 'Laptopuri pentru lucru, studiu și mobilitate.',
    icon: Laptop,
  },
  offers: {
    title: 'Oferte',
    description: 'Produse cu preț promoțional activ, indiferent de categorie.',
    icon: Percent,
  },
}

function categorySlug(mode: CatalogMode) {
  if (mode === 'all' || mode === 'offers') return undefined
  return mode
}

function sortParts(value: string) {
  if (value === 'price-asc') return { sort: 'price', direction: 'asc' }
  if (value === 'price-desc') return { sort: 'price', direction: 'desc' }
  if (value === 'name-asc') return { sort: 'name', direction: 'asc' }
  return { sort: 'createdAt', direction: 'desc' }
}

function CatalogFilters({
  sort,
  onSort,
}: {
  sort: string
  onSort: (value: string) => void
}) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
      <Select value={sort} onValueChange={onSort}>
        <SelectTrigger className="w-full sm:w-56">
          <SelectValue placeholder="Sortare" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="created-desc">Cele mai noi</SelectItem>
          <SelectItem value="price-asc">Preț crescător</SelectItem>
          <SelectItem value="price-desc">Preț descrescător</SelectItem>
          <SelectItem value="name-asc">Denumire A-Z</SelectItem>
        </SelectContent>
      </Select>
    </div>
  )
}

export default function CatalogPage({ mode = 'all' }: { mode?: CatalogMode }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = Number(searchParams.get('page') ?? '0')
  const sort = searchParams.get('sort') ?? 'created-desc'
  const { direction, sort: sortField } = sortParts(sort)
  const info = categoryInfo[mode]
  const Icon = info.icon

  const queryParams = useMemo(
    () => ({
      categorySlug: categorySlug(mode),
      hasPromotion: mode === 'offers' ? true : undefined,
      page: Number.isFinite(page) && page >= 0 ? page : 0,
      size: 12,
      sort: sortField,
      direction,
    }),
    [direction, mode, page, sortField],
  )

  const productsQuery = useQuery({
    queryKey: queryKeys.products(queryParams),
    queryFn: () => productService.list(queryParams),
  })

  const setPage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(Math.max(0, nextPage)))
    setSearchParams(next)
  }

  const setSort = (value: string) => {
    const next = new URLSearchParams(searchParams)
    next.set('sort', value)
    next.set('page', '0')
    setSearchParams(next)
  }

  const products: ProductDto[] = productsQuery.data?.data ?? []

  return (
    <PageShell>
      <SectionHeader
        title={info.title}
        description={info.description}
        action={
          <div className="hidden sm:block">
            <CatalogFilters sort={sort} onSort={setSort} />
          </div>
        }
      />

      <div className="mb-5 flex items-center justify-between gap-3 sm:hidden">
        <Sheet>
          <SheetTrigger asChild>
            <Button variant="outline">
              <Filter />
              Filtre
            </Button>
          </SheetTrigger>
          <SheetContent>
            <SheetHeader>
              <SheetTitle>Filtrare catalog</SheetTitle>
            </SheetHeader>
            <div className="mt-6">
              <CatalogFilters sort={sort} onSort={setSort} />
            </div>
          </SheetContent>
        </Sheet>
      </div>

      {mode === 'offers' && (
        <Tabs value="offers" className="mb-6">
          <TabsList>
            <TabsTrigger value="offers">Oferte curate</TabsTrigger>
            <TabsTrigger value="smartphones" asChild>
              <Link to="/categories/smartphones">Smartphone-uri</Link>
            </TabsTrigger>
            <TabsTrigger value="laptops" asChild>
              <Link to="/categories/laptops">Laptop-uri</Link>
            </TabsTrigger>
          </TabsList>
        </Tabs>
      )}

      {productsQuery.isLoading && <LoadingGrid count={12} />}
      {productsQuery.isError && <ApiErrorAlert error={productsQuery.error} onRetry={() => productsQuery.refetch()} />}
      {productsQuery.isSuccess && products.length === 0 && (
        <EmptyState
          icon={<Icon />}
          title="Nu există produse pentru această selecție"
          description="Încercați altă categorie sau reveniți la pagina principală."
          action={
            <Button asChild variant="outline">
              <Link to="/home">Înapoi la acasă</Link>
            </Button>
          }
        />
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {products.map((product) => (
          <ProductCard key={product.id} product={product} />
        ))}
      </div>

      {productsQuery.isSuccess && productsQuery.data.totalPages > 1 && (() => {
        const data = productsQuery.data
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
