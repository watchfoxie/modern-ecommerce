import { lazy, Suspense, type ReactNode } from 'react'

export const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))
export const CartLayout = lazy(() => import('@/pages/cart/CartLayout'))
export const CartPage = lazy(() => import('@/pages/cart/CartPage'))
export const CatalogLayout = lazy(() => import('@/pages/catalog/CatalogLayout'))
export const CatalogPage = lazy(() => import('@/pages/catalog/CatalogPage'))
export const HomePage = lazy(() => import('@/pages/catalog/HomePage'))
export const ProductDetailPage = lazy(() => import('@/pages/catalog/ProductDetailPage'))
export const SearchPage = lazy(() => import('@/pages/catalog/SearchPage'))

export const SignInPage = lazy(() => import('@/pages/auth/AuthPages').then((module) => ({ default: module.SignInPage })))
export const SignUpPage = lazy(() => import('@/pages/auth/AuthPages').then((module) => ({ default: module.SignUpPage })))
export const PasswordResetPage = lazy(() =>
  import('@/pages/auth/AuthPages').then((module) => ({ default: module.PasswordResetPage })),
)
export const DeliveryPage = lazy(() =>
  import('@/pages/cart/CheckoutPages').then((module) => ({ default: module.DeliveryPage })),
)
export const PayPage = lazy(() => import('@/pages/cart/CheckoutPages').then((module) => ({ default: module.PayPage })))
export const PersonalDataPage = lazy(() =>
  import('@/pages/cart/CheckoutPages').then((module) => ({ default: module.PersonalDataPage })),
)
export const ProfileLandingPage = lazy(() =>
  import('@/pages/profile/ProfilePages').then((module) => ({ default: module.ProfileLandingPage })),
)
export const AccountLayout = lazy(() =>
  import('@/pages/profile/ProfilePages').then((module) => ({ default: module.AccountLayout })),
)
export const AccountOverviewPage = lazy(() =>
  import('@/pages/profile/ProfilePages').then((module) => ({ default: module.AccountOverviewPage })),
)
export const PersonalPage = lazy(() =>
  import('@/pages/profile/ProfilePages').then((module) => ({ default: module.PersonalPage })),
)
export const OrderHistoryPage = lazy(() =>
  import('@/pages/profile/ProfilePages').then((module) => ({ default: module.OrderHistoryPage })),
)
export const ExpenseDashboardPage = lazy(() =>
  import('@/pages/profile/ProfilePages').then((module) => ({ default: module.ExpenseDashboardPage })),
)
export const SupportPage = lazy(() =>
  import('@/pages/static/StaticPages').then((module) => ({ default: module.SupportPage })),
)
export const ContactsPage = lazy(() =>
  import('@/pages/static/StaticPages').then((module) => ({ default: module.ContactsPage })),
)
export const AboutPage = lazy(() => import('@/pages/static/StaticPages').then((module) => ({ default: module.AboutPage })))

export function LazyRoute({ children }: { children: ReactNode }) {
  return <Suspense fallback={<div className="min-h-[40vh]" />}>{children}</Suspense>
}
