import { isAxiosError } from 'axios'

export interface ProblemDetailDto {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: string[] | Record<string, string[]>
  [extension: string]: unknown
}

export const isProblemDetailDto = (value: unknown): value is ProblemDetailDto => {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.type === 'string' ||
    typeof candidate.title === 'string' ||
    typeof candidate.status === 'number' ||
    typeof candidate.detail === 'string'
  )
}

export const toProblemDetailDto = (error: unknown): ProblemDetailDto => {
  if (isAxiosError(error) && isProblemDetailDto(error.response?.data)) {
    return error.response.data
  }

  if (error instanceof Error) {
    return {
      title: 'Request failed',
      detail: error.message,
    }
  }

  return {
    title: 'Request failed',
    detail: 'An unexpected error occurred.',
  }
}
