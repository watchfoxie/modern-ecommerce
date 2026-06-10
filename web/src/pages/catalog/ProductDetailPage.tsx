import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Minus, Plus, ShoppingCart, Tag } from 'lucide-react'
import { toast } from 'sonner'
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { AspectRatio } from '@/components/ui/aspect-ratio'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Carousel, CarouselContent, CarouselItem, CarouselNext, CarouselPrevious } from '@/components/ui/carousel'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ApiErrorAlert, EmptyState, PageShell, LoadingRows } from '@/components/app/PageState'
import { cartService } from '@/contracts/cart'
import { productService } from '@/contracts/product'
import { assetUrl } from '@/lib/assets'
import { discountPercent, formatMoney, hasActivePromotion } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'

function categoryListHref(categorySlug: string) {
  if (categorySlug === 'laptops' || categorySlug === 'smartphones') {
    return `/categories/${categorySlug}`
  }

  if (categorySlug === 'offers') {
    return '/categories/offers'
  }

  return '/categories'
}

function canonicalProductHref(categorySlug: string, productSlug: string) {
  if (categorySlug === 'laptops' || categorySlug === 'smartphones') {
    return `/categories/${categorySlug}/${productSlug}`
  }

  return `/categories/${productSlug}`
}

export default function ProductDetailPage({ promotional = false }: Readonly<{ promotional?: boolean }>) {
  const { slug = '', productId = '' } = useParams()
  const productSlug = slug || productId
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [quantity, setQuantity] = useState(1)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())
  const userId = useAuthStore((state) => state.user?.userId)
  const syncFromCart = useCartStore((state) => state.syncFromCart)

  const productQuery = useQuery({
    queryKey: queryKeys.product(productSlug),
    queryFn: () => productService.getBySlug(productSlug),
    enabled: Boolean(productSlug),
  })

  const product = productQuery.data
  const imageUrls = product?.imageUrls.map(assetUrl) ?? []
  const basePrice = Number(product?.price ?? 0)
  const promotionActive = hasActivePromotion(basePrice, product?.promotionalPrice ?? null)
  const effectivePrice = Number(promotionActive ? product?.promotionalPrice ?? 0 : basePrice)
  const discount = discountPercent(Number(product?.price), product?.promotionalPrice ? Number(product.promotionalPrice) : null)
  const totalEffectivePrice = effectivePrice * quantity
  const totalBasePrice = basePrice * quantity
  const maxQuantity = Math.max(product?.stock ?? 0, 1)

  const addMutation = useMutation({
    mutationFn: () =>
      cartService.addItem({
        productId: product!.id,
        quantity,
        priceAtAdd: effectivePrice,
        productSnapshot: {
          name: product!.name,
          imageUrl: imageUrls[0] ?? '',
          categorySlug: product!.categorySlug,
        },
      }),
    onSuccess: (cart) => {
      syncFromCart(cart)
      queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
      toast.success('Produs adăugat în coș')
    },
    onError: () => toast.error('Produsul nu a putut fi adăugat în coș'),
  })

  const addToCart = () => {
    if (!isAuthenticated) {
      navigate('/profile/sign-in', { state: { redirectTo: globalThis.location.pathname } })
      return
    }
    addMutation.mutate()
  }

  if (productQuery.isLoading) {
    return (
      <PageShell>
        <LoadingRows count={5} />
      </PageShell>
    )
  }

  if (productQuery.isError) {
    return (
      <PageShell>
        <ApiErrorAlert error={productQuery.error} onRetry={() => productQuery.refetch()} />
      </PageShell>
    )
  }

  if (!product) {
    return (
      <PageShell>
        <EmptyState
          title="Produs indisponibil"
          description="Produsul solicitat nu este disponibil în catalogul curent."
          action={
            <Button asChild variant="outline">
              <Link to="/categories">Înapoi la catalog</Link>
            </Button>
          }
        />
      </PageShell>
    )
  }

  if (promotional && !promotionActive) {
    return <Navigate to={canonicalProductHref(product.categorySlug, product.slug)} replace />
  }

  return (
    <PageShell>
      <Button asChild variant="ghost" className="mb-5">
        <Link to={categoryListHref(product.categorySlug)}>
          <ArrowLeft />
          Înapoi la catalog
        </Link>
      </Button>

      {promotional && promotionActive && (
        <Alert className="mb-6 border-destructive/20 bg-destructive/10 text-destructive">
          <Tag />
          <AlertTitle>Produs în ofertă specială</AlertTitle>
          <AlertDescription>Reducere activă de {discount}% față de prețul standard.</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1.1fr)_minmax(360px,0.9fr)]">
        <section>
          <Carousel className="rounded-lg border bg-muted/30">
            <CarouselContent>
              {(imageUrls.length ? imageUrls : [assetUrl(null)]).map((image) => (
                <CarouselItem key={image}>
                  <AspectRatio ratio={product.categorySlug === 'laptops' ? 16 / 10 : 1}>
                    <img src={image} alt={product.name} className="h-full w-full object-contain p-6" />
                  </AspectRatio>
                </CarouselItem>
              ))}
            </CarouselContent>
            <CarouselPrevious className="left-3" />
            <CarouselNext className="right-3" />
          </Carousel>
        </section>

        <aside className="space-y-6">
          <div>
            <p className="text-sm uppercase text-muted-foreground">{product.brand}</p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight">{product.name}</h1>
            <p className="mt-2 text-muted-foreground">{product.model} · {product.country}</p>
          </div>

          <div className="space-y-2">
            {promotionActive && product.promotionalPrice ? (
              <div className="flex flex-wrap items-end gap-3">
                <span className="text-3xl font-semibold text-destructive">{formatMoney(totalEffectivePrice, product.currency)}</span>
                <span className="text-lg text-muted-foreground line-through">{formatMoney(totalBasePrice, product.currency)}</span>
                {discount && <Badge variant="destructive">-{discount}%</Badge>}
              </div>
            ) : (
              <span className="text-3xl font-semibold">{formatMoney(totalBasePrice, product.currency)}</span>
            )}
            <p className="text-sm text-muted-foreground">Total pentru {quantity} {quantity === 1 ? 'unitate' : 'unități'}</p>
            <Badge variant={product.stock > 0 ? 'secondary' : 'destructive'}>
              {product.stock > 0 ? `${product.stock} în stoc` : 'Stoc epuizat'}
            </Badge>
          </div>

          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="icon" onClick={() => setQuantity((value) => Math.max(1, value - 1))}>
              <Minus />
              <span className="sr-only">Scade cantitatea</span>
            </Button>
            <Input
              className="w-20 text-center select-none"
              type="text"
              inputMode="numeric"
              readOnly
              aria-label="Cantitate selectată"
              value={quantity}
              onMouseDown={(e) => e.preventDefault()}
            />
            <Button type="button" variant="outline" size="icon" onClick={() => setQuantity((value) => Math.min(maxQuantity, value + 1))}>
              <Plus />
              <span className="sr-only">Crește cantitatea</span>
            </Button>
          </div>

          <Button className="w-full" size="lg" onClick={addToCart} disabled={addMutation.isPending || product.stock <= 0}>
            <ShoppingCart />
            Adaugă în coș
          </Button>

          <Accordion type="multiple" defaultValue={['delivery']}>
            <AccordionItem value="delivery">
              <AccordionTrigger>Livrare</AccordionTrigger>
              <AccordionContent>Livrare în Chișinău și în raioane prin curier, cu confirmarea comenzii după acceptare.</AccordionContent>
            </AccordionItem>
            <AccordionItem value="warranty">
              <AccordionTrigger>Garanție</AccordionTrigger>
              <AccordionContent>Produsele sunt livrate cu garanție comercială și suport post-vânzare Tocana Group LLC.</AccordionContent>
            </AccordionItem>
          </Accordion>
        </aside>
      </div>

      <Tabs defaultValue="specs" className="mt-10">
        <TabsList>
          <TabsTrigger value="specs">Specificații</TabsTrigger>
          <TabsTrigger value="description">Descriere</TabsTrigger>
        </TabsList>
        <TabsContent value="specs" className="mt-4">
          <Table>
            <TableBody>
              {Object.entries(product.specs).map(([key, value]) => (
                <TableRow key={key}>
                  <TableCell className="w-1/3 font-medium">{key}</TableCell>
                  <TableCell>{value}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TabsContent>
        <TabsContent value="description" className="mt-4 text-sm leading-7 text-muted-foreground">
          {product.name} combină configurația {product.model} cu disponibilitatea actuală din catalogul MEc.
        </TabsContent>
      </Tabs>
    </PageShell>
  )
}
