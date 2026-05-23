import { api } from '@/config/axios'
import type { CurrencyCode, IsoDateTimeString, PagedResponseDto, PageRequestParams } from './common'

export type OrderStatus = 'CREATED' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED'
export type PaymentMethod = 'CARD' | 'CASH'

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
  method: PaymentMethod
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
    method: PaymentMethod
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
    const response = await api.post<OrderAcceptedResponse>('/order-service/v1/orders', request)
    return response.data
  },

  async listMine(params?: PageRequestParams): Promise<PagedResponseDto<OrderDto>> {
    const response = await api.get<PagedResponseDto<OrderDto>>('/order-service/v1/orders', { params })
    return response.data
  },

  async getByOrderId(orderId: string): Promise<OrderDto> {
    const response = await api.get<OrderDto>(`/order-service/v1/orders/${orderId}`)
    return response.data
  },

  async listAll(params?: Pick<PageRequestParams, 'page' | 'size'> & { status?: string }): Promise<PagedResponseDto<OrderDto>> {
    const response = await api.get<PagedResponseDto<OrderDto>>('/order-service/v1/orders/all', { params })
    return response.data
  },

  async updateStatus(orderId: string, request: UpdateOrderStatusRequest): Promise<OrderDto> {
    const response = await api.patch<OrderDto>(`/order-service/v1/orders/${orderId}/status`, request)
    return response.data
  },
}
