import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ShoppingCart, Tag } from 'lucide-react'
import { toast } from 'sonner'
import { AspectRatio } from '@/components/ui/aspect-ratio'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import type { ProductDto } from '@/contracts/product'
import { cartService } from '@/contracts/cart'
import { firstAsset } from '@/lib/assets'
import { discountPercent, formatMoney, hasActivePromotion } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'

function productHref(product: ProductDto) {
  const categoryRoute = product.categorySlug === 'laptops' || product.categorySlug === 'smartphones' ? product.categorySlug : null
  if (hasActivePromotion(product.price, product.promotionalPrice ?? null)) {
    return categoryRoute ? `/categories/offers/${categoryRoute}/${product.slug}` : `/categories/offers/${product.slug}`
  }

  return categoryRoute ? `/categories/${categoryRoute}/${product.slug}` : `/categories/${product.slug}`
}

export function ProductCard({ product, compact = false }: Readonly<{ product: ProductDto; compact?: boolean }>) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())
  const userId = useAuthStore((state) => state.user?.userId)
  const syncFromCart = useCartStore((state) => state.syncFromCart)
  const optimisticAdd = useCartStore((state) => state.applyOptimisticItem)
  const restoreOptimisticItems = useCartStore((state) => state.replaceOptimisticItems)
  const imageUrl = firstAsset(product.imageUrls)
  const promotionActive = hasActivePromotion(product.price, product.promotionalPrice ?? null)
  const effectivePrice = Number(promotionActive ? product.promotionalPrice : product.price)
  const discount = discountPercent(Number(product.price), product.promotionalPrice ? Number(product.promotionalPrice) : null)

  const addMutation = useMutation({
    mutationFn: () =>
      cartService.addItem({
        productId: product.id,
        quantity: 1,
        priceAtAdd: effectivePrice,
        productSnapshot: {
          name: product.name,
          imageUrl,
          categorySlug: product.categorySlug,
        },
      }),
    onMutate: () => {
      const previousItems = useCartStore.getState().items
      optimisticAdd({
        productId: product.id,
        name: product.name,
        price: effectivePrice,
        quantity: 1,
        imageUrl,
        categorySlug: product.categorySlug,
      })
      return { previousItems }
    },
    onSuccess: (cart) => {
      syncFromCart(cart)
      queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
      toast.success('Produs adăugat în coș')
    },
    onError: (_error, _variables, context) => {
      if (context?.previousItems) {
        restoreOptimisticItems(context.previousItems)
      }
      toast.error('Produsul nu a putut fi adăugat în coș')
      queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
    },
  })

  const handleAdd = () => {
    if (!isAuthenticated) {
      toast.info('Autentificați-vă pentru a folosi coșul persistent')
      navigate('/profile/sign-in', { state: { redirectTo: productHref(product) } })
      return
    }
    addMutation.mutate()
  }

  return (
    <Card className="flex h-full flex-col overflow-hidden rounded-lg">
      <Link to={productHref(product)} className="block bg-muted/40">
        <AspectRatio ratio={product.categorySlug === 'laptops' ? 16 / 10 : 1}>
          <img src={imageUrl} alt={product.name} className="h-full w-full object-contain p-4 transition-transform hover:scale-[1.03]" />
        </AspectRatio>
      </Link>
      <CardContent className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs font-medium uppercase text-muted-foreground">{product.brand}</span>
          {discount && (
            <Badge variant="destructive" className="gap-1">
              <Tag className="size-3" />
              -{discount}%
            </Badge>
          )}
        </div>
        <Link to={productHref(product)} className="line-clamp-2 font-medium hover:text-primary">
          {product.name}
        </Link>
        {!compact && product.specs && (
          <p className="line-clamp-2 text-xs text-muted-foreground">
            {[product.specs.processor, product.specs.ram, product.specs.storage].filter(Boolean).join(' · ')}
          </p>
        )}
        <div className="mt-auto">
          {promotionActive && product.promotionalPrice ? (
            <div className="flex flex-wrap items-baseline gap-2">
              <span className="font-semibold text-destructive">{formatMoney(product.promotionalPrice, product.currency)}</span>
              <span className="text-sm text-muted-foreground line-through">{formatMoney(product.price, product.currency)}</span>
            </div>
          ) : (
            <span className="font-semibold">{formatMoney(product.price, product.currency)}</span>
          )}
          <p className="mt-1 text-xs text-muted-foreground">{product.stock > 0 ? `${product.stock} disponibile` : 'Stoc epuizat'}</p>
        </div>
      </CardContent>
      <CardFooter className="p-4 pt-0">
        <Button type="button" className="w-full" onClick={handleAdd} disabled={addMutation.isPending || product.stock <= 0}>
          {addMutation.isPending ? <Spinner /> : <ShoppingCart />}
          Adaugă în coș
        </Button>
      </CardFooter>
    </Card>
  )
}
