import { useEffect } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Textarea } from '@/components/ui/textarea'
import { ApiErrorAlert, EmptyState, LoadingRows, PageShell, SectionHeader } from '@/components/app/PageState'
import { cartService } from '@/contracts/cart'
import { orderService } from '@/contracts/order'
import { userService } from '@/contracts/user'
import { formatMoney } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { useAuthStore } from '@/stores/authStore'
import { useCartStore } from '@/stores/cartStore'
import { useCheckoutStore } from '@/stores/checkoutStore'

const addressSchema = z.object({
  recipientName: z.string().min(2, 'Introduceți numele destinatarului'),
  recipientPhone: z.string().min(6, 'Introduceți un număr valid'),
  city: z.string().min(2, 'Introduceți orașul'),
  district: z.string().min(2, 'Introduceți raionul/sectorul'),
  street: z.string().min(4, 'Introduceți strada și numărul'),
  postalCode: z.string().optional(),
})

const contactSchema = z.object({
  firstName: z.string().min(2, 'Prenumele este obligatoriu'),
  lastName: z.string().min(2, 'Numele este obligatoriu'),
  email: z.string().email('Email invalid'),
  phone: z.string().min(6, 'Telefon invalid'),
})

const paymentSchema = z.object({
  method: z.enum(['CARD', 'CASH']),
  transactionId: z.string().optional(),
  notes: z.string().max(500).optional(),
})

