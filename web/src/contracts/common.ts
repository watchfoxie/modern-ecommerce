export type IsoDateString = string
export type IsoDateTimeString = string

export interface PagedResponseDto<T> {
  data: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface PageRequestParams {
  page?: number
  size?: number
  sort?: string
  direction?: 'asc' | 'desc' | string
}

export type CurrencyCode = 'MDL' | string
