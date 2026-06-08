import { useEffect, useState, type ComponentPropsWithoutRef } from 'react'
import { Link, Navigate, NavLink, Outlet } from 'react-router-dom'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { BarChart3, Eye, Package, Pencil, ShieldCheck, Trash2, User } from 'lucide-react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { toast } from 'sonner'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ApiErrorAlert, EmptyState, LoadingRows, PageShell, SectionHeader } from '@/components/app/PageState'
import { orderService, type OrderDto } from '@/contracts/order'
import { userService, type UpsertUserAddressRequest } from '@/contracts/user'
import { assetUrl } from '@/lib/assets'
import { formatDateTime, formatMoney, initials, orderStatusLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { clearSessionState } from '@/lib/session'
import { useAuthStore } from '@/stores/authStore'

const profileSchema = z.object({
  firstName: z.string().min(2),
  lastName: z.string().min(2),
  phone: z.string().optional(),
  birthDate: z.string().optional(),
})

const addressSchema = z.object({
  label: z.string().optional(),
  street: z.string().min(3),
  city: z.string().min(2),
  district: z.string().min(2),
  postalCode: z.string().optional(),
  isDefault: z.boolean(),
})

const CHART_BLUE_PALETTE = ['#aaf2ff', '#80ebff', '#00ddff', '#00b3f4', '#0090ed'] as const
const CHART_TEXT_COLOR = 'var(--foreground)'
const PIE_LABEL_TEXT_COLOR = '#000000'
const PIE_LABEL_HORIZONTAL_PADDING = 12
const LINE_Y_AXIS_WIDTH = 92
const BAR_Y_AXIS_WIDTH = 48
const CHART_TOOLTIP_CONTENT_STYLE = {
  backgroundColor: 'var(--popover)',
  borderColor: 'var(--border)',
  color: CHART_TEXT_COLOR,
}
const CHART_TOOLTIP_TEXT_STYLE = {
  color: CHART_TEXT_COLOR,
}

function formatChartMoney(value: number | string) {
  return formatMoney(value)
}

function estimateSvgTextWidth(text: string) {
  return text.length * 7.2
}

function renderDistributionLabel({
  name,
  value,
  ...labelProps
}: {
  name?: string
  value?: number | string
  x?: number | string
  textAnchor?: string
  cx?: number | string
} & ComponentPropsWithoutRef<'text'>) {
  const labelText = `${name ?? ''}: ${formatChartMoney(typeof value === 'number' || typeof value === 'string' ? value : 0)}`
  const numericX = typeof labelProps.x === 'number' ? labelProps.x : Number(labelProps.x ?? 0)
  const numericCx = typeof labelProps.cx === 'number' ? labelProps.cx : Number(labelProps.cx ?? 0)
  const estimatedWidth = estimateSvgTextWidth(labelText)
  const chartWidth = numericCx > 0 ? numericCx * 2 : 320
  let adjustedX = numericX

  if (labelProps.textAnchor === 'end') {
    adjustedX = Math.max(numericX, estimatedWidth + PIE_LABEL_HORIZONTAL_PADDING)
  }

  if (labelProps.textAnchor === 'start') {
    adjustedX = Math.min(numericX, chartWidth - estimatedWidth - PIE_LABEL_HORIZONTAL_PADDING)
  }

  return (
    <text {...labelProps} x={adjustedX} fill={PIE_LABEL_TEXT_COLOR}>
      {labelText}
    </text>
  )
}

function useProfileQuery(userId?: string | null) {
  return useQuery({
    queryKey: queryKeys.profile(userId),
    queryFn: () => userService.getMe(),
    enabled: Boolean(userId),
  })
}

export function ProfileLandingPage() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())
  return <Navigate to={isAuthenticated ? '/profile/account' : '/profile/sign-in'} replace />
}

