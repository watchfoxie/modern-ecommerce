import { describe, expect, it } from 'vitest'
import { fieldErrors, problemDetail, problemTitle } from '@/lib/problem'
import { isProblemDetailDto, toProblemDetailDto } from '@/contracts/problem-detail'

describe('ProblemDetail frontend normalization', () => {
  it('preserves RFC 7807 payloads returned by Axios errors', () => {
    const payload = {
      type: 'about:blank',
      title: 'Unprocessable Entity',
      status: 422,
      detail: 'Quantity must be positive.',
      errors: { quantity: ['must be greater than or equal to 1'] },
    }

    const problem = toProblemDetailDto({
      isAxiosError: true,
      response: { data: payload },
    })

    expect(problem).toEqual(payload)
    expect(isProblemDetailDto(problem)).toBe(true)
    expect(problemTitle({ isAxiosError: true, response: { data: payload } })).toBe('Unprocessable Entity')
    expect(problemDetail({ isAxiosError: true, response: { data: payload } })).toBe('Quantity must be positive.')
    expect(fieldErrors(problem)).toEqual({ quantity: ['must be greater than or equal to 1'] })
  })

  it('maps generic errors and global error arrays to user-facing fallbacks', () => {
    expect(toProblemDetailDto(new Error('Network unavailable'))).toEqual({
      title: 'Request failed',
      detail: 'Network unavailable',
    })

    expect(fieldErrors({ title: 'Bad request', errors: ['Invalid payload'] })).toEqual({
      _global: ['Invalid payload'],
    })
  })
})
