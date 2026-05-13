import { describe, expect, it } from 'vitest'
import { assetUrl } from '@/lib/assets'
import { discountPercent, formatMoney } from '@/lib/format'

describe('frontend shared helpers', () => {
  it('normalizes repository static asset paths for nested routes', () => {
    expect(assetUrl('static/assets/images/prod-images/support/hero.png')).toBe('/static/assets/images/prod-images/support/hero.png')
  })

  it('formats MDL amounts and discount percentages', () => {
    expect(formatMoney(100, 'MDL')).toContain('100')
    expect(discountPercent(100, 75)).toBe(25)
  })
})