export function AccountLayout() {
  const userId = useAuthStore((state) => state.user?.userId)
  const profileQuery = useProfileQuery(userId)
  const hasAdminRole = useAuthStore((state) => state.hasRole('ROLE_ADMIN'))

  const navigationItems = [
    { to: '/profile/account/personal', label: 'Date personale', icon: User },
    { to: '/profile/account/order-history', label: 'Istoric comenzi', icon: Package },
    { to: '/profile/account/expense-dashboard', label: 'Cheltuieli', icon: BarChart3 },
    ...(hasAdminRole ? [{ to: '/profile/account/admin', label: 'Administrare', icon: ShieldCheck }] : []),
  ]

  return (
    <PageShell>
      <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
        <aside className="h-fit rounded-lg border p-4">
          <div className="flex items-center gap-3">
            <Avatar>
              <AvatarFallback>{initials(profileQuery.data?.firstName, profileQuery.data?.lastName, profileQuery.data?.email)}</AvatarFallback>
            </Avatar>
            <div className="min-w-0">
              <p className="truncate font-medium">{profileQuery.data ? `${profileQuery.data.firstName} ${profileQuery.data.lastName}` : 'Cont MEc'}</p>
              <p className="truncate text-xs text-muted-foreground">{profileQuery.data?.email}</p>
            </div>
          </div>
          <Separator className="my-4" />
          <nav className="grid gap-1">
            {navigationItems.map((item) => (
              <Button key={item.to} asChild variant="ghost" className="justify-start">
                <NavLink to={item.to} className={({ isActive }) => (isActive ? 'bg-accent text-accent-foreground' : '')}>
                  <item.icon />
                  {item.label}
                </NavLink>
              </Button>
            ))}
          </nav>
          <Separator className="my-4" />
          <Button
            type="button"
            variant="outline"
            className="w-full"
            onClick={() => {
              clearSessionState()
              globalThis.location.assign('/home')
            }}
          >
            Deconectare
          </Button>
        </aside>
        <div>
          <Outlet />
        </div>
      </div>
    </PageShell>
  )
}

export function AccountOverviewPage() {
  return <Navigate to="/profile/account/personal" replace />
}

