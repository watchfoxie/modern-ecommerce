import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { CartDto } from '@/contracts/cart'

export interface CartSummaryItem {
  productId: string
  name: string
  price: number
  quantity: number
  imageUrl?: string
  categorySlug?: string
}

interface CartState {
  items: CartSummaryItem[]
  lastSyncedAt: string | null
  syncFromCart: (cart: CartDto | null) => void
  applyOptimisticItem: (item: CartSummaryItem) => void
  replaceOptimisticItems: (items: CartSummaryItem[]) => void
  removeOptimisticItem: (productId: string) => void
  updateOptimisticQuantity: (productId: string, quantity: number) => void
  clearCart: () => void
  totalItems: () => number
  totalPrice: () => number
}

function fromCart(cart: CartDto | null): CartSummaryItem[] {
  return (
    cart?.items.map((item) => ({
      productId: item.productId,
      name: item.productSnapshot.name,
      price: Number(item.priceAtAdd),
      quantity: item.quantity,
      imageUrl: item.productSnapshot.imageUrl,
      categorySlug: item.productSnapshot.categorySlug,
    })) ?? []
  )
}

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],
      lastSyncedAt: null,
      syncFromCart: (cart) =>
        set({
          items: fromCart(cart),
          lastSyncedAt: new Date().toISOString(),
        }),
      applyOptimisticItem: (item) =>
        set((state) => {
          const existing = state.items.find((current) => current.productId === item.productId)
          if (!existing) {
            return { items: [...state.items, item] }
          }

          return {
            items: state.items.map((current) =>
              current.productId === item.productId
                ? { ...current, quantity: current.quantity + item.quantity }
                : current,
            ),
          }
        }),
      replaceOptimisticItems: (items) => set({ items }),
      removeOptimisticItem: (productId) =>
        set((state) => ({
          items: state.items.filter((item) => item.productId !== productId),
        })),
      updateOptimisticQuantity: (productId, quantity) =>
        set((state) => ({
          items:
            quantity <= 0
              ? state.items.filter((item) => item.productId !== productId)
              : state.items.map((item) => (item.productId === productId ? { ...item, quantity } : item)),
        })),
      clearCart: () => set({ items: [], lastSyncedAt: null }),
      totalItems: () => get().items.reduce((sum, item) => sum + item.quantity, 0),
      totalPrice: () => get().items.reduce((sum, item) => sum + item.price * item.quantity, 0),
    }),
    {
      name: 'cart-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        items: state.items,
        lastSyncedAt: state.lastSyncedAt,
      }),
    },
  ),
)
