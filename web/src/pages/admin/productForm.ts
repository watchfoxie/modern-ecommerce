import { z } from 'zod'
import type { CategoryDto } from '@/contracts/category'
import type { ProductDto, UpsertProductRequest } from '@/contracts/product'

const PRODUCT_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const CURRENCY_PATTERN = /^[A-Z]{3}$/
const ASSET_OR_URL_PATTERN = /^(?:https?:\/\/|\/|static\/).+/i

export const PRODUCT_SPEC_KEYS = [
    'screenSize',
    'processor',
    'ram',
    'storage',
    'os',
    'battery',
    'camera',
    'gpu',
    'batteryLife',
] as const

export const PERSISTABLE_PRODUCT_CATEGORY_SLUGS = ['smartphones', 'laptops'] as const

const PRODUCT_SPEC_KEY_SET = new Set<string>(PRODUCT_SPEC_KEYS)
const PERSISTABLE_PRODUCT_CATEGORY_SET = new Set<string>(PERSISTABLE_PRODUCT_CATEGORY_SLUGS)

export const productFormSchema = z
    .object({
        categoryId: z.string().min(1, 'Selectează categoria produsului.'),
        slug: z
            .string()
            .trim()
            .min(2, 'Slug-ul trebuie să aibă cel puțin 2 caractere.')
            .max(180, 'Slug-ul nu poate depăși 180 de caractere.')
            .regex(PRODUCT_SLUG_PATTERN, 'Slug-ul trebuie să fie lowercase și compatibil URL.'),
        name: z.string().trim().min(2, 'Numele trebuie să aibă cel puțin 2 caractere.').max(160, 'Numele nu poate depăși 160 de caractere.'),
        brand: z.string().trim().min(2, 'Brandul trebuie să aibă cel puțin 2 caractere.').max(80, 'Brandul nu poate depăși 80 de caractere.'),
        model: z.string().trim().min(1, 'Modelul este obligatoriu.').max(120, 'Modelul nu poate depăși 120 de caractere.'),
        country: z.string().trim().min(2, 'Țara este obligatorie.').max(80, 'Țara nu poate depăși 80 de caractere.'),
        price: z.coerce.number().positive('Prețul trebuie să fie pozitiv.'),
        promotionalPrice: z.string().optional(),
        currency: z.string().trim().regex(CURRENCY_PATTERN, 'Moneda trebuie să fie un cod ISO din 3 litere mari.'),
        stock: z.coerce.number().int().min(0, 'Stocul nu poate fi negativ.'),
        imageUrlsText: z.string(),
        specsText: z.string(),
        isActive: z.boolean(),
    })
    .superRefine((values, context) => {
        const imageUrls = parseLines(values.imageUrlsText)
        if (imageUrls.length === 0) {
            context.addIssue({
                code: 'custom',
                path: ['imageUrlsText'],
                message: 'Adaugă cel puțin o imagine.',
            })
        }

        imageUrls.forEach((imageUrl, index) => {
            if (!ASSET_OR_URL_PATTERN.test(imageUrl)) {
                context.addIssue({
                    code: 'custom',
                    path: ['imageUrlsText'],
                    message: `Imaginea ${index + 1} trebuie să fie un URL valid sau o cale statică din proiect.`,
                })
            }
        })

        const parsedSpecs = parseSpecsText(values.specsText)
        if (Object.keys(parsedSpecs.specs).length === 0) {
            context.addIssue({
                code: 'custom',
                path: ['specsText'],
                message: 'Adaugă cel puțin o specificație în formatul cheie: valoare.',
            })
        }

        for (const issue of parsedSpecs.issues) {
            context.addIssue({
                code: 'custom',
                path: ['specsText'],
                message: issue,
            })
        }

        const promotionalPrice = parsePromotionalPrice(values.promotionalPrice, values.price)
        if (values.promotionalPrice?.trim() && promotionalPrice === 'invalid') {
            context.addIssue({
                code: 'custom',
                path: ['promotionalPrice'],
                message: 'Prețul promoțional trebuie să fie pozitiv și mai mic sau egal cu prețul standard.',
            })
        }
    })

export type ProductFormInput = z.input<typeof productFormSchema>
export type ProductFormValues = z.output<typeof productFormSchema>

type ParsedSpecsResult = {
    specs: Record<string, string>
    issues: string[]
}