export function PersonalPage() {
  const userId = useAuthStore((state) => state.user?.userId)
  const queryClient = useQueryClient()
  const profileQuery = useProfileQuery(userId)
  const [editingAddress, setEditingAddress] = useState<{ index: number | null; values: UpsertUserAddressRequest } | null>(null)
  const profileForm = useForm<z.infer<typeof profileSchema>>({ resolver: zodResolver(profileSchema) })
  const addressForm = useForm<z.infer<typeof addressSchema>>({
    resolver: zodResolver(addressSchema),
    defaultValues: { label: '', street: '', city: '', district: '', postalCode: '', isDefault: false },
  })

  useEffect(() => {
    if (profileQuery.data) {
      profileForm.reset({
        firstName: profileQuery.data.firstName,
        lastName: profileQuery.data.lastName,
        phone: profileQuery.data.phone ?? '',
        birthDate: profileQuery.data.birthDate ?? '',
      })
    }
  }, [profileForm, profileQuery.data])

  useEffect(() => {
    if (editingAddress) {
      addressForm.reset({
        ...editingAddress.values,
        label: editingAddress.values.label ?? '',
        postalCode: editingAddress.values.postalCode ?? '',
      })
    }
  }, [addressForm, editingAddress])

  const updateProfile = useMutation({
    mutationFn: (values: z.infer<typeof profileSchema>) =>
      userService.updateMe({
        firstName: values.firstName,
        lastName: values.lastName,
        phone: values.phone || null,
        birthDate: values.birthDate || null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.profile(userId) })
      toast.success('Profil actualizat')
    },
  })

  const upsertAddress = useMutation({
    mutationFn: (values: z.infer<typeof addressSchema>) =>
      editingAddress?.index === null
        ? userService.addAddress({ ...values, label: values.label || null, postalCode: values.postalCode || null })
        : userService.updateAddress(editingAddress!.index, { ...values, label: values.label || null, postalCode: values.postalCode || null }),
    onSuccess: () => {
      setEditingAddress(null)
      queryClient.invalidateQueries({ queryKey: queryKeys.profile(userId) })
      toast.success('Adresa a fost salvată')
    },
  })

  const deleteAddress = useMutation({
    mutationFn: (index: number) => userService.deleteAddress(index),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.profile(userId) })
      toast.success('Adresa a fost ștearsă')
    },
  })

  return (
    <div className="space-y-6">
      <SectionHeader title="Datele contului" description="Profil și adrese persistente în `user-service`." />
      {profileQuery.isLoading && <LoadingRows count={3} />}
      {profileQuery.isError && <ApiErrorAlert error={profileQuery.error} onRetry={() => profileQuery.refetch()} />}
      <Card className="rounded-lg">
        <CardHeader><CardTitle>Informații personale</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={profileForm.handleSubmit((values) => updateProfile.mutate(values))} className="grid gap-4 md:grid-cols-2">
            {(['firstName', 'lastName', 'phone', 'birthDate'] as const).map((name) => (
              <Field key={name}>
                <FieldLabel htmlFor={name}>{name}</FieldLabel>
                <Input id={name} type={name === 'birthDate' ? 'date' : 'text'} {...profileForm.register(name)} />
                <FieldError>{profileForm.formState.errors[name]?.message}</FieldError>
              </Field>
            ))}
            <Field>
              <FieldLabel>Email</FieldLabel>
              <Input value={profileQuery.data?.email ?? ''} disabled />
            </Field>
            <div className="md:col-span-2">
              <Button type="submit" disabled={updateProfile.isPending}>Salvează modificările</Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card className="rounded-lg">
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle>Adresele mele</CardTitle>
          <Dialog open={Boolean(editingAddress)} onOpenChange={(open) => !open && setEditingAddress(null)}>
            <DialogTrigger asChild>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditingAddress({ index: null, values: { label: '', street: '', city: '', district: '', postalCode: '', isDefault: false } })}
              >
                Adaugă adresă
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Adresă</DialogTitle>
                <DialogDescription>Configurează o adresă de livrare persistentă pentru checkout.</DialogDescription>
              </DialogHeader>
              <form onSubmit={addressForm.handleSubmit((values) => upsertAddress.mutate(values))} className="space-y-3">
                {(['label', 'street', 'city', 'district', 'postalCode'] as const).map((name) => (
                  <Field key={name}>
                    <FieldLabel>{name}</FieldLabel>
                    <Input {...addressForm.register(name)} />
                    <FieldError>{addressForm.formState.errors[name]?.message}</FieldError>
                  </Field>
                ))}
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" {...addressForm.register('isDefault')} />
                  <span>Adresă implicită</span>
                </label>
                <Button type="submit" className="w-full">Salvează</Button>
              </form>
            </DialogContent>
          </Dialog>
        </CardHeader>
        <CardContent className="grid gap-3">
          {profileQuery.data?.addresses?.map((address, index) => (
            <div key={`${address.street}-${index}`} className="flex flex-col gap-3 rounded-lg border p-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="font-medium">{address.label || 'Adresă'}</p>
                <p className="text-sm text-muted-foreground">{address.street}, {address.city}, {address.district}</p>
              </div>
              <div className="flex gap-2">
                <Button variant="ghost" size="icon" onClick={() => setEditingAddress({ index, values: address })}>
                  <Pencil /><span className="sr-only">Editează</span>
                </Button>
                <Button variant="ghost" size="icon" onClick={() => deleteAddress.mutate(index)}>
                  <Trash2 /><span className="sr-only">Șterge</span>
                </Button>
              </div>
            </div>
          ))}
          {!profileQuery.data?.addresses?.length && <p className="text-sm text-muted-foreground">Nu există adrese salvate.</p>}
        </CardContent>
      </Card>
    </div>
  )
}