export function DeliveryPage() {
  const navigate = useNavigate()
  const saved = useCheckoutStore((state) => state.deliveryAddress)
  const setDeliveryAddress = useCheckoutStore((state) => state.setDeliveryAddress)
  const form = useForm<z.infer<typeof addressSchema>>({
    resolver: zodResolver(addressSchema),
    defaultValues: saved ? { ...saved, postalCode: saved.postalCode ?? '' } : {
      recipientName: '',
      recipientPhone: '',
      city: 'Chișinău',
      district: 'Chișinău',
      street: '',
      postalCode: '',
    },
  })

  const submit = form.handleSubmit((values) => {
    setDeliveryAddress({ ...values, postalCode: values.postalCode || null })
    navigate('/cart/personal-data')
  })

  return (
    <PageShell>
      <SectionHeader title="Livrare" description="Adresa este transmisă către `order-service` ca snapshot de checkout." />
      <Card className="mx-auto max-w-2xl rounded-lg">
        <CardHeader>
          <CardTitle>Adresă livrare</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <FieldGroup>
              {(['recipientName', 'recipientPhone', 'city', 'district', 'street', 'postalCode'] as const).map((name) => (
                <Field key={name}>
                  <FieldLabel htmlFor={name}>{name === 'postalCode' ? 'Cod poștal' : name}</FieldLabel>
                  <Input id={name} {...form.register(name)} aria-invalid={Boolean(form.formState.errors[name])} />
                  <FieldError>{form.formState.errors[name]?.message}</FieldError>
                </Field>
              ))}
            </FieldGroup>
            <Button type="submit" className="w-full">Continuă</Button>
          </form>
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function PersonalDataPage() {
  const navigate = useNavigate()
  const userId = useAuthStore((state) => state.user?.userId)
  const setContact = useCheckoutStore((state) => state.setContact)
  const saved = useCheckoutStore((state) => state.contact)
  const profileQuery = useQuery({
    queryKey: queryKeys.profile,
    queryFn: () => userService.getMe(),
    enabled: Boolean(userId),
  })
  const form = useForm<z.infer<typeof contactSchema>>({
    resolver: zodResolver(contactSchema),
    defaultValues: saved ?? { firstName: '', lastName: '', email: '', phone: '' },
  })

  useEffect(() => {
    if (profileQuery.data && !saved) {
      form.reset({
        firstName: profileQuery.data.firstName,
        lastName: profileQuery.data.lastName,
        email: profileQuery.data.email,
        phone: profileQuery.data.phone ?? '',
      })
    }
  }, [form, profileQuery.data, saved])

  const submit = form.handleSubmit((values) => {
    setContact(values)
    navigate('/cart/pay')
  })

  return (
    <PageShell>
      <SectionHeader title="Date personale" description="Datele sunt precompletate din `user-service`, acolo unde există." />
      {profileQuery.isLoading && <LoadingRows count={2} />}
      {profileQuery.isError && <ApiErrorAlert error={profileQuery.error} onRetry={() => profileQuery.refetch()} />}
      <Card className="mx-auto max-w-2xl rounded-lg">
        <CardContent className="pt-4">
          <form onSubmit={submit} className="space-y-4">
            {(['firstName', 'lastName', 'email', 'phone'] as const).map((name) => (
              <Field key={name}>
                <FieldLabel htmlFor={name}>{name}</FieldLabel>
                <Input id={name} {...form.register(name)} aria-invalid={Boolean(form.formState.errors[name])} />
                <FieldError>{form.formState.errors[name]?.message}</FieldError>
              </Field>
            ))}
            <Button type="submit" className="w-full">Continuă spre plată</Button>
          </form>
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function PayPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const userId = useAuthStore((state) => state.user?.userId)
  const address = useCheckoutStore((state) => state.deliveryAddress)
  const setPayment = useCheckoutStore((state) => state.setPayment)
  const setNotes = useCheckoutStore((state) => state.setNotes)
  const resetCheckout = useCheckoutStore((state) => state.resetCheckout)
  const clearLocalCart = useCartStore((state) => state.clearCart)
  const cartQuery = useQuery({ queryKey: queryKeys.cart(userId), queryFn: () => cartService.getMe(), enabled: Boolean(userId) })
  const form = useForm<z.infer<typeof paymentSchema>>({
    resolver: zodResolver(paymentSchema),
    defaultValues: { method: 'CARD', transactionId: '', notes: '' },
  })

  const createOrder = useMutation({
    mutationFn: (values: z.infer<typeof paymentSchema>) =>
      orderService.create({
        deliveryAddress: address!,
        payment: { method: values.method, transactionId: values.transactionId || null },
        notes: values.notes || undefined,
      }),
    onSuccess: (response) => {
      clearLocalCart()
      resetCheckout()
      queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
      queryClient.invalidateQueries({ queryKey: queryKeys.orders() })
      toast.success(`Comanda ${response.orderNumber} a fost acceptată`)
      navigate('/profile/account/order-history')
    },
  })

  if (!address) {
    return <Navigate to="/cart/delivery" replace />
  }

  if (cartQuery.isLoading) {
    return (
      <PageShell>
        <SectionHeader title="Plată" description="Comanda este derivată server-side din coșul persistent." />
        <LoadingRows count={3} />
      </PageShell>
    )
  }

  if (cartQuery.isError) {
    return (
      <PageShell>
        <SectionHeader title="Plată" description="Comanda este derivată server-side din coșul persistent." />
        <ApiErrorAlert error={cartQuery.error} onRetry={() => cartQuery.refetch()} />
      </PageShell>
    )
  }

  if (cartQuery.data?.items.length === 0) {
    return (
      <PageShell>
        <SectionHeader title="Plată" description="Comanda este derivată server-side din coșul persistent." />
        <EmptyState
          title="Coșul este gol"
          description="Adăugați produse înainte de plasarea unei comenzi."
          action={
            <Button asChild>
              <Link to="/categories">Înapoi la catalog</Link>
            </Button>
          }
        />
      </PageShell>
    )
  }

  const total = cartQuery.data?.items.reduce((sum, item) => sum + Number(item.priceAtAdd) * item.quantity, 0) ?? 0

  const submit = form.handleSubmit((values) => {
    setPayment({ method: values.method, transactionId: values.transactionId || null })
    setNotes(values.notes ?? '')
    createOrder.mutate(values)
  })

  return (
    <PageShell>
      <SectionHeader title="Plată" description="Comanda este derivată server-side din coșul persistent." />
      {createOrder.isError && <ApiErrorAlert error={createOrder.error} />}
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <Card className="rounded-lg">
          <CardContent className="pt-4">
            <form onSubmit={submit} className="space-y-5">
              <Field>
                <FieldLabel>Metodă plată</FieldLabel>
                <RadioGroup
                  defaultValue="CARD"
                  onValueChange={(value) => form.setValue('method', value as 'CARD' | 'CASH')}
                  className="grid gap-2"
                >
                  <label className="flex items-center gap-2 rounded-lg border p-3">
                    <RadioGroupItem value="CARD" />
                    Card bancar
                  </label>
                  <label className="flex items-center gap-2 rounded-lg border p-3">
                    <RadioGroupItem value="CASH" />
                    Numerar la livrare
                  </label>
                </RadioGroup>
              </Field>
              <Field>
                <FieldLabel htmlFor="transactionId">ID tranzacție</FieldLabel>
                <Input id="transactionId" {...form.register('transactionId')} placeholder="Opțional" />
              </Field>
              <Field>
                <FieldLabel htmlFor="notes">Observații</FieldLabel>
                <Textarea id="notes" {...form.register('notes')} />
                <FieldError>{form.formState.errors.notes?.message}</FieldError>
              </Field>
              <Button type="submit" className="w-full" disabled={createOrder.isPending || !cartQuery.data?.items.length}>
                <CheckCircle2 />
                Plasează comanda
              </Button>
            </form>
          </CardContent>
        </Card>
        <aside className="h-fit rounded-lg border p-5">
          <h2 className="font-medium">Total checkout</h2>
          <p className="mt-3 text-2xl font-semibold">{formatMoney(total)}</p>
          <p className="mt-2 text-sm text-muted-foreground">{cartQuery.data?.items.length ?? 0} poziții în coș</p>
          <Button asChild variant="outline" className="mt-5 w-full">
            <Link to="/cart">Revizuiește coșul</Link>
          </Button>
        </aside>
      </div>
    </PageShell>
  )
}
