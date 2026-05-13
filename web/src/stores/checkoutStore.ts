import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { CreateOrderRequest, OrderAddressDto } from '@/contracts/order'

export interface CheckoutContact {
  firstName: string
  lastName: string
  email: string
  phone: string
}

interface CheckoutState {
  deliveryAddress: OrderAddressDto | null
  contact: CheckoutContact | null
  payment: CreateOrderRequest['payment']
  notes: string
  setDeliveryAddress: (address: OrderAddressDto) => void
  setContact: (contact: CheckoutContact) => void
  setPayment: (payment: CreateOrderRequest['payment']) => void
  setNotes: (notes: string) => void
  resetCheckout: () => void
}

export const useCheckoutStore = create<CheckoutState>()(
  persist(
    (set) => ({
      deliveryAddress: null,
      contact: null,
      payment: { method: 'CARD' },
      notes: '',
      setDeliveryAddress: (deliveryAddress) => set({ deliveryAddress }),
      setContact: (contact) => set({ contact }),
      setPayment: (payment) => set({ payment }),
      setNotes: (notes) => set({ notes }),
      resetCheckout: () =>
        set({
          deliveryAddress: null,
          contact: null,
          payment: { method: 'CARD' },
          notes: '',
        }),
    }),
    {
      name: 'checkout-storage',
      storage: createJSONStorage(() => localStorage),
    },
  ),
)
