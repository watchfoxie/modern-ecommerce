import { api } from '@/config/axios'
import type { CurrencyCode, IsoDateTimeString, PagedResponseDto, PageRequestParams } from './common'

export type OrderStatus = 'CREATED' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | string
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED' | string

export interface OrderAddressDto {
  recipientName: string
  recipientPhone: string
  city: string
  district: string
  street: string
  postalCode?: string | null
}

export interface OrderItemDto {
  productId: string
  name: string
  brand: string
  imageUrl: string
  quantity: number
  unitPrice: number
}

export interface OrderPaymentDto {
  method: string
  status: PaymentStatus
  transactionId?: string | null
}

export interface OrderDto {
  id: string
  orderNumber: string
  userId: string
  items: OrderItemDto[]
  deliveryAddress: OrderAddressDto
  payment: OrderPaymentDto
  status: OrderStatus
  totalAmount: number
  currency: CurrencyCode
  notes?: string | null
  createdAt: IsoDateTimeString
  updatedAt: IsoDateTimeString
}

export interface CreateOrderRequest {
  deliveryAddress: OrderAddressDto
  payment: {
    method: string
    transactionId?: string | null
  }
  notes?: string
}

export interface OrderAcceptedResponse {
  status: 'ACCEPTED' | string
  orderId: string
  orderNumber: string
  message: string
}

export interface UpdateOrderStatusRequest {
  status: OrderStatus
}

export const orderService = {
  async create(request: CreateOrderRequest): Promise<OrderAcceptedResponse> {
    const response = await api.post<OrderAcceptedResponse>('/order-service/orders', request)
    return response.data
  },

  async listMine(params?: PageRequestParams): Promise<PagedResponseDto<OrderDto>> {
    const response = await api.get<PagedResponseDto<OrderDto>>('/order-service/orders', { params })
    return response.data
  },

  async getByOrderId(orderId: string): Promise<OrderDto> {
    const response = await api.get<OrderDto>(`/order-service/orders/${orderId}`)
    return response.data
  },

  async listAll(params?: Pick<PageRequestParams, 'page' | 'size'> & { status?: string }): Promise<PagedResponseDto<OrderDto>> {
    const response = await api.get<PagedResponseDto<OrderDto>>('/order-service/orders/all', { params })
    return response.data
  },

  async updateStatus(orderId: string, request: UpdateOrderStatusRequest): Promise<OrderDto> {
    const response = await api.patch<OrderDto>(`/order-service/orders/${orderId}/status`, request)
    return response.data
  },
}
