import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { CreditCard, ShoppingCart, Truck, UserRound } from 'lucide-react'
import { cn } from '@/lib/utils'

const steps = [
  { to: '/cart', label: 'Coș', icon: ShoppingCart, end: true },
  { to: '/cart/delivery', label: 'Livrare', icon: Truck },
  { to: '/cart/personal-data', label: 'Date', icon: UserRound },
  { to: '/cart/pay', label: 'Plată', icon: CreditCard },
]

export default function CartLayout() {
  const location = useLocation()

  return (
    <>
      <div className="border-b bg-muted/20">
        <div className="mx-auto grid w-full max-w-7xl gap-2 px-4 py-3 sm:grid-cols-4 sm:px-6 lg:px-8">
          {steps.map((step, index) => {
            const isPast = steps.findIndex((item) => item.to === location.pathname) > index

            return (
              <NavLink
                key={step.to}
                to={step.to}
                end={step.end}
                className={({ isActive }) =>
                  cn(
                    'flex h-11 items-center gap-2 rounded-md border px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-background hover:text-foreground',
                    (isActive || isPast) && 'border-primary/40 bg-background text-foreground',
                  )
                }
              >
                <step.icon className="size-4" />
                {step.label}
              </NavLink>
            )
          })}
        </div>
      </div>
      <Outlet />
    </>
  )
}
