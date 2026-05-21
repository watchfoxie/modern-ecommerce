import { createBrowserRouter, Navigate, type RouteObject } from 'react-router-dom'
import App from './App'
import {
  AboutPage,
  AccountLayout,
  AccountOverviewPage,
  CartLayout,
  CartPage,
  CatalogLayout,
  CatalogPage,
  ContactsPage,
  DeliveryPage,
  ExpenseDashboardPage,
  HomePage,
  LazyRoute,
  NotFoundPage,
  OrderHistoryPage,
  PasswordResetPage,
  PayPage,
  PersonalDataPage,
  PersonalPage,
  ProductDetailPage,
  ProfileLandingPage,
  SearchPage,
  SignInPage,
  SignUpPage,
  SupportPage,
} from './route-elements'
import { RequireAuth, RequireGuest } from '@/components/app/RouteGuards'

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
      { path: 'home', element: <LazyRoute><HomePage /></LazyRoute> },
      {
        path: 'categories',
        element: <LazyRoute><CatalogLayout /></LazyRoute>,
        children: [
          { index: true, element: <LazyRoute><CatalogPage mode="all" /></LazyRoute> },
          { path: 'smartphones', element: <LazyRoute><CatalogPage mode="smartphones" /></LazyRoute> },
          { path: 'laptops', element: <LazyRoute><CatalogPage mode="laptops" /></LazyRoute> },
          { path: 'offers', element: <LazyRoute><CatalogPage mode="offers" /></LazyRoute> },
          { path: 'smartphones/:productId', element: <LazyRoute><ProductDetailPage /></LazyRoute> },
          { path: 'laptops/:productId', element: <LazyRoute><ProductDetailPage /></LazyRoute> },
          { path: 'offers/:productId', element: <LazyRoute><ProductDetailPage promotional /></LazyRoute> },
          { path: 'offers/smartphones/:productId', element: <LazyRoute><ProductDetailPage promotional /></LazyRoute> },
          { path: 'offers/laptops/:productId', element: <LazyRoute><ProductDetailPage promotional /></LazyRoute> },
          { path: ':productId', element: <LazyRoute><ProductDetailPage /></LazyRoute> },
        ],
      },
      { path: 'search', element: <LazyRoute><SearchPage /></LazyRoute> },
      {
        path: 'cart',
        element: <RequireAuth><LazyRoute><CartLayout /></LazyRoute></RequireAuth>,
        children: [
          { index: true, element: <LazyRoute><CartPage /></LazyRoute> },
          { path: 'delivery', element: <LazyRoute><DeliveryPage /></LazyRoute> },
          { path: 'personal-data', element: <LazyRoute><PersonalDataPage /></LazyRoute> },
          { path: 'pay', element: <LazyRoute><PayPage /></LazyRoute> },
        ],
      },
      {
        path: 'profile',
        children: [
          { index: true, element: <LazyRoute><ProfileLandingPage /></LazyRoute> },
          { path: 'sign-up', element: <RequireGuest><LazyRoute><SignUpPage /></LazyRoute></RequireGuest> },
          { path: 'sign-in', element: <RequireGuest><LazyRoute><SignInPage /></LazyRoute></RequireGuest> },
          { path: 'password-reset', element: <RequireGuest><LazyRoute><PasswordResetPage /></LazyRoute></RequireGuest> },
          {
            path: 'account',
            element: <RequireAuth><LazyRoute><AccountLayout /></LazyRoute></RequireAuth>,
            children: [
              { index: true, element: <LazyRoute><AccountOverviewPage /></LazyRoute> },
              { path: 'order-history', element: <LazyRoute><OrderHistoryPage /></LazyRoute> },
              { path: 'expense-dashboard', element: <LazyRoute><ExpenseDashboardPage /></LazyRoute> },
              { path: 'personal', element: <LazyRoute><PersonalPage /></LazyRoute> },
            ],
          },
        ],
      },
      { path: 'support', element: <LazyRoute><SupportPage /></LazyRoute> },
      { path: 'contacts', element: <LazyRoute><ContactsPage /></LazyRoute> },
      { path: 'about', element: <LazyRoute><AboutPage /></LazyRoute> },
      ...developmentRoutes,
      { path: '*', element: <LazyRoute><NotFoundPage /></LazyRoute> },
    ],
  },
])