export function OrderHistoryPage() {
  const userId = useAuthStore((state) => state.user?.userId)
  const [page, setPage] = useState(0)
  const ordersQuery = useQuery({
    queryKey: queryKeys.orders(userId, page),
    queryFn: () => orderService.listMine({ page, size: 10, sort: 'createdAt', direction: 'desc' }),
    enabled: Boolean(userId),
  })

  return (
    <div>
      <SectionHeader title="Istoric comenzi" description="Comenzi personale returnate de `order-service`." />
      {ordersQuery.isLoading && <LoadingRows count={4} />}
      {ordersQuery.isError && <ApiErrorAlert error={ordersQuery.error} onRetry={() => ordersQuery.refetch()} />}
      {ordersQuery.isSuccess && ordersQuery.data.data.length === 0 && (
        <EmptyState icon={<Package />} title="Nu ai plasat nicio comandă" action={<Button asChild><Link to="/home">Descoperă produsele</Link></Button>} />
      )}
      {ordersQuery.data?.data.length ? (
        <div className="overflow-hidden rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Număr</TableHead>
                <TableHead>Data</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Total</TableHead>
                <TableHead className="text-right">Detalii</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {ordersQuery.data.data.map((order) => (
                <TableRow key={order.id}>
                  <TableCell className="font-mono text-xs">{order.orderNumber}</TableCell>
                  <TableCell>{formatDateTime(order.createdAt)}</TableCell>
                  <TableCell><Badge variant="secondary">{orderStatusLabel(order.status)}</Badge></TableCell>
                  <TableCell>{formatMoney(order.totalAmount, order.currency)}</TableCell>
                  <TableCell className="text-right"><OrderDialog order={order} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      ) : null}
      {ordersQuery.data && ordersQuery.data.totalPages > 1 && (
        <div className="mt-4 flex justify-center gap-2">
          <Button variant="outline" disabled={ordersQuery.data.first} onClick={() => setPage((value) => value - 1)}>Anterior</Button>
          <Button variant="outline" disabled={ordersQuery.data.last} onClick={() => setPage((value) => value + 1)}>Următor</Button>
        </div>
      )}
    </div>
  )
}

function OrderDialog({ order }: Readonly<{ order: OrderDto }>) {
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="ghost" size="icon"><Eye /><span className="sr-only">Detalii</span></Button>
      </DialogTrigger>
      <DialogContent className="max-w-2xl">
        <DialogHeader><DialogTitle>Comanda {order.orderNumber}</DialogTitle></DialogHeader>
        <div className="space-y-4">
          <Table>
            <TableBody>
              {order.items.map((item) => (
                <TableRow key={item.productId}>
                  <TableCell><img src={assetUrl(item.imageUrl)} alt="" className="h-12 w-12 object-contain" /></TableCell>
                  <TableCell>{item.name}</TableCell>
                  <TableCell>{item.quantity} x {formatMoney(item.unitPrice, order.currency)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <p className="text-sm text-muted-foreground">{order.deliveryAddress.street}, {order.deliveryAddress.city}</p>
          <p className="font-semibold">Total: {formatMoney(order.totalAmount, order.currency)}</p>
        </div>
      </DialogContent>
    </Dialog>
  )
}

export function ExpenseDashboardPage() {
  const userId = useAuthStore((state) => state.user?.userId)
  const ordersQuery = useQuery({
    queryKey: queryKeys.ordersDashboard(userId),
    queryFn: () => orderService.listMine({ page: 0, size: 100, sort: 'createdAt', direction: 'desc' }),
    enabled: Boolean(userId),
  })
  const orders = ordersQuery.data?.data ?? []
  const total = orders.reduce((sum, order) => sum + Number(order.totalAmount), 0)
  const monthlyMap = new Map<string, number>()
  orders.forEach((order) => {
    const month = order.createdAt.slice(0, 7)
    monthlyMap.set(month, (monthlyMap.get(month) ?? 0) + Number(order.totalAmount))
  })
  const monthly = [...monthlyMap.entries()].map(([month, amount]) => ({ month, amount }))
  const monthlyOrderCounts = monthly.map((item, index) => ({
    ...item,
    count: orders.filter((order) => order.createdAt.startsWith(item.month)).length,
    fill: CHART_BLUE_PALETTE[(index + 2) % CHART_BLUE_PALETTE.length],
  }))
  const categoryMap = new Map<string, number>()
  orders.flatMap((order) => order.items).forEach((item) => {
    const key = item.name.toLowerCase().includes('book') || item.name.toLowerCase().includes('matebook') ? 'Laptopuri' : 'Smartphone-uri'
    categoryMap.set(key, (categoryMap.get(key) ?? 0) + Number(item.unitPrice) * item.quantity)
  })
  const byCategory = [...categoryMap.entries()].map(([name, value], index) => ({
    name,
    value,
    fill: CHART_BLUE_PALETTE[index % CHART_BLUE_PALETTE.length],
  }))

  return (
    <div className="space-y-6">
      <SectionHeader title="Tablou cheltuieli" description="Agregări client-side din istoricul comenzilor." />
      {ordersQuery.isLoading && <LoadingRows count={3} />}
      {ordersQuery.isError && <ApiErrorAlert error={ordersQuery.error} onRetry={() => ordersQuery.refetch()} />}
      {ordersQuery.isSuccess && orders.length === 0 && (
        <EmptyState
          icon={<BarChart3 />}
          title="Nu există cheltuieli de afișat"
          description="Tabloul de bord devine disponibil după plasarea primei comenzi."
          action={
            <Button asChild>
              <Link to="/home">Descoperă produsele</Link>
            </Button>
          }
        />
      )}
      {ordersQuery.isSuccess && orders.length > 0 && (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            <Card className="rounded-lg"><CardContent className="pt-4"><p className="text-sm text-muted-foreground">Total cheltuieli</p><p className="text-2xl font-semibold">{formatMoney(total)}</p></CardContent></Card>
            <Card className="rounded-lg"><CardContent className="pt-4"><p className="text-sm text-muted-foreground">Comenzi</p><p className="text-2xl font-semibold">{orders.length}</p></CardContent></Card>
            <Card className="rounded-lg"><CardContent className="pt-4"><p className="text-sm text-muted-foreground">Medie comandă</p><p className="text-2xl font-semibold">{formatMoney(total / orders.length)}</p></CardContent></Card>
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <Card className="rounded-lg"><CardHeader><CardTitle>Cheltuieli lunare</CardTitle></CardHeader><CardContent className="h-72"><ResponsiveContainer initialDimension={{ width: 320, height: 288 }}><LineChart data={monthly}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="month" /><YAxis width={LINE_Y_AXIS_WIDTH} tick={{ fill: CHART_TEXT_COLOR }} tickFormatter={formatChartMoney} /><ChartTooltip contentStyle={CHART_TOOLTIP_CONTENT_STYLE} itemStyle={CHART_TOOLTIP_TEXT_STYLE} labelStyle={CHART_TOOLTIP_TEXT_STYLE} formatter={(value) => formatChartMoney(typeof value === 'number' || typeof value === 'string' ? value : 0)} /><Line type="monotone" dataKey="amount" stroke={CHART_BLUE_PALETTE[4]} strokeWidth={3} dot={{ fill: CHART_BLUE_PALETTE[3], stroke: CHART_BLUE_PALETTE[4] }} activeDot={{ r: 6, fill: CHART_BLUE_PALETTE[4] }} /></LineChart></ResponsiveContainer></CardContent></Card>
            <Card className="rounded-lg"><CardHeader><CardTitle>Distribuție</CardTitle></CardHeader><CardContent className="h-72"><ResponsiveContainer initialDimension={{ width: 320, height: 288 }}><PieChart><Pie data={byCategory} dataKey="value" nameKey="name" label={renderDistributionLabel} /><ChartTooltip contentStyle={CHART_TOOLTIP_CONTENT_STYLE} itemStyle={CHART_TOOLTIP_TEXT_STYLE} labelStyle={CHART_TOOLTIP_TEXT_STYLE} formatter={(value) => formatChartMoney(typeof value === 'number' || typeof value === 'string' ? value : 0)} /></PieChart></ResponsiveContainer></CardContent></Card>
            <Card className="rounded-lg lg:col-span-2"><CardHeader><CardTitle>Număr comenzi</CardTitle></CardHeader><CardContent className="h-72"><ResponsiveContainer initialDimension={{ width: 640, height: 288 }}><BarChart data={monthlyOrderCounts}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="month" /><YAxis width={BAR_Y_AXIS_WIDTH} tick={{ fill: CHART_TEXT_COLOR }} /><ChartTooltip contentStyle={CHART_TOOLTIP_CONTENT_STYLE} itemStyle={CHART_TOOLTIP_TEXT_STYLE} labelStyle={CHART_TOOLTIP_TEXT_STYLE} /><Bar dataKey="count" fill={CHART_BLUE_PALETTE[2]} /></BarChart></ResponsiveContainer></CardContent></Card>
          </div>
        </>
      )}
    </div>
  )
}
