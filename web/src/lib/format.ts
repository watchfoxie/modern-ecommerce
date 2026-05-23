import { format, parseISO } from 'date-fns'

export function formatMoney(value: number | string | null | undefined, currency = 'MDL') {
  const amount = Number(value ?? 0)
  return new Intl.NumberFormat('ro-MD', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(Number.isFinite(amount) ? amount : 0)
}

export function formatDateTime(value?: string | null) {
  if (!value) {
    return 'Nedisponibil'
  }

  try {
    return format(parseISO(value), 'dd.MM.yyyy HH:mm')
  } catch {
    return value
  }
}

export function discountPercent(price?: number | null, promotionalPrice?: number | null) {
  if (!price || !promotionalPrice || promotionalPrice >= price) {
    return null
  }

  return Math.round((1 - promotionalPrice / price) * 100)
}

export function orderStatusLabel(status: string) {
  const labels: Record<string, string> = {
    CREATED: 'Creată',
    CONFIRMED: 'Confirmată',
    PROCESSING: 'În procesare',
    SHIPPED: 'Expediată',
    DELIVERED: 'Livrată',
    CANCELLED: 'Anulată',
    PENDING: 'În așteptare',
    COMPLETED: 'Finalizată',
    FAILED: 'Eșuată',
  }

  return labels[status] ?? status
}

export function initials(firstName?: string, lastName?: string, email?: string) {
  const value = `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.trim()
  return (value || email?.slice(0, 2) || 'ME').toUpperCase()
}
