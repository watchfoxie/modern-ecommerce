import { NavLink, Outlet } from 'react-router-dom'
import { Laptop, Percent, Smartphone } from 'lucide-react'
import { cn } from '@/lib/utils'

const catalogNav = [
  { to: '/categories', label: 'Toate', icon: Smartphone, end: true },
  { to: '/categories/smartphones', label: 'Smartphone-uri', icon: Smartphone },
  { to: '/categories/laptops', label: 'Laptop-uri', icon: Laptop },
  { to: '/categories/offers', label: 'Oferte', icon: Percent },
]

export default function CatalogLayout() {
  return (
    <>
      <div className="border-b bg-muted/20">
        <div className="mx-auto flex w-full max-w-7xl gap-2 overflow-x-auto px-4 py-3 sm:px-6 lg:px-8">
          {catalogNav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'inline-flex h-10 shrink-0 items-center gap-2 rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-background hover:text-foreground',
                  isActive && 'bg-background text-foreground shadow-xs',
                )
              }
            >
              <item.icon className="size-4" />
              {item.label}
            </NavLink>
          ))}
        </div>
      </div>
      <Outlet />
    </>
  )
}
