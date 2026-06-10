import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useTheme } from 'next-themes'
import { useQuery } from '@tanstack/react-query'
import { Menu, Moon, Search, ShoppingCart, Sun, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { productService, type ProductDto } from '@/contracts/product'
import { assetUrl } from '@/lib/assets'
import { queryKeys } from '@/lib/queryKeys'
import { clearSessionState } from '@/lib/session'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'

const navItems = [
  { to: '/categories/smartphones', label: 'Smartphone-uri' },
  { to: '/categories/laptops', label: 'Laptop-uri' },
  { to: '/categories/offers', label: 'Oferte' },
]

function productHref(product: ProductDto) {
  if (product.categorySlug === 'laptops' || product.categorySlug === 'smartphones') {
    return `/categories/${product.categorySlug}/${product.slug}`
  }
  return `/categories/${product.slug}`
}

function SearchForm({ compact = false, onSubmitDone }: { compact?: boolean; onSubmitDone?: () => void }) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [open, setOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300)
    return () => clearTimeout(timer)
  }, [query])

  const suggestionsQuery = useQuery({
    queryKey: queryKeys.searchSuggestions(debouncedQuery),
    queryFn: () => productService.search({ q: debouncedQuery, page: 0, size: 5 }),
    enabled: debouncedQuery.trim().length > 1,
    staleTime: 30_000,
  })

  const suggestions: ProductDto[] = suggestionsQuery.data?.data ?? []
  const showDropdown = open && query.trim().length > 1 && suggestions.length > 0

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const normalized = query.trim()
    if (normalized) {
      setOpen(false)
      setQuery('')
      navigate(`/search?q=${encodeURIComponent(normalized)}`)
      onSubmitDone?.()
    }
  }

  const selectSuggestion = (product: ProductDto) => {
    setQuery('')
    setOpen(false)
    navigate(productHref(product))
    onSubmitDone?.()
  }

  return (
    <form onSubmit={submit} className={compact ? 'w-full' : 'hidden min-w-64 flex-1 md:block'}>
      <div className="relative">
        <Search className="pointer-events-none absolute left-2 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          ref={inputRef}
          value={query}
          onChange={(event) => { setQuery(event.target.value); setOpen(true) }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          className="pl-8"
          placeholder="Caută telefoane, laptopuri, branduri"
          aria-label="Caută produse"
          autoComplete="off"
        />
        {showDropdown && (
          <div className="absolute left-0 right-0 top-full z-50 mt-1 overflow-hidden rounded-lg border bg-popover shadow-lg">
            {suggestions.map((product) => (
              <button
                key={product.id}
                type="button"
                className="flex w-full items-center gap-3 px-3 py-2 text-left text-sm hover:bg-accent"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => selectSuggestion(product)}
              >
                <img
                  src={assetUrl(product.imageUrls[0] ?? null)}
                  alt=""
                  className="h-8 w-8 shrink-0 rounded object-contain"
                />
                <div className="min-w-0">
                  <p className="truncate font-medium">{product.name}</p>
                  <p className="truncate text-xs text-muted-foreground">{product.brand}</p>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </form>
  )
}

function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme()
  const isDark = resolvedTheme === 'dark'

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button type="button" variant="ghost" size="icon" onClick={() => setTheme(isDark ? 'light' : 'dark')}>
          {isDark ? <Sun /> : <Moon />}
          <span className="sr-only">Comută tema</span>
        </Button>
      </TooltipTrigger>
      <TooltipContent>Comută tema</TooltipContent>
    </Tooltip>
  )
}

export default function Navbar() {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const totalItems = useCartStore((state) => state.totalItems())
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())
  const email = useAuthStore((state) => state.user?.email)

  const logout = () => {
    clearSessionState()
    navigate('/home')
  }

  return (
    <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
      <nav className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4 sm:px-6 lg:px-8">
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <Button type="button" variant="ghost" size="icon" className="md:hidden">
              <Menu />
              <span className="sr-only">Deschide meniul</span>
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-80">
            <SheetHeader>
              <SheetTitle>Modern Electronics Commerce</SheetTitle>
            </SheetHeader>
            <div className="mt-6 space-y-4">
              <SearchForm compact onSubmitDone={() => setOpen(false)} />
              <div className="grid gap-2">
                {[{ to: '/home', label: 'Acasă' }, ...navItems, { to: '/support', label: 'Suport' }].map((item) => (
                  <Button key={item.to} asChild variant="ghost" className="justify-start">
                    <Link to={item.to} onClick={() => setOpen(false)}>
                      {item.label}
                    </Link>
                  </Button>
                ))}
              </div>
            </div>
          </SheetContent>
        </Sheet>

        <Link to="/home" className="flex min-w-fit items-center gap-2">
          <img src="/static/assets/icons/prod-icons/dark-theme-logo.svg" alt="" className="hidden h-12 w-32 dark:block" />
          <img src="/static/assets/icons/prod-icons/white-theme-logo.svg" alt="" className="h-12 w-32 dark:hidden" />
        </Link>

        <div className="hidden items-center gap-1 md:flex">
          {navItems.map((item) => (
            <Button key={item.to} asChild variant="ghost">
              <NavLink to={item.to} className={({ isActive }) => (isActive ? 'text-primary' : '')}>
                {item.label}
              </NavLink>
            </Button>
          ))}
        </div>

        <SearchForm />

        <div className="ml-auto flex items-center gap-1">
          <ThemeToggle />
          <Tooltip>
            <TooltipTrigger asChild>
              <Button asChild variant="ghost" size="icon" className="relative">
                <Link to="/cart">
                  <ShoppingCart />
                  {totalItems > 0 && (
                    <span className="absolute -right-1 -top-1 grid min-w-5 place-items-center rounded-full bg-primary px-1 text-[10px] text-primary-foreground">
                      {totalItems}
                    </span>
                  )}
                  <span className="sr-only">Coș</span>
                </Link>
              </Button>
            </TooltipTrigger>
            <TooltipContent>Coș</TooltipContent>
          </Tooltip>
          <Button asChild variant="ghost" size="icon">
            <Link to={isAuthenticated ? '/profile/account' : '/profile/sign-in'} aria-label={email ?? 'Cont'}>
              <UserRound />
            </Link>
          </Button>
          {isAuthenticated && (
            <Button type="button" variant="outline" size="sm" onClick={logout} className="hidden sm:inline-flex">
              Deconectare
            </Button>
          )}
        </div>
      </nav>
    </header>
  )
}
