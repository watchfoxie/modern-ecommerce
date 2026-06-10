export const queryKeys = {
  categories: ['categories'] as const,
  products: (filters: unknown) => ['products', filters] as const,
  product: (slug: string) => ['product', slug] as const,
  search: (query: string, page: number) => ['search', query, page] as const,
  searchSuggestions: (query: string) => ['search-suggestions', query] as const,
  cart: (userId?: string | null) => ['cart', userId ?? 'anonymous'] as const,
  profile: (userId?: string | null) => ['profile', userId ?? 'anonymous'] as const,
  orders: (userId?: string | null, page?: number | string) => ['orders', userId ?? 'anonymous', page ?? 'mine'] as const,
  ordersDashboard: (userId?: string | null) => ['orders', userId ?? 'anonymous', 'dashboard'] as const,
}
