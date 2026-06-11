import { Link } from 'react-router-dom'
import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PackageOpen, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { ApiErrorAlert, EmptyState, LoadingRows, PageShell, SectionHeader } from '@/components/app/PageState'
import { cartService, type CartDto } from '@/contracts/cart'
import { assetUrl } from '@/lib/assets'
import { formatMoney } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'

function cartTotal(cart?: CartDto) {
  return cart?.items.reduce((sum, item) => sum + Number(item.priceAtAdd) * item.quantity, 0) ?? 0
}

function categoryLabel(slug: string) {
  if (slug === 'laptops') return 'Laptop'
  if (slug === 'smartphones') return 'Smartphone'
  return 'Produs'
}

export default function CartPage() {
  const queryClient = useQueryClient()
  const userId = useAuthStore((state) => state.user?.userId)
  const syncFromCart = useCartStore((state) => state.syncFromCart)
  const clearLocalCart = useCartStore((state) => state.clearCart)

  const cartQuery = useQuery({
    queryKey: queryKeys.cart(userId),
    queryFn: () => cartService.getMe(),
    enabled: Boolean(userId),
  })

  useEffect(() => {
    if (cartQuery.data) {
      syncFromCart(cartQuery.data)
    }
  }, [cartQuery.data, syncFromCart])

  const updateMutation = useMutation({
    mutationFn: ({ productId, quantity }: { productId: string; quantity: number }) => cartService.updateItem(productId, { quantity }),
    onSuccess: (cart) => {
      syncFromCart(cart)
      queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
    },
    onError: () => toast.error('Cantitatea nu a putut fi actualizată'),
  })

  const removeMutation = useMutation({
    mutationFn: (productId: string) => cartService.removeItem(productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
      toast.success('Produs eliminat din coș')
    },
  })

  const clearMutation = useMutation({
    mutationFn: () => cartService.clear(),
    onSuccess: () => {
      clearLocalCart()
      queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
      toast.success('Coș golit')
    },
  })

  const cart = cartQuery.data
  const isEmpty = !cart?.items.length

  return (
    <PageShell>
      <SectionHeader title="Coșul de cumpărături" description="Produsele selectate pentru achiziție." />
      {cartQuery.isLoading && <LoadingRows count={4} />}
      {cartQuery.isError && <ApiErrorAlert error={cartQuery.error} onRetry={() => cartQuery.refetch()} />}
      {cartQuery.isSuccess && isEmpty && (
        <EmptyState
          icon={<PackageOpen />}
          title="Coșul este gol"
          description="Adăugați produse din catalog pentru a finaliza o comandă."
          action={
            <Button asChild>
              <Link to="/categories/smartphones">Descoperă produse</Link>
            </Button>
          }
        />
      )}
      {cart && !isEmpty && (
        <div className="grid gap-8 lg:grid-cols-[1fr_340px]">
          <div className="space-y-4">
            {cart.items.map((item) => (
              <div key={item.productId} className="grid gap-4 rounded-lg border p-4 sm:grid-cols-[96px_1fr_auto]">
                <img src={assetUrl(item.productSnapshot.imageUrl)} alt="" className="h-24 w-24 rounded-md object-contain" />
                <div className="min-w-0">
                  <h2 className="font-medium">{item.productSnapshot.name}</h2>
                  <p className="text-sm text-muted-foreground">{categoryLabel(item.productSnapshot.categorySlug)}</p>
                  <p className="mt-2 font-medium">{formatMoney(item.priceAtAdd)}</p>
                </div>
                <div className="flex items-center gap-2 sm:flex-col sm:items-end">
                  <Input
                    aria-label="Cantitate"
                    className="w-20 text-center"
                    type="number"
                    min={1}
                    value={item.quantity}
                    onChange={(event) =>
                      updateMutation.mutate({ productId: item.productId, quantity: Number(event.target.value) || 1 })
                    }
                  />
                  <Button variant="ghost" size="icon" onClick={() => removeMutation.mutate(item.productId)}>
                    <Trash2 />
                    <span className="sr-only">Elimină</span>
                  </Button>
                </div>
              </div>
            ))}
          </div>
          <aside className="h-fit rounded-lg border p-5">
            <h2 className="font-medium">Sumar comandă</h2>
            <Separator className="my-4" />
            <div className="flex justify-between text-sm">
              <span>Produse</span>
              <span>{cart.items.reduce((sum, item) => sum + item.quantity, 0)}</span>
            </div>
            <div className="mt-3 flex justify-between font-semibold">
              <span>Total</span>
              <span>{formatMoney(cartTotal(cart))}</span>
            </div>
            <Button asChild className="mt-5 w-full">
              <Link to="/cart/delivery">Continuă spre livrare</Link>
            </Button>
            <Button
              variant="outline"
              className="mt-2 w-full"
              disabled={clearMutation.isPending}
              onClick={() => clearMutation.mutate()}
            >
              Golește coșul
            </Button>
          </aside>
        </div>
      )}
    </PageShell>
  )
}
