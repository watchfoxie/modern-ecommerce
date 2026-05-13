export const queryKeys = {
  categories: ['categories'] as const,
  products: (filters: unknown) => ['products', filters] as const,
  product: (slug: string) => ['product', slug] as const,
  search: (query: string, page: number) => ['search', query, page] as const,
  cart: (userId?: string | null) => ['cart', userId ?? 'anonymous'] as const,
  profile: ['profile'] as const,
  orders: (page?: number | string) => ['orders', page ?? 'mine'] as const,
  ordersDashboard: ['orders', 'dashboard'] as const,
}