export function emptyProductFormValues(categoryId = ''): ProductFormValues {
    return {
        categoryId,
        slug: '',
        name: '',
        brand: '',
        model: '',
        country: '',
        price: 1,
        promotionalPrice: '',
        currency: 'MDL',
        stock: 0,
        imageUrlsText: '',
        specsText: '',
        isActive: true,
    }
}

export function getSelectableProductCategories(categories: CategoryDto[]) {
    return categories.filter((category) => PERSISTABLE_PRODUCT_CATEGORY_SET.has(category.slug))
}

export function buildProductUpsertRequest(values: ProductFormValues, categories: CategoryDto[]): UpsertProductRequest {
    const category = categories.find((item) => item.id === values.categoryId)
    if (!category) {
        throw new Error('Categoria selectată nu mai este disponibilă.')
    }

    if (!PERSISTABLE_PRODUCT_CATEGORY_SET.has(category.slug)) {
        throw new Error('Categoria `Oferte` este virtuală și nu poate fi persistată direct pe produs.')
    }

    const parsedSpecs = parseSpecsText(values.specsText)
    const promotionalPrice = parsePromotionalPrice(values.promotionalPrice, values.price)
    if (promotionalPrice === 'invalid') {
        throw new Error('Prețul promoțional trebuie să fie pozitiv și mai mic sau egal cu prețul standard.')
    }

    return {
        categoryId: category.id,
        categorySlug: category.slug,
        slug: values.slug.trim(),
        name: values.name.trim(),
        brand: values.brand.trim(),
        model: values.model.trim(),
        country: values.country.trim(),
        price: values.price,
        promotionalPrice,
        currency: values.currency.trim(),
        stock: values.stock,
        imageUrls: parseLines(values.imageUrlsText),
        specs: parsedSpecs.specs,
        isActive: values.isActive,
    }
}

export function productToFormValues(product: ProductDto, categories: CategoryDto[]): ProductFormValues {
    const resolvedCategorySlug = resolvePersistableCategorySlug(product)
    const categoryId = categories.find((category) => category.slug === resolvedCategorySlug)?.id ?? ''

    return {
        categoryId,
        slug: product.slug,
        name: product.name,
        brand: product.brand,
        model: product.model,
        country: product.country,
        price: product.price,
        promotionalPrice: product.promotionalPrice?.toString() ?? '',
        currency: product.currency,
        stock: product.stock,
        imageUrlsText: product.imageUrls.join('\n'),
        specsText: stringifySpecs(product.specs),
        isActive: product.isActive,
    }
}

export function stringifySpecs(specs: Record<string, string>) {
    return Object.entries(specs)
        .map(([key, value]) => `${key}: ${value}`)
        .join('\n')
}

function parseLines(value: string) {
    return value
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
}

function parsePromotionalPrice(promotionalPrice: string | undefined, price: number) {
    if (!promotionalPrice?.trim()) {
        return null
    }

    const value = Number(promotionalPrice)
    if (!Number.isFinite(value) || value <= 0 || value > price) {
        return 'invalid' as const
    }

    if (value === price) {
        return null
    }

    return value
}

function parseSpecsText(value: string): ParsedSpecsResult {
    const specs: Record<string, string> = {}
    const issues: string[] = []

    parseLines(value).forEach((line, index) => {
        const [rawKey, ...rawValueParts] = line.split(/[:=]/)
        const key = rawKey?.trim() ?? ''
        const specValue = rawValueParts.join(':').trim()

        if (!key || !specValue) {
            issues.push(`Linia ${index + 1} trebuie să respecte formatul cheie: valoare.`)
            return
        }

        if (!PRODUCT_SPEC_KEY_SET.has(key)) {
            issues.push(`Cheia \`${key}\` nu este permisă. Folosește una dintre: ${PRODUCT_SPEC_KEYS.join(', ')}.`)
            return
        }

        if (specs[key]) {
            issues.push(`Cheia \`${key}\` este duplicată. Păstrează o singură valoare per specificație.`)
            return
        }

        specs[key] = specValue
    })

    return { specs, issues }
}

function resolvePersistableCategorySlug(product: ProductDto) {
    if (PERSISTABLE_PRODUCT_CATEGORY_SET.has(product.categorySlug)) {
        return product.categorySlug
    }

    const categoryHint = [product.slug, product.name, product.model, ...product.imageUrls]
        .join(' ')
        .toLowerCase()

    if (/\/laptops\/|\blaptop\b|\bnotebook\b|\bmatebook\b|\bmacbook\b|\bthinkpad\b|\bzenbook\b/.test(categoryHint)) {
        return 'laptops'
    }

    return 'smartphones'
}