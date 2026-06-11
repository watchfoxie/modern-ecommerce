import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ChevronRight } from 'lucide-react'
import { AspectRatio } from '@/components/ui/aspect-ratio'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Carousel, CarouselContent, CarouselItem, CarouselNext, CarouselPrevious } from '@/components/ui/carousel'
import { Separator } from '@/components/ui/separator'
import { ProductCard } from '@/components/commerce/ProductCard'
import { ApiErrorAlert, EmptyState, LoadingGrid, LoadingRows, PageShell, SectionHeader } from '@/components/app/PageState'
import { categoryService } from '@/contracts/category'
import { productService } from '@/contracts/product'
import { assetUrl } from '@/lib/assets'
import { queryKeys } from '@/lib/queryKeys'

const carouselImages = [
  '/static/assets/images/prod-images/carousel/carousel-1.png',
  '/static/assets/images/prod-images/carousel/carousel-2.png',
  '/static/assets/images/prod-images/carousel/carousel-3.png',
]

export default function HomePage() {
  const categoriesQuery = useQuery({
    queryKey: queryKeys.categories,
    queryFn: () => categoryService.list(),
  })
  const featuredQuery = useQuery({
    queryKey: queryKeys.products({ home: 'featured' }),
    queryFn: () => productService.list({ page: 0, size: 8, sort: 'createdAt', direction: 'desc' }),
  })
  const offersQuery = useQuery({
    queryKey: queryKeys.products({ home: 'offers' }),
    queryFn: () => productService.list({ page: 0, size: 4, hasPromotion: true }),
  })

  const categories = categoriesQuery.data?.data.filter((category) => category.slug !== 'electronics') ?? []

  return (
    <div>
      <section className="relative overflow-hidden">
        <Carousel opts={{ loop: true }} className="w-full">
          <CarouselContent>
            {carouselImages.map((image) => (
              <CarouselItem key={image}>
                <div className="relative">
                  <AspectRatio ratio={16 / 7} className="min-h-[360px] bg-muted md:min-h-[480px]">
                    <img src={image} alt="" className="h-full w-full object-cover" />
                  </AspectRatio>
                  <div className="absolute inset-0 bg-gradient-to-r from-background/90 via-background/40 to-transparent" />
                  <div className="absolute inset-x-0 bottom-10 mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
                    <div className="max-w-xl">
                      <h1 className="text-4xl font-semibold tracking-tight md:text-6xl">MEc</h1>
                      <p className="mt-4 text-base text-muted-foreground-2 md:text-lg">
                        Smartphone-uri și laptopuri premium.
                      </p>
                      <div className="mt-6 flex flex-wrap gap-3">
                        <Button asChild size="lg">
                          <Link to="/categories/smartphones">
                            Explorează catalogul
                            <ArrowRight />
                          </Link>
                        </Button>
                        <Button asChild variant="outline" size="lg">
                          <Link to="/categories/offers">Vezi ofertele</Link>
                        </Button>
                      </div>
                    </div>
                  </div>
                </div>
              </CarouselItem>
            ))}
          </CarouselContent>
          <CarouselPrevious className="left-4" />
          <CarouselNext className="right-4" />
        </Carousel>
      </section>

      <PageShell>
        <SectionHeader title="Categorii" description="Navigare rapidă către familiile principale de produse." />
        {categoriesQuery.isLoading && <LoadingRows count={3} />}
        {categoriesQuery.isError && <ApiErrorAlert error={categoriesQuery.error} onRetry={() => categoriesQuery.refetch()} />}
        {categoriesQuery.isSuccess && categories.length === 0 && (
          <EmptyState title="Nu există categorii active" description="Catalogul nu a publicat încă familii de produse disponibile." />
        )}
        <div className="grid gap-4 md:grid-cols-3">
          {categories.map((category) => (
            <Link key={category.slug} to={category.slug === 'offers' ? '/categories/offers' : `/categories/${category.slug}`}>
              <Card className="h-full rounded-lg transition hover:border-primary/40">
                <CardContent className="flex items-center gap-4 p-4">
                  <img src={assetUrl(category.imageUrl)} alt="" className="h-20 w-24 rounded-md object-contain" />
                  <div className="min-w-0 flex-1">
                    <h2 className="font-medium">{category.name}</h2>
                    <p className="line-clamp-2 text-sm text-muted-foreground">{category.description ?? 'Produse selectate din catalogul MEc.'}</p>
                  </div>
                  <ChevronRight className="size-4 text-muted-foreground" />
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>

        <Separator className="my-10" />

        <SectionHeader title="Produse recomandate" description="Cele mai recente produse active din catalog." />
        {featuredQuery.isLoading && <LoadingGrid count={8} />}
        {featuredQuery.isError && <ApiErrorAlert error={featuredQuery.error} onRetry={() => featuredQuery.refetch()} />}
        {featuredQuery.isSuccess && featuredQuery.data.data.length === 0 && (
          <EmptyState title="Nu există produse recomandate" description="Reveniți după actualizarea catalogului de produse." />
        )}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {featuredQuery.data?.data.map((product) => <ProductCard key={product.id} product={product} />)}
        </div>

        <Separator className="my-10" />

        <SectionHeader title="Oferte active" description="Produse cu preț promoțional disponibil acum." />
        {offersQuery.isLoading && <LoadingGrid count={4} />}
        {offersQuery.isError && <ApiErrorAlert error={offersQuery.error} onRetry={() => offersQuery.refetch()} />}
        {offersQuery.isSuccess && offersQuery.data.data.length === 0 && (
          <EmptyState title="Nu există oferte active" description="Catalogul nu conține produse cu preț promoțional în acest moment." />
        )}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {offersQuery.data?.data.map((product) => <ProductCard key={product.id} product={product} compact />)}
        </div>
      </PageShell>
    </div>
  )
}
