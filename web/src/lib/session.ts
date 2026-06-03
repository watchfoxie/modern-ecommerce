import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'
import { useCheckoutStore } from '@/stores/checkoutStore'

export function clearSessionState() {
    useAuthStore.getState().clearAuth()
    useCartStore.getState().clearCart()
    useCheckoutStore.getState().resetCheckout()
}