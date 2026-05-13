import { createBrowserRouter, Navigate, type RouteObject } from 'react-router-dom'
import App from './App'
import NotFoundPage from '@/pages/NotFoundPage'
import { RequireAuth, RequireGuest } from '@/components/app/RouteGuards'
import { SignInPage, SignUpPage, PasswordResetPage } from '@/pages/auth/AuthPages'
import CartLayout from '@/pages/cart/CartLayout'
import CartPage from '@/pages/cart/CartPage'
import { DeliveryPage, PayPage, PersonalDataPage } from '@/pages/cart/CheckoutPages'
import CatalogLayout from '@/pages/catalog/CatalogLayout'
import CatalogPage from '@/pages/catalog/CatalogPage'
import HomePage from '@/pages/catalog/HomePage'
import ProductDetailPage from '@/pages/catalog/ProductDetailPage'
import SearchPage from '@/pages/catalog/SearchPage'
import {
  AccountLayout,
  AccountOverviewPage,
  ExpenseDashboardPage,
  OrderHistoryPage,
  PersonalPage,
  ProfileLandingPage,
} from '@/pages/profile/ProfilePages'
import { AboutPage, ContactsPage, SupportPage } from '@/pages/static/StaticPages'

const developmentRoutes: RouteObject[] = import.meta.env.DEV
  ? [
      {
        path: 'demo',
        lazy: async () => {
          const { default: DemoPage } = await import('@/pages/DemoPage')
          return { Component: DemoPage }
        },
      },
    ]
  : []

export const router = createBrowserRouter([
  {
    path: '/',
    Component: App,
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      { path: 'home', element: <HomePage /> },
      {
        path: 'categories',
        element: <CatalogLayout />,
        children: [
          { index: true, element: <CatalogPage mode="all" /> },
          { path: 'smartphones', element: <CatalogPage mode="smartphones" /> },
          { path: 'laptops', element: <CatalogPage mode="laptops" /> },
          { path: 'offers', element: <CatalogPage mode="offers" /> },
          { path: 'smartphones/:productId', element: <ProductDetailPage /> },
          { path: 'laptops/:productId', element: <ProductDetailPage /> },
          { path: 'offers/:productId', element: <ProductDetailPage promotional /> },
          { path: 'offers/smartphones/:productId', element: <ProductDetailPage promotional /> },
          { path: 'offers/laptops/:productId', element: <ProductDetailPage promotional /> },
          { path: ':productId', element: <ProductDetailPage /> },
        ],
      },
      { path: 'search', element: <SearchPage /> },
      {
        path: 'cart',
        element: <RequireAuth><CartLayout /></RequireAuth>,
        children: [
          { index: true, element: <CartPage /> },
          { path: 'delivery', element: <DeliveryPage /> },
          { path: 'personal-data', element: <PersonalDataPage /> },
          { path: 'pay', element: <PayPage /> },
        ],
      },
      {
        path: 'profile',
        children: [
          { index: true, element: <ProfileLandingPage /> },
          { path: 'sign-up', element: <RequireGuest><SignUpPage /></RequireGuest> },
          { path: 'sign-in', element: <RequireGuest><SignInPage /></RequireGuest> },
          { path: 'password-reset', element: <RequireGuest><PasswordResetPage /></RequireGuest> },
          {
            path: 'account',
            element: <RequireAuth><AccountLayout /></RequireAuth>,
            children: [
              { index: true, element: <AccountOverviewPage /> },
              { path: 'order-history', element: <OrderHistoryPage /> },
              { path: 'expense-dashboard', element: <ExpenseDashboardPage /> },
              { path: 'personal', element: <PersonalPage /> },
            ],
          },
        ],
      },
      { path: 'support', element: <SupportPage /> },
      { path: 'contacts', element: <ContactsPage /> },
      { path: 'about', element: <AboutPage /> },
      ...developmentRoutes,
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
